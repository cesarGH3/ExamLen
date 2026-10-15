package servlet;

import logica.Estudiante;
import logica.MaterialPractica;
import logica.NivelIngles;
import logica.OpcionRespuesta;
import logica.Pregunta;
import logica.RespuestaPractica;
import logica.TipoPregunta;
import logica.Validador;
import modelo.PreguntaDAO;
import modelo.RespuestaPracticaDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Práctica del estudiante (RF-59 a RF-62): al abrir un material se crea un intento, cada pregunta
 * se responde y persiste por separado con retroalimentación inmediata, y al terminar se calcula el
 * porcentaje. El orden de las preguntas se baraja de forma determinista por intento (RF-61).
 */
@WebServlet(name = "EstudiantePracticaServlet", urlPatterns = "/app/estudiante/practica")
public class EstudiantePracticaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/estudiante/practica";
    private static final Logger LOG = Logger.getLogger(EstudiantePracticaServlet.class.getName());
    private static final String MATERIAL_NO_DISPONIBLE = "Este material de práctica no está disponible en este "
            + "momento. Si el problema continúa, avísale a tu docente.";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        int idResultado = Validador.parsearId(req.getParameter("resultado"));
        if (idResultado > 0) {
            mostrarResultado(req, resp, estudiante, idResultado);
            return;
        }
        int idIntento = Validador.parsearId(req.getParameter("intento"));
        if (idIntento > 0) {
            mostrarIntento(req, resp, estudiante, idIntento);
            return;
        }
        int id = Validador.parsearId(req.getParameter("id"));
        if (id < 0) {
            mostrarLista(req, resp, estudiante);
            return;
        }
        if (!materialDAO.esDeEstudiante(id, estudiante.getIdUsuario())) {
            verificarPropiedad(false, materialDAO.buscarPorId(id).isPresent(), "Material no encontrado");
        }
        MaterialPractica material = materialDAO.buscarPorId(id)
                .orElseThrow(() -> new logica.RecursoNoEncontradoException("Material no encontrado"));
        if (materialNoDisponible(id)) {
            agregarMensaje(req, MensajeFlash.Tipo.INFO, MATERIAL_NO_DISPONIBLE);
            mostrarLista(req, resp, estudiante);
            return;
        }
        // RF-61: cada práctica es un intento independiente con su propio orden de preguntas.
        logica.IntentoPractica intento = intentoPracticaDAO.crearIntentoEnCurso(estudiante, material);
        redirigir(req, resp, RUTA + "?intento=" + intento.getIdIntentoPractica());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        int idIntento = parametroId(req, "idIntento");
        logica.IntentoPractica intento = intentoDelEstudiante(estudiante, idIntento);
        int idMaterial = intentoPracticaDAO.idMaterialDeIntento(idIntento);
        if (idMaterial < 0 || materialNoDisponible(idMaterial)) {
            throw new logica.RecursoNoEncontradoException("Material no encontrado");
        }
        MaterialPractica material = materialDAO.buscarPorId(idMaterial)
                .orElseThrow(() -> new logica.RecursoNoEncontradoException("Material no encontrado"));
        try {
            switch (parametro(req, "accion")) {
                case "responder": {
                    int idPregunta = parametroId(req, "idPregunta");
                    Pregunta pregunta = null;
                    for (Pregunta candidata : materialDAO.listarPreguntas(idMaterial)) {
                        if (candidata.getIdPregunta() == idPregunta) {
                            pregunta = candidata;
                            break;
                        }
                    }
                    if (pregunta == null) {
                        throw new logica.RecursoNoEncontradoException("Pregunta no encontrada");
                    }
                    RespuestaPractica respuesta = respuestaDe(req, pregunta);
                    intentoPracticaDAO.guardarRespuesta(idIntento, respuesta);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Respuesta guardada.");
                    break;
                }
                case "terminar": {
                    List<Pregunta> preguntas = materialDAO.listarPreguntas(idMaterial);
                    int respondidas = respuestaPracticaDAO.listarPorIntentoPractica(idIntento).size();
                    if (respondidas < preguntas.size()) {
                        throw new logica.ValidacionException("Responde todas las preguntas antes de terminar.");
                    }
                    intentoPracticaDAO.finalizarIntento(idIntento);
                    redirigir(req, resp, RUTA + "?resultado=" + idIntento);
                    return;
                }
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
            }
        } catch (logica.ValidacionException e) {
            agregarErrores(req, e.getErrores());
        }
        redirigir(req, resp, RUTA + "?intento=" + idIntento);
    }

    // ------------------------------------------------------------------ intento en curso

    /** RNF-02: intento inexistente -> 404; intento de otro estudiante -> 403. */
    private logica.IntentoPractica intentoDelEstudiante(Estudiante estudiante, int idIntento) {
        java.util.Optional<logica.IntentoPractica> propio = intentoPracticaDAO
                .buscarPorIdDeEstudiante(idIntento, estudiante.getIdUsuario());
        verificarPropiedad(propio.isPresent(), intentoPracticaDAO.buscarPorId(idIntento).isPresent(),
                "Intento no encontrado");
        return propio.get();
    }

    private void mostrarIntento(HttpServletRequest req, HttpServletResponse resp, Estudiante estudiante,
                                int idIntento) throws IOException {
        logica.IntentoPractica intento = intentoDelEstudiante(estudiante, idIntento);
        if (intento.fechaFinIntentoPractica != null) {
            redirigir(req, resp, RUTA + "?resultado=" + idIntento);
            return;
        }
        int idMaterial = intentoPracticaDAO.idMaterialDeIntento(idIntento);
        MaterialPractica material = materialDAO.buscarPorId(idMaterial)
                .orElseThrow(() -> new logica.RecursoNoEncontradoException("Material no encontrado"));
        if (materialNoDisponible(idMaterial)) {
            agregarMensaje(req, MensajeFlash.Tipo.INFO, MATERIAL_NO_DISPONIBLE);
            mostrarLista(req, resp, estudiante);
            return;
        }
        List<Pregunta> preguntas = materialDAO.listarPreguntas(idMaterial);
        // Orden barajado y estable por intento: dos intentos distintos ven órdenes distintos.
        Collections.shuffle(preguntas, new Random(idIntento));
        Map<Integer, RespuestaPractica> respondidas = new HashMap<>();
        for (RespuestaPractica respuesta : respuestaPracticaDAO.listarConPregunta(idIntento)) {
            respondidas.put(respuesta.preguntaRespuestaPractica.getIdPregunta(), respuesta);
        }

        List<Map<String, String>> filas = new ArrayList<>();
        int numero = 1;
        int pendientes = 0;
        for (Pregunta pregunta : preguntas) {
            RespuestaPractica respuesta = respondidas.get(pregunta.getIdPregunta());
            Map<String, String> fila = new HashMap<>();
            fila.put("numero", String.valueOf(numero));
            fila.put("enunciado", pregunta.enunciadoPregunta);
            fila.put("ctx", req.getContextPath());
            fila.put("csrf", csrf(req));
            fila.put("idIntento", String.valueOf(idIntento));
            fila.put("idPregunta", String.valueOf(pregunta.getIdPregunta()));
            if (respuesta == null) {
                pendientes++;
                fila.put("bloqueRespuesta", bloqueRespuesta(req, pregunta, "", "required"));
                fila.put("feedback", "");
            } else {
                fila.put("bloqueRespuesta", bloqueRespuesta(req, pregunta,
                        respuesta.contenidoRespuestaPractica == null ? "" : respuesta.contenidoRespuestaPractica,
                        "disabled"));
                fila.put("feedback", feedbackDe(respuesta));
            }
            filas.add(fila);
            numero++;
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("idIntento", String.valueOf(idIntento));
        datos.put("titulo", material.tituloMaterialPractica);
        datos.put("descripcion", material.descripcionMaterialPractica == null ? ""
                : material.descripcionMaterialPractica);
        datos.put("analisis", ruta(req, RUTA + "/historial"));
        datos.put("filasPreguntas", componerFilas("_pregunta-practica.html", filas,
                parrafoVacio("Este material no tiene preguntas.")));
        datos.put("accionesTerminar", pendientes == 0 && !preguntas.isEmpty()
                ? formularioTerminar(req, idIntento)
                : parrafoAyuda("Responde todas las preguntas (" + pendientes + " pendiente(s)) para terminar."));
        renderizar(req, resp, "estudiante-practica.html", datos, null);
    }

    private String bloqueRespuesta(HttpServletRequest req, Pregunta pregunta, String valor, String atributos) {
        if (pregunta.opcionesPregunta.isEmpty()) {
            Map<String, String> datosTexto = new HashMap<>();
            datosTexto.put("id", String.valueOf(pregunta.getIdPregunta()));
            datosTexto.put("valor", valor);
            datosTexto.put("atributos", atributos);
            return vistas.componer("_respuesta-texto.html", datosTexto);
        }
        StringBuilder opciones = new StringBuilder();
        for (OpcionRespuesta opcion : pregunta.opcionesPregunta) {
            Map<String, String> datosOpcion = new HashMap<>();
            datosOpcion.put("id", String.valueOf(pregunta.getIdPregunta()));
            datosOpcion.put("valor", String.valueOf(opcion.getIdOpcionRespuesta()));
            datosOpcion.put("atributos", atributos);
            datosOpcion.put("texto", opcion.textoOpcionRespuesta);
            opciones.append(vistas.componer("_opcion-examen.html", datosOpcion));
        }
        return opciones.toString();
    }

    private String feedbackDe(RespuestaPractica respuesta) {
        String resultado;
        String clase;
        if (respuesta.esCorrectaRespuestaPractica == null) {
            resultado = "Sin calificar (respuesta abierta)";
            clase = "alerta-info";
        } else if (respuesta.esCorrectaRespuestaPractica) {
            resultado = "Correcta";
            clase = "alerta-exito";
        } else {
            resultado = "Incorrecta";
            clase = "alerta-error";
        }
        StringBuilder mensaje = new StringBuilder(resultado);
        String correcta = respuesta.respuestaCorrectaRespuestaPractica != null
                ? respuesta.respuestaCorrectaRespuestaPractica
                : respuesta.preguntaRespuestaPractica.respuestaEsperadaPregunta;
        if (correcta != null && !correcta.isBlank() && !Boolean.TRUE.equals(respuesta.esCorrectaRespuestaPractica)) {
            mensaje.append(". Respuesta correcta/esperada: ").append(correcta);
        }
        if (respuesta.retroalimentacionRespuestaPractica != null
                && !respuesta.retroalimentacionRespuestaPractica.isBlank()) {
            mensaje.append(". ").append(respuesta.retroalimentacionRespuestaPractica);
        }
        return Html.alerta(vistas, clase, mensaje.toString());
    }

    private String formularioTerminar(HttpServletRequest req, int idIntento) {
        Map<String, String> datos = new HashMap<>();
        datos.put("destino", ruta(req, RUTA));
        datos.put("csrf", csrf(req));
        datos.put("idIntento", String.valueOf(idIntento));
        return vistas.componer("_form-terminar-practica.html", datos);
    }

    // ------------------------------------------------------------------ lista y resultado

    private void mostrarLista(HttpServletRequest req, HttpServletResponse resp, Estudiante estudiante)
            throws IOException {
        NivelIngles filtro = Formulario.enumeracion(NivelIngles.class, req.getParameter("nivel"));
        List<Map<String, String>> filas = new ArrayList<>();
        for (MaterialPractica material : materialDAO.listarPublicadosDeEstudiante(estudiante.getIdUsuario(), filtro)) {
            Map<String, String> fila = new HashMap<>();
            fila.put("titulo", material.tituloMaterialPractica);
            fila.put("descripcion", Html.resumir(material.descripcionMaterialPractica, 120));
            fila.put("nivel", Etiquetas.de(material.nivelMaterialPractica));
            fila.put("preguntas", String.valueOf(materialDAO.contarPreguntas(material.getIdMaterialPractica())));
            Map<String, String> enlace = new HashMap<>();
            enlace.put("clase", "boton boton-pequeno");
            enlace.put("url", ruta(req, RUTA + "?id=" + material.getIdMaterialPractica()));
            enlace.put("texto", "Practicar");
            fila.put("accion", vistas.componer("_enlace-accion.html", enlace));
            filas.add(fila);
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-practica-estudiante.html", filas,
                filaVacia("5", "No hay material de práctica publicado en tus cursos.")));
        datos.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(), filtro, "Todos los niveles"));
        if (filtro == null) {
            datos.put("limpiarFiltro", "");
        } else {
            Map<String, String> enlace = new HashMap<>();
            enlace.put("clase", "boton boton-contorno boton-pequeno");
            enlace.put("url", ruta(req, RUTA));
            enlace.put("texto", "Quitar filtro");
            datos.put("limpiarFiltro", vistas.componer("_enlace-accion.html", enlace));
        }
        datos.put("historial", ruta(req, RUTA + "/historial"));
        renderizar(req, resp, "estudiante-practicas.html", datos, null);
    }

    private void mostrarResultado(HttpServletRequest req, HttpServletResponse resp, Estudiante estudiante,
                                  int idIntento) throws IOException {
        logica.IntentoPractica intento = intentoDelEstudiante(estudiante, idIntento);

        List<RespuestaPractica> respuestas = respuestaPracticaDAO.listarConPregunta(idIntento);
        List<Map<String, String>> filas = new ArrayList<>();
        int calificables = 0;
        for (RespuestaPractica respuesta : respuestas) {
            Boolean correcta = respuesta.esCorrectaRespuestaPractica;
            if (correcta != null) {
                calificables++;
            }
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", Html.resumir(respuesta.preguntaRespuestaPractica.enunciadoPregunta, 110));
            fila.put("dada", respuesta.opcionRespuestaRespuestaPractica != null
                    ? respuesta.opcionRespuestaRespuestaPractica.textoOpcionRespuesta
                    : (respuesta.contenidoRespuestaPractica == null || respuesta.contenidoRespuestaPractica.isBlank()
                            ? "(sin responder)" : respuesta.contenidoRespuestaPractica));
            fila.put("correcta", respuestaCorrecta(respuesta));
            fila.put("resultado", correcta == null ? "Sin calificar" : (correcta ? "Correcta" : "Incorrecta"));
            fila.put("claseResultado", correcta == null ? "insignia-neutra"
                    : (correcta ? "insignia-activo" : "insignia-inactivo"));
            fila.put("retroalimentacion", respuesta.retroalimentacionRespuestaPractica == null
                    ? "" : respuesta.retroalimentacionRespuestaPractica);
            filas.add(fila);
        }

        int idMaterial = intentoPracticaDAO.idMaterialDeIntento(idIntento);
        Map<String, String> datos = new HashMap<>();
        datos.put("titulo", intentoPracticaDAO.tituloMaterialDeIntento(idIntento));
        datos.put("idMaterial", String.valueOf(idMaterial));
        if (intento.calificacionIntentoPractica == null) {
            datos.put("nota", "Sin calificación automática");
            datos.put("unidad", "");
        } else {
            datos.put("nota", formatoNota(intento.calificacionIntentoPractica));
            datos.put("unidad", "% de respuestas correctas");
        }
        if (respuestas.isEmpty()) {
            datos.put("calculo", "El intento no tiene respuestas.");
        } else if (calificables == 0) {
            datos.put("calculo", "");
        } else {
            datos.put("calculo", "Resultado sobre " + calificables + " de " + respuestas.size()
                    + " preguntas calificables.");
        }
        datos.put("filas", componerFilas("_fila-respuesta-practica.html", filas,
                filaVacia("5", "El intento no tiene respuestas.")));
        datos.put("repetir", ruta(req, RUTA + "?id=" + idMaterial));
        datos.put("pdfSinRespuestas", ruta(req, RUTA + "/pdf?id=" + idMaterial + "&respuestas=0"));
        datos.put("pdfConRespuestas", ruta(req, RUTA + "/pdf?id=" + idMaterial + "&respuestas=1"));
        datos.put("analisis", ruta(req, RUTA + "/historial"));
        renderizar(req, resp, "estudiante-practica-resultado.html", datos, null);
    }

    // ------------------------------------------------------------------ auxiliares

    private boolean materialNoDisponible(int idMaterialPractica) {
        if (materialDAO.esIntegro(idMaterialPractica)) {
            return false;
        }
        LOG.log(Level.WARNING, "Material de practica publicado no disponible: id={0}, preguntasNoAprobadas={1}",
                new Object[]{idMaterialPractica, materialDAO.contarPreguntasNoAprobadas(idMaterialPractica)});
        return true;
    }

    /** Evalúa la respuesta enviada (RF-60) y prepara su retroalimentación. */
    private RespuestaPractica respuestaDe(HttpServletRequest req, Pregunta pregunta) {
        String contenido = "";
        OpcionRespuesta elegida = null;
        int idOpcion = Validador.parsearId(req.getParameter("opcion_" + pregunta.getIdPregunta()));
        if (idOpcion > 0) {
            for (OpcionRespuesta opcion : pregunta.opcionesPregunta) {
                if (opcion.getIdOpcionRespuesta() == idOpcion) {
                    elegida = opcion;
                    contenido = opcion.textoOpcionRespuesta;
                    break;
                }
            }
        } else {
            String texto = req.getParameter("contenido_" + pregunta.getIdPregunta());
            contenido = texto == null ? "" : Validador.limpiarTexto(texto);
        }
        RespuestaPractica respuesta = new RespuestaPractica();
        respuesta.preguntaRespuestaPractica = pregunta;
        respuesta.contenidoRespuestaPractica = contenido;
        respuesta.opcionRespuestaRespuestaPractica = elegida;
        respuesta.esCorrectaRespuestaPractica = RespuestaPracticaDAO.evaluar(pregunta, elegida, contenido);
        respuesta.retroalimentacionRespuestaPractica = retroalimentacion(pregunta,
                respuesta.esCorrectaRespuestaPractica);
        return respuesta;
    }

    private static String retroalimentacion(Pregunta pregunta, Boolean correcta) {
        if (pregunta.tipoPregunta == TipoPregunta.RESPUESTA_ABIERTA) {
            return "Esta pregunta es de respuesta abierta y no se califica automáticamente: "
                    + "compara tu respuesta con la esperada.";
        }
        if (!Boolean.FALSE.equals(correcta)) {
            return "";
        }
        String tema = pregunta.temaPregunta == null ? "" : pregunta.temaPregunta.strip();
        String competencia = pregunta.competenciaPregunta == null ? "" : pregunta.competenciaPregunta.name();
        if (tema.isEmpty() && competencia.isEmpty()) {
            return "";
        }
        if (tema.isEmpty()) {
            return "Repasa: " + competencia;
        }
        if (competencia.isEmpty()) {
            return "Repasa: " + tema;
        }
        return "Repasa: " + tema + " (" + competencia + ")";
    }

    private static String respuestaCorrecta(RespuestaPractica respuesta) {
        Pregunta pregunta = respuesta.preguntaRespuestaPractica;
        if (pregunta != null && PreguntaDAO.usaOpciones(pregunta.tipoPregunta)) {
            return respuesta.respuestaCorrectaRespuestaPractica == null
                    ? "" : respuesta.respuestaCorrectaRespuestaPractica;
        }
        return pregunta != null && pregunta.respuestaEsperadaPregunta != null
                ? pregunta.respuestaEsperadaPregunta : "";
    }

    private static String formatoNota(double nota) {
        if (nota == Math.floor(nota)) {
            return String.valueOf((int) nota);
        }
        return String.valueOf(nota);
    }
}
