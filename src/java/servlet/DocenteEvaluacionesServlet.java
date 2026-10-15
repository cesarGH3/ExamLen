package servlet;

import logica.Docente;
import logica.EstadoEvaluacion;
import logica.EstadoPregunta;
import logica.Evaluacion;
import logica.Pregunta;
import logica.ValidacionException;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DocenteEvaluacionesServlet", urlPatterns = "/app/docente/evaluaciones")
public class DocenteEvaluacionesServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    /** ERS: evaluacion.titulo VARCHAR(150) NOT NULL; descripcion TEXT NULL (sin máximo). */
    private static final int MAX_TITULO = 150;
    private static final int MIN_PREGUNTAS = 1;
    private static final int MIN_VERSIONES = 1;
    private static final int MAX_VERSIONES = 10;
    private static final int MIN_DURACION = 1;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        Map<String, String> formulario = recuperarFormulario(req);

        List<Map<String, String>> filas = new ArrayList<>();
        for (Evaluacion evaluacion : evaluacionDAO.listarPorDocente(docente.getIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, "/app/docente/evaluacion?id=" + evaluacion.getIdEvaluacion()));
            fila.put("titulo", evaluacion.tituloEvaluacion);
            fila.put("preguntas", String.valueOf(evaluacion.numeroPreguntasEvaluacion));
            fila.put("versiones", String.valueOf(versionDAO.contarVersionesDeEvaluacion(evaluacion.getIdEvaluacion())));
            fila.put("estado", Etiquetas.de(evaluacion.estadoEvaluacion));
            fila.put("claseEstado", claseEstado(evaluacion.estadoEvaluacion));
            filas.add(fila);
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-evaluacion.html", filas,
                filaVacia("5", "Todavía no tienes evaluaciones. Crea la primera con el formulario.")));
        datos.put("titulo", formulario.getOrDefault("titulo", ""));
        datos.put("descripcion", formulario.getOrDefault("descripcion", ""));
        datos.put("numeroPreguntas", formulario.getOrDefault("numeroPreguntas", "10"));
        datos.put("numeroVersiones", formulario.getOrDefault("numeroVersiones", "2"));
        datos.put("duracionMinutos", formulario.getOrDefault("duracionMinutos", "30"));
        datos.put("opcionesPreguntas", opcionesPreguntas(docente, formulario.get("idPregunta")));
        renderizar(req, resp, "docente-evaluaciones.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        String titulo = parametro(req, "titulo");
        String descripcion = Validador.limpiarTexto(req.getParameter("descripcion") == null
                ? "" : req.getParameter("descripcion"));
        int numeroPreguntas = entero(req, "numeroPreguntas");
        int numeroVersiones = entero(req, "numeroVersiones");
        int duracionMinutos = entero(req, "duracionMinutos");
        int idPregunta = Validador.parsearId(req.getParameter("idPregunta"));

        List<String> errores = new ArrayList<>();
        Validador.validarLongitud(titulo, 1, MAX_TITULO,
                "El título es obligatorio (máximo 150 caracteres).", errores);
        if (numeroPreguntas < MIN_PREGUNTAS) {
            errores.add("El número de preguntas por versión debe ser al menos 1.");
        }
        if (numeroVersiones < MIN_VERSIONES || numeroVersiones > MAX_VERSIONES) {
            errores.add("El número de versiones debe estar entre 1 y 10.");
        }
        if (duracionMinutos < MIN_DURACION) {
            errores.add("La duración debe ser al menos 1 minuto.");
        }
        if (idPregunta < 0) {
            errores.add("Selecciona la primera pregunta aprobada de la evaluación.");
        }
        if (!errores.isEmpty()) {
            agregarErrores(req, errores);
            guardarFormulario(req, Map.of("titulo", titulo, "descripcion", descripcion,
                    "numeroPreguntas", String.valueOf(numeroPreguntas), "numeroVersiones",
                    String.valueOf(numeroVersiones), "duracionMinutos", String.valueOf(duracionMinutos),
                    "idPregunta", String.valueOf(idPregunta)));
            redirigir(req, resp, "/app/docente/evaluaciones");
            return;
        }
        try {
            Pregunta primera = preguntaDAO.buscarPorId(idPregunta)
                    .orElseThrow(() -> new ValidacionException("Selecciona la primera pregunta aprobada de la evaluación."));
            if (!preguntaDAO.esDeDocente(idPregunta, docente.getIdUsuario())) {
                throw new ValidacionException("Selecciona la primera pregunta aprobada de la evaluación.");
            }
            if (primera.estadoPregunta != EstadoPregunta.APROBADA) {
                throw new ValidacionException("Solo se pueden seleccionar preguntas aprobadas.");
            }
            Evaluacion evaluacion = evaluacionDAO.crearEvaluacionConPregunta(docente, titulo, descripcion,
                    numeroPreguntas, numeroVersiones, duracionMinutos, idPregunta);
            agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                    "Evaluación creada con su primera pregunta. Agrega más preguntas si lo necesitas.");
            redirigir(req, resp, "/app/docente/evaluacion?id=" + evaluacion.getIdEvaluacion());
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
            redirigir(req, resp, "/app/docente/evaluaciones");
        }
    }

    /** Opciones del select de la primera pregunta: solo aprobadas de los bancos del docente. */
    private String opcionesPreguntas(Docente docente, String seleccionada) {
        StringBuilder sb = new StringBuilder();
        sb.append(Formulario.opcion(vistas, "", "Selecciona una pregunta aprobada", seleccionada == null));
        for (Pregunta pregunta : preguntaDAO.listarAprobadasDeDocente(docente.getIdUsuario())) {
            String texto = Html.resumir(pregunta.enunciadoPregunta, 90) + " (" + Etiquetas.de(pregunta.nivelPregunta)
                    + " · " + Etiquetas.de(pregunta.competenciaPregunta) + " · "
                    + Etiquetas.de(pregunta.dificultadPregunta) + ")";
            sb.append(Formulario.opcion(vistas, String.valueOf(pregunta.getIdPregunta()), texto,
                    String.valueOf(pregunta.getIdPregunta()).equals(seleccionada)));
        }
        return sb.toString();
    }

    private static int entero(HttpServletRequest req, String nombre) {
        try {
            return Integer.parseInt(Validador.limpiar(req.getParameter(nombre)));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    static String claseEstado(EstadoEvaluacion estado) {
        switch (estado) {
            case BORRADOR:
                return "insignia-borrador";
            case LISTA:
                return "insignia-activo";
            default:
                return "insignia-neutra";
        }
    }
}
