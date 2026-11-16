package servlet;

import logica.EstadoIntento;
import logica.Estudiante;
import logica.Evaluacion;
import logica.EvaluacionVirtual;
import logica.Inscripcion;
import logica.Intento;
import logica.RecursoNoEncontradoException;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import logica.VersionEvaluacion;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RNF-18 / CU-27: pantalla previa al intento. Muestra instrucciones, duración y, si la virtual
 * está supervisada, el aviso de qué se registra. Desde aquí se crea el intento.
 */
@WebServlet(name = "EstudianteInicioIntentoServlet", urlPatterns = "/app/estudiante/intento/inicio")
public class EstudianteInicioIntentoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/estudiante/intento/inicio";
    private static final String RUTA_INTENTO = "/app/estudiante/intento";
    private static final String RUTA_EVALUACIONES = "/app/estudiante/evaluaciones";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        EvaluacionVirtual virtual = virtualDAO.buscarPorId(parametroId(req, "id")).orElse(null);
        Inscripcion inscripcion = virtual == null ? null : inscripcionDe(estudiante, virtual);
        if (inscripcion == null) {
            agregarMensaje(req, MensajeFlash.Tipo.ERROR, "Evaluación no encontrada.");
            redirigir(req, resp, RUTA_EVALUACIONES);
            return;
        }
        List<Intento> intentos = intentoDAO.listarPorEvaluacionEInscripcion(
                virtual.consultarIdEvaluacionVirtual(), inscripcion.consultarIdInscripcion());
        Intento existente = null;
        for (Intento intento : intentos) {
            if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
                existente = intento;
                break;
            }
        }
        if (existente != null) {
            redirigir(req, resp, RUTA_INTENTO + "?id=" + existente.consultarIdIntento());
            return;
        }
        int intentosPermitidos = virtual.consultarIntentosPermitidos();
        if (intentos.size() >= intentosPermitidos) {
            agregarMensaje(req, MensajeFlash.Tipo.ERROR,
                    "Ya usaste los " + intentosPermitidos + " intentos permitidos para esta evaluación.");
            redirigir(req, resp, RUTA_EVALUACIONES);
            return;
        }
        if (!virtualDAO.disponibleAhora(virtual.consultarIdEvaluacionVirtual())) {
            agregarMensaje(req, MensajeFlash.Tipo.ERROR, "La evaluación no está disponible en este momento.");
            redirigir(req, resp, RUTA_EVALUACIONES);
            return;
        }

        Evaluacion evaluacion = virtual.evaluacionEvaluacionVirtual;
        Map<String, String> datos = new HashMap<>();
        datos.put("curso", inscripcion.grupoInscripcion.cursoGrupo.nombreCurso);
        datos.put("grupo", inscripcion.grupoInscripcion.nombreGrupo);
        datos.put("titulo", evaluacion.tituloEvaluacion);
        datos.put("descripcion", evaluacion.descripcionEvaluacion == null ? "" : evaluacion.descripcionEvaluacion);
        datos.put("duracion", String.valueOf(evaluacion.duracionMinutosEvaluacion));
        datos.put("preguntas", String.valueOf(evaluacion.numeroPreguntasEvaluacion));
        datos.put("idVirtual", String.valueOf(virtual.consultarIdEvaluacionVirtual()));
        datos.put("idInscripcion", String.valueOf(inscripcion.consultarIdInscripcion()));
        int siguienteIntento = 1;
        for (Intento previo : intentos) {
            siguienteIntento = Math.max(siguienteIntento, previo.consultarNumeroIntento() + 1);
        }
        datos.put("intentosUsados", String.valueOf(intentos.size()));
        datos.put("intentosPermitidos", String.valueOf(intentosPermitidos));
        datos.put("numeroIntento", String.valueOf(siguienteIntento));
        datos.put("destino", ruta(req, RUTA));
        datos.put("avisoEnvio", virtual.consultarExigirRespuestasCompletas()
                ? Html.alerta(vistas, "alerta-info", "Para enviar esta evaluación manualmente deberás "
                        + "responder todas las preguntas. Si se vence el tiempo, se enviará automáticamente.")
                : "");
        if (virtual.supervisionActivaEvaluacionVirtual) {
            datos.put("avisoSupervision", Html.alertaConLista(vistas, "alerta-info",
                    "Durante este examen se registran, con fecha y hora, estos indicadores:",
                    List.of("Cambios de pestaña", "Pérdidas de foco", "Salidas de la página",
                            "Desconexiones", "Reconexiones")));
            datos.put("notaSupervision", Html.alerta(vistas, "alerta-info",
                    "Los indicadores son información para que tu docente tenga contexto del intento: "
                    + "no son una acusación ni una conclusión automática."));
        } else {
            datos.put("avisoSupervision", "");
            datos.put("notaSupervision", "");
        }
        renderizar(req, resp, "estudiante-intento-inicio.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        try {
            int idVirtual = parametroId(req, "idVirtual");
            int idInscripcion = parametroId(req, "idInscripcion");
            Inscripcion inscripcion = inscripcionDAO
                    .buscarPorIdDeEstudiante(idInscripcion, estudiante.consultarIdUsuario())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Inscripción no encontrada"));
            EvaluacionVirtual virtual = virtualDAO.buscarPorId(idVirtual)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Evaluación no encontrada"));
            if (virtualDAO.idGrupoDeVirtual(idVirtual) != inscripcion.grupoInscripcion.consultarIdGrupo()) {
                throw new RecursoNoEncontradoException("Evaluación no encontrada");
            }
            List<VersionEvaluacion> versiones = virtualDAO.listarVersiones(idVirtual);
            Intento intento = intentoDAO.iniciarIntento(virtual, inscripcion, versiones);
            sesionDeExamen(req, intentoDAO.segundosRestantes(intento.consultarIdIntento()));
            redirigir(req, resp, RUTA_INTENTO + "?id=" + intento.consultarIdIntento());
        } catch (ValidacionException e) {
            agregarErrores(req, e.consultarErrores());
            redirigir(req, resp, RUTA_EVALUACIONES);
        }
    }

    /**
     * RNF-03: durante un intento EN_CURSO la sesión no debe expirar por la regla de 30 minutos;
     * se ajusta a tiempo restante + margen de 15 min (mínimo 30 min, tope 24 h).
     */
    static void sesionDeExamen(HttpServletRequest req, int segundosRestantes) {
        int segundos = Math.max(1800, Math.min(segundosRestantes + 900, 24 * 3600));
        req.getSession(true).setMaxInactiveInterval(segundos);
    }

    /** RNF-03: al salir del contexto del examen la sesión vuelve a los 30 minutos. */
    static void sesionNormal(HttpServletRequest req) {
        req.getSession(true).setMaxInactiveInterval(1800);
    }

    private Inscripcion inscripcionDe(Estudiante estudiante, EvaluacionVirtual virtual) {
        int idGrupo = virtualDAO.idGrupoDeVirtual(virtual.consultarIdEvaluacionVirtual());
        for (Inscripcion inscripcion : inscripcionDAO.listarActivasPorEstudiante(estudiante.consultarIdUsuario())) {
            if (inscripcion.grupoInscripcion.consultarIdGrupo() == idGrupo) {
                return inscripcion;
            }
        }
        return null;
    }
}
