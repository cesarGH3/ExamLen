package servlet;

import logica.Calificacion;
import logica.EstadoIntento;
import logica.EstadoEvaluacionVirtual;
import logica.Estudiante;
import logica.Intento;
import logica.RecursoNoEncontradoException;
import logica.Respuesta;
import logica.ValidacionException;
import logica.Validador;
import logica.VersionOpcion;
import logica.VersionPregunta;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet(name = "EstudianteIntentoServlet", urlPatterns = "/app/estudiante/intento")
public class EstudianteIntentoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/estudiante/intento";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        int id = parametroId(req, "id");
        Intento intento = intentoDelEstudiante(estudiante, id);

        if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
            int segundos = intentoDAO.segundosRestantes(id);
            if (segundos > 0) {
                mostrarExamen(req, resp, intento, segundos);
                return;
            }
            servicioVirtual.finalizarIntentoVencido(id);
            intento = intentoDelEstudiante(estudiante, id);
        }
        mostrarResultado(req, resp, intento);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        int id = parametroId(req, "id");
        Intento intento = intentoDelEstudiante(estudiante, id);

        switch (parametro(req, "accion")) {
            case "responder":
                autosave(req, resp, intento);
                break;
            case "finalizar":
                if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
                    int segundos = intentoDAO.segundosRestantes(id);
                    if (segundos > 0) {
                        // Estricto: fuera del limite efectivo no se guarda ninguna respuesta (RF-47).
                        guardarRespuestasDelFormulario(req, intento);
                        List<String> sinResponder = !confirmado(req)
                                ? preguntasSinResponder(intento) : List.of();
                        if (!sinResponder.isEmpty()) {
                            confirmar(req, resp, "Finalizar evaluación",
                                    "Tienes " + sinResponder.size()
                                    + (sinResponder.size() == 1 ? " pregunta sin responder: " : " preguntas sin responder: ")
                                    + String.join(", ", sinResponder)
                                    + ". Si finalizas ahora, se enviarán en blanco.",
                                    null, req.getContextPath() + RUTA, "finalizar", id,
                                    "Finalizar evaluación", "boton", req.getContextPath() + RUTA + "?id=" + id);
                            return;
                        }
                    }
                    finalizar(intento);
                }
                redirigir(req, resp, RUTA + "?id=" + id);
                break;
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    // ------------------------------------------------------------------ vistas

    private void mostrarExamen(HttpServletRequest req, HttpServletResponse resp, Intento intento, int segundos)
            throws IOException {
        EstudianteInicioIntentoServlet.sesionDeExamen(req, segundos);
        int idVersion = intento.versionEvaluacionIntento.getIdVersionEvaluacion();
        List<VersionPregunta> preguntas = versionPreguntaDAO.listarPorVersionEvaluacion(idVersion);
        Map<Integer, Respuesta> previas = new HashMap<>();
        for (VersionPregunta pregunta : preguntas) {
            pregunta.versionOpcionesVersionPregunta
                    .addAll(versionOpcionDAO.listarPorVersionPregunta(pregunta.getIdVersionPregunta()));
            respuestaDAO.buscarPorIntentoYVersionPregunta(intento.getIdIntento(), pregunta.getIdVersionPregunta())
                    .ifPresent(respuesta -> previas.put(pregunta.getIdVersionPregunta(), respuesta));
        }

        List<Map<String, String>> filas = new ArrayList<>();
        int numero = 1;
        for (VersionPregunta pregunta : preguntas) {
            Respuesta previa = previas.get(pregunta.getIdVersionPregunta());
            Map<String, String> fila = new HashMap<>();
            fila.put("numero", String.valueOf(numero));
            fila.put("enunciado", pregunta.enunciadoVersionPregunta);
            fila.put("puntaje", formatoNota(pregunta.puntajeVersionPregunta));
            if (!pregunta.versionOpcionesVersionPregunta.isEmpty()) {
                StringBuilder opciones = new StringBuilder();
                for (VersionOpcion opcion : pregunta.versionOpcionesVersionPregunta) {
                    boolean marcada = previa != null && previa.versionOpcionRespuesta != null
                            && previa.versionOpcionRespuesta.getIdVersionOpcion() == opcion.getIdVersionOpcion();
                    Map<String, String> datosOpcion = new HashMap<>();
                    datosOpcion.put("id", String.valueOf(pregunta.getIdVersionPregunta()));
                    datosOpcion.put("valor", String.valueOf(opcion.getIdVersionOpcion()));
                    datosOpcion.put("atributos", marcada ? " checked" : "");
                    datosOpcion.put("texto", opcion.textoVersionOpcion);
                    opciones.append(vistas.componer("_opcion-examen.html", datosOpcion));
                }
                fila.put("opciones", opciones.toString());
                fila.put("respuestaTexto", "");
            } else {
                String valor = previa != null && previa.contenidoRespuesta != null ? previa.contenidoRespuesta : "";
                fila.put("opciones", "");
                Map<String, String> datosTexto = new HashMap<>();
                datosTexto.put("id", String.valueOf(pregunta.getIdVersionPregunta()));
                datosTexto.put("valor", valor);
                fila.put("respuestaTexto", vistas.componer("_respuesta-texto.html", datosTexto));
            }
            filas.add(fila);
            numero++;
        }

        int idVirtual = intentoDAO.idVirtualDeIntento(intento.getIdIntento());
        boolean supervision = virtualDAO.buscarPorId(idVirtual)
                .map(v -> v.supervisionActivaEvaluacionVirtual).orElse(false);

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(intento.getIdIntento()));
        datos.put("titulo", intentoDAO.tituloEvaluacionDeIntento(intento.getIdIntento()).orElse("Evaluación"));
        datos.put("version", intento.versionEvaluacionIntento.codigoVersionEvaluacion);
        datos.put("segundos", String.valueOf(segundos));
        datos.put("supervision", supervision ? "1" : "0");
        datos.put("csrf", csrf(req));
        datos.put("filasPreguntas", componerFilas("_pregunta-examen.html", filas,
                parrafoVacio("Esta versión no tiene preguntas.")));
        renderizar(req, resp, "estudiante-intento.html", datos, null);
    }

    private void mostrarResultado(HttpServletRequest req, HttpServletResponse resp, Intento intento)
            throws IOException {
        EstudianteInicioIntentoServlet.sesionNormal(req);
        int pendientes = respuestaDAO.contarPendientes(intento.getIdIntento());
        EstadoEvaluacionVirtual estadoVirtual = virtualDAO.buscarPorId(intentoDAO.idVirtualDeIntento(intento.getIdIntento()))
                .map(v -> v.estadoEvaluacionVirtual).orElse(null);

        Map<String, String> datos = new HashMap<>();
        datos.put("titulo", intentoDAO.tituloEvaluacionDeIntento(intento.getIdIntento()).orElse("Evaluación"));
        if (estadoVirtual == EstadoEvaluacionVirtual.CANCELADA) {
            Map<String, String> cuerpo = new HashMap<>();
            cuerpo.put("filasDetalle", componerFilas("_fila-resultado-cancelado.html", detalleFilas(intento),
                    filaVacia("2", "Esta versión no tiene preguntas.")));
            datos.put("estado", "Cancelada");
            datos.put("cuerpo", vistas.componer("_resultado-cancelada.html", cuerpo));
        } else if (estadoVirtual == EstadoEvaluacionVirtual.ABIERTA
                || intento.estadoIntento != EstadoIntento.CALIFICADO) {
            datos.put("estado", "Pendiente de calificación");
            datos.put("cuerpo", vistas.componer("_resultado-pendiente.html", new HashMap<>()));
        } else {
            Map<String, String> cuerpo = new HashMap<>();
            cuerpo.put("nota", Calificacion.formato(BigDecimal.valueOf(intento.calificacionIntento)));
            cuerpo.put("total", Calificacion.formato(Calificacion.MAXIMA));
            cuerpo.put("aviso", pendientes > 0
                    ? Html.alerta(vistas, "alerta-info", "Tu evaluación tiene respuestas abiertas pendientes de calificación del docente.")
                    : "");
            cuerpo.put("filasDetalle", componerFilas("_fila-resultado-estudiante.html", detalleFilas(intento),
                    filaVacia("4", "Esta versión no tiene preguntas.")));
            datos.put("estado", Etiquetas.de(intento.estadoIntento));
            datos.put("cuerpo", vistas.componer("_resultado-examen.html", cuerpo));
        }
        renderizar(req, resp, "estudiante-resultado.html", datos, null);
    }

    /** Filas del detalle por pregunta: lo respondido, el puntaje y el comentario del docente (RF-56). */
    private List<Map<String, String>> detalleFilas(Intento intento) {
        Map<Integer, Respuesta> respuestas = new HashMap<>();
        for (Respuesta respuesta : respuestaDAO.listarConPregunta(intento.getIdIntento())) {
            respuestas.put(respuesta.versionPreguntaRespuesta.getIdVersionPregunta(), respuesta);
        }
        Set<Integer> pendientes = new HashSet<>(respuestaDAO.idsPendientes(intento.getIdIntento()));
        List<Map<String, String>> filas = new ArrayList<>();
        for (VersionPregunta pregunta : versionPreguntaDAO
                .listarPorVersionEvaluacion(intento.versionEvaluacionIntento.getIdVersionEvaluacion())) {
            Respuesta respuesta = respuestas.get(pregunta.getIdVersionPregunta());
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", Html.resumir(pregunta.enunciadoVersionPregunta, 110));
            fila.put("dada", respuesta == null ? "(sin responder)"
                    : (respuesta.versionOpcionRespuesta != null
                            ? respuesta.versionOpcionRespuesta.textoVersionOpcion
                            : (respuesta.contenidoRespuesta == null || respuesta.contenidoRespuesta.isBlank()
                                    ? "(sin responder)" : respuesta.contenidoRespuesta)));
            if (respuesta != null && pendientes.contains(respuesta.getIdRespuesta())) {
                fila.put("puntaje", "Pendiente");
            } else {
                fila.put("puntaje", respuesta == null
                        ? "0 / " + formatoNota(pregunta.puntajeVersionPregunta)
                        : formatoNota(respuesta.puntajeRespuesta) + " / " + formatoNota(pregunta.puntajeVersionPregunta));
            }
            fila.put("comentario", respuesta == null || respuesta.comentarioDocenteRespuesta == null
                    || respuesta.comentarioDocenteRespuesta.isBlank() ? "—" : respuesta.comentarioDocenteRespuesta);
            filas.add(fila);
        }
        return filas;
    }

    // ------------------------------------------------------------------ respuestas

    private void autosave(HttpServletRequest req, HttpServletResponse resp, Intento intento) throws IOException {
        if (intento.estadoIntento != EstadoIntento.EN_CURSO
                || intentoDAO.segundosRestantes(intento.getIdIntento()) <= 0) {
            servicioVirtual.finalizarIntentoVencido(intento.getIdIntento());
            EstudianteInicioIntentoServlet.sesionNormal(req);
            json(resp, HttpServletResponse.SC_CONFLICT, "{\"ok\":false,\"motivo\":\"El tiempo del intento terminó.\"}");
            return;
        }
        int idVersionPregunta = Validador.parsearId(req.getParameter("idVersionPregunta"));
        int idVersion = intento.versionEvaluacionIntento.getIdVersionEvaluacion();
        if (idVersionPregunta < 0 || !versionPreguntaDAO.perteneceAVersion(idVersionPregunta, idVersion)) {
            json(resp, HttpServletResponse.SC_BAD_REQUEST, "{\"ok\":false,\"motivo\":\"Pregunta inválida.\"}");
            return;
        }
        int idOpcion = Validador.parsearId(req.getParameter("idOpcion"));
        if (idOpcion > 0 && !versionOpcionDAO.perteneceAVersionPregunta(idOpcion, idVersionPregunta)) {
            json(resp, HttpServletResponse.SC_BAD_REQUEST, "{\"ok\":false,\"motivo\":\"Opción inválida.\"}");
            return;
        }
        VersionPregunta pregunta = new VersionPregunta();
        pregunta.setIdVersionPregunta(idVersionPregunta);
        String contenido = idOpcion > 0 ? "" : Validador.limpiarTexto(req.getParameter("contenido") == null
                ? "" : req.getParameter("contenido"));
        if (!guardarRespuesta(intento, pregunta, contenido, idOpcion)) {
            // El limite efectivo paso entre la comprobacion rapida y la escritura: nada se guarda.
            servicioVirtual.finalizarIntentoVencido(intento.getIdIntento());
            EstudianteInicioIntentoServlet.sesionNormal(req);
            json(resp, HttpServletResponse.SC_CONFLICT, "{\"ok\":false,\"motivo\":\"El tiempo del intento terminó.\"}");
            return;
        }
        json(resp, HttpServletResponse.SC_OK, "{\"ok\":true}");
    }

    private void guardarRespuestasDelFormulario(HttpServletRequest req, Intento intento) {
        int idVersion = intento.versionEvaluacionIntento.getIdVersionEvaluacion();
        for (VersionPregunta pregunta : versionPreguntaDAO.listarPorVersionEvaluacion(idVersion)) {
            String idOpcionTexto = Validador.limpiar(req.getParameter("opcion_" + pregunta.getIdVersionPregunta()));
            String contenido = req.getParameter("contenido_" + pregunta.getIdVersionPregunta());
            if (!idOpcionTexto.isEmpty()) {
                int idOpcion = Validador.parsearId(idOpcionTexto);
                if (idOpcion > 0 && versionOpcionDAO.perteneceAVersionPregunta(idOpcion, pregunta.getIdVersionPregunta())) {
                    guardarRespuesta(intento, pregunta, "", idOpcion);
                }
            } else if (contenido != null && !contenido.isBlank()) {
                guardarRespuesta(intento, pregunta, Validador.limpiarTexto(contenido), -1);
            }
        }
    }

    /** Guarda solo si el intento sigue vigente (RF-47); devuelve false si llego tarde. */
    private boolean guardarRespuesta(Intento intento, VersionPregunta pregunta, String contenido, int idOpcion) {
        return respuestaDAO.guardarRespuestaVigente(intento, pregunta, contenido, idOpcion);
    }

    /**
     * Califica lo automático y finaliza; si quedan abiertas queda pendiente de calificación.
     * Si el límite efectivo ya pasó, lo finaliza el servicio con la fecha del límite (RF-47).
     */
    private void finalizar(Intento intento) {
        if (intentoDAO.segundosRestantes(intento.getIdIntento()) <= 0) {
            servicioVirtual.finalizarIntentoVencido(intento.getIdIntento());
            return;
        }
        int pendientes = respuestaDAO.calificarAutomaticas(intento.getIdIntento());
        if (pendientes == 0) {
            intentoDAO.calificarIntento(intento);
        } else {
            intentoDAO.finalizarIntento(intento);
        }
    }

    private Intento intentoDelEstudiante(Estudiante estudiante, int id) {
        java.util.Optional<Intento> propio = intentoDAO.buscarPorIdDeEstudiante(id, estudiante.getIdUsuario());
        verificarPropiedad(propio.isPresent(), intentoDAO.buscarPorId(id).isPresent(), "Intento no encontrado");
        return propio.get();
    }

    /** Números (1..N) de las preguntas de la versión que quedaron sin responder (RF-48). */
    private List<String> preguntasSinResponder(Intento intento) {
        Map<Integer, Respuesta> guardadas = new HashMap<>();
        for (Respuesta respuesta : respuestaDAO.listarConPregunta(intento.getIdIntento())) {
            guardadas.put(respuesta.versionPreguntaRespuesta.getIdVersionPregunta(), respuesta);
        }
        List<String> numeros = new ArrayList<>();
        int numero = 1;
        for (VersionPregunta pregunta : versionPreguntaDAO
                .listarPorVersionEvaluacion(intento.versionEvaluacionIntento.getIdVersionEvaluacion())) {
            Respuesta respuesta = guardadas.get(pregunta.getIdVersionPregunta());
            boolean respondida = respuesta != null
                    && (respuesta.versionOpcionRespuesta != null
                            || (respuesta.contenidoRespuesta != null && !respuesta.contenidoRespuesta.isBlank()));
            if (!respondida) {
                numeros.add(String.valueOf(numero));
            }
            numero++;
        }
        return numeros;
    }

    private static boolean confirmado(HttpServletRequest req) {
        return "1".equals(req.getParameter("confirmado"));
    }

    private static void json(HttpServletResponse resp, int estado, String cuerpo) throws IOException {
        resp.setStatus(estado);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(cuerpo);
    }

    private static String formatoNota(double nota) {
        if (nota == Math.floor(nota)) {
            return String.valueOf((int) nota);
        }
        return String.valueOf(nota);
    }
}
