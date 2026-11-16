package servlet;

import logica.Estudiante;
import logica.IntentoPractica;
import modelo.RespuestaPracticaDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** RF-62 / CU-34: historial de práctica del estudiante con el resumen de errores por competencia y tema. */
@WebServlet(name = "EstudianteHistorialPracticaServlet", urlPatterns = "/app/estudiante/practica/historial")
public class EstudianteHistorialPracticaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        Map<String, String> datos = new HashMap<>();
        datos.put("filasIntentos", filasIntentos(req, estudiante));
        datos.put("filasCompetencias", filasResumen(
                respuestaPracticaDAO.resumenPorCompetencia(estudiante.consultarIdUsuario())));
        datos.put("filasTemas", filasResumen(
                respuestaPracticaDAO.resumenPorTema(estudiante.consultarIdUsuario())));
        renderizar(req, resp, "estudiante-historial-practica.html", datos, null);
    }

    private String filasIntentos(HttpServletRequest req, Estudiante estudiante) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (IntentoPractica intento : intentoPracticaDAO.listarPorEstudiante(estudiante.consultarIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("fecha", fechaHora(intento.fechaInicioIntentoPractica));
            fila.put("material", intento.tituloMaterialPractica);
            fila.put("resultado", intento.calificacionIntentoPractica == null
                    ? "Sin calificación automática" : formatoNota(intento.calificacionIntentoPractica) + " %");
            Map<String, String> enlace = new HashMap<>();
            enlace.put("clase", "boton boton-contorno boton-pequeno");
            enlace.put("url", ruta(req, "/app/estudiante/practica?resultado=" + intento.consultarIdIntentoPractica()));
            enlace.put("texto", "Ver detalle");
            fila.put("accion", vistas.componer("_enlace-accion.html", enlace));
            filas.add(fila);
        }
        return componerFilas("_fila-historial-practica.html", filas,
                filaVacia("4", "Todavía no has resuelto material de práctica."));
    }

    private String filasResumen(List<RespuestaPracticaDAO.Resumen> resumen) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (RespuestaPracticaDAO.Resumen filaResumen : resumen) {
            Map<String, String> fila = new HashMap<>();
            fila.put("nombre", filaResumen.nombre);
            fila.put("respondidas", String.valueOf(filaResumen.respondidas));
            fila.put("correctas", String.valueOf(filaResumen.correctas));
            fila.put("porcentaje", formatoNota(porcentaje(filaResumen)) + " %");
            filas.add(fila);
        }
        return componerFilas("_fila-resumen-practica.html", filas,
                filaVacia("4", "Todavía no hay respuestas calificadas."));
    }

    private static double porcentaje(RespuestaPracticaDAO.Resumen fila) {
        return Math.round((fila.correctas * 1000.0) / fila.respondidas) / 10.0;
    }

    private static String formatoNota(double nota) {
        if (nota == Math.floor(nota)) {
            return String.valueOf((int) nota);
        }
        return String.valueOf(nota);
    }
}
