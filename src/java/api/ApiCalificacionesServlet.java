package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import logica.EstadoInscripcion;
import logica.EstadoIntento;
import logica.EvaluacionVirtual;
import logica.Fechas;
import logica.Grupo;
import logica.Inscripcion;
import logica.Intento;
import logica.Validador;
import modelo.EvaluacionVirtualDAO;
import modelo.GrupoDAO;
import modelo.InscripcionDAO;
import modelo.IntentoDAO;
import servlet.InicializadorAplicacion;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * GET /api/v1/calificaciones?grupoId=X&evaluacionId=Y (evaluacionId = evaluación virtual del
 * grupo). Solo el docente dueño del grupo. Distingue CALIFICADO, PENDIENTE y NO_PRESENTADO
 * (NO_PRESENTADO nunca se convierte en nota 0: calificación y fecha quedan en null).
 */
@WebServlet(name = "ApiCalificacionesServlet", urlPatterns = "/api/v1/calificaciones")
public class ApiCalificacionesServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private GrupoDAO grupoDAO;
    private InscripcionDAO inscripcionDAO;
    private EvaluacionVirtualDAO virtualDAO;
    private IntentoDAO intentoDAO;

    @Override
    public void init() throws ServletException {
        grupoDAO = (GrupoDAO) getServletContext().getAttribute(InicializadorAplicacion.GRUPO_DAO);
        inscripcionDAO = (InscripcionDAO) getServletContext().getAttribute(InicializadorAplicacion.INSCRIPCION_DAO);
        virtualDAO = (EvaluacionVirtualDAO) getServletContext()
                .getAttribute(InicializadorAplicacion.VIRTUAL_DAO);
        intentoDAO = (IntentoDAO) getServletContext().getAttribute(InicializadorAplicacion.INTENTO_DAO);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            consultar(req, resp);
        } catch (RuntimeException e) {
            ApioJson.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                    "No fue posible completar la operación.");
        }
    }

    private void consultar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApioJson.ROL_DOCENTE.equals(ApioJson.rol(req))) {
            ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                    "Solo el docente dueño del grupo puede consultar calificaciones.");
            return;
        }
        int grupoId = Validador.parsearId(req.getParameter("grupoId"));
        int evaluacionId = Validador.parsearId(req.getParameter("evaluacionId"));
        if (grupoId < 0 || evaluacionId < 0) {
            ApioJson.error(resp, HttpServletResponse.SC_BAD_REQUEST, "PARAMETRO_INVALIDO",
                    "Los parámetros grupoId y evaluacionId son obligatorios.");
            return;
        }
        int idDocente = ApioJson.idUsuario(req);
        Grupo grupo = grupoDAO.buscarPorIdDeDocente(grupoId, idDocente).orElse(null);
        if (grupo == null) {
            if (grupoDAO.buscarPorId(grupoId).isPresent()) {
                ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                        "El grupo pertenece a otro docente.");
            } else {
                ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO", "Grupo no encontrado.");
            }
            return;
        }
        EvaluacionVirtual virtual = virtualDAO.buscarPorIdDeDocente(evaluacionId, idDocente).orElse(null);
        if (virtual == null) {
            if (virtualDAO.buscarPorId(evaluacionId).isPresent()) {
                ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                        "La evaluación pertenece a otro docente.");
            } else {
                ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO",
                        "Evaluación no encontrada.");
            }
            return;
        }
        if (virtualDAO.idGrupoDeVirtual(evaluacionId) != grupoId) {
            ApioJson.error(resp, HttpServletResponse.SC_BAD_REQUEST, "PARAMETROS_INCONSISTENTES",
                    "La evaluación indicada no pertenece a ese grupo.");
            return;
        }

        JsonObject grupoJson = new JsonObject();
        grupoJson.addProperty("id", grupo.consultarIdGrupo());
        grupoJson.addProperty("nombre", grupo.nombreGrupo);
        grupoJson.addProperty("periodo", grupo.periodoGrupo);

        JsonObject evaluacionJson = new JsonObject();
        evaluacionJson.addProperty("id", evaluacionId);
        evaluacionJson.addProperty("titulo", virtual.evaluacionEvaluacionVirtual.tituloEvaluacion);

        JsonArray resultados = new JsonArray();
        for (Inscripcion inscripcion : inscripcionDAO.listarPorGrupo(grupoId)) {
            if (inscripcion.estadoInscripcion != EstadoInscripcion.ACTIVA) {
                continue;
            }
            List<Intento> intentos = intentoDAO.listarPorEvaluacionEInscripcion(evaluacionId,
                    inscripcion.consultarIdInscripcion());
            JsonObject fila = new JsonObject();
            fila.addProperty("identificacion", inscripcion.estudianteInscripcion.identificacionUsuario);
            fila.addProperty("nombre", inscripcion.estudianteInscripcion.nombreUsuario);
            JsonArray intentosJson = new JsonArray();
            for (Intento intento : intentos) {
                JsonObject item = new JsonObject();
                item.addProperty("id", intento.consultarIdIntento());
                item.addProperty("numeroIntento", intento.consultarNumeroIntento());
                item.addProperty("estado", intento.estadoIntento.name());
                if (intento.estadoIntento == EstadoIntento.CALIFICADO) {
                    item.addProperty("calificacion", intento.calificacionIntento);
                } else {
                    item.add("calificacion", com.google.gson.JsonNull.INSTANCE);
                }
                if (intento.fechaFinIntento != null) {
                    item.addProperty("fechaPresentacion", Fechas.formato(Fechas.PATRON_FECHA_HORA,
                            intento.fechaFinIntento));
                } else {
                    item.add("fechaPresentacion", com.google.gson.JsonNull.INSTANCE);
                }
                intentosJson.add(item);
            }
            fila.add("intentos", intentosJson);
            if (intentos.isEmpty()) {
                fila.addProperty("estado", "NO_PRESENTADO");
                fila.add("calificacion", com.google.gson.JsonNull.INSTANCE);
                fila.add("fechaPresentacion", com.google.gson.JsonNull.INSTANCE);
            } else if (intentos.size() == 1) {
                Intento intento = intentos.get(0);
                if (intento.estadoIntento == EstadoIntento.CALIFICADO) {
                    fila.addProperty("estado", "CALIFICADO");
                    fila.addProperty("calificacion", intento.calificacionIntento);
                    fila.addProperty("fechaPresentacion", Fechas.formato(Fechas.PATRON_FECHA_HORA,
                            intento.fechaFinIntento));
                } else {
                    fila.addProperty("estado", "PENDIENTE");
                    fila.add("calificacion", com.google.gson.JsonNull.INSTANCE);
                    fila.add("fechaPresentacion", com.google.gson.JsonNull.INSTANCE);
                }
            } else {
                fila.addProperty("estado", "MULTIPLES");
                fila.add("calificacion", com.google.gson.JsonNull.INSTANCE);
                fila.add("fechaPresentacion", com.google.gson.JsonNull.INSTANCE);
            }
            resultados.add(fila);
        }

        JsonObject cuerpo = new JsonObject();
        cuerpo.add("grupo", grupoJson);
        cuerpo.add("evaluacion", evaluacionJson);
        cuerpo.add("resultados", resultados);
        ApioJson.responderCorrecto(resp, cuerpo);
    }
}
