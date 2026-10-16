package modelo;

import conexion.ConfiguracionBD;
import jakarta.servlet.ServletContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Inicializacion automatica del esquema de ExamLen al arrancar la aplicacion:
 * crea la base de datos si no existe, aplica las migraciones pendientes de
 * WEB-INF/sql/migraciones (una sola vez, en orden, con tabla tecnica
 * esquema_migracion) y reconoce como baseline una base existente que ya coincide
 * con el esquema final. Nunca copia datos academicos y nunca altera una base
 * existente que no pueda identificarse con seguridad.
 */
public final class InicializadorEsquema {

    private static final Logger LOG = Logger.getLogger(InicializadorEsquema.class.getName());

    private static final String CARPETA_MIGRACIONES = "/WEB-INF/sql/migraciones/";
    private static final String INDICE = CARPETA_MIGRACIONES + "indice.txt";
    private static final String TABLA_CONTROL = "esquema_migracion";
    private static final int FK_ESPERADAS = 47;
    private static final int MIGRACIONES_ESPERADAS = 17;

    private static final List<String> TABLAS_ESPERADAS = List.of(
            "administrador", "banco_preguntas", "curso", "curso_material", "docente", "documento_fuente",
            "estudiante", "evaluacion", "evaluacion_pregunta", "evaluacion_virtual", "evaluacion_virtual_version",
            "evento_supervision", "grupo", "inscripcion", "intento", "intento_practica", "material_practica",
            "material_pregunta", "opcion_respuesta", "pregunta", "respuesta", "respuesta_practica",
            "solicitud_generacion", "token_recuperacion", "usuario", "version_evaluacion", "version_opcion",
            "version_pregunta");

    /** Columnas representativas del esquema final (verificacion de baseline). */
    private static final List<String[]> COLUMNAS_CLAVE = List.of(
            new String[]{"usuario", "identificacion"},
            new String[]{"usuario", "correo"},
            new String[]{"usuario", "contrasena_hash"},
            new String[]{"usuario", "activo"},
            new String[]{"usuario", "debe_cambiar_contrasena"},
            new String[]{"evaluacion", "titulo"},
            new String[]{"evaluacion", "descripcion"},
            new String[]{"version_pregunta", "puntaje"},
            new String[]{"solicitud_generacion", "cantidad_valida"});

    private static final Pattern USE_HARDCODEADO = Pattern.compile("(?mi)^\\s*USE\\s+examlen\\s*;");

    private InicializadorEsquema() {
    }

    /**
     * Punto de entrada: se invoca al arrancar la aplicacion. Un fallo aqui nunca
     * impide el arranque (la verificacion de disponibilidad existente mantiene el 503).
     */
    public static void inicializar(ServletContext contexto) {
        try {
            ejecutar(contexto);
        } catch (Exception e) {
            LOG.severe("No fue posible inicializar el esquema automaticamente: " + e.getMessage()
                    + ". La aplicacion continuara y respondera 503 hasta que MySQL quede disponible.");
        }
    }

    private static void ejecutar(ServletContext contexto) throws SQLException, IOException {
        ConfiguracionBD config = ConfiguracionBD.resolver();
        if (!config.hayCredenciales()) {
            LOG.warning("Inicializacion de esquema omitida: configura examlen.db.user y examlen.db.password en "
                    + "{catalina.base}/conf/catalina.properties (ver config/catalina.properties.ejemplo) "
                    + "o las variables DB_USER / DB_PASSWORD. Detalle en README-ENTREGA.md.");
            return;
        }

        cargarDriver();
        try (Connection servidor = DriverManager.getConnection(config.urlServidor(), config.usuario(), config.contrasena())) {
            if (!existeBase(servidor, config.base())) {
                crearBase(servidor, config.base());
                LOG.info("Base de datos creada: " + config.base() + " en " + config.host() + ":" + config.puerto());
            }
        } catch (SQLException e) {
            if (esErrorDePermisos(e)) {
                LOG.severe("MySQL rechazo la operacion: " + mensajePermisos(config, e));
                return;
            }
            throw e;
        }

        try (Connection c = DriverManager.getConnection(config.urlBase(), config.usuario(), config.contrasena())) {
            asegurarTablaControl(c);
            List<String> aplicadas = listarAplicadas(c);
            if (aplicadas.size() >= MIGRACIONES_ESPERADAS) {
                LOG.info("Esquema ExamLen al dia (" + aplicadas.size() + " migraciones registradas).");
                return;
            }

            int funcionales = contarTablasEsperadas(c);
            if (funcionales == 0) {
                if (contarTablasTotales(c) == 0 && aplicadas.isEmpty()) {
                    aplicarPendientes(c, contexto, aplicadas);
                    return;
                }
                LOG.severe("La base " + config.base() + " contiene tablas ajenas a ExamLen y no puede "
                        + "inicializarse automaticamente. Revisala manualmente; no se aplico ninguna migracion.");
                return;
            }

            if (funcionales == TABLAS_ESPERADAS.size()) {
                if (!estructuraCoincide(c)) {
                    LOG.severe("La base " + config.base() + " tiene las 28 tablas de ExamLen pero su estructura no "
                            + "coincide exactamente con el esquema final (columnas clave o FK distintas). No se "
                            + "aplico ninguna migracion automatica; revisala manualmente.");
                    return;
                }
                if (aplicadas.isEmpty()) {
                    registrarBaseline(c, leerIndice(contexto));
                    LOG.info("Base existente reconocida como esquema final de ExamLen: se registraron "
                            + MIGRACIONES_ESPERADAS + " migraciones como baseline sin re-ejecutar nada.");
                    return;
                }
                aplicarPendientes(c, contexto, aplicadas);
                return;
            }

            LOG.severe("La base " + config.base() + " esta parcialmente inicializada (" + funcionales + " de "
                    + TABLAS_ESPERADAS.size() + " tablas de ExamLen). No se aplican migraciones automaticas sobre "
                    + "estados ambiguos; revisala manualmente.");
        }
    }

    private static void cargarDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // El driver se registra via SPI al estar en WEB-INF/lib; si no, DriverManager lo reportara.
        }
    }

    private static boolean existeBase(Connection c, String base) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, base);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void crearBase(Connection c, String base) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE `" + base + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
    }

    private static void asegurarTablaControl(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + TABLA_CONTROL + " ("
                    + "nombre VARCHAR(100) NOT NULL PRIMARY KEY, "
                    + "fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private static List<String> listarAplicadas(Connection c) throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM " + TABLA_CONTROL + " ORDER BY nombre")) {
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        }
        return lista;
    }

    private static void aplicarPendientes(Connection c, ServletContext contexto, List<String> aplicadas)
            throws SQLException, IOException {
        Set<String> hechas = new LinkedHashSet<>(aplicadas);
        List<String> pendientes = new ArrayList<>();
        for (String nombre : leerIndice(contexto)) {
            if (!hechas.contains(nombre)) {
                pendientes.add(nombre);
            }
        }
        if (pendientes.isEmpty()) {
            LOG.info("No hay migraciones pendientes.");
            return;
        }
        LOG.info("Aplicando " + pendientes.size() + " migracion(es) pendiente(s)...");
        for (String nombre : pendientes) {
            String sql = leerRecurso(contexto, CARPETA_MIGRACIONES + nombre);
            sql = USE_HARDCODEADO.matcher(sql).replaceAll("");
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + TABLA_CONTROL + " (nombre) VALUES (?)")) {
                ps.setString(1, nombre);
                ps.executeUpdate();
            }
            LOG.info("Migracion aplicada: " + nombre);
        }
    }

    private static void registrarBaseline(Connection c, List<String> indice) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO " + TABLA_CONTROL + " (nombre) VALUES (?)")) {
            for (String nombre : indice) {
                ps.setString(1, nombre);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static int contarTablasEsperadas(Connection c) throws SQLException {
        int total = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES t WHERE t.TABLE_SCHEMA = DATABASE() "
                        + "AND t.TABLE_NAME = ?")) {
            for (String tabla : TABLAS_ESPERADAS) {
                ps.setString(1, tabla);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    private static int contarTablasTotales(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static boolean estructuraCoincide(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            for (String[] clave : COLUMNAS_CLAVE) {
                ps.setString(1, clave[0]);
                ps.setString(2, clave[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getInt(1) == 0) {
                        return false;
                    }
                }
            }
        }
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                     + "WHERE CONSTRAINT_SCHEMA = DATABASE()")) {
            return rs.next() && rs.getInt(1) == FK_ESPERADAS;
        }
    }

    private static List<String> leerIndice(ServletContext contexto) throws IOException {
        List<String> nombres = new ArrayList<>();
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(abrirRecurso(contexto, INDICE), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                String limpia = linea.trim();
                if (!limpia.isEmpty() && !limpia.startsWith("#")) {
                    nombres.add(limpia);
                }
            }
        }
        return nombres;
    }

    private static String leerRecurso(ServletContext contexto, String ruta) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(abrirRecurso(contexto, ruta), StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int leidos;
            while ((leidos = lector.read(buffer)) != -1) {
                sb.append(buffer, 0, leidos);
            }
        }
        return sb.toString();
    }

    private static InputStream abrirRecurso(ServletContext contexto, String ruta) throws IOException {
        InputStream entrada = contexto.getResourceAsStream(ruta);
        if (entrada == null) {
            throw new IOException("No se encontro el recurso " + ruta
                    + " (verifica que WEB-INF/sql/migraciones viaje en el despliegue).");
        }
        return entrada;
    }

    private static boolean esErrorDePermisos(SQLException e) {
        int codigo = e.getErrorCode();
        return codigo == 1044 || codigo == 1045 || codigo == 1142;
    }

    private static String mensajePermisos(ConfiguracionBD config, SQLException e) {
        return "(" + e.getErrorCode() + ") El usuario MySQL '" + config.usuario()
                + "' no tiene permisos para crear o migrar la base '" + config.base()
                + "'. Ejecuta UNA VEZ como administrador de MySQL un grant equivalente a: "
                + "GRANT CREATE, ALTER, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE ON `" + config.base()
                + "`.* TO '" + config.usuario() + "'@'localhost'; (ajusta el host del usuario). "
                + "La aplicacion continuara en modo 503.";
    }

}
