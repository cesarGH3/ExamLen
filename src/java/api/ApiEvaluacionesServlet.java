package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import logica.Evaluacion;
import logica.Fechas;
import logica.Validador;
import logica.VersionEvaluacion;
import modelo.EvaluacionDAO;
import modelo.VersionEvaluacionDAO;
import modelo.VersionPreguntaDAO;
import servlet.InicializadorAplicacion;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Evaluaciones del docente autenticado (solo DOCENTE; propiedad real de ExamLen):
 *   GET /api/v1/evaluaciones
 *   GET /api/v1/evaluaciones/{id}/versiones
 *   GET /api/v1/evaluaciones/{id}/versiones/{codigo}
 *   GET /api/v1/evaluaciones/{id}/versiones/{codigo}/pdf    (mismo generador PDF de ExamLen)
 *   GET /api/v1/evaluaciones/{id}/versiones/{codigo}/clave  (docente dueño; estudiantes 403)
 */
@WebServlet(name = "ApiEvaluacionesServlet", urlPatterns = "/api/v1/evaluaciones/*")
public class ApiEvaluacionesServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private EvaluacionDAO evaluacionDAO;
    private VersionEvaluacionDAO versionDAO;
    private VersionPreguntaDAO versionPreguntaDAO;

    @Override
    public void init() throws ServletException {
        evaluacionDAO = (EvaluacionDAO) getServletContext().getAttribute(InicializadorAplicacion.EVALUACION_DAO);
        versionDAO = (VersionEvaluacionDAO) getServletContext().getAttribute(InicializadorAplicacion.VERSION_DAO);
        versionPreguntaDAO = (VersionPreguntaDAO) getServletContext()
                .getAttribute(InicializadorAplicacion.VERSION_PREGUNTA_DAO);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            atender(req, resp);
        } catch (RuntimeException e) {
            ApioJson.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                    "No fue posible completar la operación.");
        }
    }

    private void atender(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApioJson.ROL_DOCENTE.equals(ApioJson.rol(req))) {
            ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                    "Solo un docente puede consultar evaluaciones y versiones.");
            return;
        }
        String ruta = req.getPathInfo() == null ? "" : req.getPathInfo();
        if (ruta.isBlank() || "/".equals(ruta)) {
            listar(req, resp);
            return;
        }
        String[] partes = ruta.replaceAll("^/+", "").split("/");
        if (partes.length >= 2 && "versiones".equals(partes[1])) {
            int idEvaluacion = Validador.parsearId(partes[0]);
            if (idEvaluacion < 0) {
                ApioJson.error(resp, HttpServletResponse.SC_BAD_REQUEST, "PARAMETRO_INVALIDO",
                        "El identificador de la evaluación no es válido.");
                return;
            }
            Evaluacion evaluacion = evaluacionPermitida(req, resp, idEvaluacion);
            if (evaluacion == null) {
                return;
            }
            if (partes.length == 2) {
                versiones(resp, evaluacion);
                return;
            }
            String codigo = partes[2];
            VersionEvaluacion version = versionPorCodigo(evaluacion.getIdEvaluacion(), codigo);
            if (version == null) {
                ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO",
                        "La versión indicada no existe.");
                return;
            }
            if (partes.length == 3) {
                detalle(resp, evaluacion, version);
                return;
            }
            if (partes.length == 4 && "pdf".equals(partes[3])) {
                byte[] pdf = versionDAO.generarPDFVersionEvaluacion(version, null);
                archivo(resp, pdf, "Evaluacion_" + ApioJson.nombreArchivoSeguro(evaluacion.tituloEvaluacion)
                        + "_" + ApioJson.nombreArchivoSeguro(version.codigoVersionEvaluacion) + ".pdf");
                return;
            }
            if (partes.length == 4 && "clave".equals(partes[3])) {
                byte[] clave = versionDAO.generarClaveRespuestasPDFVersionEvaluacion(version, null);
                archivo(resp, clave, "Evaluacion_" + ApioJson.nombreArchivoSeguro(evaluacion.tituloEvaluacion)
                        + "_" + ApioJson.nombreArchivoSeguro(version.codigoVersionEvaluacion) + "_clave.pdf");
                return;
            }
            ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO",
                    "Recurso no encontrado.");
            return;
        }
        ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO", "Recurso no encontrado.");
    }

    private void listar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonArray lista = new JsonArray();
        for (Evaluacion evaluacion : evaluacionDAO.listarPorDocente(ApioJson.idUsuario(req))) {
            JsonObject objeto = new JsonObject();
            objeto.addProperty("id", evaluacion.getIdEvaluacion());
            objeto.addProperty("titulo", evaluacion.tituloEvaluacion);
            objeto.addProperty("estado", evaluacion.estadoEvaluacion.name());
            objeto.addProperty("numeroPreguntas", evaluacion.numeroPreguntasEvaluacion);
            objeto.addProperty("numeroVersiones", evaluacion.numeroVersionesEvaluacion);
            objeto.addProperty("duracionMinutos", evaluacion.duracionMinutosEvaluacion);
            lista.add(objeto);
        }
        JsonObject cuerpo = new JsonObject();
        cuerpo.add("evaluaciones", lista);
        ApioJson.ok(resp, cuerpo);
    }

    private Evaluacion evaluacionPermitida(HttpServletRequest req, HttpServletResponse resp, int idEvaluacion)
            throws IOException {
        Evaluacion propia = evaluacionDAO.buscarPorIdDeDocente(idEvaluacion, ApioJson.idUsuario(req))
                .orElse(null);
        if (propia != null) {
            return propia;
        }
        if (evaluacionDAO.buscarPorId(idEvaluacion).isPresent()) {
            ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                    "La evaluación pertenece a otro docente.");
        } else {
            ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO", "Evaluación no encontrada.");
        }
        return null;
    }

    private VersionEvaluacion versionPorCodigo(int idEvaluacion, String codigo) {
        for (VersionEvaluacion version : versionDAO.listarPorEvaluacion(idEvaluacion)) {
            if (version.codigoVersionEvaluacion != null && version.codigoVersionEvaluacion
                    .equalsIgnoreCase(codigo.strip())) {
                return version;
            }
        }
        return null;
    }

    private void versiones(HttpServletResponse resp, Evaluacion evaluacion) throws IOException {
        JsonArray lista = new JsonArray();
        for (VersionEvaluacion version : versionDAO.listarPorEvaluacion(evaluacion.getIdEvaluacion())) {
            lista.add(aVersionJson(version));
        }
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("evaluacionId", evaluacion.getIdEvaluacion());
        cuerpo.addProperty("titulo", evaluacion.tituloEvaluacion);
        cuerpo.add("versiones", lista);
        ApioJson.ok(resp, cuerpo);
    }

    private void detalle(HttpServletResponse resp, Evaluacion evaluacion, VersionEvaluacion version)
            throws IOException {
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("evaluacionId", evaluacion.getIdEvaluacion());
        cuerpo.addProperty("titulo", evaluacion.tituloEvaluacion);
        cuerpo.addProperty("duracionMinutos", evaluacion.duracionMinutosEvaluacion);
        cuerpo.addProperty("codigo", version.codigoVersionEvaluacion);
        cuerpo.addProperty("numeroPreguntas",
                versionPreguntaDAO.listarPorVersionEvaluacion(version.getIdVersionEvaluacion()).size());
        cuerpo.addProperty("fechaGeneracion",
                Fechas.formato(Fechas.PATRON_FECHA_HORA, version.fechaGeneracionVersionEvaluacion));
        ApioJson.ok(resp, cuerpo);
    }

    private JsonObject aVersionJson(VersionEvaluacion version) {
        JsonObject objeto = new JsonObject();
        objeto.addProperty("id", version.getIdVersionEvaluacion());
        objeto.addProperty("codigo", version.codigoVersionEvaluacion);
        objeto.addProperty("numeroPreguntas",
                versionPreguntaDAO.listarPorVersionEvaluacion(version.getIdVersionEvaluacion()).size());
        objeto.addProperty("fechaGeneracion",
                Fechas.formato(Fechas.PATRON_FECHA_HORA, version.fechaGeneracionVersionEvaluacion));
        return objeto;
    }

    private static void archivo(HttpServletResponse resp, byte[] contenido, String nombre) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
        resp.setContentLength(contenido.length);
        resp.getOutputStream().write(contenido);
    }
}
