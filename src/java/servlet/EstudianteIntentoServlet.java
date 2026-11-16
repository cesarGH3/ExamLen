package servlet;

import logica.Calificacion;
import logica.EstadoIntento;
import logica.EstadoEvaluacionVirtual;
import logica.Estudiante;
import logica.EvaluacionVirtual;
import logica.Intento;
import logica.Respuesta;
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
                guardarAutomaticamente(req, resp, intento);
                break;
            case "finalizar":
                if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
                    int segundos = intentoDAO.segundosRestantes(id);
                    if (segundos > 0) {
                        // Estricto: fuera del limite efectivo no se guarda ninguna respuesta (RF-47).
                        guardarRespuestasDelFormulario(req, intento);
                        EvaluacionVirtual virtual = virtualDAO.buscarPorId(
                                intentoDAO.idVirtualDeIntento(id)).orElse(null);
                        List<Integer> pendientes = servicioVirtual.consultarPreguntasPendientes(intento);
                        boolean permiteEnvio = virtual == null
                                || servicioVirtual.puedeFinalizarManualmente(intento, virtual);
                        if (!permiteEnvio) {
                            agregarErrores(req, List.of("No puedes enviar todavía la evaluación. "
                                    + "Tienes " + pendientes.size()
                                    + (pendientes.size() == 1 ? " pregunta sin responder: " : " preguntas sin responder: ")
                                    + enumerarPreguntas(pendientes)
                                    + ". El intento sigue en curso."));
                            redirigir(req, resp, RUTA + "?id=" + id);
                            return;
                        }
                        if (!pendientes.isEmpty() && !confirmado(req)) {
                            confirmar(req, resp, "Finalizar evaluación",
                                    "Tienes " + pendientes.size()
                                    + (pendientes.size() == 1 ? " pregunta sin responder: " : " preguntas sin responder: ")
                                    + enumerarPreguntas(pendientes)
                                    + ". Si finalizas ahora, esas preguntas se enviarán sin respuesta. ¿Deseas enviarla?",
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
        int idVersion = intento.versionEvaluacionIntento.consultarIdVersionEvaluacion();
        List<VersionPregunta> preguntas = versionPreguntaDAO.listarPorVersionEvaluacion(idVersion);
        Map<Integer, Respuesta> previas = new HashMap<>();
        for (VersionPregunta pregunta : preguntas) {
            pregunta.versionOpcionesVersionPregunta
                    .addAll(versionOpcionDAO.listarPorVersionPregunta(pregunta.consultarIdVersionPregunta()));
            respuestaDAO.buscarPorIntentoYVersionPregunta(intento.consultarIdIntento(), pregunta.consultarIdVersionPregunta())
                    .ifPresent(respuesta -> previas.put(pregunta.consultarIdVersionPregunta(), respuesta));
        }

        List<Map<String, String>> filas = new ArrayList<>();
        int numero = 1;
        for (VersionPregunta pregunta : preguntas) {
            Respuesta previa = previas.get(pregunta.consultarIdVersionPregunta());
            Map<String, String> fila = new HashMap<>();
            fila.put("numero", String.valueOf(numero));
            fila.put("enunciado", pregunta.enunciadoVersionPregunta);
            fila.put("puntaje", formatoNota(pregunta.puntajeVersionPregunta));
            if (!pregunta.versionOpcionesVersionPregunta.isEmpty()) {
                StringBuilder opciones = new StringBuilder();
                for (VersionOpcion opcion : pregunta.versionOpcionesVersionPregunta) {
                    boolean marcada = previa != null && previa.versionOpcionRespuesta != null
                            && previa.versionOpcionRespuesta.consultarIdVersionOpcion() == opcion.consultarIdVersionOpcion();
                    Map<String, String> datosOpcion = new HashMap<>();
                    datosOpcion.put("id", String.valueOf(pregunta.consultarIdVersionPregunta()));
                    datosOpcion.put("valor", String.valueOf(opcion.consultarIdVersionOpcion()));
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
                datosTexto.put("id", String.valueOf(pregunta.consultarIdVersionPregunta()));
                datosTexto.put("valor", valor);
                fila.put("respuestaTexto", vistas.componer("_respuesta-texto.html", datosTexto));
            }
            filas.add(fila);
            numero++;
        }

        int idVirtual = intentoDAO.idVirtualDeIntento(intento.consultarIdIntento());
        EvaluacionVirtual virtual = virtualDAO.buscarPorId(idVirtual).orElse(null);
        boolean supervision = virtual != null && virtual.supervisionActivaEvaluacionVirtual;
        boolean exigirCompletas = virtual != null && virtual.consultarExigirRespuestasCompletas();

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(intento.consultarIdIntento()));
        datos.put("numero", String.valueOf(intento.consultarNumeroIntento()));
        datos.put("titulo", intentoDAO.tituloEvaluacionDeIntento(intento.consultarIdIntento()).orElse("EvaluaciA3n"));
        datos.put("version", intento.versionEvaluacionIntento.codigoVersionEvaluacion);
        datos.put("segundos", String.valueOf(segundos));
        datos.put("supervision", supervision ? "1" : "0");
        datos.put("avisoEnvio", exigirCompletas
                ? Html.alerta(vistas, "alerta-info", "Esta evaluación exige responder todas las preguntas "
                        + "antes de enviarla manualmente. Si se vence el tiempo, se enviará automáticamente.")
                : "");
        datos.put("csrf", csrf(req));
        datos.put("filasPreguntas", componerFilas("_pregunta-examen.html", filas,
                parrafoVacio("Esta versión no tiene preguntas.")));
        renderizar(req, resp, "estudiante-intento.html", datos, null);
    }

    private void mostrarResultado(HttpServletRequest req, HttpServletResponse resp, Intento intento)
            throws IOException {
        EstudianteInicioIntentoServlet.sesionNormal(req);
        int pendientes = respuestaDAO.contarPendientes(intento.consultarIdIntento());
        EstadoEvaluacionVirtual estadoVirtual = virtualDAO.buscarPorId(intentoDAO.idVirtualDeIntento(intento.consultarIdIntento()))
                .map(v -> v.estadoEvaluacionVirtual).orElse(null);

        Map<String, String> datos = new HashMap<>();
        datos.put("titulo", intentoDAO.tituloEvaluacionDeIntento(intento.consultarIdIntento()).orElse("Evaluación"));
        datos.put("numero", String.valueOf(intento.consultarNumeroIntento()));
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
        for (Respuesta respuesta : respuestaDAO.listarConPregunta(intento.consultarIdIntento())) {
            respuestas.put(respuesta.versionPreguntaRespuesta.consultarIdVersionPregunta(), respuesta);
        }
        Set<Integer> pendientes = new HashSet<>(respuestaDAO.idsPendientes(intento.consultarIdIntento()));
        List<Map<String, String>> filas = new ArrayList<>();
        for (VersionPregunta pregunta : versionPreguntaDAO
                .listarPorVersionEvaluacion(intento.versionEvaluacionIntento.consultarIdVersionEvaluacion())) {
            Respuesta respuesta = respuestas.get(pregunta.consultarIdVersionPregunta());
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", Html.resumir(pregunta.enunciadoVersionPregunta, 110));
            fila.put("dada", respuesta == null ? "(sin responder)"
                    : (respuesta.versionOpcionRespuesta != null
                            ? respuesta.versionOpcionRespuesta.textoVersionOpcion
                            : (respuesta.contenidoRespuesta == null || respuesta.contenidoRespuesta.isBlank()
                                    ? "(sin responder)" : respuesta.contenidoRespuesta)));
            if (respuesta != null && pendientes.contains(respuesta.consultarIdRespuesta())) {
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

    private void guardarAutomaticamente(HttpServletRequest req, HttpServletResponse resp, Intento intento) throws IOException {
        if (intento.estadoIntento != EstadoIntento.EN_CURSO
                || intentoDAO.segundosRestantes(intento.consultarIdIntento()) <= 0) {
            servicioVirtual.finalizarIntentoVencido(intento.consultarIdIntento());
            EstudianteInicioIntentoServlet.sesionNormal(req);
            json(resp, HttpServletResponse.SC_CONFLICT, "{\"ok\":false,\"motivo\":\"El tiempo del intento terminó.\"}");
            return;
        }
        int idVersionPregunta = Validador.parsearId(req.getParameter("idVersionPregunta"));
        int idVersion = intento.versionEvaluacionIntento.consultarIdVersionEvaluacion();
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
        pregunta.establecerIdVersionPregunta(idVersionPregunta);
        String contenido = idOpcion > 0 ? "" : Validador.limpiarTexto(req.getParameter("contenido") == null
                ? "" : req.getParameter("contenido"));
        if (!guardarRespuesta(intento, pregunta, contenido, idOpcion)) {
            // El limite efectivo paso entre la comprobacion rapida y la escritura: nada se guarda.
            servicioVirtual.finalizarIntentoVencido(intento.consultarIdIntento());
            EstudianteInicioIntentoServlet.sesionNormal(req);
            json(resp, HttpServletResponse.SC_CONFLICT, "{\"ok\":false,\"motivo\":\"El tiempo del intento terminó.\"}");
            return;
        }
        json(resp, HttpServletResponse.SC_OK, "{\"ok\":true}");
    }

    private void guardarRespuestasDelFormulario(HttpServletRequest req, Intento intento) {
        int idVersion = intento.versionEvaluacionIntento.consultarIdVersionEvaluacion();
        for (VersionPregunta pregunta : versionPreguntaDAO.listarPorVersionEvaluacion(idVersion)) {
            String idOpcionTexto = Validador.limpiar(req.getParameter("opcion_" + pregunta.consultarIdVersionPregunta()));
            String contenido = req.getParameter("contenido_" + pregunta.consultarIdVersionPregunta());
            if (!idOpcionTexto.isEmpty()) {
                int idOpcion = Validador.parsearId(idOpcionTexto);
                if (idOpcion > 0 && versionOpcionDAO.perteneceAVersionPregunta(idOpcion, pregunta.consultarIdVersionPregunta())) {
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
        if (intentoDAO.segundosRestantes(intento.consultarIdIntento()) <= 0) {
            servicioVirtual.finalizarIntentoVencido(intento.consultarIdIntento());
            return;
        }
        int pendientes = respuestaDAO.calificarAutomaticas(intento.consultarIdIntento());
        if (pendientes == 0) {
            intentoDAO.calificarIntento(intento);
        } else {
            intentoDAO.finalizarIntento(intento);
        }
    }

    private Intento intentoDelEstudiante(Estudiante estudiante, int id) {
        java.util.Optional<Intento> propio = intentoDAO.buscarPorIdDeEstudiante(id, estudiante.consultarIdUsuario());
        verificarPropiedad(propio.isPresent(), intentoDAO.buscarPorId(id).isPresent(), "Intento no encontrado");
        return propio.get();
    }

    private static String enumerarPreguntas(List<Integer> numeros) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < numeros.size(); i++) {
            if (i > 0) {
                texto.append(i == numeros.size() - 1 ? " y " : ", ");
            }
            texto.append(numeros.get(i));
        }
        return texto.toString();
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
