package servlet;

import logica.Docente;
import logica.EstadoEvaluacion;
import logica.EstadoGrupo;
import logica.Evaluacion;
import logica.EvaluacionVirtual;
import logica.Grupo;
import logica.Inscripcion;
import logica.RecursoNoEncontradoException;
import logica.RegistroEnUsoException;
import logica.ValidacionException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DocenteGrupoServlet", urlPatterns = "/app/docente/grupo")
public class DocenteGrupoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/grupo";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idGrupo = parametroId(req, "id");
        Grupo grupo = grupoDelDocente(docente, idGrupo);
        boolean abierto = grupo.estadoGrupo == EstadoGrupo.ABIERTO;

        List<Map<String, String>> activos = new ArrayList<>();
        List<Map<String, String>> retirados = new ArrayList<>();
        for (Inscripcion inscripcion : inscripcionDAO.listarPorGrupo(idGrupo)) {
            Map<String, String> fila = new HashMap<>();
            fila.put("identificacion", inscripcion.estudianteInscripcion.identificacionUsuario);
            fila.put("nombre", inscripcion.estudianteInscripcion.nombreUsuario);
            fila.put("correo", inscripcion.estudianteInscripcion.correoUsuario);
            fila.put("fecha", fecha(inscripcion.fechaInscripcion));
            String idInscripcion = String.valueOf(inscripcion.consultarIdInscripcion());
            if (inscripcion.estadoInscripcion == logica.EstadoInscripcion.ACTIVA) {
                fila.put("accion", abierto ? boton(req, RUTA, idGrupo, "quitar", "Quitar",
                        "boton-peligro boton-pequeno", "idInscripcion", idInscripcion) : "");
                activos.add(fila);
            } else {
                fila.put("accion", abierto ? boton(req, RUTA, idGrupo, "reincorporar", "Reincorporar",
                        "boton-contorno boton-pequeno", "idInscripcion", idInscripcion) : "");
                retirados.add(fila);
            }
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("cursoNombre", grupo.cursoGrupo.nombreCurso);
        datos.put("cursoUrl", ruta(req, "/app/docente/curso?id=" + grupo.cursoGrupo.consultarIdCurso()));
        datos.put("grupoNombre", grupo.nombreGrupo);
        datos.put("periodo", grupo.periodoGrupo);
        datos.put("estado", Etiquetas.de(grupo.estadoGrupo));
        datos.put("claseEstado", abierto ? "insignia-activo" : "insignia-neutra");
        datos.put("codigo", grupo.codigoAccesoGrupo);
        datos.put("totalActivos", String.valueOf(activos.size()));
        datos.put("ayudaCodigo", abierto
                ? "Compártelo con tus estudiantes para que se unan desde Mis grupos. Si se filtra, genera uno nuevo: el anterior deja de funcionar."
                : "El grupo está cerrado, así que este código no permite inscribirse.");
        datos.put("botonRegenerar", abierto ? boton(req, RUTA, idGrupo, "regenerarCodigo", "Generar código nuevo", "boton-contorno") : "");
        datos.put("accionesGrupo", abierto
                ? boton(req, RUTA, idGrupo, "cerrar", "Cerrar grupo", "boton-peligro")
                : boton(req, RUTA, idGrupo, "reabrir", "Reabrir grupo", "boton-contorno"));
        datos.put("filasActivos", componerFilas("_fila-inscrito.html", activos,
                filaVacia("5", "Aún no hay estudiantes. Comparte el código de acceso para que se unan.")));

        if (retirados.isEmpty()) {
            datos.put("seccionRetirados", "");
        } else {
            datos.put("seccionRetirados", vistas.componer("_seccion-retirados.html",
                    Map.of("filas", componerFilas("_fila-inscrito.html", retirados, ""))));
        }

        // Evaluaciones virtuales del grupo
        List<Map<String, String>> filasVirtuales = new ArrayList<>();
        for (EvaluacionVirtual virtual : virtualDAO.listarPorGrupoDeDocente(idGrupo, docente.consultarIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, "/app/docente/virtual?id=" + virtual.consultarIdEvaluacionVirtual()));
            fila.put("titulo", virtual.evaluacionEvaluacionVirtual.tituloEvaluacion);
            fila.put("ventana", fechaHora(virtual.fechaInicioEvaluacionVirtual) + " — "
                    + fechaHora(virtual.fechaFinEvaluacionVirtual));
            fila.put("estado", Etiquetas.de(virtual.estadoEvaluacionVirtual));
            fila.put("claseEstado", DocenteVirtualServlet.claseEstado(virtual.estadoEvaluacionVirtual));
            fila.put("supervision", virtual.supervisionActivaEvaluacionVirtual ? "Activa" : "Inactiva");
            fila.put("intentosPermitidos", String.valueOf(virtual.consultarIntentosPermitidos()));
            fila.put("intentos", String.valueOf(virtualDAO.contarIntentos(virtual.consultarIdEvaluacionVirtual())));
            filasVirtuales.add(fila);
        }
        datos.put("filasVirtuales", componerFilas("_fila-virtual.html", filasVirtuales,
                filaVacia("5", "Todavía no hay evaluaciones virtuales programadas.")));

        StringBuilder opciones = new StringBuilder(Formulario.opcion(vistas, "", "Selecciona la evaluación", false));
        for (Evaluacion evaluacion : evaluacionDAO.listarPorDocente(docente.consultarIdUsuario())) {
            if (evaluacion.estadoEvaluacion == EstadoEvaluacion.LISTA) {
                opciones.append(Formulario.opcion(vistas, String.valueOf(evaluacion.consultarIdEvaluacion()),
                        evaluacion.tituloEvaluacion, false));
            }
        }
        datos.put("opcionesEvaluaciones", opciones.toString());
        datos.put("opcionesVersiones", opcionesVersiones(docente));
        datos.put("idGrupo", String.valueOf(idGrupo));
        renderizar(req, resp, "docente-grupo.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idGrupo = parametroId(req, "id");
        String destino = RUTA + "?id=" + idGrupo;

        switch (parametro(req, "accion")) {
            case "regenerarCodigo":
                ejecutar(req, resp, destino, null, () -> {
                    String codigo = grupoDAO.generarCodigoAccesoGrupo(grupoDelDocente(docente, idGrupo));
                    return "Código nuevo: " + codigo + ". El anterior ya no funciona.";
                });
                break;
            case "cerrar":
                ejecutar(req, resp, destino, null, () -> {
                    grupoDAO.cerrarGrupo(grupoDelDocente(docente, idGrupo));
                    return "Grupo cerrado. Los estudiantes conservan su historial.";
                });
                break;
            case "reabrir":
                ejecutar(req, resp, destino, null, () -> {
                    grupoDAO.reabrirGrupo(grupoDelDocente(docente, idGrupo));
                    return "Grupo reabierto.";
                });
                break;
            case "quitar": {
                int idInscripcion = parametroId(req, "idInscripcion");
                try {
                    Inscripcion inscripcion = inscripcionDAO.buscarPorIdDeDocente(idInscripcion, docente.consultarIdUsuario())
                            .orElseThrow(() -> new RecursoNoEncontradoException("Inscripción no encontrada"));
                    try {
                        inscripcionDAO.eliminarInscripcion(inscripcion);
                        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Estudiante quitado del grupo.");
                    } catch (RegistroEnUsoException e) {
                        inscripcionDAO.retirarInscripcion(inscripcion);
                        agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                                "El estudiante ya tenía actividad en el grupo: quedó como retirado y su historial se conserva.");
                    }
                } catch (ValidacionException e) {
                    agregarErrores(req, e.consultarErrores());
                }
                redirigir(req, resp, destino);
                break;
            }
            case "reincorporar": {
                int idInscripcion = parametroId(req, "idInscripcion");
                ejecutar(req, resp, destino, null, () -> {
                    Inscripcion inscripcion = inscripcionDAO.buscarPorIdDeDocente(idInscripcion, docente.consultarIdUsuario())
                            .orElseThrow(() -> new RecursoNoEncontradoException("Inscripción no encontrada"));
                    inscripcionDAO.reincorporarInscripcion(inscripcion);
                    return "Estudiante reincorporado.";
                });
                break;
            }
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private Grupo grupoDelDocente(Docente docente, int idGrupo) {
        java.util.Optional<Grupo> propio = grupoDAO.buscarPorIdDeDocente(idGrupo, docente.consultarIdUsuario());
        verificarPropiedad(propio.isPresent(), grupoDAO.buscarPorId(idGrupo).isPresent(), "Grupo no encontrado");
        return propio.get();
    }

    /** RF-41: versiones disponibles agrupadas por evaluación (optgroup). */
    private String opcionesVersiones(Docente docente) {
        StringBuilder sb = new StringBuilder(Formulario.opcion(vistas, "",
                "Selecciona al menos una versión", false));
        for (Evaluacion evaluacion : evaluacionDAO.listarPorDocente(docente.consultarIdUsuario())) {
            List<logica.VersionEvaluacion> versiones = versionDAO.listarPorEvaluacion(evaluacion.consultarIdEvaluacion());
            if (versiones.isEmpty()) {
                continue;
            }
            sb.append(vistas.componer("_optgroup-abrir.html", Map.of("etiqueta",
                    evaluacion.tituloEvaluacion + " (" + Etiquetas.de(evaluacion.estadoEvaluacion) + ")")));
            for (logica.VersionEvaluacion version : versiones) {
                sb.append(Formulario.opcion(vistas, String.valueOf(version.consultarIdVersionEvaluacion()),
                        "Versión " + version.codigoVersionEvaluacion, false));
            }
            sb.append(vistas.componer("_optgroup-cerrar.html", Map.of()));
        }
        return sb.toString();
    }
}