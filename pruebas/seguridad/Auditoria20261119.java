package api;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.sun.net.httpserver.HttpServer;
import conexion.ConfiguracionBD;
import jakarta.servlet.ServletContext;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import logica.Administrador;
import logica.ValidacionException;
import modelo.AdministradorDAO;
import servicio.CifradoClaves;
import servicio.ClienteGemini;
import servicio.GeneracionFallidaException;
import servicio.Instalacion;
import servicio.ProbadorClaveIA;
import servicio.ResultadoGeneracion;
import servicio.Secretos;

/**
 * Pruebas de la auditoria del 19 de noviembre de 2026: bootstrap concurrente, secretos
 * persistentes, TLS, cabecera de Gemini y continuidad entre procesos.
 */
public final class Auditoria20261119 {

    private static final Path MIGRACIONES = Path.of("web/WEB-INF/sql/migraciones");
    private static final String BOOTSTRAP = "bootstrap-" + UUID.randomUUID() + "-" + UUID.randomUUID();
    private static int verificaciones;

    public static void main(String[] args) throws Exception {
        pruebasSecretos();
        pruebasTlsConfiguracion();
        pruebasGemini();
        pruebasContinuidadEntreProcesos();
        String servidor = System.getenv("EXAMLEN_TEST_SERVER");
        if (servidor == null) {
            System.out.println("OK: " + verificaciones
                    + " verificaciones sin BD. BD de bootstrap omitida: definir EXAMLEN_TEST_SERVER.");
            return;
        }
        pruebasBootstrap(servidor);
        pruebasInstalacionLimpia(servidor);
        pruebasActualizacionDesde021(servidor);
        pruebasTlsReal(servidor);
        pruebasTlsIdentidad(servidor);
        System.out.println("OK: " + verificaciones + " verificaciones de auditoria; BD temporales eliminadas.");
    }

    private static void pruebasSecretos() {
        String anteriorEntorno = System.getProperty("examlen.env");
        String anteriorEfimeras = System.getProperty("examlen.allowEphemeralSecrets");
        String anteriorApi = System.getProperty("examlen.api.token.secret");
        String anteriorOrden = System.getProperty("examlen.order.secret");
        String anteriorCifrado = System.getProperty("examlen.ia.crypto.key");
        String anteriorArchivo = System.getProperty("examlen.secrets.file");
        try {
            System.setProperty("examlen.secrets.file",
                    Path.of(System.getProperty("java.io.tmpdir"), "examlen-sin-secretos.properties").toString());
            System.clearProperty("examlen.api.token.secret");
            System.clearProperty("examlen.order.secret");
            System.setProperty("examlen.env", "produccion");
            System.clearProperty("examlen.allowEphemeralSecrets");
            boolean fallo = false;
            try {
                Secretos.validarConfiguracion();
            } catch (IllegalStateException e) {
                fallo = true;
                comprobar(!e.getMessage().contains("bootstrap-"), "Error de secreto sin exponer valores");
            }
            comprobar(fallo, "Produccion sin secreto de API falla al arrancar");

            System.setProperty("examlen.env", "desarrollo");
            System.setProperty("examlen.allowEphemeralSecrets", "true");
            Secretos.validarConfiguracion();
            byte[] primera = Secretos.claveApi();
            byte[] segunda = Secretos.claveApi();
            comprobar(MessageDigest.isEqual(primera, segunda), "Clave efimera estable en el mismo proceso");

            String api = "a".repeat(64);
            String orden = "b".repeat(64);
            System.setProperty("examlen.api.token.secret", api);
            System.setProperty("examlen.order.secret", orden);
            comprobar(MessageDigest.isEqual(Secretos.claveApi(), sha256(api)), "Secreto de API configurado");
            comprobar(!MessageDigest.isEqual(Secretos.claveOrden(), Secretos.claveApi()),
                    "Secreto de orden independiente");

            String claveCifrado = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
            System.setProperty("examlen.ia.crypto.key", claveCifrado);
            String cifrado = CifradoClaves.cifrar("clave-personal");
            comprobar("clave-personal".equals(CifradoClaves.descifrar(cifrado)), "Cifrado IA ida y vuelta");
            System.setProperty("examlen.ia.crypto.key",
                    Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8)));
            boolean claveIncorrecta = false;
            try {
                CifradoClaves.descifrar(cifrado);
            } catch (RuntimeException e) {
                claveIncorrecta = true;
            }
            comprobar(claveIncorrecta, "Cifrado IA rechaza otra clave");
        } finally {
            restaurar("examlen.env", anteriorEntorno);
            restaurar("examlen.allowEphemeralSecrets", anteriorEfimeras);
            restaurar("examlen.api.token.secret", anteriorApi);
            restaurar("examlen.order.secret", anteriorOrden);
            restaurar("examlen.ia.crypto.key", anteriorCifrado);
            restaurar("examlen.secrets.file", anteriorArchivo);
        }
    }

    private static void pruebasTlsConfiguracion() {
        String anteriorUrl = System.getProperty("examlen.db.url");
        String anteriorUsuario = System.getProperty("examlen.db.user");
        String anteriorClave = System.getProperty("examlen.db.password");
        String anteriorSsl = System.getProperty("examlen.db.sslMode");
        String anteriorDev = System.getProperty("examlen.db.devMode");
        try {
            System.setProperty("examlen.db.url", "jdbc:mysql://db.ejemplo.com:3306/examlen");
            System.setProperty("examlen.db.user", "usuario_prueba");
            System.setProperty("examlen.db.password", "clave_prueba");
            System.clearProperty("examlen.db.sslMode");
            System.clearProperty("examlen.db.devMode");
            ConfiguracionBD seguro = ConfiguracionBD.resolver();
            comprobar("VERIFY_IDENTITY".equals(seguro.sslMode()), "TLS por defecto VERIFY_IDENTITY");
            comprobar(seguro.urlBase().contains("sslMode=VERIFY_IDENTITY"), "URL con TLS verificado");
            comprobar(!seguro.urlBase().contains("allowPublicKeyRetrieval"), "Sin allowPublicKeyRetrieval");
            comprobar(!seguro.urlBase().contains("allowMultiQueries"), "App sin allowMultiQueries");
            comprobar(seguro.urlInicializador().contains("allowMultiQueries=true"),
                    "Inicializador con allowMultiQueries");

            System.setProperty("examlen.db.url", "jdbc:mysql://127.0.0.1:3306/examlen");
            System.setProperty("examlen.db.sslMode", "REQUIRED");
            System.setProperty("examlen.db.devMode", "true");
            comprobar("REQUIRED".equals(ConfiguracionBD.resolver().sslMode()), "Desarrollo local explicito REQUIRED");

            System.setProperty("examlen.db.url", "jdbc:mysql://db.ejemplo.com:3306/examlen");
            boolean rechazo = false;
            try {
                ConfiguracionBD.resolver();
            } catch (IllegalArgumentException e) {
                rechazo = true;
            }
            comprobar(rechazo, "REQUIRED no se admite fuera de loopback");

            System.setProperty("examlen.db.url", "jdbc:mysql://127.0.0.1:3306/examlen");
            System.setProperty("examlen.db.sslMode", "PREFERRED");
            rechazo = false;
            try {
                ConfiguracionBD.resolver();
            } catch (IllegalArgumentException e) {
                rechazo = true;
            }
            comprobar(rechazo, "PREFERRED rechazado");
        } finally {
            restaurar("examlen.db.url", anteriorUrl);
            restaurar("examlen.db.user", anteriorUsuario);
            restaurar("examlen.db.password", anteriorClave);
            restaurar("examlen.db.sslMode", anteriorSsl);
            restaurar("examlen.db.devMode", anteriorDev);
        }
    }

    private static void pruebasGemini() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> consultas = new ArrayList<>();
        List<String> claves = new ArrayList<>();
        servidor.createContext("/v1beta/models", intercambio -> {
            consultas.add(intercambio.getRequestURI().getQuery());
            claves.add(intercambio.getRequestHeaders().getFirst("x-goog-api-key"));
            responder(intercambio, 200, "{\"models\":[]}");
        });
        servidor.createContext("/v1beta/models/modelo:generateContent", intercambio -> {
            consultas.add(intercambio.getRequestURI().getQuery());
            claves.add(intercambio.getRequestHeaders().getFirst("x-goog-api-key"));
            String texto = "[{\"enunciado\":\"Pregunta\",\"tipo\":\"SELECCION_MULTIPLE\"}]";
            String cuerpo = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                    + json(texto) + "}]}}]}";
            responder(intercambio, 200, cuerpo);
        });
        servidor.start();
        String anterior = System.getProperty("examlen.gemini.baseUrl");
        try {
            System.setProperty("examlen.gemini.baseUrl",
                    "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta");
            ProbadorClaveIA.probar("GEMINI", "clave-prueba");
            comprobar(consultas.get(0) == null && "clave-prueba".equals(claves.get(0)),
                    "Gemini probador usa x-goog-api-key y no ?key=");

            ResultadoGeneracion resultado = new ClienteGemini("modelo", "clave-prueba")
                    .generarPreguntas("contexto", "instrucciones", 1, "A1");
            comprobar(resultado.elementos().size() == 1, "Gemini cliente procesa respuesta");
            comprobar(consultas.get(1) == null && "clave-prueba".equals(claves.get(1)),
                    "Gemini cliente usa cabecera y no URL");

            boolean rechazo = false;
            try {
                ProbadorClaveIA.probar("GEMINI", " ");
            } catch (GeneracionFallidaException e) {
                rechazo = true;
            }
            comprobar(rechazo, "Gemini rechaza clave vacia");
        } finally {
            restaurar("examlen.gemini.baseUrl", anterior);
            servidor.stop(0);
        }
    }

    private static void pruebasContinuidadEntreProcesos() throws Exception {
        String anterior = System.getProperty("examlen.api.token.secret");
        String secreto = "s".repeat(64);
        try {
            System.setProperty("examlen.api.token.secret", secreto);
            ApiTokens.Token token = ApiTokens.emitir(7,
                    System.currentTimeMillis() / 1000L + 600, UUID.randomUUID().toString());
            comprobar("OK".equals(ejecutarProceso(token.valor, secreto)), "Mismo secreto entre procesos");
            comprobar("NO".equals(ejecutarProceso(token.valor, "t".repeat(64))),
                    "Secreto distinto rechaza token entre procesos");
        } finally {
            restaurar("examlen.api.token.secret", anterior);
        }
    }

    private static void pruebasBootstrap(String servidor) throws Exception {
        String base = "examlen_boot_" + UUID.randomUUID().toString().replace("-", "");
        String usuario = System.getenv().getOrDefault("EXAMLEN_TEST_USER", "root");
        String clave = System.getenv().getOrDefault("EXAMLEN_TEST_PASSWORD", "");
        String url = servidor + base + "?allowMultiQueries=true&useSSL=false";
        try (Connection servidorC = DriverManager.getConnection(servidor, usuario, clave)) {
            servidorC.createStatement().execute("CREATE DATABASE " + base
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try (Connection c = DriverManager.getConnection(url, usuario, clave)) {
                aplicarMigraciones(c);
                MysqlDataSource fuente = new MysqlDataSource();
                fuente.setURL(url);
                fuente.setUser(usuario);
                fuente.setPassword(clave);
                AdministradorDAO dao = new AdministradorDAO(fuente);

                System.setProperty("examlen.bootstrap.secret", BOOTSTRAP);
                comprobar(Instalacion.habilitada(), "Bootstrap habilitado con credencial");
                comprobar(Instalacion.verificar(BOOTSTRAP), "Credencial de bootstrap correcta");
                comprobar(!Instalacion.verificar("incorrecta"), "Credencial de bootstrap incorrecta");

                ExecutorService pool = Executors.newFixedThreadPool(8);
                int creados = 0;
                try {
                    CountDownLatch listo = new CountDownLatch(8);
                    CountDownLatch salida = new CountDownLatch(1);
                    List<Future<Boolean>> futuros = new ArrayList<>();
                    for (int i = 0; i < 8; i++) {
                        final int numero = i;
                        futuros.add(pool.submit(() -> {
                            listo.countDown();
                            salida.await(10, TimeUnit.SECONDS);
                            try {
                                dao.crearPrimerAdministrador("BOOT" + numero, "Bootstrap " + numero,
                                        "boot" + numero + "@test.invalid", "Clave123", "", null);
                                return Boolean.TRUE;
                            } catch (ValidacionException e) {
                                return Boolean.FALSE;
                            }
                        }));
                    }
                    listo.await(10, TimeUnit.SECONDS);
                    salida.countDown();
                    for (Future<Boolean> futuro : futuros) {
                        if (futuro.get(30, TimeUnit.SECONDS)) {
                            creados++;
                        }
                    }
                } finally {
                    pool.shutdownNow();
                }
                comprobar(creados == 1, "Solo una peticion concurrente crea el primer administrador");
                comprobar(numero(c, "SELECT COUNT(*) FROM administrador") == 1,
                        "La BD conserva exactamente un administrador inicial");

                boolean segundo = false;
                try {
                    dao.crearPrimerAdministrador("BOOTX", "Bootstrap X", "bootx@test.invalid",
                            "Clave123", "", null);
                } catch (ValidacionException e) {
                    segundo = true;
                }
                comprobar(segundo, "Segundo primer administrador rechazado");

                System.clearProperty("examlen.bootstrap.secret");
                comprobar(!Instalacion.habilitada(), "Bootstrap deshabilitado sin credencial");
            } finally {
                servidorC.createStatement().execute("DROP DATABASE IF EXISTS " + base);
            }
        } finally {
            System.clearProperty("examlen.bootstrap.secret");
        }
    }

    private static void pruebasTlsReal(String servidor) throws Exception {
        String usuario = System.getenv().getOrDefault("EXAMLEN_TEST_USER", "root");
        String clave = System.getenv().getOrDefault("EXAMLEN_TEST_PASSWORD", "");
        MysqlDataSource fuente = new MysqlDataSource();
        fuente.setURL(servidor + "?sslMode=REQUIRED");
        fuente.setUser(usuario);
        fuente.setPassword(clave);
        try (Connection c = fuente.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW STATUS LIKE 'Ssl_cipher'")) {
            comprobar(rs.next() && rs.getString(2) != null && !rs.getString(2).isBlank(),
                    "Conexion MySQL cifrada con REQUIRED (sin validar identidad)");
        }
    }

    /**
     * VERIFY_IDENTITY real: exige un truststore con la CA temporal y un certificado con SAN
     * localhost/127.0.0.1. Sin truststore, la misma conexion debe fallar.
     */
    private static void pruebasTlsIdentidad(String servidor) throws Exception {
        String truststore = System.getenv("EXAMLEN_TEST_TRUSTSTORE");
        if (truststore == null || truststore.isBlank()) {
            System.out.println("OMITIDO: VERIFY_IDENTITY real sin EXAMLEN_TEST_TRUSTSTORE.");
            return;
        }
        String usuario = System.getenv().getOrDefault("EXAMLEN_TEST_TLS_USER", "root");
        String clave = System.getenv().getOrDefault("EXAMLEN_TEST_TLS_PASSWORD", "");
        String claveTruststore = System.getenv().getOrDefault("EXAMLEN_TEST_TRUSTSTORE_PASSWORD", "changeit");

        MysqlDataSource verificado = new MysqlDataSource();
        verificado.setURL(servidor + "?sslMode=VERIFY_IDENTITY");
        verificado.setUser(usuario);
        verificado.setPassword(clave);
        verificado.setTrustCertificateKeyStoreUrl("file:" + truststore);
        verificado.setTrustCertificateKeyStorePassword(claveTruststore);
        verificado.setTrustCertificateKeyStoreType("PKCS12");
        try (Connection c = verificado.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW STATUS LIKE 'Ssl_cipher'")) {
            comprobar(rs.next() && rs.getString(2) != null && !rs.getString(2).isBlank(),
                    "Conexion VERIFY_IDENTITY con CA temporal y SAN");
        }

        MysqlDataSource sinConfianza = new MysqlDataSource();
        sinConfianza.setURL(servidor + "?sslMode=VERIFY_IDENTITY");
        sinConfianza.setUser(usuario);
        sinConfianza.setPassword(clave);
        boolean rechazo = false;
        try (Connection c = sinConfianza.getConnection()) {
            c.isValid(1);
        } catch (Exception e) {
            rechazo = true;
        }
        comprobar(rechazo, "VERIFY_IDENTITY rechaza CA no confiable");
    }

    private static void pruebasInstalacionLimpia(String servidor) throws Exception {
        String base = "examlen_fresh_" + UUID.randomUUID().toString().replace("-", "");
        String usuario = System.getenv().getOrDefault("EXAMLEN_TEST_USER", "root");
        String clave = System.getenv().getOrDefault("EXAMLEN_TEST_PASSWORD", "");
        String anteriorUrl = System.getProperty("examlen.db.url");
        String anteriorUsuario = System.getProperty("examlen.db.user");
        String anteriorClave = System.getProperty("examlen.db.password");
        String anteriorSsl = System.getProperty("examlen.db.sslMode");
        String anteriorDev = System.getProperty("examlen.db.devMode");
        try (Connection servidorC = DriverManager.getConnection(servidor, usuario, clave)) {
            boolean examlenPrevio = existeBase(servidorC, "examlen");
            try {
                System.setProperty("examlen.db.url", servidor + base);
                System.setProperty("examlen.db.user", usuario);
                System.setProperty("examlen.db.password", clave);
                System.setProperty("examlen.db.sslMode", "REQUIRED");
                System.setProperty("examlen.db.devMode", "true");
                modelo.InicializadorEsquema.inicializar(contexto());
                comprobar(modelo.InicializadorEsquema.consultarDisponibilidadEsquema(),
                        "Instalacion limpia aplica 001-023");
                modelo.InicializadorEsquema.inicializar(contexto());
                comprobar(modelo.InicializadorEsquema.consultarDisponibilidadEsquema(),
                        "Segundo arranque limpio no repite migraciones");
                try (Connection c = DriverManager.getConnection(
                        servidor + base + "?useSSL=false", usuario, clave)) {
                    comprobar(numero(c, "SELECT COUNT(*) FROM esquema_migracion") == 23,
                            "Instalacion limpia registra las 23 migraciones");
                    comprobar(numero(c, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='usuario' AND COLUMN_NAME='tipo'") == 0,
                            "Instalacion limpia sin usuario.tipo");
                    comprobar(numero(c, "SELECT COUNT(*) FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='token_api'") == 1,
                            "Instalacion limpia con token_api");
                }
            } finally {
                restaurar("examlen.db.url", anteriorUrl);
                restaurar("examlen.db.user", anteriorUsuario);
                restaurar("examlen.db.password", anteriorClave);
                restaurar("examlen.db.sslMode", anteriorSsl);
                restaurar("examlen.db.devMode", anteriorDev);
                servidorC.createStatement().execute("DROP DATABASE IF EXISTS " + base);
                if (!examlenPrevio) {
                    servidorC.createStatement().execute("DROP DATABASE IF EXISTS examlen");
                }
            }
        }
    }

    private static void pruebasActualizacionDesde021(String servidor) throws Exception {
        String base = "examlen_up_" + UUID.randomUUID().toString().replace("-", "");
        String usuario = System.getenv().getOrDefault("EXAMLEN_TEST_USER", "root");
        String clave = System.getenv().getOrDefault("EXAMLEN_TEST_PASSWORD", "");
        String anteriorUrl = System.getProperty("examlen.db.url");
        String anteriorUsuario = System.getProperty("examlen.db.user");
        String anteriorClave = System.getProperty("examlen.db.password");
        String anteriorSsl = System.getProperty("examlen.db.sslMode");
        String anteriorDev = System.getProperty("examlen.db.devMode");
        try (Connection servidorC = DriverManager.getConnection(servidor, usuario, clave)) {
            servidorC.createStatement().execute("CREATE DATABASE " + base
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try (Connection c = DriverManager.getConnection(
                    servidor + base + "?allowMultiQueries=true&useSSL=false", usuario, clave)) {
                aplicarHasta(c, "021_clave_ia_docente.sql");
            }
            try {
                System.setProperty("examlen.db.url", servidor + base);
                System.setProperty("examlen.db.user", usuario);
                System.setProperty("examlen.db.password", clave);
                System.setProperty("examlen.db.sslMode", "REQUIRED");
                System.setProperty("examlen.db.devMode", "true");
                modelo.InicializadorEsquema.inicializar(contexto());
                comprobar(modelo.InicializadorEsquema.consultarDisponibilidadEsquema(),
                        "Actualizacion desde 021 aplica 022 y 023");
                try (Connection c = DriverManager.getConnection(
                        servidor + base + "?useSSL=false", usuario, clave)) {
                    comprobar(numero(c, "SELECT COUNT(*) FROM esquema_migracion") == 23,
                            "Actualizacion registra 022 y 023");
                    comprobar(numero(c, "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='usuario' AND COLUMN_NAME='tipo'") == 0,
                            "Actualizacion elimina usuario.tipo");
                    comprobar(numero(c, "SELECT COUNT(*) FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='token_api'") == 1,
                            "Actualizacion crea token_api");
                }
            } finally {
                restaurar("examlen.db.url", anteriorUrl);
                restaurar("examlen.db.user", anteriorUsuario);
                restaurar("examlen.db.password", anteriorClave);
                restaurar("examlen.db.sslMode", anteriorSsl);
                restaurar("examlen.db.devMode", anteriorDev);
                servidorC.createStatement().execute("DROP DATABASE IF EXISTS " + base);
            }
        }
    }

    private static void aplicarMigraciones(Connection c) throws Exception {
        aplicarHasta(c, null);
    }

    private static void aplicarHasta(Connection c, String ultima) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE esquema_migracion (nombre VARCHAR(150) PRIMARY KEY, "
                    + "aplicada_en DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }
        for (String nombre : Files.readAllLines(MIGRACIONES.resolve("indice.txt"))) {
            if (nombre.isBlank() || nombre.startsWith("#")) {
                continue;
            }
            if (ultima != null && nombre.compareTo(ultima) > 0) {
                break;
            }
            String script = limpiar(Files.readString(MIGRACIONES.resolve(nombre)));
            try (Statement st = c.createStatement()) {
                boolean resultado = st.execute(script);
                while (true) {
                    if (resultado) {
                        try (ResultSet rs = st.getResultSet()) {
                            while (rs.next()) { /* consumir */ }
                        }
                    } else if (st.getUpdateCount() == -1) {
                        break;
                    }
                    resultado = st.getMoreResults();
                }
            }
            try (var ps = c.prepareStatement("INSERT INTO esquema_migracion (nombre) VALUES (?)")) {
                ps.setString(1, nombre);
                ps.executeUpdate();
            }
        }
    }

    private static boolean existeBase(Connection c, String base) throws Exception {
        try (var ps = c.prepareStatement("SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, base);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static ServletContext contexto() {
        return (ServletContext) Proxy.newProxyInstance(
                ServletContext.class.getClassLoader(), new Class<?>[]{ServletContext.class},
                (proxy, metodo, args) -> {
                    if (metodo.getName().equals("getResourceAsStream") && args != null && args.length == 1) {
                        return Files.newInputStream(Path.of("web" + args[0]));
                    }
                    return null;
                });
    }

    private static String limpiar(String script) {
        return script.replaceAll("(?m)^\\s*--.*$", "")
                .replaceAll("(?is)CREATE DATABASE.*?;", "")
                .replaceAll("(?is)CREATE USER.*?;", "")
                .replaceAll("(?is)GRANT .*?;", "")
                .replaceAll("(?is)FLUSH PRIVILEGES;", "")
                .replaceAll("(?i)USE examlen;", "");
    }

    private static String ejecutarProceso(String token, String secreto) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        ProcessBuilder constructor = new ProcessBuilder(java, "-Dexamlen.env=desarrollo",
                "-cp", System.getProperty("java.class.path"), "api.ProcesoSecreto", token);
        constructor.environment().put("EXAMLEN_API_TOKEN_SECRET", secreto);
        constructor.redirectErrorStream(true);
        Process proceso = constructor.start();
        String salida;
        try (InputStream entrada = proceso.getInputStream()) {
            salida = new String(entrada.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        proceso.waitFor(30, TimeUnit.SECONDS);
        return salida;
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, int estado, String cuerpo)
            throws IOException {
        byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        intercambio.sendResponseHeaders(estado, datos.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(datos);
        }
    }

    private static String json(String texto) {
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static byte[] sha256(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int numero(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void comprobar(boolean valido, String caso) {
        verificaciones++;
        if (!valido) {
            throw new AssertionError("FALLO: " + caso);
        }
        System.out.println("OK " + verificaciones + ": " + caso);
    }

    private static void restaurar(String propiedad, String valor) {
        if (valor == null) {
            System.clearProperty(propiedad);
        } else {
            System.setProperty(propiedad, valor);
        }
    }
}
