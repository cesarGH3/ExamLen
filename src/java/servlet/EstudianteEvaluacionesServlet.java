package servlet;

import logica.Calificacion;
import logica.EstadoEvaluacionVirtual;
import logica.EstadoIntento;
import logica.Estudiante;
import logica.EvaluacionVirtual;
import logica.Inscripcion;
import logica.Intento;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "EstudianteEvaluacionesServlet", urlPatterns = "/app/estudiante/evaluaciones")
public class EstudianteEvaluacionesServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/estudiante/evaluaciones";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        List<Map<String, String>> filas = new ArrayList<>();
        for (Inscripcion inscripcion : inscripcionDAO.listarActivasPorEstudiante(estudiante.getIdUsuario())) {
            for (EvaluacionVirtual virtual : virtualDAO.listarPorGrupo(inscripcion.grupoInscripcion.getIdGrupo())) {
                Intento intento = intentoDAO.buscarPorEvaluacionEInscripcion(virtual.getIdEvaluacionVirtual(),
                        inscripcion.getIdInscripcion()).orElse(null);
                if (virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.CANCELADA && intento == null) {
                    continue;
                }
                Map<String, String> fila = new HashMap<>();
                fila.put("curso", inscripcion.grupoInscripcion.cursoGrupo.nombreCurso);
                fila.put("grupo", inscripcion.grupoInscripcion.nombreGrupo);
                fila.put("titulo", virtual.evaluacionEvaluacionVirtual.tituloEvaluacion);
                fila.put("ventana", fechaHora(virtual.fechaInicioEvaluacionVirtual) + " — "
                        + fechaHora(virtual.fechaFinEvaluacionVirtual));
                fila.put("duracion", String.valueOf(virtual.evaluacionEvaluacionVirtual.duracionMinutosEvaluacion));
                fila.put("estado", Etiquetas.de(virtual.estadoEvaluacionVirtual));
                fila.put("claseEstado", virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.ABIERTA
                        ? "insignia-activo" : "insignia-neutra");

                if (intento != null) {
                    if (virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.CANCELADA) {
                        fila.put("intento", "Cancelada");
                    } else if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
                        fila.put("intento", Etiquetas.de(intento.estadoIntento));
                    } else if (virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.ABIERTA
                            || intento.estadoIntento != EstadoIntento.CALIFICADO) {
                        fila.put("intento", "Pendiente de calificación");
                    } else {
                        fila.put("intento", Etiquetas.de(intento.estadoIntento)
                                + " · Nota: " + Calificacion.formatoConMaximo(
                                        BigDecimal.valueOf(intento.calificacionIntento)));
                    }
                    Map<String, String> enlace = new HashMap<>();
                    enlace.put("clase", "boton boton-contorno boton-pequeno");
                    enlace.put("url", ruta(req, "/app/estudiante/intento?id=" + intento.getIdIntento()));
                    enlace.put("texto", "Ver");
                    fila.put("accion", vistas.componer("_enlace-accion.html", enlace));
                } else if (virtualDAO.disponibleAhora(virtual.getIdEvaluacionVirtual())) {
                    fila.put("intento", "Sin presentar");
                    Map<String, String> enlace = new HashMap<>();
                    enlace.put("clase", "boton boton-pequeno");
                    enlace.put("url", ruta(req, "/app/estudiante/intento/inicio?id="
                            + virtual.getIdEvaluacionVirtual()));
                    enlace.put("texto", "Iniciar");
                    fila.put("accion", vistas.componer("_enlace-accion.html", enlace));
                } else {
                    fila.put("intento", "Sin presentar");
                    fila.put("accion", "");
                }
                filas.add(fila);
            }
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-evaluacion-estudiante.html", filas,
                filaVacia("7", "No hay evaluaciones virtuales en tus grupos.")));
        renderizar(req, resp, "estudiante-evaluaciones.html", datos, null);
    }

}
