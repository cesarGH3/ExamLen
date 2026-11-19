package modelo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ejecucion reanudable de migraciones y lectura de sus definiciones de disparadores.
 *
 * <p>MySQL 8 hace atomica cada sentencia DDL, pero no una migracion completa (cada DDL hace
 * commit implicito). Por eso cada script se ejecuta sentencia a sentencia y, tras cada paso
 * persistente, se anota el avance en {@code esquema_migracion_paso} junto con la huella SHA-256
 * del script; ademas, justo antes de cada paso persistente se anota que ese paso esta en curso,
 * de modo que nunca hay un cambio aplicado sin registro. Si el proceso se interrumpe, el
 * siguiente arranque continua en el paso pendiente
 * en lugar de dejar el esquema a medias o reejecutar DDL ya aplicado. Una huella distinta
 * (script cambiado tras una ejecucion parcial) detiene la migracion.
 */
final class EjecutorMigraciones {

    private static final Logger LOG = Logger.getLogger(EjecutorMigraciones.class.getName());
    static final String TABLA_BITACORA = "esquema_migracion_paso";

    /** Errores "ya existe / ya no existe": solo se toleran en el primer paso reanudado. */
    private static final Set<Integer> YA_APLICADO = Set.of(1050, 1060, 1061, 1091, 1359, 1360, 1826, 3821, 3822);

    private static final Pattern TEMPORAL_CREAR =
            Pattern.compile("(?is)^\\s*(?:CREATE|DROP)\\s+TEMPORARY\\s+TABLE\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?`?(\\w+)`?");
    private static final Pattern INSERTAR_EN = Pattern.compile("(?is)^\\s*INSERT\\s+INTO\\s+`?(\\w+)`?");
    private static final Pattern CREAR_TRIGGER = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TRIGGER\\s+`?(\\w+)`?\\s+(BEFORE|AFTER)\\s+(INSERT|UPDATE|DELETE)\\s+ON\\s+`?(\\w+)`?"
            + "\\s+FOR\\s+EACH\\s+ROW\\s+(.+)$");
    private static final Pattern BORRAR_TRIGGER =
            Pattern.compile("(?is)^\\s*DROP\\s+TRIGGER\\s+(?:IF\\s+EXISTS\\s+)?`?(\\w+)`?");

    /** Disparador esperado segun la ultima definicion en las migraciones. */
    static final class Disparador {
        final String nombre;
        final String tabla;
        final String momento;
        final String evento;
        final String cuerpo;

        Disparador(String nombre, String tabla, String momento, String evento, String cuerpo) {
            this.nombre = nombre;
            this.tabla = tabla;
            this.momento = momento;
            this.evento = evento;
            this.cuerpo = cuerpo;
        }
    }

    /** Comprobacion previa propia de una migracion; solo se ejecuta si no hay avance anotado. */
    interface Precondicion {
        void comprobar() throws SQLException;
    }

    private EjecutorMigraciones() {
    }

    static void asegurarBitacora(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + TABLA_BITACORA + " ("
                    + "nombre VARCHAR(150) NOT NULL PRIMARY KEY, "
                    + "huella CHAR(64) NOT NULL, "
                    + "pasos_completados INT UNSIGNED NOT NULL, "
                    + "fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        }
    }

    /** Borra el avance de migraciones ya registradas como aplicadas (restos de una interrupcion final). */
    static void limpiarBitacora(Connection c, List<String> aplicadas) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, TABLA_BITACORA);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) == 0) return; // sin migraciones pendientes nunca se creo
            }
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLA_BITACORA + " WHERE nombre = ?")) {
            for (String nombre : aplicadas) {
                ps.setString(1, nombre);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Ejecuta {@code sql} paso a paso, reanudando si hay avance anotado para {@code nombre}.
     * Al terminar NO registra la migracion en esquema_migracion: lo hace el llamador tras sus
     * verificaciones finales, y despues llama a {@link #limpiarBitacora}.
     */
    static void aplicar(Connection c, String nombre, String sql, Precondicion precondicion) throws SQLException {
        List<String> pasos = dividir(sql);
        String huella = huella(sql);
        int hechos = 0;
        boolean iniciada = false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT huella, pasos_completados FROM " + TABLA_BITACORA + " WHERE nombre = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (!huella.equals(rs.getString(1))) {
                        throw new SQLException("La migracion " + nombre + " quedo a medias con otra version del "
                                + "script. Restaurar el respaldo o reparar manualmente antes de continuar.");
                    }
                    hechos = rs.getInt(2);
                    iniciada = true;
                }
            }
        }
        if (hechos > pasos.size()) {
            throw new SQLException("Bitacora de " + nombre + " incoherente con el script.");
        }
        if (!iniciada) {
            precondicion.comprobar();
        } else {
            LOG.warning("Reanudando la migracion " + nombre + " en el paso " + (hechos + 1) + " de " + pasos.size()
                    + " tras una interrupcion anterior.");
        }
        Set<String> temporales = tablasTemporales(pasos);
        for (int i = hechos; i < pasos.size(); i++) {
            String paso = pasos.get(i);
            boolean persistente = !esTemporal(paso, temporales);
            if (persistente) {
                // Se anota ANTES de ejecutar: "paso i en curso". Asi no existe ventana en la que un
                // cambio persistente quede sin rastro en la bitacora. Si el proceso cae entre esta
                // anotacion y la siguiente, el reinicio retoma en el paso i y, si ya se habia
                // aplicado, reconoce el error "ya existe / ya no existe" (DDL atomico en MySQL 8).
                anotar(c, nombre, huella, i);
            }
            try {
                ejecutar(c, paso);
            } catch (SQLException e) {
                if (i == hechos && iniciada && YA_APLICADO.contains(e.getErrorCode())) {
                    LOG.warning("Paso " + (i + 1) + " de " + nombre + " ya estaba aplicado antes de la interrupcion: "
                            + e.getMessage());
                } else {
                    throw new SQLException("Migracion " + nombre + ", paso " + (i + 1) + " de " + pasos.size()
                            + ": " + e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
                }
            }
            if (persistente) {
                anotar(c, nombre, huella, i + 1);
            }
        }
    }

    private static void anotar(Connection c, String nombre, String huella, int pasos) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLA_BITACORA
                + " (nombre, huella, pasos_completados) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE pasos_completados = VALUES(pasos_completados)")) {
            ps.setString(1, nombre);
            ps.setString(2, huella);
            ps.setInt(3, pasos);
            ps.executeUpdate();
        }
    }

    private static void ejecutar(Connection c, String paso) throws SQLException {
        try (Statement st = c.createStatement()) {
            boolean resultado = st.execute(paso);
            while (true) {
                if (resultado) {
                    try (ResultSet filas = st.getResultSet()) {
                        while (filas.next()) { /* consumir */ }
                    }
                } else if (st.getUpdateCount() == -1) {
                    break;
                }
                resultado = st.getMoreResults();
            }
        }
    }

    private static Set<String> tablasTemporales(List<String> pasos) {
        Set<String> nombres = new HashSet<>();
        for (String paso : pasos) {
            Matcher m = TEMPORAL_CREAR.matcher(paso);
            if (m.find()) nombres.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return nombres;
    }

    /** Pasos sin efecto persistente: tablas temporales de preflight y verificacion. */
    private static boolean esTemporal(String paso, Set<String> temporales) {
        if (TEMPORAL_CREAR.matcher(paso).find()) return true;
        Matcher m = INSERTAR_EN.matcher(paso);
        return m.find() && temporales.contains(m.group(1).toLowerCase(Locale.ROOT));
    }

    /**
     * Disparadores que deben existir al aplicar los scripts en orden: cada CREATE TRIGGER
     * define (o redefine) uno y cada DROP TRIGGER lo retira.
     */
    static Map<String, Disparador> disparadoresEsperados(List<String> scripts) {
        Map<String, Disparador> esperados = new LinkedHashMap<>();
        for (String script : scripts) {
            for (String paso : dividir(script)) {
                Matcher crear = CREAR_TRIGGER.matcher(paso);
                if (crear.find()) {
                    String nombre = crear.group(1);
                    esperados.put(nombre, new Disparador(nombre, crear.group(4), crear.group(2).toUpperCase(Locale.ROOT),
                            crear.group(3).toUpperCase(Locale.ROOT), normalizar(crear.group(5))));
                    continue;
                }
                Matcher borrar = BORRAR_TRIGGER.matcher(paso);
                if (borrar.find()) esperados.remove(borrar.group(1));
            }
        }
        return esperados;
    }

    /** Forma canonica de un cuerpo SQL: sin comentarios, sin comillas invertidas, espacios unicos, minusculas. */
    static String normalizar(String sql) {
        String sinComentarios = quitarComentarios(sql).replace("`", "");
        return sinComentarios.replaceAll("\\s+", " ").trim().replaceAll(";$", "").trim().toLowerCase(Locale.ROOT);
    }

    /** Divide un script en sentencias respetando comillas y comentarios; omite sentencias vacias. */
    static List<String> dividir(String sql) {
        List<String> sentencias = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        int n = sql.length();
        for (int i = 0; i < n; i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' || ch == '"' || ch == '`') {
                int fin = finCadena(sql, i, ch);
                actual.append(sql, i, fin);
                i = fin - 1;
            } else if (esComentarioLinea(sql, i)) {
                int fin = sql.indexOf('\n', i);
                i = (fin < 0 ? n : fin) - 1;
                actual.append(' ');
            } else if (ch == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int fin = sql.indexOf("*/", i + 2);
                i = (fin < 0 ? n : fin + 2) - 1;
                actual.append(' ');
            } else if (ch == ';') {
                agregar(sentencias, actual);
            } else {
                actual.append(ch);
            }
        }
        agregar(sentencias, actual);
        return sentencias;
    }

    private static String quitarComentarios(String sql) {
        StringBuilder salida = new StringBuilder();
        int n = sql.length();
        for (int i = 0; i < n; i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' || ch == '"' || ch == '`') {
                int fin = finCadena(sql, i, ch);
                salida.append(sql, i, fin);
                i = fin - 1;
            } else if (esComentarioLinea(sql, i)) {
                int fin = sql.indexOf('\n', i);
                i = (fin < 0 ? n : fin) - 1;
                salida.append(' ');
            } else if (ch == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int fin = sql.indexOf("*/", i + 2);
                i = (fin < 0 ? n : fin + 2) - 1;
                salida.append(' ');
            } else {
                salida.append(ch);
            }
        }
        return salida.toString();
    }

    private static void agregar(List<String> sentencias, StringBuilder actual) {
        String texto = actual.toString().trim();
        if (!texto.isEmpty()) sentencias.add(texto);
        actual.setLength(0);
    }

    private static boolean esComentarioLinea(String sql, int i) {
        char ch = sql.charAt(i);
        if (ch == '#') return true;
        return ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-'
                && (i + 2 >= sql.length() || Character.isWhitespace(sql.charAt(i + 2)));
    }

    /** Indice siguiente al cierre de una cadena o identificador entre comillas. */
    private static int finCadena(String sql, int inicio, char comilla) {
        int i = inicio + 1;
        while (i < sql.length()) {
            char ch = sql.charAt(i);
            if (ch == '\\' && comilla != '`') {
                i += 2;
                continue;
            }
            if (ch == comilla) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == comilla) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    static String huella(String texto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
