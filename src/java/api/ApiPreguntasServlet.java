package api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import logica.Competencia;
import logica.CriteriosBusquedaPregunta;
import logica.Dificultad;
import logica.EstadoPregunta;
import logica.NivelIngles;
import logica.OpcionRespuesta;
import logica.Pregunta;
import logica.TipoPregunta;
import modelo.OpcionRespuestaDAO;
import modelo.PreguntaDAO;
import servlet.InicializadorAplicacion;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * GET /api/v1/preguntas: banco de preguntas APROBADAS del docente autenticado, con filtros
 * opcionales (nivel, competencia, dificultad, tema, tipo) aplicados con PreparedStatement en
 * el DAO real. Solo DOCENTE (dueño de sus bancos); estudiantes y administradores reciben 403.
 * Para el docente dueño se incluyen respuestaEsperada y esCorrecta (ya los ve en ExamLen).
 */
@WebServlet(name = "ApiPreguntasServlet", urlPatterns = "/api/v1/preguntas")
public class ApiPreguntasServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private PreguntaDAO preguntaDAO;
    private OpcionRespuestaDAO opcionDAO;

    @Override
    public void init() throws ServletException {
        preguntaDAO = (PreguntaDAO) getServletContext().getAttribute(InicializadorAplicacion.PREGUNTA_DAO);
        opcionDAO = (OpcionRespuestaDAO) getServletContext().getAttribute(InicializadorAplicacion.OPCION_DAO);
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
                    "Solo un docente puede consultar el banco de preguntas.");
            return;
        }
        String nivelParam = req.getParameter("nivel");
        String competenciaParam = req.getParameter("competencia");
        String dificultadParam = req.getParameter("dificultad");
        String tipoParam = req.getParameter("tipo");
        NivelIngles nivel = ApioJson.enumerar(NivelIngles.class, nivelParam);
        Competencia competencia = ApioJson.enumerar(Competencia.class, competenciaParam);
        Dificultad dificultad = ApioJson.enumerar(Dificultad.class, dificultadParam);
        TipoPregunta tipo = ApioJson.enumerar(TipoPregunta.class, tipoParam);
        if ((nivelParam != null && !nivelParam.isBlank() && nivel == null)
                || (competenciaParam != null && !competenciaParam.isBlank() && competencia == null)
                || (dificultadParam != null && !dificultadParam.isBlank() && dificultad == null)
                || (tipoParam != null && !tipoParam.isBlank() && tipo == null)) {
            ApioJson.error(resp, HttpServletResponse.SC_BAD_REQUEST, "FILTRO_INVALIDO",
                    "Alguno de los filtros enviados no es válido.");
            return;
        }

        CriteriosBusquedaPregunta criterios = new CriteriosBusquedaPregunta();
        criterios.estado = EstadoPregunta.APROBADA;
        criterios.nivel = nivel;
        criterios.competencia = competencia;
        criterios.dificultad = dificultad;
        criterios.tipo = tipo;
        criterios.tema = req.getParameter("tema");

        JsonArray lista = new JsonArray();
        for (Pregunta pregunta : preguntaDAO.buscarDeDocente(ApioJson.idUsuario(req), criterios)) {
            if (pregunta.estadoPregunta != EstadoPregunta.APROBADA) {
                continue;
            }
            JsonObject objeto = new JsonObject();
            objeto.addProperty("id", pregunta.getIdPregunta());
            objeto.addProperty("enunciado", pregunta.enunciadoPregunta);
            objeto.addProperty("tipo", pregunta.tipoPregunta == null ? null : pregunta.tipoPregunta.name());
            objeto.addProperty("nivel", pregunta.nivelPregunta == null ? null : pregunta.nivelPregunta.name());
            objeto.addProperty("dificultad",
                    pregunta.dificultadPregunta == null ? null : pregunta.dificultadPregunta.name());
            objeto.addProperty("competencia",
                    pregunta.competenciaPregunta == null ? null : pregunta.competenciaPregunta.name());
            objeto.addProperty("tema", pregunta.temaPregunta);
            objeto.addProperty("respuestaEsperada", pregunta.respuestaEsperadaPregunta);
            JsonArray opciones = new JsonArray();
            for (OpcionRespuesta opcion : opcionDAO.listarPorPregunta(pregunta.getIdPregunta())) {
                JsonObject opcionJson = new JsonObject();
                opcionJson.addProperty("id", opcion.getIdOpcionRespuesta());
                opcionJson.addProperty("texto", opcion.textoOpcionRespuesta);
                opcionJson.addProperty("esCorrecta", opcion.esCorrectaOpcionRespuesta);
                opciones.add(opcionJson);
            }
            objeto.add("opciones", opciones);
            lista.add(objeto);
        }
        JsonObject cuerpo = new JsonObject();
        cuerpo.add("preguntas", lista);
        ApioJson.ok(resp, cuerpo);
    }
}
