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
        for (Inscripcion inscripcion : inscripcionDAO.listarActivasPorEstudiante(estudiante.consultarIdUsuario())) {
            for (EvaluacionVirtual virtual : virtualDAO.listarPorGrupo(inscripcion.grupoInscripcion.consultarIdGrupo())) {
                List<Intento> intentos = intentoDAO.listarPorEvaluacionEInscripcion(
                        virtual.consultarIdEvaluacionVirtual(), inscripcion.consultarIdInscripcion());
                if (virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.CANCELADA && intentos.isEmpty()) {
                    continue;
                }
                int permitidos = virtual.consultarIntentosPermitidos();
                boolean disponible = virtualDAO.disponibleAhora(virtual.consultarIdEvaluacionVirtual());
                if (intentos.isEmpty()) {
                    Map<String, String> fila = filaBaseEvaluacion(inscripcion, virtual);
                    fila.put("usados", "0 de " + permitidos);
                    fila.put("intento", disponible ? "Intento 1 — Sin presentar" : "Sin presentar");
                    fila.put("accion", disponible ? enlaceInicio(req, virtual) : "");
                    filas.add(fila);
                    continue;
                }
                boolean enCurso = false;
                for (Intento intento : intentos) {
                    enCurso |= intento.estadoIntento == EstadoIntento.EN_CURSO;
                    Map<String, String> fila = filaBaseEvaluacion(inscripcion, virtual);
                    fila.put("usados", intentos.size() + " de " + permitidos);
                    fila.put("intento", "Intento " + intento.consultarNumeroIntento() + " — "
                            + resultadoDe(virtual, intento));
                    Map<String, String> enlace = new HashMap<>();
                    enlace.put("clase", "boton boton-contorno boton-pequeno");
                    enlace.put("url", ruta(req, "/app/estudiante/intento?id=" + intento.consultarIdIntento()));
                    enlace.put("texto", intento.estadoIntento == EstadoIntento.EN_CURSO ? "Continuar" : "Ver");
                    fila.put("accion", vistas.componer("_enlace-accion.html", enlace));
                    filas.add(fila);
                }
                if (!enCurso && disponible && intentos.size() < permitidos) {
                    int siguiente = 1;
                    for (Intento intento : intentos) {
                        siguiente = Math.max(siguiente, intento.consultarNumeroIntento() + 1);
                    }
                    Map<String, String> fila = filaBaseEvaluacion(inscripcion, virtual);
                    fila.put("usados", intentos.size() + " de " + permitidos);
                    fila.put("intento", "Intento " + siguiente + " — Disponible");
                    fila.put("accion", enlaceInicio(req, virtual));
                    filas.add(fila);
                }
            }
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-evaluacion-estudiante.html", filas,
                filaVacia("8", "No hay evaluaciones virtuales en tus grupos.")));
        renderizar(req, resp, "estudiante-evaluaciones.html", datos, null);
    }

    private Map<String, String> filaBaseEvaluacion(Inscripcion inscripcion, EvaluacionVirtual virtual) {
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
        return fila;
    }

    private static String resultadoDe(EvaluacionVirtual virtual, Intento intento) {
        if (virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.CANCELADA) {
            return "Cancelada";
        }
        if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
            return Etiquetas.de(intento.estadoIntento);
        }
        if (virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.ABIERTA
                || intento.estadoIntento != EstadoIntento.CALIFICADO) {
            return "Pendiente de calificación";
        }
        return Etiquetas.de(intento.estadoIntento) + " · Nota: "
                + Calificacion.formatoConMaximo(BigDecimal.valueOf(intento.calificacionIntento));
    }

    private String enlaceInicio(HttpServletRequest req, EvaluacionVirtual virtual) {
        Map<String, String> enlace = new HashMap<>();
        enlace.put("clase", "boton boton-pequeno");
        enlace.put("url", ruta(req, "/app/estudiante/intento/inicio?id="
                + virtual.consultarIdEvaluacionVirtual()));
        enlace.put("texto", "Iniciar intento");
        return vistas.componer("_enlace-accion.html", enlace);
    }

}
