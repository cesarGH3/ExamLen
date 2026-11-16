package servlet;

import logica.Docente;
import logica.EstadoEvaluacion;
import logica.Evaluacion;
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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        Map<String, String> formulario = recuperarFormulario(req);

        List<Map<String, String>> filas = new ArrayList<>();
        for (Evaluacion evaluacion : evaluacionDAO.listarPorDocente(docente.consultarIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, "/app/docente/evaluacion?id=" + evaluacion.consultarIdEvaluacion()));
            fila.put("titulo", evaluacion.tituloEvaluacion);
            fila.put("preguntas", String.valueOf(evaluacion.numeroPreguntasEvaluacion));
            fila.put("versiones", String.valueOf(versionDAO.contarVersionesDeEvaluacion(evaluacion.consultarIdEvaluacion())));
            fila.put("estado", Etiquetas.de(evaluacion.estadoEvaluacion));
            fila.put("claseEstado", claseEstado(evaluacion.estadoEvaluacion));
            filas.add(fila);
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-evaluacion.html", filas,
                filaVacia("5", "Todavía no tienes evaluaciones. Crea la primera con el formulario.")));
        datos.put("titulo", formulario.getOrDefault("titulo", ""));
        datos.put("descripcion", formulario.getOrDefault("descripcion", ""));
        renderizar(req, resp, "docente-evaluaciones.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        String titulo = parametro(req, "titulo");
        String descripcion = Validador.limpiarTexto(req.getParameter("descripcion") == null
                ? "" : req.getParameter("descripcion"));
        List<String> errores = new ArrayList<>();
        Validador.validarLongitud(titulo, 1, MAX_TITULO,
                "El título es obligatorio (máximo 150 caracteres).", errores);
        if (!errores.isEmpty()) {
            agregarErrores(req, errores);
            guardarFormulario(req, Map.of("titulo", titulo, "descripcion", descripcion));
            redirigir(req, resp, "/app/docente/evaluaciones");
            return;
        }
        Evaluacion evaluacion = evaluacionDAO.crearEvaluacion(docente, titulo, descripcion);
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Evaluación creada. Escribe sus preguntas y completa la configuración.");
        redirigir(req, resp, "/app/docente/evaluacion?id=" + evaluacion.consultarIdEvaluacion());
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
