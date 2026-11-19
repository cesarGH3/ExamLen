package modelo;

import conexion.ConfiguracionBD;
import jakarta.servlet.ServletContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
    private static final String MIGRACION_PROPIETARIO = "018_pregunta_propietario_banco_opcional.sql";
    private static final String MIGRACION_INTENTOS = "019_intentos_evaluacion_virtual.sql";
    private static final String MIGRACION_RESPUESTAS = "020_politica_respuestas_completas.sql";
    private static final String MIGRACION_CLAVES_IA = "021_clave_ia_docente.sql";
    private static final String MIGRACION_SEGURIDAD = "022_integridad_respuestas_tokens.sql";
    private static final String MIGRACION_ROLES = "023_roles_sin_columna_tipo.sql";
    private static final String MIGRACION_INTEGRIDAD = "024_restricciones_integridad.sql";
    private static volatile boolean esquemaDisponible;

    /** Disparadores esperados: {nombre, tabla, momento, evento}. 022 (5) + 024 (13). */
    private static final List<String[]> TRIGGERS_ESPERADOS = List.of(
            new String[]{"trg_respuesta_contexto_bi", "respuesta", "BEFORE", "INSERT"},
            new String[]{"trg_respuesta_contexto_bu", "respuesta", "BEFORE", "UPDATE"},
            new String[]{"trg_rp_contexto_bi", "respuesta_practica", "BEFORE", "INSERT"},
            new String[]{"trg_rp_contexto_bu", "respuesta_practica", "BEFORE", "UPDATE"},
            new String[]{"trg_vp_puntaje_bu", "version_pregunta", "BEFORE", "UPDATE"},
            new String[]{"trg_intento_bi", "intento", "BEFORE", "INSERT"},
            new String[]{"trg_intento_bu", "intento", "BEFORE", "UPDATE"},
            new String[]{"trg_inscripcion_bu", "inscripcion", "BEFORE", "UPDATE"},
            new String[]{"trg_evaluacion_virtual_bu", "evaluacion_virtual", "BEFORE", "UPDATE"},
            new String[]{"trg_version_evaluacion_bu", "version_evaluacion", "BEFORE", "UPDATE"},
            new String[]{"trg_evaluacion_virtual_version_bi", "evaluacion_virtual_version", "BEFORE", "INSERT"},
            new String[]{"trg_evaluacion_virtual_version_bu", "evaluacion_virtual_version", "BEFORE", "UPDATE"},
            new String[]{"trg_administrador_rol_bi", "administrador", "BEFORE", "INSERT"},
            new String[]{"trg_administrador_rol_bu", "administrador", "BEFORE", "UPDATE"},
            new String[]{"trg_docente_rol_bi", "docente", "BEFORE", "INSERT"},
            new String[]{"trg_docente_rol_bu", "docente", "BEFORE", "UPDATE"},
            new String[]{"trg_estudiante_rol_bi", "estudiante", "BEFORE", "INSERT"},
            new String[]{"trg_estudiante_rol_bu", "estudiante", "BEFORE", "UPDATE"});

    /** Restricciones de 024: {nombre, tabla, tipo, columnas en orden}. */
    private static final List<String[]> RESTRICCIONES_INTEGRIDAD = List.of(
            new String[]{"fk_intento_virtual_version", "intento", "FOREIGN KEY", "id_evaluacion_virtual,id_version_evaluacion"},
            new String[]{"uk_respuesta_intento_pregunta", "respuesta", "UNIQUE", "id_intento,id_version_pregunta"},
            new String[]{"fk_clave_ia_docente", "clave_ia_docente", "FOREIGN KEY", "id_docente"},
            new String[]{"ck_respuesta_puntaje", "respuesta", "CHECK", null},
            new String[]{"ck_evaluacion_virtual_fechas", "evaluacion_virtual", "CHECK", null},
            new String[]{"ck_evaluacion_pregunta_puntaje", "evaluacion_pregunta", "CHECK", null});

    public static boolean consultarDisponibilidadEsquema() {
        return esquemaDisponible;
    }

    private static final List<String> TABLAS_ESPERADAS = List.of(
            "administrador", "banco_preguntas", "curso", "curso_material", "docente", "documento_fuente",
            "estudiante", "evaluacion", "evaluacion_pregunta", "evaluacion_virtual", "evaluacion_virtual_version",
            "evento_supervision", "grupo", "inscripcion", "intento", "intento_practica", "material_practica",
            "material_pregunta", "opcion_respuesta", "pregunta", "respuesta", "respuesta_practica",
            "solicitud_generacion", "token_recuperacion", "usuario", "version_evaluacion", "version_opcion",
            "version_pregunta", "clave_ia_docente");

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
    private static final Pattern CREAR_BASE_HARDCODEADA =
            Pattern.compile("(?is)\\bCREATE\\s+DATABASE\\b[^;]*;");

    private InicializadorEsquema() {
    }

    /**
     * Punto de entrada: se invoca al arrancar la aplicacion. Un fallo aqui nunca
     * impide el arranque (la verificacion de disponibilidad existente mantiene el 503).
     */
    public static void inicializar(ServletContext contexto) {
        esquemaDisponible = false;
        try {
            ejecutar(contexto);
        } catch (Exception e) {
            LOG.severe("No fue posible inicializar el esquema automaticamente: " + e.getMessage()
                    + ". La aplicación responderá 503. Corrige el esquema y reinicia para verificarlo antes de operar.");
        }
    }

    private static void ejecutar(ServletContext contexto) throws SQLException, IOException {
        ConfiguracionBD config = ConfiguracionBD.resolver();
        if (!config.hayCredenciales()) {
            LOG.warning("Inicializacion de esquema omitida: configura examlen.db.user y examlen.db.password en "
                    + "{catalina.base}/conf/catalina.properties (ver config/catalina.properties.ejemplo) "
                    + "o las variables DB_USER / DB_PASSWORD (README-ENTREGA.md en la raiz del proyecto, seccion \"Base de datos\").");
            return;
        }

        try (Connection servidor = config.fuenteInicializador(false).getConnection()) {
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

        try (Connection c = config.fuenteInicializador(true).getConnection()) {
            try (PreparedStatement bloqueo = c.prepareStatement("SELECT GET_LOCK(CONCAT(DATABASE(), ':migraciones'), 30)");
                 ResultSet resultado = bloqueo.executeQuery()) {
                if (!resultado.next() || resultado.getInt(1) != 1) throw new SQLException("Otra instancia está migrando el esquema.");
            }
            List<String> indice = leerIndice(contexto);
            if (new LinkedHashSet<>(indice).size() != indice.size()
                    || !indice.contains(MIGRACION_PROPIETARIO)
                    || !indice.contains(MIGRACION_INTENTOS)
                    || !indice.contains(MIGRACION_RESPUESTAS)
                    || !indice.contains(MIGRACION_CLAVES_IA)
                    || !indice.contains(MIGRACION_SEGURIDAD)
                    || !indice.contains(MIGRACION_ROLES)
                    || !indice.contains(MIGRACION_INTEGRIDAD)) {
                throw new SQLException("Índice de migraciones inválido.");
            }
            for (String nombre : indice) leerRecurso(contexto, CARPETA_MIGRACIONES + nombre);
            asegurarTablaControl(c);
            List<String> aplicadas = listarAplicadas(c);
            if (aplicadas.size() > indice.size() || !indice.subList(0, aplicadas.size()).equals(aplicadas)) {
                throw new SQLException("Registro de migraciones desconocido o discontinuo.");
            }
            int funcionales = contarTablasEsperadas(c);
            if (funcionales == 0) {
                if (!aplicadas.isEmpty() || contarTablasTotales(c) != 1) {
                    throw new SQLException("La base contiene un estado ajeno o parcial; no se modifica.");
                }
            } else if (funcionales == TABLAS_ESPERADAS.size() - 1
                    && aplicadas.contains(MIGRACION_RESPUESTAS)
                    && !aplicadas.contains(MIGRACION_CLAVES_IA)) {
                verificarEsquemaPreClaves(c);
            } else if (funcionales == TABLAS_ESPERADAS.size()) {
                if (aplicadas.contains(MIGRACION_PROPIETARIO)) {
                    verificarEsquemaPropietario(c);
                } else {
                    verificarEsquemaAnterior(c);
                    if (aplicadas.isEmpty()) {
                        List<String> anteriores = indice.subList(0, indice.indexOf(MIGRACION_PROPIETARIO));
                        registrarBaseline(c, anteriores);
                        aplicadas = new ArrayList<>(anteriores);
                    }
                }
            } else {
                throw new SQLException("Esquema parcial; requiere recuperación antes de continuar.");
            }
            aplicarPendientes(c, contexto, aplicadas);
            if (contarTablasEsperadas(c) != TABLAS_ESPERADAS.size()) {
                throw new SQLException("Esquema final incompleto: faltan tablas esperadas.");
            }
            verificarEsquemaPropietario(c);
            if (listarAplicadas(c).contains(MIGRACION_INTENTOS)) {
                verificarEsquemaIntentos(c);
            }
            if (listarAplicadas(c).contains(MIGRACION_RESPUESTAS)) {
                verificarEsquemaRespuestasCompletas(c);
            }
            if (listarAplicadas(c).contains(MIGRACION_CLAVES_IA)) {
                verificarEsquemaClavesIA(c);
            }
            verificarEsquemaSeguridad(c);
            if (listarAplicadas(c).contains(MIGRACION_ROLES)) {
                verificarEsquemaRoles(c);
            }
            if (!listarAplicadas(c).equals(indice)) {
                throw new SQLException("Quedaron migraciones sin aplicar; el esquema no se marca disponible.");
            }
            verificarEsquemaIntegridad(c);
            esquemaDisponible = true;
            LOG.info("Esquema verificado y disponible.");
        }
    }

    private static void verificarEsquemaAnterior(Connection conexion) throws SQLException {
        if (!estructuraCoincide(conexion) || !columnaCoincide(conexion, "id_banco_preguntas", "NO")
                || columnaExiste(conexion, "id_docente")) {
            throw new SQLException("No se reconoce el esquema 017 o hay una migración parcial. No se continúa.");
        }
    }

    private static boolean columnaExiste(Connection conexion, String columna) throws SQLException {
        return columnaExiste(conexion, "pregunta", columna);
    }

    private static boolean columnaExiste(Connection conexion, String tabla, String columna) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement("SELECT 1 FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            sentencia.setString(1, tabla);
            sentencia.setString(2, columna);
            try (ResultSet resultado = sentencia.executeQuery()) { return resultado.next(); }
        }
    }

    private static boolean columnaCoincide(Connection conexion, String columna, String permiteNulo) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement("SELECT IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pregunta' AND COLUMN_NAME = ?")) {
            sentencia.setString(1, columna);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() && permiteNulo.equals(resultado.getString(1)) && "int unsigned".equals(resultado.getString(2));
            }
        }
    }

    private static void verificarDatosPropietario(Connection conexion) throws SQLException {
        String comprobacion = "SELECT p.id_pregunta FROM pregunta p LEFT JOIN banco_preguntas b "
                + "ON b.id_banco_preguntas = p.id_banco_preguntas LEFT JOIN docente d ON d.id_usuario = b.id_docente "
                + "WHERE d.id_usuario IS NULL UNION ALL "
                + "SELECT p.id_pregunta FROM pregunta p JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "JOIN evaluacion_pregunta ep ON ep.id_pregunta = p.id_pregunta JOIN evaluacion e ON e.id_evaluacion = ep.id_evaluacion "
                + "WHERE e.id_docente <> b.id_docente UNION ALL "
                + "SELECT p.id_pregunta FROM pregunta p JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "JOIN material_pregunta mp ON mp.id_pregunta = p.id_pregunta JOIN material_practica m ON m.id_material_practica = mp.id_material_practica "
                + "WHERE m.id_docente <> b.id_docente UNION ALL "
                + "SELECT p.id_pregunta FROM pregunta p JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "JOIN version_pregunta vp ON vp.id_pregunta = p.id_pregunta JOIN version_evaluacion v ON v.id_version_evaluacion = vp.id_version_evaluacion "
                + "JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion WHERE e.id_docente <> b.id_docente UNION ALL "
                + "SELECT p.id_pregunta FROM pregunta p JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "JOIN solicitud_generacion sg ON sg.id_solicitud_generacion = p.id_solicitud_generacion "
                + "WHERE sg.id_docente <> b.id_docente LIMIT 1";
        try (Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(comprobacion)) {
            if (resultado.next()) throw new SQLException("Propietarios o asociaciones inconsistentes. No se aplica 018.");
        }
    }

    private static void verificarEsquemaPropietario(Connection conexion) throws SQLException {
        // Se ejecuta tambien justo despues de 018, antes de que existan las tablas de 021;
        // por eso valida columnas y claves, no el total final de tablas.
        if (!columnaCoincide(conexion, "id_docente", "NO")
                || !columnaCoincide(conexion, "id_banco_preguntas", "YES")) {
            throw new SQLException("Esquema 018 incompleto.");
        }
        try (Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(
                "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'pregunta' AND ((CONSTRAINT_NAME = 'fk_pregunta_docente' AND COLUMN_NAME = 'id_docente' "
                + "AND REFERENCED_TABLE_NAME = 'docente' AND REFERENCED_COLUMN_NAME = 'id_usuario') "
                + "OR (CONSTRAINT_NAME = 'fk_pregunta_banco_docente' AND REFERENCED_TABLE_NAME = 'banco_preguntas' "
                + "AND COLUMN_NAME = REFERENCED_COLUMN_NAME AND COLUMN_NAME IN ('id_banco_preguntas','id_docente')))")) {
            if (!resultado.next() || resultado.getInt(1) != 3) throw new SQLException("Restricciones de propiedad incompletas.");
        }
    }

    private static void verificarDatosIntentos(Connection conexion) throws SQLException {
        exigirSinFilas(conexion, "SELECT id_evaluacion_virtual, id_inscripcion FROM intento "
                + "GROUP BY id_evaluacion_virtual, id_inscripcion HAVING COUNT(*) > 1 LIMIT 1",
                "Hay intentos historicos duplicados para la misma evaluacion e inscripcion.");
        exigirSinFilas(conexion, "SELECT id_intento FROM intento WHERE id_evaluacion_virtual IS NULL "
                + "OR id_inscripcion IS NULL LIMIT 1",
                "Hay intentos sin evaluacion virtual o sin inscripcion.");
        exigirSinFilas(conexion, "SELECT i.id_intento FROM intento i "
                + "JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = i.id_evaluacion_virtual "
                + "JOIN inscripcion n ON n.id_inscripcion = i.id_inscripcion "
                + "WHERE n.id_grupo <> ev.id_grupo LIMIT 1",
                "Hay intentos cuya inscripcion no pertenece al grupo de la evaluacion virtual.");
    }

    private static void verificarEsquemaIntentos(Connection conexion) throws SQLException {
        if (!columnaTipo(conexion, "evaluacion_virtual", "intentos_permitidos", "tinyint unsigned", "NO")
                || !columnaTipo(conexion, "intento", "numero_intento", "tinyint unsigned", "NO")) {
            throw new SQLException("Faltan las columnas de intentos de la migracion 019.");
        }
        if (contarFilas(conexion, "SELECT COUNT(*) FROM evaluacion_virtual "
                + "WHERE intentos_permitidos NOT IN (1, 2)") != 0
                || contarFilas(conexion, "SELECT COUNT(*) FROM intento "
                + "WHERE numero_intento NOT IN (1, 2)") != 0) {
            throw new SQLException("Hay valores invalidos de intentos permitidos o numero de intento.");
        }
        String indice = cadena(conexion, "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'intento' "
                + "AND INDEX_NAME = 'uk_intento_virtual_inscripcion_numero' AND NON_UNIQUE = 0");
        if (!"id_evaluacion_virtual,id_inscripcion,numero_intento".equals(indice)) {
            throw new SQLException("La clave unica compuesta de intentos no esta completa.");
        }
    }

    private static void verificarEsquemaRespuestasCompletas(Connection conexion) throws SQLException {
        if (!columnaTipo(conexion, "evaluacion_virtual", "exigir_respuestas_completas",
                "tinyint(1)", "NO")) {
            throw new SQLException("Falta la columna de politica de respuestas completas (020).");
        }
        if (contarFilas(conexion, "SELECT COUNT(*) FROM evaluacion_virtual "
                + "WHERE exigir_respuestas_completas NOT IN (0, 1)") != 0) {
            throw new SQLException("Hay valores invalidos en exigir_respuestas_completas.");
        }
    }

    private static void verificarEsquemaPreClaves(Connection conexion) throws SQLException {
        if (contarTablasEsperadas(conexion) != TABLAS_ESPERADAS.size() - 1) {
            throw new SQLException("El esquema 020 no contiene las tablas esperadas antes de la migracion 021.");
        }
        verificarEsquemaIntentos(conexion);
        verificarEsquemaRespuestasCompletas(conexion);
    }

    private static void verificarEsquemaClavesIA(Connection conexion) throws SQLException {
        String columnas = cadena(conexion,
                "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'clave_ia_docente'");
        if (!"id_docente,proveedor,clave_cifrada,fecha_actualizacion".equals(columnas)) {
            throw new SQLException("La tabla de claves de IA no tiene la estructura esperada (021).");
        }
    }

    private static void verificarEsquemaSeguridad(Connection c) throws SQLException {
        if (contarFilas(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'token_api'") != 1
                || contarFilas(c, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK' AND ENFORCED = 'YES' "
                + "AND CONSTRAINT_NAME IN ('ck_intento_calificacion_rango','ck_ip_calificacion_rango',"
                + "'ck_vp_puntaje_no_negativo','ck_token_api_expira','ck_token_api_revocado')") != 5
                || contarFilas(c, "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN ("
                + "'fk_respuesta_intento_version','fk_respuesta_pregunta_version','fk_respuesta_opcion_pregunta',"
                + "'fk_rp_intento_material','fk_rp_material_pregunta','fk_rp_opcion_pregunta',"
                + "'fk_curso_rol_docente','fk_banco_rol_docente','fk_evaluacion_rol_docente','fk_material_rol_docente',"
                + "'fk_solicitud_rol_docente','fk_inscripcion_rol_estudiante','fk_practica_rol_estudiante',"
                + "'fk_token_api_usuario')") != 14) {
            throw new SQLException("Migracion de seguridad 022 incompleta.");
        }
        String indice = cadena(c, "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
                + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'respuesta_practica' AND INDEX_NAME = 'uk_rp_intento_pregunta' AND NON_UNIQUE = 0");
        if (!"id_intento_practica,id_pregunta".equals(indice)) {
            throw new SQLException("Falta unicidad de respuestas de practica (022).");
        }
    }

    /**
     * Datos previos a 023: cada usuario debe tener exactamente una fila de rol y, mientras
     * exista la columna historica, tipo debe coincidir con esa fila. No corrige ni borra.
     */
    private static void verificarDatosRoles(Connection conexion) throws SQLException {
        if (!columnaExiste(conexion, "usuario", "tipo")) {
            throw new SQLException("Migracion 023 parcial: falta usuario.tipo. Restaurar o reparar antes de continuar.");
        }
        exigirSinFilas(conexion, "SELECT u.id_usuario FROM usuario u "
                + "LEFT JOIN administrador a ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN docente d ON d.id_usuario = u.id_usuario "
                + "LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario "
                + "WHERE (a.id_usuario IS NOT NULL) + (d.id_usuario IS NOT NULL) "
                + "+ (e.id_usuario IS NOT NULL) <> 1 LIMIT 1",
                "Hay usuarios sin rol o con mas de un rol. Resolverlos antes de aplicar 023.");
        exigirSinFilas(conexion, "SELECT u.id_usuario FROM usuario u "
                + "LEFT JOIN administrador a ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN docente d ON d.id_usuario = u.id_usuario "
                + "LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario "
                + "WHERE u.tipo <> CASE WHEN a.id_usuario IS NOT NULL THEN 'ADMINISTRADOR' "
                + "WHEN d.id_usuario IS NOT NULL THEN 'DOCENTE' "
                + "WHEN e.id_usuario IS NOT NULL THEN 'ESTUDIANTE' ELSE 'SIN_ROL' END LIMIT 1",
                "usuario.tipo no coincide con la tabla de rol. Resolver antes de aplicar 023.");
    }

    /** Verificacion final de 023: el rol solo vive en las tablas hijas. */
    private static void verificarEsquemaRoles(Connection conexion) throws SQLException {
        if (columnaExiste(conexion, "usuario", "tipo")) {
            throw new SQLException("La migracion 023 no elimino usuario.tipo.");
        }
        if (contarFilas(conexion, "SELECT COUNT(*) FROM usuario u "
                + "LEFT JOIN administrador a ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN docente d ON d.id_usuario = u.id_usuario "
                + "LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario "
                + "WHERE (a.id_usuario IS NOT NULL) + (d.id_usuario IS NOT NULL) "
                + "+ (e.id_usuario IS NOT NULL) <> 1") != 0) {
            throw new SQLException("Hay usuarios sin rol o con mas de un rol tras 023.");
        }
    }

    /**
     * Datos previos a 024: misma comprobacion que el preflight SQL, con un mensaje por causa
     * para que el administrador sepa que reparar. No corrige ni borra.
     */
    private static void verificarDatosIntegridad(Connection conexion) throws SQLException {
        exigirSinFilas(conexion, "SELECT i.id_intento FROM intento i "
                + "JOIN inscripcion n ON n.id_inscripcion = i.id_inscripcion "
                + "JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = i.id_evaluacion_virtual "
                + "JOIN version_evaluacion v ON v.id_version_evaluacion = i.id_version_evaluacion "
                + "LEFT JOIN evaluacion_virtual_version evv ON evv.id_evaluacion_virtual = i.id_evaluacion_virtual "
                + "AND evv.id_version_evaluacion = i.id_version_evaluacion "
                + "WHERE n.id_grupo <> ev.id_grupo OR v.id_evaluacion <> ev.id_evaluacion "
                + "OR evv.id_evaluacion_virtual IS NULL LIMIT 1",
                "Hay intentos incoherentes con su inscripcion, grupo o version. No se aplica 024.");
        exigirSinFilas(conexion, "SELECT evv.id_evaluacion_virtual FROM evaluacion_virtual_version evv "
                + "JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = evv.id_evaluacion_virtual "
                + "JOIN version_evaluacion v ON v.id_version_evaluacion = evv.id_version_evaluacion "
                + "WHERE v.id_evaluacion <> ev.id_evaluacion LIMIT 1",
                "Hay versiones asociadas a una evaluacion virtual de otra evaluacion. No se aplica 024.");
        exigirSinFilas(conexion, "SELECT id_intento FROM respuesta GROUP BY id_intento, id_version_pregunta "
                + "HAVING COUNT(*) > 1 LIMIT 1",
                "Hay respuestas duplicadas para el mismo intento y pregunta. No se aplica 024.");
        exigirSinFilas(conexion, "SELECT id_evaluacion_virtual FROM evaluacion_virtual "
                + "WHERE fecha_fin <= fecha_inicio LIMIT 1",
                "Hay evaluaciones virtuales con fecha de fin no posterior al inicio. No se aplica 024.");
        exigirSinFilas(conexion, "SELECT id_evaluacion FROM evaluacion_pregunta "
                + "WHERE puntaje_evaluacion_pregunta < 0 UNION ALL "
                + "SELECT id_respuesta FROM respuesta WHERE puntaje < 0 LIMIT 1",
                "Hay puntajes negativos en evaluacion_pregunta o respuesta. No se aplica 024.");
        exigirSinFilas(conexion, "SELECT k.id_docente FROM clave_ia_docente k "
                + "LEFT JOIN docente d ON d.id_usuario = k.id_docente WHERE d.id_usuario IS NULL LIMIT 1",
                "Hay claves de IA sin docente. No se aplica 024.");
        exigirSinFilas(conexion, "SELECT u.id_usuario FROM usuario u "
                + "LEFT JOIN administrador a ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN docente d ON d.id_usuario = u.id_usuario "
                + "LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario "
                + "WHERE (a.id_usuario IS NOT NULL) + (d.id_usuario IS NOT NULL) "
                + "+ (e.id_usuario IS NOT NULL) > 1 LIMIT 1",
                "Hay usuarios con mas de un rol. No se aplica 024.");
    }

    /**
     * Verificacion final completa: todos los disparadores de 022 y 024 (nombre, tabla, momento y
     * evento) y las restricciones de 024 (tipo, tabla, columnas, CHECK aplicado). El esquema no se
     * marca disponible si falta cualquiera, aunque el historial diga que la migracion se aplico.
     */
    private static void verificarEsquemaIntegridad(Connection conexion) throws SQLException {
        // information_schema.TRIGGERS solo muestra disparadores a cuentas con privilegio TRIGGER.
        // Sin el, no se puede demostrar que existen: se exige en lugar de suponerlos presentes.
        if (contarFilas(conexion, "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                + "WHERE TRIGGER_SCHEMA = DATABASE()") == 0) {
            throw new SQLException("No se ven disparadores en la base: faltan o la cuenta carece del privilegio "
                    + "TRIGGER necesario para verificarlos. Concede TRIGGER sobre la base y reinicia.");
        }
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE() "
                + "AND TRIGGER_NAME = ? AND EVENT_OBJECT_TABLE = ? AND ACTION_TIMING = ? "
                + "AND EVENT_MANIPULATION = ?")) {
            for (String[] trigger : TRIGGERS_ESPERADOS) {
                for (int i = 0; i < trigger.length; i++) sentencia.setString(i + 1, trigger[i]);
                try (ResultSet resultado = sentencia.executeQuery()) {
                    if (!resultado.next() || resultado.getInt(1) != 1) {
                        throw new SQLException("Falta o no coincide el disparador " + trigger[0] + ".");
                    }
                }
            }
        }
        for (String[] restriccion : RESTRICCIONES_INTEGRIDAD) {
            String nombre = restriccion[0];
            String tabla = restriccion[1];
            String tipo = restriccion[2];
            String sql = "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                    + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? "
                    + "AND CONSTRAINT_TYPE = ?" + ("CHECK".equals(tipo) ? " AND ENFORCED = 'YES'" : "");
            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
                sentencia.setString(1, tabla);
                sentencia.setString(2, nombre);
                sentencia.setString(3, tipo);
                try (ResultSet resultado = sentencia.executeQuery()) {
                    if (!resultado.next() || resultado.getInt(1) != 1) {
                        throw new SQLException("Falta la restriccion " + nombre + " en " + tabla + ".");
                    }
                }
            }
            if (restriccion[3] != null) {
                try (PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) "
                        + "FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?")) {
                    sentencia.setString(1, tabla);
                    sentencia.setString(2, nombre);
                    try (ResultSet resultado = sentencia.executeQuery()) {
                        if (!resultado.next() || !restriccion[3].equals(resultado.getString(1))) {
                            throw new SQLException("La restriccion " + nombre + " no cubre las columnas esperadas.");
                        }
                    }
                }
            }
        }
    }

    private static boolean columnaTipo(Connection conexion, String tabla, String columna,
                                       String tipo, String permiteNulo) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(
                "SELECT IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            sentencia.setString(1, tabla);
            sentencia.setString(2, columna);
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() && permiteNulo.equals(resultado.getString(1))
                        && tipo.equalsIgnoreCase(resultado.getString(2));
            }
        }
    }

    private static int contarFilas(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery(sql)) {
            return resultado.next() ? resultado.getInt(1) : 0;
        }
    }

    private static String cadena(Connection conexion, String sql) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery(sql)) {
            return resultado.next() ? resultado.getString(1) : null;
        }
    }

    private static void exigirSinFilas(Connection conexion, String sql, String mensaje) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery(sql)) {
            if (resultado.next()) {
                throw new SQLException(mensaje);
            }
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
        if (contarFilas(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = '" + TABLA_CONTROL + "'") == 1) return;

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
            if (MIGRACION_PROPIETARIO.equals(nombre)) {
                verificarEsquemaAnterior(c);
                verificarDatosPropietario(c);
            }
            if (MIGRACION_INTENTOS.equals(nombre)) {
                verificarEsquemaPropietario(c);
                verificarDatosIntentos(c);
            }
            if (MIGRACION_RESPUESTAS.equals(nombre)) {
                verificarEsquemaIntentos(c);
            }
            if (MIGRACION_SEGURIDAD.equals(nombre) && contarFilas(c,
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND TABLE_NAME = 'respuesta' AND COLUMN_NAME = 'id_version_evaluacion'") != 0) {
                throw new SQLException("Migracion 022 parcial: restaurar o reparar antes de continuar.");
            }
            if (MIGRACION_ROLES.equals(nombre)) {
                verificarDatosRoles(c);
            }
            if (MIGRACION_INTEGRIDAD.equals(nombre)) {
                verificarDatosIntegridad(c);
            }
            String sql = leerRecurso(contexto, CARPETA_MIGRACIONES + nombre);
            sql = USE_HARDCODEADO.matcher(sql).replaceAll("");
            sql = CREAR_BASE_HARDCODEADA.matcher(sql).replaceAll("");
            String base = c.getCatalog();
            if (base != null && !base.isBlank()) {
                // Los GRANT de 001/002 nombran examlen; se redirigen a la base real.
                sql = sql.replaceAll("(?i)\\bexamlen\\.", "`" + base + "`.");
            }
            try (Statement st = c.createStatement()) {
                boolean resultado = st.execute(sql);
                while (true) {
                    if (resultado) { try (ResultSet filas = st.getResultSet()) { while (filas.next()) { /* consumir */ } } }
                    else if (st.getUpdateCount() == -1) break;
                    resultado = st.getMoreResults();
                }
            }
            if (MIGRACION_SEGURIDAD.equals(nombre)) verificarEsquemaSeguridad(c);
            if (MIGRACION_ROLES.equals(nombre)) verificarEsquemaRoles(c);
            if (MIGRACION_INTEGRIDAD.equals(nombre)) verificarEsquemaIntegridad(c);
            if (MIGRACION_PROPIETARIO.equals(nombre)) verificarEsquemaPropietario(c);
            if (MIGRACION_INTENTOS.equals(nombre)) verificarEsquemaIntentos(c);
            if (MIGRACION_RESPUESTAS.equals(nombre)) verificarEsquemaRespuestasCompletas(c);
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
                + "GRANT CREATE, ALTER, INDEX, REFERENCES, TRIGGER, SELECT, INSERT, UPDATE, DELETE ON `" + config.base()
                + "`.* TO '" + config.usuario() + "'@'localhost'; (ajusta el host del usuario). "
                + "La aplicacion continuara en modo 503.";
    }

}
