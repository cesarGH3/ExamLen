package servlet;

import logica.Administrador;
import logica.Curso;
import logica.Docente;
import logica.EstadoEvaluacion;
import logica.EstadoEvaluacionVirtual;
import logica.EstadoSolicitud;
import logica.Estudiante;
import logica.Evaluacion;
import logica.EvaluacionVirtual;
import logica.Inscripcion;
import logica.Intento;
import logica.NivelIngles;
import logica.SolicitudGeneracion;
import logica.Usuario;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Inicio: saludo y resumen util por rol (contadores con datos reales). Las casillas que
 * duplicaban el menu superior se retiraron. Solo el administrador ve, ademas, la herramienta
 * temporal de rendimiento (dos botones).
 */
@WebServlet(name = "InicioServlet", urlPatterns = "/app/inicio")
public class InicioServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Usuario usuario = usuarioActual(req);
        Map<String, String> datos = new HashMap<>();
        datos.put("nombre", usuario.nombreUsuario);
        datos.put("tipo", Etiquetas.rol(usuario));
        datos.put("resumen", resumenPorTipo(usuario, req.getContextPath()));
        // TEMPORAL - HERRAMIENTA ACADEMICA DE RENDIMIENTO (solo ADMIN; retirar despues de la prueba)
        datos.put("herramientaRendimiento", usuario instanceof Administrador
                ? vistas.componer("_rendimiento-herramienta.html",
                        Map.of("ctx", req.getContextPath(), "csrf", csrf(req)))
                : "");
        renderizar(req, resp, "inicio.html", datos, null);
    }

    private String resumenPorTipo(Usuario usuario, String ctx) {
        if (usuario instanceof Administrador) {
            return dato(usuarioDAO.contarActivosPorRol("ADMINISTRADOR"), "Administradores activos",
                        ctx + "/app/admin/administradores", "Gestionar")
                    + dato(usuarioDAO.contarActivosPorRol("DOCENTE"), "Docentes activos",
                        ctx + "/app/admin/docentes", "Gestionar")
                    + dato(usuarioDAO.contarActivosPorRol("ESTUDIANTE"), "Estudiantes activos",
                        ctx + "/app/admin/estudiantes", "Gestionar");
        }
        if (usuario instanceof Docente) {
            Docente docente = (Docente) usuario;
            int cursos = 0;
            int gruposAbiertos = 0;
            for (Curso curso : cursoDAO.listarPorDocente(docente.consultarIdUsuario())) {
                cursos++;
                gruposAbiertos += grupoDAO.contarAbiertosPorCurso(curso.consultarIdCurso());
            }
            int evaluacionesListas = 0;
            for (Evaluacion evaluacion : evaluacionDAO.listarPorDocente(docente.consultarIdUsuario())) {
                if (evaluacion.estadoEvaluacion == EstadoEvaluacion.LISTA) {
                    evaluacionesListas++;
                }
            }
            int iaPendientes = 0;
            for (SolicitudGeneracion solicitud : solicitudDAO.listarPorDocente(docente.consultarIdUsuario())) {
                if (solicitud.estadoSolicitudGeneracion == EstadoSolicitud.EN_PROCESO
                        || solicitud.estadoSolicitudGeneracion == EstadoSolicitud.FALLIDA) {
                    iaPendientes++;
                }
            }
            return dato(cursos, "Cursos", ctx + "/app/docente/cursos", "Ver cursos")
                    + dato(gruposAbiertos, "Grupos abiertos", ctx + "/app/docente/cursos", "Ver cursos")
                    + dato(evaluacionesListas, "Evaluaciones listas", ctx + "/app/docente/evaluaciones",
                        "Ver evaluaciones")
                    + dato(iaPendientes, "IA en proceso o fallida", ctx + "/app/docente/ia",
                        "Ver generación IA");
        }
        Estudiante estudiante = (Estudiante) usuario;
        int disponibles = 0;
        for (Inscripcion inscripcion : inscripcionDAO.listarActivasPorEstudiante(estudiante.consultarIdUsuario())) {
            for (EvaluacionVirtual virtual : virtualDAO.listarPorGrupo(inscripcion.grupoInscripcion.consultarIdGrupo())) {
                if (virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.CANCELADA) {
                    continue;
                }
                java.util.List<Intento> intentos = intentoDAO.listarPorEvaluacionEInscripcion(
                        virtual.consultarIdEvaluacionVirtual(), inscripcion.consultarIdInscripcion());
                boolean enCurso = intentos.stream()
                        .anyMatch(intento -> intento.estadoIntento == logica.EstadoIntento.EN_CURSO);
                if (enCurso) {
                    disponibles++;
                    continue;
                }
                if (intentos.size() < virtual.consultarIntentosPermitidos()
                        && virtualDAO.disponibleAhora(virtual.consultarIdEvaluacionVirtual())) {
                    disponibles++;
                }
            }
        }
        NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, estudiante.nivelInglesEstudiante);
        int practicas = materialDAO.listarPublicadosDeEstudiante(estudiante.consultarIdUsuario(), nivel).size();
        int realizadas = intentoPracticaDAO.contarPorEstudiante(estudiante.consultarIdUsuario());
        return dato(disponibles, "Evaluaciones disponibles ahora", ctx + "/app/estudiante/evaluaciones",
                    "Ver evaluaciones")
                + dato(practicas, "Prácticas publicadas", ctx + "/app/estudiante/practicas", "Ver prácticas")
                + dato(realizadas, "Prácticas realizadas", ctx + "/app/estudiante/practica/historial",
                    "Ver historial");
    }

    private String dato(int valor, String etiqueta, String enlace, String accion) {
        Map<String, String> enlaceDatos = new HashMap<>();
        enlaceDatos.put("clase", "boton boton-contorno boton-pequeno");
        enlaceDatos.put("url", enlace);
        enlaceDatos.put("texto", accion);
        Map<String, String> datos = new HashMap<>();
        datos.put("valor", String.valueOf(valor));
        datos.put("etiqueta", etiqueta);
        datos.put("enlace", vistas.componer("_enlace-accion.html", enlaceDatos));
        return vistas.componer("_dato-resumen.html", datos);
    }
}
