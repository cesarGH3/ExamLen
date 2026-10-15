package servlet;

import logica.Calificacion;
import logica.Docente;
import logica.EstadoEvaluacion;
import logica.EstadoEvaluacionVirtual;
import logica.EstadoIntento;
import logica.Fechas;
import logica.Evaluacion;
import logica.EvaluacionVirtual;
import logica.Grupo;
import logica.Inscripcion;
import logica.Intento;
import logica.RecursoNoEncontradoException;
import logica.ValidacionException;
import logica.Validador;
import logica.VersionEvaluacion;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DocenteVirtualServlet", urlPatterns = "/app/docente/virtual")
public class DocenteVirtualServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/virtual";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = parametroId(req, "id");
        EvaluacionVirtual virtual = virtualDelDocente(docente, id);
        Grupo grupo = virtualDAO.grupoDeVirtual(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Grupo no encontrado"));

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(id));
        datos.put("titulo", virtual.evaluacionEvaluacionVirtual.tituloEvaluacion);
        datos.put("estado", Etiquetas.de(virtual.estadoEvaluacionVirtual));
        datos.put("claseEstado", claseEstado(virtual.estadoEvaluacionVirtual));
        datos.put("inicio", fechaHora(virtual.fechaInicioEvaluacionVirtual));
        datos.put("fin", fechaHora(virtual.fechaFinEvaluacionVirtual));
        datos.put("duracion", String.valueOf(virtual.evaluacionEvaluacionVirtual.duracionMinutosEvaluacion));
        datos.put("intentos", String.valueOf(virtualDAO.contarIntentos(id)));
        datos.put("cursoNombre", grupo.cursoGrupo.nombreCurso);
        datos.put("grupoNombre", grupo.nombreGrupo);
        datos.put("urlGrupo", ruta(req, "/app/docente/grupo?id=" + grupo.getIdGrupo()));
        datos.put("accionesEstado", accionesEstado(req, virtual));
        datos.put("filasVersiones", construirFilasVersiones(req, virtual));
        datos.put("filasIntentos", construirFilasIntentos(req, virtual, grupo));
        datos.put("supervision", virtual.supervisionActivaEvaluacionVirtual ? "Activa" : "Desactivada");
        datos.put("confirmarCancelacion", confirmarCancelacion(req, virtual));
        renderizar(req, resp, "docente-virtual.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        try {
            switch (parametro(req, "accion")) {
                case "programar": {
                    int idGrupo = parametroId(req, "idGrupo");
                    Grupo grupo = grupoDAO.buscarPorIdDeDocente(idGrupo, docente.getIdUsuario())
                            .orElseThrow(() -> new RecursoNoEncontradoException("Grupo no encontrado"));
                    int idEvaluacion = parametroId(req, "idEvaluacion");
                    Evaluacion evaluacion = evaluacionDAO.buscarPorIdDeDocente(idEvaluacion, docente.getIdUsuario())
                            .orElseThrow(() -> new RecursoNoEncontradoException("Evaluación no encontrada"));
                    if (grupo.estadoGrupo != logica.EstadoGrupo.ABIERTO) {
                        throw new ValidacionException("Solo se puede programar en un grupo abierto.");
                    }
                    if (evaluacion.estadoEvaluacion != EstadoEvaluacion.LISTA) {
                        throw new ValidacionException("Solo se pueden programar evaluaciones en estado lista.");
                    }
                    Date inicio = fechaHoraDe(req, "fechaInicio");
                    Date fin = fechaHoraDe(req, "fechaFin");
                    if (inicio == null || fin == null) {
                        throw new ValidacionException("Indica la fecha y hora de inicio y de fin.");
                    }
                    if (inicio.after(fin) || inicio.equals(fin)) {
                        throw new ValidacionException("La fecha de inicio debe ser anterior a la fecha de fin.");
                    }
                    List<VersionEvaluacion> versiones = versionDAO.listarPorEvaluacion(idEvaluacion);
                    if (versiones.isEmpty()) {
                        throw new ValidacionException("La evaluación no tiene versiones generadas: genera "
                                + "versiones y vuelve a intentarlo.");
                    }
                    List<Integer> seleccionadas = idsVersionesSeleccionadas(req);
                    if (seleccionadas.isEmpty()) {
                        throw new ValidacionException("Selecciona al menos una versión para aplicar.");
                    }
                    for (Integer idVersion : seleccionadas) {
                        if (!versionDAO.perteneceAEvaluacion(idVersion, idEvaluacion)) {
                            throw new ValidacionException("Una de las versiones seleccionadas no pertenece "
                                    + "a esta evaluación.");
                        }
                    }
                    boolean supervision = req.getParameter("supervision") != null;
                    EvaluacionVirtual virtual = virtualDAO.programarEvaluacionVirtual(grupo, evaluacion, inicio, fin,
                            supervision);
                    for (Integer idVersion : seleccionadas) {
                        virtualDAO.agregarVersion(virtual.getIdEvaluacionVirtual(), idVersion);
                    }
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                            "Evaluación virtual programada con " + seleccionadas.size() + " versión(es). "
                            + "Revisa las versiones a aplicar y ábrela cuando quieras.");
                    redirigir(req, resp, RUTA + "?id=" + virtual.getIdEvaluacionVirtual());
                    return;
                }
                case "abrir": {
                    EvaluacionVirtual virtual = virtualDelDocente(docente, parametroId(req, "id"));
                    if (!servicioVirtual.abrir(virtual.getIdEvaluacionVirtual())) {
                        throw new ValidacionException("Solo se puede abrir una evaluación virtual programada.");
                    }
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Evaluación virtual abierta.");
                    redirigir(req, resp, RUTA + "?id=" + virtual.getIdEvaluacionVirtual());
                    return;
                }
                case "cerrar": {
                    EvaluacionVirtual virtual = virtualDelDocente(docente, parametroId(req, "id"));
                    servicioVirtual.cerrar(virtual.getIdEvaluacionVirtual());
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                            "Evaluación virtual cerrada. Los intentos en curso quedaron finalizados.");
                    redirigir(req, resp, RUTA + "?id=" + virtual.getIdEvaluacionVirtual());
                    return;
                }
                case "cancelar": {
                    EvaluacionVirtual virtual = virtualDelDocente(docente, parametroId(req, "id"));
                    int intentos = virtualDAO.contarIntentos(virtual.getIdEvaluacionVirtual());
                    if (intentos > 0 && !"1".equals(req.getParameter("confirmado"))) {
                        // CU-26 3a: con intentos se advierte y se pide doble confirmación.
                        agregarErrores(req, List.of("Esta evaluación ya tiene " + intentos
                                + " intento(s): si la cancelas, quedarán sin efecto. "
                                + "Confirma la cancelación para continuar."));
                        redirigir(req, resp, RUTA + "?id=" + virtual.getIdEvaluacionVirtual()
                                + "&accion=cancelar");
                        return;
                    }
                    if (!servicioVirtual.cancelar(virtual.getIdEvaluacionVirtual())) {
                        throw new ValidacionException("La evaluación ya está cancelada o cerrada.");
                    }
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Evaluación virtual cancelada. "
                            + (intentos > 0 ? "Los intentos existentes quedaron sin efecto (la historia se conserva)." : ""));
                    redirigir(req, resp, RUTA + "?id=" + virtual.getIdEvaluacionVirtual());
                    return;
                }
                case "agregarVersion":
                case "quitarVersion": {
                    EvaluacionVirtual virtual = virtualDelDocente(docente, parametroId(req, "id"));
                    if (virtual.estadoEvaluacionVirtual != EstadoEvaluacionVirtual.PROGRAMADA) {
                        throw new ValidacionException("Las versiones solo se pueden cambiar mientras está programada.");
                    }
                    int idVersion = parametroId(req, "idVersion");
                    if (!versionDAO.perteneceAEvaluacion(idVersion, virtual.evaluacionEvaluacionVirtual.getIdEvaluacion())) {
                        throw new ValidacionException("La versión indicada no pertenece a esta evaluación.");
                    }
                    if ("agregarVersion".equals(parametro(req, "accion"))) {
                        virtualDAO.agregarVersion(virtual.getIdEvaluacionVirtual(), idVersion);
                        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Versión agregada a la aplicación.");
                    } else {
                        if (virtualDAO.listarVersiones(virtual.getIdEvaluacionVirtual()).size() <= 1) {
                            throw new ValidacionException("Debe quedar al menos una versión para aplicar.");
                        }
                        virtualDAO.quitarVersion(virtual.getIdEvaluacionVirtual(), idVersion);
                        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Versión quitada de la aplicación.");
                    }
                    redirigir(req, resp, RUTA + "?id=" + virtual.getIdEvaluacionVirtual());
                    return;
                }
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
            String destino = Validador.parsearId(req.getParameter("id")) > 0
                    ? RUTA + "?id=" + Validador.parsearId(req.getParameter("id"))
                    : "/app/docente/cursos";
            redirigir(req, resp, destino);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private EvaluacionVirtual virtualDelDocente(Docente docente, int id) {
        java.util.Optional<EvaluacionVirtual> propia = virtualDAO.buscarPorIdDeDocente(id, docente.getIdUsuario());
        verificarPropiedad(propia.isPresent(), virtualDAO.buscarPorId(id).isPresent(),
                "Evaluación virtual no encontrada");
        return propia.get();
    }

    /** CU-26 3a: bloque de doble confirmación cuando se intenta cancelar con intentos. */
    private String confirmarCancelacion(HttpServletRequest req, EvaluacionVirtual virtual) {
        if (!"cancelar".equals(parametro(req, "accion"))) {
            return "";
        }
        int intentos = virtualDAO.contarIntentos(virtual.getIdEvaluacionVirtual());
        if (intentos == 0) {
            return "";
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("mensaje", "Esta evaluación tiene " + intentos + " intento(s). Si la cancelas, quedarán sin "
                + "efecto (la historia se conserva). Confirma para cancelarla definitivamente.");
        datos.put("destino", ruta(req, RUTA));
        datos.put("csrf", csrf(req));
        datos.put("id", String.valueOf(virtual.getIdEvaluacionVirtual()));
        datos.put("confirmados", confirmacionesCancelacion(intentos));
        return vistas.componer("_confirmar-cancelar-virtual.html", datos);
    }

    private static String confirmacionesCancelacion(int intentos) {
        return intentos == 1 ? "1 intento" : intentos + " intentos";
    }

    /** Versiones seleccionadas en el formulario de programación (RF-41). */
    private static List<Integer> idsVersionesSeleccionadas(HttpServletRequest req) {
        List<Integer> ids = new ArrayList<>();
        String[] valores = req.getParameterValues("idVersiones");
        if (valores == null) {
            return ids;
        }
        for (String valor : valores) {
            int id = Validador.parsearId(valor);
            if (id > 0 && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Date fechaHoraDe(HttpServletRequest req, String nombre) {
        return Fechas.parsear("yyyy-MM-dd'T'HH:mm", Validador.limpiar(req.getParameter(nombre)));
    }

    static String claseEstado(EstadoEvaluacionVirtual estado) {
        switch (estado) {
            case ABIERTA:
                return "insignia-activo";
            case CANCELADA:
                return "insignia-inactivo";
            default:
                return "insignia-neutra";
        }
    }

    private String construirFilasVersiones(HttpServletRequest req, EvaluacionVirtual virtual) {
        List<VersionEvaluacion> todas = versionDAO.listarPorEvaluacion(
                virtual.evaluacionEvaluacionVirtual.getIdEvaluacion());
        List<VersionEvaluacion> aplicadas = virtualDAO.listarVersiones(virtual.getIdEvaluacionVirtual());
        boolean editable = virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.PROGRAMADA;

        List<Map<String, String>> filas = new ArrayList<>();
        for (VersionEvaluacion version : todas) {
            boolean aplicada = false;
            for (VersionEvaluacion aplicadaVersion : aplicadas) {
                if (aplicadaVersion.getIdVersionEvaluacion() == version.getIdVersionEvaluacion()) {
                    aplicada = true;
                    break;
                }
            }
            Map<String, String> fila = new HashMap<>();
            fila.put("codigo", version.codigoVersionEvaluacion);
            fila.put("aplicada", aplicada ? "Sí" : "No");
            fila.put("claseAplicada", aplicada ? "insignia-activo" : "insignia-neutra");
            String accion = "";
            if (editable) {
                accion = aplicada
                        ? boton(req, RUTA, virtual.getIdEvaluacionVirtual(), "quitarVersion", "Quitar",
                                "boton-peligro boton-pequeno", "idVersion", String.valueOf(version.getIdVersionEvaluacion()))
                        : boton(req, RUTA, virtual.getIdEvaluacionVirtual(), "agregarVersion", "Aplicar",
                                "boton-contorno boton-pequeno", "idVersion", String.valueOf(version.getIdVersionEvaluacion()));
            }
            fila.put("accion", accion);
            filas.add(fila);
        }
        return componerFilas("_fila-version-virtual.html", filas,
                filaVacia("3", "La evaluación no tiene versiones."));
    }

    private String construirFilasIntentos(HttpServletRequest req, EvaluacionVirtual virtual, Grupo grupo) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (Inscripcion inscripcion : inscripcionDAO.listarPorGrupo(grupo.getIdGrupo())) {
            Intento intento = intentoDAO
                    .buscarPorEvaluacionEInscripcion(virtual.getIdEvaluacionVirtual(), inscripcion.getIdInscripcion())
                    .orElse(null);
            Map<String, String> fila = new HashMap<>();
            fila.put("estudiante", inscripcion.estudianteInscripcion.nombreUsuario);
            fila.put("identificacion", inscripcion.estudianteInscripcion.identificacionUsuario);
            if (intento == null) {
                fila.put("version", "—");
                fila.put("inicio", "—");
                fila.put("fin", "—");
                fila.put("tiempo", "—");
                fila.put("estado", "No presentó");
                fila.put("claseEstado", "insignia-neutra");
                fila.put("nota", "—");
                fila.put("eventos", "—");
                fila.put("accion", "");
            } else {
                fila.put("version", intento.versionEvaluacionIntento.codigoVersionEvaluacion);
                fila.put("inicio", fechaHora(intento.fechaInicioIntento));
                fila.put("fin", fechaHora(intento.fechaFinIntento));
                fila.put("tiempo", formatoDuracion(intento.fechaInicioIntento, intento.fechaFinIntento));
                fila.put("estado", Etiquetas.de(intento.estadoIntento));
                fila.put("claseEstado", claseEstadoIntento(intento.estadoIntento));
                if (intento.estadoIntento == EstadoIntento.EN_CURSO) {
                    fila.put("nota", "—");
                } else if (intento.estadoIntento == EstadoIntento.CALIFICADO) {
                    fila.put("nota", Calificacion.formatoConMaximo(
                            BigDecimal.valueOf(intento.calificacionIntento)));
                } else {
                    fila.put("nota", "Pendiente");
                }
                fila.put("eventos", String.valueOf(eventoDAO.contarPorIntento(intento.getIdIntento())));
                Map<String, String> enlace = new HashMap<>();
                enlace.put("clase", "boton boton-contorno boton-pequeno");
                enlace.put("url", ruta(req, "/app/docente/intento?id=" + intento.getIdIntento()));
                enlace.put("texto", "Ver");
                fila.put("accion", vistas.componer("_enlace-accion.html", enlace));
            }
            filas.add(fila);
        }
        return componerFilas("_fila-intento-docente.html", filas,
                filaVacia("9", "El grupo no tiene estudiantes inscritos."));
    }

    /** Tiempo empleado en minutos (o «H h M min» si pasa de una hora); «—» si sigue en curso. */
    private static String formatoDuracion(Date inicio, Date fin) {
        if (inicio == null || fin == null) {
            return "—";
        }
        long minutos = Math.round((fin.getTime() - inicio.getTime()) / 60000.0);
        if (minutos < 60) {
            return minutos + " min";
        }
        return (minutos / 60) + " h " + (minutos % 60) + " min";
    }

    private static String claseEstadoIntento(EstadoIntento estado) {
        switch (estado) {
            case EN_CURSO:
                return "insignia-borrador";
            case FINALIZADO:
                return "insignia-neutra";
            default:
                return "insignia-activo";
        }
    }

    private String accionesEstado(HttpServletRequest req, EvaluacionVirtual virtual) {
        int id = virtual.getIdEvaluacionVirtual();
        StringBuilder sb = new StringBuilder();
        switch (virtual.estadoEvaluacionVirtual) {
            case PROGRAMADA:
                sb.append(boton(req, RUTA, id, "abrir", "Abrir", ""));
                sb.append(boton(req, RUTA, id, "cancelar", "Cancelar", "boton-peligro"));
                break;
            case ABIERTA:
                sb.append(boton(req, RUTA, id, "cerrar", "Cerrar", "boton-peligro"));
                sb.append(boton(req, RUTA, id, "cancelar", "Cancelar", "boton-contorno"));
                break;
            default:
                break;
        }
        return sb.toString();
    }
}
