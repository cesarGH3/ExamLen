package conexion;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuracion de conexion a MySQL para la aplicacion y para el inicializador.
 *
 * Orden de resolucion por campo: variable de entorno -> propiedad del sistema ->
 * archivo examlen.properties incluido en WEB-INF/classes -> archivo
 * {catalina.base}/conf/catalina.properties -> valor por defecto (solo host,
 * puerto y nombre de base admiten defecto). La contrasena nunca tiene valor por
 * defecto y jamas se registra en logs.
 *
 * TLS: por defecto sslMode=VERIFY_IDENTITY. En produccion solo se admite esa
 * modalidad; REQUIRED queda reservado para desarrollo local en loopback y debe
 * declararse explicitamente con examlen.db.sslMode=REQUIRED y examlen.db.devMode=true.
 * allowPublicKeyRetrieval no se usa. allowMultiQueries solo se activa en la
 * conexion interna del inicializador.
 */
public final class ConfiguracionBD {

    private static final String PROPIEDAD_URL = "examlen.db.url";
    private static final String PROPIEDAD_USUARIO = "examlen.db.user";
    private static final String PROPIEDAD_CONTRASENA = "examlen.db.password";
    private static final String PROPIEDAD_SSL_MODE = "examlen.db.sslMode";
    private static final String PROPIEDAD_TRUSTSTORE = "examlen.db.trustStore";
    private static final String PROPIEDAD_TRUSTSTORE_PASSWORD = "examlen.db.trustStorePassword";
    private static final String PROPIEDAD_TRUSTSTORE_TYPE = "examlen.db.trustStoreType";
    private static final String PROPIEDAD_DESARROLLO = "examlen.db.devMode";
    private static final String VARIABLE_SSL_MODE = "DB_SSL_MODE";
    private static final String VARIABLE_TRUSTSTORE = "DB_TRUSTSTORE";
    private static final String VARIABLE_TRUSTSTORE_PASSWORD = "DB_TRUSTSTORE_PASSWORD";
    private static final String VARIABLE_TRUSTSTORE_TYPE = "DB_TRUSTSTORE_TYPE";
    private static final String VARIABLE_DESARROLLO = "EXAMLEN_DB_DEV";
    private static final String ARCHIVO_APP = "examlen.properties";
    private static final String SSL_POR_DEFECTO = "VERIFY_IDENTITY";
    private static final String SSL_DESARROLLO = "REQUIRED";

    private static final Properties PROPIEDADES_APP = leerPropiedadesApp();
    private static final String HOST_POR_DEFECTO = "localhost";
    private static final int PUERTO_POR_DEFECTO = 3306;
    private static final String BASE_POR_DEFECTO = "examlen";

    private static final Pattern BASE_VALIDA = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern URL_JDBC = Pattern.compile("jdbc:mysql://([^:/]+):(\\d+)/([^?]+)(?:\\?.*)?");

    private final String host;
    private final int puerto;
    private final String base;
    private final String usuario;
    private final String contrasena;
    private final String sslMode;
    private final String trustStore;
    private final String trustStorePassword;
    private final String trustStoreType;
    private final boolean desarrollo;

    private ConfiguracionBD(String host, int puerto, String base, String usuario, String contrasena,
                            String sslMode, String trustStore, String trustStorePassword,
                            String trustStoreType, boolean desarrollo) {
        this.host = host;
        this.puerto = puerto;
        this.base = base;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.sslMode = sslMode;
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
        this.trustStoreType = trustStoreType;
        this.desarrollo = desarrollo;
    }

    public static ConfiguracionBD resolver() {
        Properties catalina = leerCatalinaProperties();

        String url = primero(System.getProperty(PROPIEDAD_URL), PROPIEDADES_APP.getProperty(PROPIEDAD_URL),
                catalina.getProperty(PROPIEDAD_URL));
        String hostUrl = null;
        String puertoUrl = null;
        String baseUrl = null;
        if (url != null) {
            Matcher m = URL_JDBC.matcher(url.trim());
            if (m.matches()) {
                hostUrl = m.group(1);
                puertoUrl = m.group(2);
                baseUrl = m.group(3);
            }
        }

        String host = primero(System.getenv("DB_HOST"), hostUrl, HOST_POR_DEFECTO);
        String puertoTexto = primero(System.getenv("DB_PORT"), puertoUrl);
        String base = primero(System.getenv("DB_NAME"), baseUrl, BASE_POR_DEFECTO);

        if (!BASE_VALIDA.matcher(base).matches()) {
            throw new IllegalArgumentException("El nombre de la base de datos no es un identificador valido (A-Z, a-z, 0-9, _): " + base);
        }

        String usuario = primero(System.getenv("DB_USER"), System.getProperty(PROPIEDAD_USUARIO),
                PROPIEDADES_APP.getProperty(PROPIEDAD_USUARIO), catalina.getProperty(PROPIEDAD_USUARIO));
        String contrasena = primero(System.getenv("DB_PASSWORD"), System.getProperty(PROPIEDAD_CONTRASENA),
                PROPIEDADES_APP.getProperty(PROPIEDAD_CONTRASENA), catalina.getProperty(PROPIEDAD_CONTRASENA));

        int puerto = PUERTO_POR_DEFECTO;
        if (puertoTexto != null && !puertoTexto.isBlank()) {
            try {
                puerto = Integer.parseInt(puertoTexto.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El puerto de MySQL no es numerico: " + puertoTexto);
            }
        }

        String sslMode = normalizarSsl(primero(System.getenv(VARIABLE_SSL_MODE),
                System.getProperty(PROPIEDAD_SSL_MODE), catalina.getProperty(PROPIEDAD_SSL_MODE),
                SSL_POR_DEFECTO));
        boolean desarrollo = booleano(primero(System.getenv(VARIABLE_DESARROLLO),
                System.getProperty(PROPIEDAD_DESARROLLO), catalina.getProperty(PROPIEDAD_DESARROLLO)), false);
        validarSsl(sslMode, desarrollo, host);

        String trustStore = primero(System.getenv(VARIABLE_TRUSTSTORE),
                System.getProperty(PROPIEDAD_TRUSTSTORE), catalina.getProperty(PROPIEDAD_TRUSTSTORE));
        String trustStorePassword = primero(System.getenv(VARIABLE_TRUSTSTORE_PASSWORD),
                System.getProperty(PROPIEDAD_TRUSTSTORE_PASSWORD),
                catalina.getProperty(PROPIEDAD_TRUSTSTORE_PASSWORD));
        String trustStoreType = primero(System.getenv(VARIABLE_TRUSTSTORE_TYPE),
                System.getProperty(PROPIEDAD_TRUSTSTORE_TYPE),
                catalina.getProperty(PROPIEDAD_TRUSTSTORE_TYPE), "JKS");

        return new ConfiguracionBD(host, puerto, base, usuario, contrasena, sslMode,
                normalizarRuta(trustStore), trustStorePassword, trustStoreType, desarrollo);
    }

    private static void validarSsl(String sslMode, boolean desarrollo, String host) {
        if (SSL_POR_DEFECTO.equals(sslMode)) {
            return;
        }
        if (SSL_DESARROLLO.equals(sslMode)) {
            if (!desarrollo || !esLoopback(host)) {
                throw new IllegalArgumentException("sslMode=REQUIRED solo se admite en desarrollo local "
                        + "(examlen.db.devMode=true y host loopback). Usa VERIFY_IDENTITY en produccion.");
            }
            return;
        }
        throw new IllegalArgumentException("sslMode no admitido: " + sslMode
                + ". Usa VERIFY_IDENTITY (o REQUIRED solo para desarrollo local en loopback).");
    }

    private static String normalizarSsl(String valor) {
        return valor == null ? SSL_POR_DEFECTO : valor.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean esLoopback(String host) {
        if (host == null) {
            return false;
        }
        String normalizado = host.trim().toLowerCase(Locale.ROOT);
        return normalizado.equals("localhost") || normalizado.equals("127.0.0.1")
                || normalizado.equals("::1") || normalizado.equals("0:0:0:0:0:0:0:1");
    }

    private static String normalizarRuta(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        String valor = ruta.trim();
        return (valor.startsWith("file:") || valor.startsWith("classpath:")) ? valor : "file:" + valor;
    }

    /** true si hay usuario y contrasena configurados (sin ellos no se intenta inicializar). */
    public boolean hayCredenciales() {
        return usuario != null && !usuario.isBlank() && contrasena != null;
    }

    public String host() { return host; }

    public int puerto() { return puerto; }

    public String base() { return base; }

    public String usuario() { return usuario; }

    public String contrasena() { return contrasena; }

    public String sslMode() { return sslMode; }

    public boolean desarrollo() { return desarrollo; }

    /** Descripcion sin contrasena, apta para logs. */
    public String descripcion() {
        return host + ":" + puerto + "/" + base + " (usuario " + usuario + ", TLS " + sslMode + ")";
    }

    /** URL sin base de datos, sin allowMultiQueries: uso de la aplicacion. */
    public String urlServidor() {
        return construirUrl("", false);
    }

    /** URL con base de datos, sin allowMultiQueries: uso de la aplicacion. */
    public String urlBase() {
        return construirUrl("/" + base, false);
    }

    /** URL del inicializador, que si necesita varias sentencias por script. */
    public String urlInicializador() {
        return construirUrl("/" + base, true);
    }

    /** Fuente de la aplicacion: sin allowMultiQueries y con el truststore configurado. */
    public MysqlDataSource fuenteAplicacion() {
        return crearFuente("/" + base, false);
    }

    /** Fuente del inicializador: allowMultiQueries solo en esta conexion interna. */
    public MysqlDataSource fuenteInicializador(boolean conBase) {
        return crearFuente(conBase ? "/" + base : "", true);
    }

    private MysqlDataSource crearFuente(String rutaBase, boolean multiSentencias) {
        MysqlDataSource fuente = new MysqlDataSource();
        fuente.setURL(construirUrl(rutaBase, multiSentencias));
        fuente.setUser(usuario);
        fuente.setPassword(contrasena);
        if (trustStore != null) {
            try {
                fuente.setTrustCertificateKeyStoreUrl(trustStore);
                if (trustStorePassword != null) {
                    fuente.setTrustCertificateKeyStorePassword(trustStorePassword);
                }
                fuente.setTrustCertificateKeyStoreType(trustStoreType);
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("No se pudo configurar el truststore de MySQL.", e);
            }
        }
        return fuente;
    }

    private String construirUrl(String rutaBase, boolean multiSentencias) {
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(host).append(':').append(puerto).append(rutaBase)
                .append("?sslMode=").append(sslMode)
                .append("&connectionTimeZone=America/Bogota")
                .append("&sessionVariables=time_zone='-05:00'")
                .append("&connectTimeout=3000&socketTimeout=10000&characterEncoding=UTF-8");
        if (multiSentencias) {
            url.append("&allowMultiQueries=true");
        }
        return url.toString();
    }

    private static boolean booleano(String valor, boolean porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : Boolean.parseBoolean(valor.trim());
    }

    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }

    private static Properties leerPropiedadesApp() {
        Properties propiedades = new Properties();
        try (InputStream entrada = ConfiguracionBD.class.getClassLoader().getResourceAsStream(ARCHIVO_APP)) {
            if (entrada != null) {
                propiedades.load(entrada);
            }
        } catch (IOException e) {
            // Sin archivo de aplicacion se conserva el mecanismo externo de Tomcat.
        }
        return propiedades;
    }

    private static Properties leerCatalinaProperties() {
        Properties propiedades = new Properties();
        String cat = System.getProperty("catalina.base");
        if (cat == null) {
            cat = System.getProperty("catalina.home");
        }
        if (cat == null) {
            return propiedades;
        }
        Path archivo = Path.of(cat, "conf", "catalina.properties");
        if (!Files.isReadable(archivo)) {
            return propiedades;
        }
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
        } catch (IOException e) {
            // Sin archivo legible simplemente se continua con entorno/propiedades/defectos.
        }
        return propiedades;
    }
}
