package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import logica.Curso;
import logica.EvaluacionVirtual;
import logica.Grupo;
import logica.Validador;
import modelo.CursoDAO;
import modelo.EvaluacionVirtualDAO;
import modelo.GrupoDAO;
import servlet.InicializadorAplicacion;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Catalogos auxiliares para los selectores de ExamBridge (no cuentan como servicios):
 *   GET /api/v1/catalogos/grupos
 *   GET /api/v1/catalogos/evaluaciones?grupoId=X
 * Respetan los permisos reales de ExamLen: docente solo sus cursos/grupos; estudiante sus
 * inscripciones activas; otros roles no aplican (403).
 */
@WebServlet(name = "ApiCatalogosServlet", urlPatterns = "/api/v1/catalogos/*")
public class ApiCatalogosServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private CursoDAO cursoDAO;
    private GrupoDAO grupoDAO;
    private EvaluacionVirtualDAO virtualDAO;

    @Override
    public void init() throws ServletException {
        cursoDAO = (CursoDAO) getServletContext().getAttribute(InicializadorAplicacion.CURSO_DAO);
        grupoDAO = (GrupoDAO) getServletContext().getAttribute(InicializadorAplicacion.GRUPO_DAO);
        virtualDAO = (EvaluacionVirtualDAO) getServletContext()
                .getAttribute(InicializadorAplicacion.VIRTUAL_DAO);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String ruta = req.getPathInfo() == null ? "" : req.getPathInfo();
            if ("/grupos".equals(ruta)) {
                grupos(req, resp);
            } else if ("/evaluaciones".equals(ruta)) {
                evaluaciones(req, resp);
            } else {
                ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO", "Catálogo no encontrado.");
            }
        } catch (RuntimeException e) {
            ApioJson.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                    "No fue posible completar la operación.");
        }
    }

    private void grupos(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int idUsuario = ApioJson.idUsuario(req);
        String rol = ApioJson.rol(req);
        JsonArray lista = new JsonArray();
        if (ApioJson.ROL_DOCENTE.equals(rol)) {
            for (Curso curso : cursoDAO.listarPorDocente(idUsuario)) {
                for (Grupo grupo : grupoDAO.listarPorCurso(curso.consultarIdCurso())) {
                    lista.add(agrupar(grupo, curso.nombreCurso));
                }
            }
        } else {
            ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                    "Este rol no tiene acceso a catálogos académicos.");
            return;
        }
        JsonObject cuerpo = new JsonObject();
        cuerpo.add("grupos", lista);
        ApioJson.responderCorrecto(resp, cuerpo);
    }

    private static JsonObject agrupar(Grupo grupo, String curso) {
        JsonObject objeto = new JsonObject();
        objeto.addProperty("id", grupo.consultarIdGrupo());
        objeto.addProperty("nombre", grupo.nombreGrupo);
        objeto.addProperty("periodo", grupo.periodoGrupo);
        objeto.addProperty("curso", curso);
        return objeto;
    }

    private void evaluaciones(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rol = ApioJson.rol(req);
        if (!ApioJson.ROL_DOCENTE.equals(rol)) {
            ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                    "Este rol no tiene acceso a catálogos académicos.");
            return;
        }
        int grupoId = Validador.parsearId(req.getParameter("grupoId"));
        if (grupoId < 0) {
            ApioJson.error(resp, HttpServletResponse.SC_BAD_REQUEST, "PARAMETRO_INVALIDO",
                    "El parámetro grupoId es obligatorio.");
            return;
        }
        int idDocente = ApioJson.idUsuario(req);
        if (grupoDAO.buscarPorIdDeDocente(grupoId, idDocente).isEmpty()) {
            if (grupoDAO.buscarPorId(grupoId).isPresent()) {
                ApioJson.error(resp, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                        "El grupo pertenece a otro docente.");
            } else {
                ApioJson.error(resp, HttpServletResponse.SC_NOT_FOUND, "NO_ENCONTRADO", "Grupo no encontrado.");
            }
            return;
        }
        JsonArray lista = new JsonArray();
        for (EvaluacionVirtual virtual : virtualDAO.listarPorGrupoDeDocente(grupoId, idDocente)) {
            JsonObject objeto = new JsonObject();
            objeto.addProperty("id", virtual.consultarIdEvaluacionVirtual());
            objeto.addProperty("titulo", virtual.evaluacionEvaluacionVirtual.tituloEvaluacion);
            objeto.addProperty("estado", virtual.estadoEvaluacionVirtual.name());
            objeto.addProperty("intentosPermitidos", virtual.consultarIntentosPermitidos());
            objeto.addProperty("inicio", logica.Fechas.formato(logica.Fechas.PATRON_FECHA_HORA,
                    virtual.fechaInicioEvaluacionVirtual));
            objeto.addProperty("fin", logica.Fechas.formato(logica.Fechas.PATRON_FECHA_HORA,
                    virtual.fechaFinEvaluacionVirtual));
            lista.add(objeto);
        }
        JsonObject cuerpo = new JsonObject();
        cuerpo.add("evaluaciones", lista);
        ApioJson.responderCorrecto(resp, cuerpo);
    }
}
