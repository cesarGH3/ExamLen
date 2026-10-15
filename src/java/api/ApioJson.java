package api;

import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Salida JSON de la API /api/v1: formato de error consistente y sin datos tecnicos.
 * Reutiliza Gson (ya presente en WEB-INF/lib de ExamLen).
 */
public final class ApioJson {

    public static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    public static final String ROL_DOCENTE = "DOCENTE";
    public static final String ROL_ESTUDIANTE = "ESTUDIANTE";

    public static final String ATTR_ID_USUARIO = "examlen.api.idUsuario";
    public static final String ATTR_ROL = "examlen.api.rol";
    public static final String ATTR_NOMBRE = "examlen.api.nombre";
    public static final String ATTR_CORREO = "examlen.api.correo";
    public static final String ATTR_IDENTIFICACION = "examlen.api.identificacion";

    private ApioJson() {
    }

    public static void ok(HttpServletResponse resp, JsonObject cuerpo) throws IOException {
        escribir(resp, HttpServletResponse.SC_OK, cuerpo);
    }

    public static void error(HttpServletResponse resp, int estado, String codigo, String mensaje) throws IOException {
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("error", codigo);
        cuerpo.addProperty("message", mensaje);
        escribir(resp, estado, cuerpo);
    }

    private static void escribir(HttpServletResponse resp, int estado, JsonObject cuerpo) throws IOException {
        resp.setStatus(estado);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(cuerpo.toString());
    }

    public static int idUsuario(jakarta.servlet.http.HttpServletRequest req) {
        Object valor = req.getAttribute(ATTR_ID_USUARIO);
        return valor instanceof Integer ? (Integer) valor : -1;
    }

    public static String rol(jakarta.servlet.http.HttpServletRequest req) {
        Object valor = req.getAttribute(ATTR_ROL);
        return valor instanceof String ? (String) valor : "";
    }

    public static String nombre(jakarta.servlet.http.HttpServletRequest req) {
        return texto(req, ATTR_NOMBRE);
    }

    public static String correo(jakarta.servlet.http.HttpServletRequest req) {
        return texto(req, ATTR_CORREO);
    }

    public static String identificacion(jakarta.servlet.http.HttpServletRequest req) {
        return texto(req, ATTR_IDENTIFICACION);
    }

    private static String texto(jakarta.servlet.http.HttpServletRequest req, String atributo) {
        Object valor = req.getAttribute(atributo);
        return valor instanceof String ? (String) valor : "";
    }

    /** Enumeracion segura: null si el valor es nulo, vacio o desconocido. */
    public static <E extends Enum<E>> E enumerar(Class<E> tipo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Nombre de archivo seguro para Content-Disposition. */
    public static String nombreArchivoSeguro(String texto) {
        String limpio = texto == null ? "" : texto.replaceAll("[^A-Za-z0-9._-]+", "_");
        return limpio.isBlank() ? "evaluacion" : limpio;
    }
}
