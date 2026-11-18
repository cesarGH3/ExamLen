package servlet;

import logica.Calificacion;
import logica.Docente;
import logica.EstadoIntento;
import logica.EventoSupervision;
import logica.Intento;
import logica.RecursoNoEncontradoException;
import logica.Respuesta;
import logica.ValidacionException;
import logica.Validador;
import modelo.EventoSupervisionDAO;
import modelo.IntentoDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DocenteIntentoServlet", urlPatterns = "/app/docente/intento")
public class DocenteIntentoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/intento";
    private static final int MAX_COMENTARIO = 1000;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = parametroId(req, "id");
        Intento intento = intentoDelDocente(docente, id);
        int idVirtual = intentoDAO.idVirtualDeIntento(id);

        List<String> pendientes = new ArrayList<>();
        for (Integer idPendiente : respuestaDAO.idsPendientes(id)) {
            pendientes.add(String.valueOf(idPendiente));
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(id));
        datos.put("numero", String.valueOf(intento.consultarNumeroIntento()));
        datos.put("titulo", intentoDAO.tituloEvaluacionDeIntento(id).orElse("Evaluación"));
        datos.put("estudiante", intentoDAO.estudianteDeIntento(id)
                .map(e -> e.nombreUsuario + " · " + e.identificacionUsuario).orElse(""));
        datos.put("estado", Etiquetas.de(intento.estadoIntento));
        datos.put("nota", notaDe(intento));
        datos.put("inicio", fechaHora(intento.fechaInicioIntento));
        datos.put("fin", fechaHora(intento.fechaFinIntento));
        datos.put("pendientes", String.valueOf(pendientes.size()));
        datos.put("urlVirtual", ruta(req, "/app/docente/virtual?id=" + idVirtual));
        datos.put("filasRespuestas", construirFilasRespuestas(req, intento, pendientes));
        datos.put("notaIndicadores", Html.alerta(vistas, "alerta-info",
                "Los indicadores no determinan por sí solos una conducta indebida."));
        datos.put("resumenEventos", construirResumenEventos(id));
        datos.put("filasEventos", construirFilasEventos(id));
        renderizar(req, resp, "docente-intento.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idIntento = parametroId(req, "id");
        try {
            Intento intento = intentoDelDocente(docente, idIntento);
            if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
                throw new ValidacionException("El intento todavía está en curso.");
            }
            int idRespuesta = parametroId(req, "idRespuesta");
            verificarPropiedad(respuestaDAO.perteneceAIntento(idRespuesta, idIntento),
                    respuestaDAO.buscarPorId(idRespuesta).isPresent(), "Respuesta no encontrada");
            Respuesta respuesta = respuestaDAO.buscarConPregunta(idRespuesta)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Respuesta no encontrada"));
            double maximo = respuesta.versionPreguntaRespuesta.puntajeVersionPregunta;
            double puntaje = decimal(req, "puntaje");
            if (!Double.isFinite(puntaje) || puntaje < 0 || puntaje > maximo) {
                throw new ValidacionException("El puntaje debe estar entre 0 y " + formatoNota(maximo)
                        + ": es el máximo de la pregunta en esta versión.");
            }
            String comentario = Validador.limpiarTexto(req.getParameter("comentario") == null
                    ? "" : req.getParameter("comentario"));
            if (comentario.length() > MAX_COMENTARIO) {
                throw new ValidacionException("El comentario admite máximo 1000 caracteres.");
            }
            respuestaDAO.calificarManualmenteRespuesta(respuesta, puntaje, comentario.isEmpty() ? null : comentario);
            Double nota = respuestaDAO.contarPendientes(idIntento) == 0
                    ? intentoDAO.calificarIntento(intento)
                    : intentoDAO.actualizarCalificacion(intento);
            if (nota == null) {
                agregarMensaje(req, MensajeFlash.Tipo.ERROR, "El puntaje quedó guardado, pero no se pudo calcular "
                        + "la calificación: la versión tiene máximo 0 o hay puntos mayores al máximo. "
                        + "Revisa los puntajes de la versión.");
            } else {
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Calificación guardada.");
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.consultarErrores());
        }
        redirigir(req, resp, RUTA + "?id=" + idIntento);
    }

    private Intento intentoDelDocente(Docente docente, int id) {
        java.util.Optional<Intento> propio = intentoDAO.buscarPorIdDeDocente(id, docente.consultarIdUsuario());
        verificarPropiedad(propio.isPresent(), intentoDAO.buscarPorId(id).isPresent(), "Intento no encontrado");
        return propio.get();
    }

    private String construirFilasRespuestas(HttpServletRequest req, Intento intento, List<String> pendientes) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (Respuesta respuesta : respuestaDAO.listarConPregunta(intento.consultarIdIntento())) {
            boolean pendiente = pendientes.contains(String.valueOf(respuesta.consultarIdRespuesta()));
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", Html.resumir(respuesta.versionPreguntaRespuesta.enunciadoVersionPregunta, 110));
            if (respuesta.versionOpcionRespuesta != null) {
                fila.put("dada", respuesta.versionOpcionRespuesta.textoVersionOpcion);
                fila.put("claseDada", respuesta.versionOpcionRespuesta.esCorrectaVersionOpcion
                        ? "insignia-activo" : "insignia-inactivo");
            } else {
                fila.put("dada", respuesta.contenidoRespuesta == null || respuesta.contenidoRespuesta.isBlank()
                        ? "(sin responder)" : Html.resumir(respuesta.contenidoRespuesta, 110));
                fila.put("claseDada", "insignia-neutra");
            }
            fila.put("maximo", formatoNota(respuesta.versionPreguntaRespuesta.puntajeVersionPregunta));
            fila.put("puntaje", pendiente ? "Pendiente" : formatoNota(respuesta.puntajeRespuesta));
            fila.put("comentario", respuesta.comentarioDocenteRespuesta == null ? "" : respuesta.comentarioDocenteRespuesta);
            Map<String, String> form = new HashMap<>();
            form.put("ctx", req.getContextPath());
            form.put("csrf", csrf(req));
            form.put("id", String.valueOf(intento.consultarIdIntento()));
            form.put("idRespuesta", String.valueOf(respuesta.consultarIdRespuesta()));
            form.put("maximo", formatoNota(respuesta.versionPreguntaRespuesta.puntajeVersionPregunta));
            form.put("puntajeValor", pendiente ? "" : formatoNota(respuesta.puntajeRespuesta));
            form.put("comentarioValor", respuesta.comentarioDocenteRespuesta == null
                    ? "" : respuesta.comentarioDocenteRespuesta);
            form.put("textoBoton", pendiente ? "Calificar" : "Recalificar");
            fila.put("accion", vistas.componer("_form-calificar.html", form));
            filas.add(fila);
        }
        return componerFilas("_fila-respuesta-calificar.html", filas,
                filaVacia("5", "El intento no tiene respuestas registradas."));
    }

    /** Indicadores agrupados por tipo con su conteo (RF-55); vacío si el intento no tiene eventos. */
    private String construirResumenEventos(int idIntento) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (EventoSupervisionDAO.ResumenTipo resumen : eventoDAO.contarPorTipo(idIntento)) {
            Map<String, String> fila = new HashMap<>();
            fila.put("tipo", Etiquetas.de(resumen.tipo));
            fila.put("cantidad", String.valueOf(resumen.total));
            filas.add(fila);
        }
        if (filas.isEmpty()) {
            return "";
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-resumen-evento.html", filas, ""));
        return vistas.componer("_tabla-resumen-eventos.html", datos);
    }

    private String construirFilasEventos(int idIntento) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (EventoSupervision evento : eventoDAO.listarPorIntento(idIntento)) {
            Map<String, String> fila = new HashMap<>();
            fila.put("tipo", Etiquetas.de(evento.tipoEventoSupervision));
            fila.put("descripcion", evento.descripcionEventoSupervision == null
                    ? "" : evento.descripcionEventoSupervision);
            fila.put("fecha", fechaHora(evento.fechaHoraEventoSupervision));
            filas.add(fila);
        }
        return componerFilas("_fila-evento.html", filas,
                filaVacia("3", "No se registraron eventos de supervisión."));
    }

    /** Nota final en escala 0,0-5,0; para el docente incluye tambien los puntos brutos. */
    private String notaDe(Intento intento) {
        if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
            return "—";
        }
        if (intento.estadoIntento != EstadoIntento.CALIFICADO) {
            return "Pendiente de calificación";
        }
        String nota = Calificacion.formatoConMaximo(BigDecimal.valueOf(intento.calificacionIntento));
        IntentoDAO.Puntos puntos = intentoDAO.puntosDeIntento(intento.consultarIdIntento());
        if (puntos == null) {
            return nota;
        }
        return nota + " · Puntos: " + formatoNota(puntos.obtenidos) + " / " + formatoNota(puntos.maximos);
    }

    private static double decimal(HttpServletRequest req, String nombre) {
        try {
            return Double.parseDouble(Validador.limpiar(req.getParameter(nombre)));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatoNota(double nota) {
        if (nota == Math.floor(nota)) {
            return String.valueOf((int) nota);
        }
        return String.valueOf(nota);
    }
}
