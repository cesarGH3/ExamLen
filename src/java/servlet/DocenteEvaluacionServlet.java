package servlet;

import logica.Competencia;
import logica.Dificultad;
import logica.Docente;
import logica.EstadoEvaluacion;
import logica.EstadoPregunta;
import logica.Evaluacion;
import logica.NivelIngles;
import logica.Pregunta;
import logica.RecursoNoEncontradoException;
import logica.ValidacionException;
import logica.Validador;
import logica.VersionEvaluacion;
import logica.VersionPregunta;
import modelo.PreguntaDAO;
import modelo.VersionEvaluacionDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DocenteEvaluacionServlet", urlPatterns = "/app/docente/evaluacion")
public class DocenteEvaluacionServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/evaluacion";
    private static final int MIN_PREGUNTAS = 1;
    private static final int MIN_VERSIONES = 1;
    private static final int MAX_VERSIONES = 10;
    private static final int MIN_DURACION = 1;
    private static final double MAX_PUNTAJE_GRUPO = 1000;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = parametroId(req, "id");
        Evaluacion evaluacion = evaluacionDelDocente(docente, id);

        List<Pregunta> seleccionadas = preguntaDAO.listarPorEvaluacion(id);
        Map<Integer, Integer> ordenes = preguntaDAO.ordenDeEvaluacion(id);
        Map<Integer, Double> puntajes = preguntaDAO.puntajeDeEvaluacion(id);
        List<Grupo> grupos = agrupar(seleccionadas, ordenes, puntajes);

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(id));
        datos.put("titulo", evaluacion.tituloEvaluacion);
        datos.put("estado", Etiquetas.de(evaluacion.estadoEvaluacion));
        datos.put("claseEstado", DocenteEvaluacionesServlet.claseEstado(evaluacion.estadoEvaluacion));
        datos.put("totalGrupos", String.valueOf(grupos.size()));
        datos.put("totalSeleccionadas", String.valueOf(seleccionadas.size()));
        datos.put("numeroPreguntas", String.valueOf(evaluacion.numeroPreguntasEvaluacion));
        datos.put("numeroVersiones", String.valueOf(evaluacion.numeroVersionesEvaluacion));
        datos.put("duracionMinutos", String.valueOf(evaluacion.duracionMinutosEvaluacion));
        datos.put("filasGrupos", construirFilasGrupos(req, evaluacion, grupos));
        datos.put("filasDisponibles", construirDisponibles(req, docente, evaluacion, seleccionadas));
        datos.put("resumenTema", resumenDistribucion(seleccionadas, 0));
        datos.put("resumenCompetencia", resumenDistribucion(seleccionadas, 1));
        datos.put("resumenDificultad", resumenDistribucion(seleccionadas, 2));
        datos.put("opcionesFiltroTema", opcionesFiltroTema(seleccionadas, parametro(req, "tema")));
        datos.put("opcionesFiltroNivel", Formulario.opciones(vistas, NivelIngles.values(),
                Formulario.enumeracion(NivelIngles.class, req.getParameter("nivel")), "Todos"));
        datos.put("opcionesFiltroDificultad", Formulario.opciones(vistas, Dificultad.values(),
                Formulario.enumeracion(Dificultad.class, req.getParameter("dificultad")), "Todas"));
        datos.put("opcionesFiltroCompetencia", Formulario.opciones(vistas, Competencia.values(),
                Formulario.enumeracion(Competencia.class, req.getParameter("competencia")), "Todas"));
        datos.put("limpiarFiltros", hayFiltros(req)
                ? vistas.componer("_enlace-accion.html", Map.of("clase", "boton boton-contorno boton-pequeno",
                        "url", ruta(req, RUTA + "?id=" + id), "texto", "Quitar filtros"))
                : "");
        datos.put("accionesEstado", accionesEstado(req, evaluacion));
        datos.put("botonGenerar", boton(req, RUTA, id, "generarVersiones", "Generar versiones", ""));
        datos.put("filasVersiones", construirVersiones(req, id));
        datos.put("comparativa", construirComparativa(evaluacion, seleccionadas, puntajes));
        datos.put("detalleVersion", construirDetalleVersion(req, docente, id));
        datos.put("opcionesCursos", opcionesCursos(docente));

        renderizar(req, resp, "docente-evaluacion.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = parametroId(req, "id");
        String destino = RUTA + "?id=" + id;
        try {
            Evaluacion evaluacion = evaluacionDelDocente(docente, id);
            switch (parametro(req, "accion")) {
                case "configurar": {
                    int numeroPreguntas = entero(req, "numeroPreguntas");
                    int numeroVersiones = entero(req, "numeroVersiones");
                    int duracionMinutos = entero(req, "duracionMinutos");
                    List<String> errores = new ArrayList<>();
                    if (numeroPreguntas < MIN_PREGUNTAS) {
                        errores.add("El número de preguntas debe ser al menos 1.");
                    }
                    if (numeroVersiones < MIN_VERSIONES || numeroVersiones > MAX_VERSIONES) {
                        errores.add("El número de versiones debe estar entre 1 y 10.");
                    }
                    if (duracionMinutos < MIN_DURACION) {
                        errores.add("La duración debe ser al menos 1 minuto.");
                    }
                    if (!errores.isEmpty()) {
                        throw new ValidacionException(errores);
                    }
                    exigirEditable(evaluacion);
                    evaluacionDAO.volverBorrador(evaluacion);
                    evaluacionDAO.configurarEvaluacion(evaluacion, numeroPreguntas, numeroVersiones, duracionMinutos);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Configuración guardada.");
                    break;
                }
                case "seleccionarPregunta": {
                    int idPregunta = parametroId(req, "idPregunta");
                    Pregunta pregunta = preguntaDeDocente(docente, idPregunta);
                    if (pregunta.estadoPregunta != EstadoPregunta.APROBADA) {
                        throw new ValidacionException("Solo se pueden seleccionar preguntas aprobadas.");
                    }
                    exigirEditable(evaluacion);
                    evaluacionDAO.volverBorrador(evaluacion);
                    preguntaDAO.seleccionarPregunta(pregunta, evaluacion);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta agregada a la evaluación.");
                    break;
                }
                case "quitarPregunta": {
                    int idPregunta = parametroId(req, "idPregunta");
                    Pregunta pregunta = preguntaDeDocente(docente, idPregunta);
                    exigirEditable(evaluacion);
                    if (preguntaDAO.listarPorEvaluacion(id).size() <= 1) {
                        throw new ValidacionException("La evaluación debe conservar al menos una pregunta.");
                    }
                    evaluacionDAO.volverBorrador(evaluacion);
                    preguntaDAO.quitarPregunta(pregunta, evaluacion);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta quitada de la evaluación.");
                    break;
                }
                case "subirGrupo":
                case "bajarGrupo": {
                    exigirEditable(evaluacion);
                    evaluacionDAO.volverBorrador(evaluacion);
                    if (moverGrupo(evaluacion, req, "subirGrupo".equals(parametro(req, "accion")))) {
                        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Orden de los grupos actualizado.");
                    }
                    break;
                }
                case "puntajeGrupo": {
                    double puntaje = decimal(req, "puntaje");
                    if (puntaje < 0 || puntaje > MAX_PUNTAJE_GRUPO) {
                        throw new ValidacionException("El puntaje debe estar entre 0 y 1000.");
                    }
                    NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
                    Competencia competencia = Formulario.enumeracion(Competencia.class, parametro(req, "competencia"));
                    Dificultad dificultad = Formulario.enumeracion(Dificultad.class, parametro(req, "dificultad"));
                    if (nivel == null || competencia == null || dificultad == null) {
                        throw new ValidacionException("El grupo indicado no existe.");
                    }
                    exigirEditable(evaluacion);
                    evaluacionDAO.volverBorrador(evaluacion);
                    preguntaDAO.asignarPuntajeGrupoEnEvaluacion(id, nivel, competencia, dificultad, puntaje);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Puntaje del grupo actualizado.");
                    break;
                }
                case "marcarLista": {
                    exigirEditable(evaluacion);
                    if (evaluacion.numeroPreguntasEvaluacion < MIN_PREGUNTAS
                            || evaluacion.numeroVersionesEvaluacion < MIN_VERSIONES
                            || evaluacion.numeroVersionesEvaluacion > MAX_VERSIONES
                            || evaluacion.duracionMinutosEvaluacion < MIN_DURACION) {
                        throw new ValidacionException("Configura la evaluación antes de marcarla como lista.");
                    }
                    List<Pregunta> noAprobadas = preguntaDAO.listarNoAprobadasDeEvaluacion(id);
                    if (!noAprobadas.isEmpty()) {
                        throw new ValidacionException(PreguntaDAO.erroresPreguntasNoAprobadas(
                                "No se puede marcar la evaluación como lista", noAprobadas));
                    }
                    List<Pregunta> seleccionadas = preguntaDAO.listarPorEvaluacion(id);
                    int faltan = evaluacion.numeroPreguntasEvaluacion - seleccionadas.size();
                    if (faltan > 0) {
                        throw new ValidacionException("Faltan " + faltan + (faltan == 1 ? " pregunta" : " preguntas")
                                + " para completar la evaluación.");
                    }
                    evaluacionDAO.marcarListaEvaluacion(evaluacion);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Evaluación marcada como lista.");
                    break;
                }
                case "archivar": {
                    if (evaluacion.estadoEvaluacion == EstadoEvaluacion.ARCHIVADA) {
                        throw new ValidacionException("La evaluación ya está archivada.");
                    }
                    evaluacionDAO.archivarEvaluacion(evaluacion);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Evaluación archivada.");
                    break;
                }
                case "generarVersiones": {
                    exigirEditable(evaluacion);
                    List<Pregunta> seleccionadas = preguntaDAO.listarPorEvaluacion(id);
                    if (seleccionadas.isEmpty()) {
                        throw new ValidacionException("Selecciona preguntas aprobadas antes de generar versiones.");
                    }
                    List<VersionEvaluacion> versiones = versionDAO.generarVersionEvaluacion(evaluacion);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Versiones generadas: "
                            + versiones.stream().map(v -> v.codigoVersionEvaluacion).reduce((a, b) -> a + ", " + b).orElse("") + ".");
                    break;
                }
                case "moverPreguntaVersion": {
                    int idVersion = parametroId(req, "idVersion");
                    verificarPropiedad(versionDAO.buscarPorIdDeDocente(idVersion, docente.getIdUsuario()).isPresent(),
                            versionDAO.buscarPorId(idVersion).isPresent(), "Versión no encontrada");
                    String motivo = motivoBloqueoVersion(idVersion);
                    if (motivo != null) {
                        throw new ValidacionException(motivo);
                    }
                    moverPreguntaVersion(req, idVersion);
                    destino = destino + "&version=" + idVersion;
                    break;
                }
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
        }
        redirigir(req, resp, destino);
    }

    // ------------------------------------------------------------------ acciones auxiliares

    private Evaluacion evaluacionDelDocente(Docente docente, int id) {
        java.util.Optional<Evaluacion> propia = evaluacionDAO.buscarPorIdDeDocente(id, docente.getIdUsuario());
        verificarPropiedad(propia.isPresent(), evaluacionDAO.buscarPorId(id).isPresent(),
                "Evaluación no encontrada");
        return propia.get();
    }

    private Pregunta preguntaDeDocente(Docente docente, int idPregunta) {
        Pregunta pregunta = preguntaDAO.buscarPorId(idPregunta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pregunta no encontrada"));
        verificarPropiedad(preguntaDAO.esDeDocente(idPregunta, docente.getIdUsuario()), true,
                "Pregunta no encontrada");
        return pregunta;
    }

    private void exigirEditable(Evaluacion evaluacion) throws ValidacionException {
        if (evaluacion.estadoEvaluacion == EstadoEvaluacion.ARCHIVADA) {
            throw new ValidacionException("La evaluación está archivada y no se puede editar.");
        }
    }

    private boolean moverGrupo(Evaluacion evaluacion, HttpServletRequest req, boolean subir) throws ValidacionException {
        NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
        Competencia competencia = Formulario.enumeracion(Competencia.class, parametro(req, "competencia"));
        Dificultad dificultad = Formulario.enumeracion(Dificultad.class, parametro(req, "dificultad"));
        if (nivel == null || competencia == null || dificultad == null) {
            throw new ValidacionException("El grupo indicado no existe.");
        }
        List<Pregunta> seleccionadas = preguntaDAO.listarPorEvaluacion(evaluacion.getIdEvaluacion());
        List<Grupo> grupos = agrupar(seleccionadas, preguntaDAO.ordenDeEvaluacion(evaluacion.getIdEvaluacion()),
                preguntaDAO.puntajeDeEvaluacion(evaluacion.getIdEvaluacion()));
        int indice = -1;
        for (int i = 0; i < grupos.size(); i++) {
            Grupo grupo = grupos.get(i);
            if (grupo.nivel == nivel && grupo.competencia == competencia && grupo.dificultad == dificultad) {
                indice = i;
                break;
            }
        }
        if (indice < 0) {
            return false;
        }
        int destinoIndice = subir ? indice - 1 : indice + 1;
        if (destinoIndice < 0 || destinoIndice >= grupos.size()) {
            return false;
        }
        Grupo actual = grupos.get(indice);
        Grupo vecino = grupos.get(destinoIndice);
        if (actual.orden == vecino.orden) {
            return false;
        }
        preguntaDAO.asignarOrdenGrupoEnEvaluacion(evaluacion.getIdEvaluacion(), actual.nivel, actual.competencia,
                actual.dificultad, vecino.orden);
        preguntaDAO.asignarOrdenGrupoEnEvaluacion(evaluacion.getIdEvaluacion(), vecino.nivel, vecino.competencia,
                vecino.dificultad, actual.orden);
        return true;
    }

    private void moverPreguntaVersion(HttpServletRequest req, int idVersion) {
        int idVersionPregunta = parametroId(req, "idVersionPregunta");
        boolean subir = "subir".equals(parametro(req, "direccion"));
        List<VersionPregunta> preguntas = versionPreguntaDAO.listarPorVersionEvaluacion(idVersion);
        int indice = -1;
        for (int i = 0; i < preguntas.size(); i++) {
            if (preguntas.get(i).getIdVersionPregunta() == idVersionPregunta) {
                indice = i;
                break;
            }
        }
        if (indice < 0) {
            return;
        }
        int destinoIndice = subir ? indice - 1 : indice + 1;
        if (destinoIndice < 0 || destinoIndice >= preguntas.size()) {
            return;
        }
        VersionPregunta actual = preguntas.get(indice);
        VersionPregunta vecina = preguntas.get(destinoIndice);
        int ordenActual = actual.ordenVersionPregunta;
        versionPreguntaDAO.actualizarOrden(actual, vecina.ordenVersionPregunta);
        versionPreguntaDAO.actualizarOrden(vecina, ordenActual);
    }

    /** Cursos del docente para elegir el contexto de impresión del PDF (RF-39). */
    private String opcionesCursos(Docente docente) {
        StringBuilder sb = new StringBuilder();
        sb.append(Formulario.opcion(vistas, "", "Sin curso", true));
        for (logica.Curso curso : cursoDAO.listarPorDocente(docente.getIdUsuario())) {
            sb.append(Formulario.opcion(vistas, String.valueOf(curso.getIdCurso()), curso.nombreCurso, false));
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

    private static double decimal(HttpServletRequest req, String nombre) {
        try {
            return Double.parseDouble(Validador.limpiar(req.getParameter(nombre)));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ grupos

    private static final class Grupo {
        NivelIngles nivel;
        Competencia competencia;
        Dificultad dificultad;
        int orden;
        double puntaje;
        final List<Pregunta> preguntas = new ArrayList<>();
    }

    private List<Grupo> agrupar(List<Pregunta> seleccionadas, Map<Integer, Integer> ordenes,
                                Map<Integer, Double> puntajes) {
        Map<String, Grupo> mapa = new LinkedHashMap<>();
        for (Pregunta pregunta : seleccionadas) {
            String clave = pregunta.nivelPregunta + "|" + pregunta.competenciaPregunta + "|" + pregunta.dificultadPregunta;
            Grupo grupo = mapa.get(clave);
            int orden = ordenes.getOrDefault(pregunta.getIdPregunta(), 0);
            if (grupo == null) {
                grupo = new Grupo();
                grupo.nivel = pregunta.nivelPregunta;
                grupo.competencia = pregunta.competenciaPregunta;
                grupo.dificultad = pregunta.dificultadPregunta;
                grupo.orden = orden;
                grupo.puntaje = puntajes.getOrDefault(pregunta.getIdPregunta(), 0.0);
                mapa.put(clave, grupo);
            } else {
                grupo.orden = Math.min(grupo.orden, orden);
            }
            grupo.preguntas.add(pregunta);
        }
        List<Grupo> grupos = new ArrayList<>(mapa.values());
        grupos.sort(Comparator.comparingInt(g -> g.orden));
        return grupos;
    }

    // ------------------------------------------------------------------ fragmentos HTML

    private String construirFilasGrupos(HttpServletRequest req, Evaluacion evaluacion, List<Grupo> grupos) {
        if (grupos.isEmpty()) {
            return parrafoVacio("Todavía no hay preguntas seleccionadas. Agrega preguntas aprobadas para formar los grupos.");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < grupos.size(); i++) {
            Grupo grupo = grupos.get(i);
            Map<String, String> fila = new HashMap<>();
            fila.put("ctx", req.getContextPath());
            fila.put("csrf", csrf(req));
            fila.put("id", String.valueOf(evaluacion.getIdEvaluacion()));
            fila.put("nivel", grupo.nivel.name());
            fila.put("competencia", grupo.competencia.name());
            fila.put("dificultad", grupo.dificultad.name());
            fila.put("nivelTexto", Etiquetas.de(grupo.nivel));
            fila.put("competenciaTexto", Etiquetas.de(grupo.competencia));
            fila.put("dificultadTexto", Etiquetas.de(grupo.dificultad));
            fila.put("orden", String.valueOf(i + 1));
            fila.put("puntaje", formatoPuntaje(grupo.puntaje));
            boolean editable = evaluacion.estadoEvaluacion != EstadoEvaluacion.ARCHIVADA;
            StringBuilder preguntas = new StringBuilder();
            for (Pregunta pregunta : grupo.preguntas) {
                if (preguntas.length() > 0) {
                    preguntas.append(vistas.componer("_salto.html", Map.of()));
                }
                Map<String, String> item = new HashMap<>();
                item.put("texto", Html.resumir(pregunta.enunciadoPregunta, 90));
                item.put("ctx", req.getContextPath());
                item.put("csrf", csrf(req));
                item.put("id", String.valueOf(evaluacion.getIdEvaluacion()));
                item.put("idPregunta", String.valueOf(pregunta.getIdPregunta()));
                item.put("deshabilitar", editable ? "" : "disabled");
                preguntas.append(vistas.componer("_pregunta-quitar.html", item));
            }
            fila.put("preguntas", preguntas.toString());
            fila.put("deshabilitarSubir", i == 0 ? "disabled" : "");
            fila.put("deshabilitarBajar", i == grupos.size() - 1 ? "disabled" : "");
            sb.append(vistas.componer("_fila-grupo-equivalencia.html", fila));
        }
        return sb.toString();
    }

    /** CU-20: las disponibles se pueden filtrar por tema, nivel, dificultad y competencia. */
    private String construirDisponibles(HttpServletRequest req, Docente docente, Evaluacion evaluacion,
                                        List<Pregunta> seleccionadas) {
        List<Integer> idsSeleccionados = new ArrayList<>();
        for (Pregunta pregunta : seleccionadas) {
            idsSeleccionados.add(pregunta.getIdPregunta());
        }
        String tema = parametro(req, "tema");
        if (tema.isBlank()) {
            tema = null;
        }
        // RF-24/CU-20: misma búsqueda común que CU-15/CU-16, fijando estado APROBADA y excluyendo
        // las ya seleccionadas. Así la selección nunca ofrece BORRADOR/ARCHIVADA.
        logica.CriteriosBusquedaPregunta criterios = new logica.CriteriosBusquedaPregunta();
        criterios.estado = EstadoPregunta.APROBADA;
        criterios.tema = tema;
        criterios.nivel = Formulario.enumeracion(NivelIngles.class, req.getParameter("nivel"));
        criterios.dificultad = Formulario.enumeracion(Dificultad.class, req.getParameter("dificultad"));
        criterios.competencia = Formulario.enumeracion(Competencia.class, req.getParameter("competencia"));
        criterios.excluirIds.addAll(idsSeleccionados);
        List<Map<String, String>> filas = new ArrayList<>();
        for (Pregunta pregunta : preguntaDAO.buscarDeDocente(docente.getIdUsuario(), criterios)) {
            Map<String, String> fila = new HashMap<>();
            fila.put("ctx", req.getContextPath());
            fila.put("csrf", csrf(req));
            fila.put("id", String.valueOf(evaluacion.getIdEvaluacion()));
            fila.put("idPregunta", String.valueOf(pregunta.getIdPregunta()));
            fila.put("tema", pregunta.temaPregunta == null ? "" : pregunta.temaPregunta);
            fila.put("nivelTexto", Etiquetas.de(pregunta.nivelPregunta));
            fila.put("competenciaTexto", Etiquetas.de(pregunta.competenciaPregunta));
            fila.put("dificultadTexto", Etiquetas.de(pregunta.dificultadPregunta));
            fila.put("enunciado", Html.resumir(pregunta.enunciadoPregunta, 110));
            filas.add(fila);
        }
        return componerFilas("_fila-disponible.html", filas,
                filaVacia("4", hayFiltros(req)
                        ? "No hay preguntas aprobadas que coincidan con los filtros."
                        : "No hay preguntas aprobadas disponibles para agregar."));
    }

    private boolean hayFiltros(HttpServletRequest req) {
        return !parametro(req, "tema").isBlank()
                || Formulario.enumeracion(NivelIngles.class, req.getParameter("nivel")) != null
                || Formulario.enumeracion(Dificultad.class, req.getParameter("dificultad")) != null
                || Formulario.enumeracion(Competencia.class, req.getParameter("competencia")) != null;
    }

    /** Resumen de las preguntas seleccionadas por tema (0), competencia (1) o dificultad (2). */
    private String resumenDistribucion(List<Pregunta> seleccionadas, int dimension) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (Pregunta pregunta : seleccionadas) {
            String clave;
            switch (dimension) {
                case 0:
                    clave = pregunta.temaPregunta == null || pregunta.temaPregunta.isBlank()
                            ? "Sin tema" : pregunta.temaPregunta;
                    break;
                case 1:
                    clave = Etiquetas.de(pregunta.competenciaPregunta);
                    break;
                default:
                    clave = Etiquetas.de(pregunta.dificultadPregunta);
                    break;
            }
            conteo.merge(clave, 1, Integer::sum);
        }
        if (conteo.isEmpty()) {
            return parrafoVacio("Sin preguntas seleccionadas.");
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : conteo.entrySet()) {
            Map<String, String> fila = new HashMap<>();
            fila.put("nombre", entrada.getKey());
            fila.put("cantidad", String.valueOf(entrada.getValue()));
            filas.add(fila);
        }
        return componerFilas("_fila-resumen-distribucion.html", filas, "");
    }

    /** Temas distintos de las preguntas seleccionadas, con el filtro elegido marcado. */
    private String opcionesFiltroTema(List<Pregunta> seleccionadas, String seleccionado) {
        if (seleccionado != null && seleccionado.isBlank()) {
            seleccionado = null;
        }
        List<String> valores = new ArrayList<>();
        for (Pregunta pregunta : seleccionadas) {
            String valor = pregunta.temaPregunta == null ? "" : pregunta.temaPregunta;
            if (!valor.isBlank() && !valores.contains(valor)) {
                valores.add(valor);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Formulario.opcion(vistas, "", "Todos", seleccionado == null));
        for (String valor : valores) {
            sb.append(Formulario.opcion(vistas, valor, valor, valor.equals(seleccionado)));
        }
        return sb.toString();
    }

    private String construirVersiones(HttpServletRequest req, int idEvaluacion) {
        List<VersionEvaluacion> versiones = versionDAO.listarPorEvaluacion(idEvaluacion);
        if (versiones.isEmpty()) {
            return filaVacia("4", "Todavía no hay versiones generadas.");
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (VersionEvaluacion version : versiones) {
            int idVersion = version.getIdVersionEvaluacion();
            String motivo = motivoBloqueoVersion(idVersion);
            boolean aplicada = !virtualDAO.estadosDeAplicacionDeVersion(idVersion).isEmpty();
            Map<String, String> fila = new HashMap<>();
            fila.put("codigo", version.codigoVersionEvaluacion);
            fila.put("fecha", fecha(version.fechaGeneracionVersionEvaluacion));
            fila.put("urlDetalle", ruta(req, RUTA + "?id=" + idEvaluacion + "&version=" + idVersion));
            fila.put("destinoPdf", "version:" + idVersion);
            fila.put("destinoClave", "version:" + idVersion + ":clave");
            Map<String, String> insignia = new HashMap<>();
            insignia.put("clase", "insignia-neutra");
            if (motivo != null) {
                insignia.put("texto", "En uso");
                fila.put("estadoVersion", vistas.componer("_insignia.html", insignia));
            } else if (aplicada) {
                insignia.put("texto", "Aplicada");
                fila.put("estadoVersion", vistas.componer("_insignia.html", insignia));
            } else {
                fila.put("estadoVersion", "");
            }
            filas.add(fila);
        }
        return componerFilas("_fila-version.html", filas, "");
    }

    private String construirDetalleVersion(HttpServletRequest req, Docente docente, int idEvaluacion) {
        int idVersion = Validador.parsearId(req.getParameter("version"));
        if (idVersion < 0) {
            return "";
        }
        VersionEvaluacion version = versionDAO.buscarPorIdDeDocente(idVersion, docente.getIdUsuario()).orElse(null);
        if (version == null) {
            return "";
        }
        List<VersionPregunta> preguntas = versionPreguntaDAO.listarPorVersionEvaluacion(idVersion);
        String motivo = motivoBloqueoVersion(idVersion);
        StringBuilder filas = new StringBuilder();
        for (int i = 0; i < preguntas.size(); i++) {
            VersionPregunta pregunta = preguntas.get(i);
            Map<String, String> fila = new HashMap<>();
            fila.put("orden", String.valueOf(i + 1));
            fila.put("enunciado", Html.resumir(pregunta.enunciadoVersionPregunta, 110));
            fila.put("puntaje", formatoPuntaje(pregunta.puntajeVersionPregunta));
            fila.put("acciones", accionesDeVersionPregunta(req, idEvaluacion, idVersion, pregunta, i,
                    preguntas.size(), motivo == null));
            filas.append(vistas.componer("_fila-version-pregunta.html", fila));
        }
        if (preguntas.isEmpty()) {
            filas.append(filaVacia("4", "Esta versión no tiene preguntas."));
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("codigo", version.codigoVersionEvaluacion);
        datos.put("filas", filas.toString());
        datos.put("aviso", motivo == null ? "" : Html.alerta(vistas, "alerta-info", motivo));
        return vistas.componer("_tabla-version.html", datos);
    }

    /** CU-22: mover y editar solo cuando la versión es editable. */
    private String accionesDeVersionPregunta(HttpServletRequest req, int idEvaluacion, int idVersion,
                                             VersionPregunta pregunta, int indice, int total, boolean editable) {
        if (!editable) {
            return "";
        }
        StringBuilder acciones = new StringBuilder();
        acciones.append(formMoverVersion(req, idEvaluacion, idVersion, pregunta, "subir",
                indice == 0, "Subir pregunta", "↑"));
        acciones.append(formMoverVersion(req, idEvaluacion, idVersion, pregunta, "bajar",
                indice == total - 1, "Bajar pregunta", "↓"));
        Map<String, String> enlace = new HashMap<>();
        enlace.put("clase", "boton boton-contorno boton-pequeno");
        enlace.put("url", ruta(req, "/app/docente/version-pregunta?id=" + pregunta.getIdVersionPregunta()));
        enlace.put("texto", "Editar");
        acciones.append(vistas.componer("_enlace-accion.html", enlace));
        return acciones.toString();
    }

    private String formMoverVersion(HttpServletRequest req, int idEvaluacion, int idVersion,
                                    VersionPregunta pregunta, String direccion, boolean deshabilitado,
                                    String aria, String flecha) {
        Map<String, String> datos = new HashMap<>();
        datos.put("destino", ruta(req, RUTA));
        datos.put("csrf", csrf(req));
        datos.put("id", String.valueOf(idEvaluacion));
        datos.put("idVersion", String.valueOf(idVersion));
        datos.put("idVersionPregunta", String.valueOf(pregunta.getIdVersionPregunta()));
        datos.put("direccion", direccion);
        datos.put("deshabilitar", deshabilitado ? "disabled" : "");
        datos.put("aria", aria);
        datos.put("flecha", flecha);
        return vistas.componer("_form-mover-version.html", datos);
    }

    // ------------------------------------------------------------------ comparativa (RF-37)

    /** CU-21: se resalta cualquier versión cuya distribución difiera de la más frecuente. */
    private String construirComparativa(Evaluacion evaluacion, List<Pregunta> seleccionadas,
                                        Map<Integer, Double> puntajes) {
        List<VersionEvaluacionDAO.Distribucion> distribuciones =
                versionDAO.listarDistribucionPorVersion(evaluacion.getIdEvaluacion());
        if (distribuciones.isEmpty()) {
            return filaVacia("6", "Todavía no hay versiones generadas.");
        }
        Map<String, Integer> modaTema = moda(distribuciones, 0);
        Map<String, Integer> modaCompetencia = moda(distribuciones, 1);
        Map<String, Integer> modaDificultad = moda(distribuciones, 2);
        double modaPuntaje = modaPuntaje(distribuciones);

        StringBuilder filas = new StringBuilder();
        for (VersionEvaluacionDAO.Distribucion d : distribuciones) {
            Map<String, String> fila = new HashMap<>();
            fila.put("codigo", d.codigo);
            fila.put("cantidad", String.valueOf(d.cantidad));
            fila.put("temas", textoDistribucion(d.porTema, null));
            fila.put("competencias", textoDistribucion(d.porCompetencia, clave -> Etiquetas.de(Competencia.valueOf(clave))));
            fila.put("dificultades", textoDistribucion(d.porDificultad, clave -> Etiquetas.de(Dificultad.valueOf(clave))));
            fila.put("puntajeTotal", formatoPuntaje(d.puntajeTotal));
            fila.put("claseTema", d.porTema.equals(modaTema) ? "" : "diferencia");
            fila.put("claseCompetencia", d.porCompetencia.equals(modaCompetencia) ? "" : "diferencia");
            fila.put("claseDificultad", d.porDificultad.equals(modaDificultad) ? "" : "diferencia");
            fila.put("clasePuntaje", Math.abs(d.puntajeTotal - modaPuntaje) < 0.001 ? "" : "diferencia");
            filas.append(vistas.componer("_fila-comparativa.html", fila));
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", filas.toString());
        datos.put("nota", notaRepeticiones(seleccionadas, puntajes, distribuciones.size()));
        return vistas.componer("_tabla-comparativa.html", datos);
    }

    private static Map<String, Integer> mapaDe(VersionEvaluacionDAO.Distribucion d, int dimension) {
        switch (dimension) {
            case 0: return d.porTema;
            case 1: return d.porCompetencia;
            default: return d.porDificultad;
        }
    }

    /** Distribución más frecuente entre las versiones (la referencia para resaltar diferencias). */
    private static Map<String, Integer> moda(List<VersionEvaluacionDAO.Distribucion> versiones, int dimension) {
        Map<String, Integer> frecuencia = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> porClave = new LinkedHashMap<>();
        for (VersionEvaluacionDAO.Distribucion d : versiones) {
            Map<String, Integer> mapa = mapaDe(d, dimension);
            String clave = mapa.toString();
            frecuencia.merge(clave, 1, Integer::sum);
            porClave.putIfAbsent(clave, mapa);
        }
        String mejor = null;
        int mejorFrecuencia = -1;
        for (Map.Entry<String, Integer> entrada : frecuencia.entrySet()) {
            if (entrada.getValue() > mejorFrecuencia) {
                mejorFrecuencia = entrada.getValue();
                mejor = entrada.getKey();
            }
        }
        return porClave.get(mejor);
    }

    private static double modaPuntaje(List<VersionEvaluacionDAO.Distribucion> versiones) {
        Map<String, Integer> frecuencia = new LinkedHashMap<>();
        Map<String, Double> porClave = new LinkedHashMap<>();
        for (VersionEvaluacionDAO.Distribucion d : versiones) {
            String clave = String.valueOf(d.puntajeTotal);
            frecuencia.merge(clave, 1, Integer::sum);
            porClave.putIfAbsent(clave, d.puntajeTotal);
        }
        String mejor = null;
        int mejorFrecuencia = -1;
        for (Map.Entry<String, Integer> entrada : frecuencia.entrySet()) {
            if (entrada.getValue() > mejorFrecuencia) {
                mejorFrecuencia = entrada.getValue();
                mejor = entrada.getKey();
            }
        }
        return porClave.get(mejor);
    }

    private static String textoDistribucion(Map<String, Integer> mapa,
                                            java.util.function.Function<String, String> etiqueta) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entrada : mapa.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            String nombre = etiqueta == null ? entrada.getKey() : etiqueta.apply(entrada.getKey());
            sb.append(nombre).append(" ").append(entrada.getValue());
        }
        return sb.toString();
    }

    /** Aviso cuando un tipo (tema · competencia · dificultad · puntaje) tiene menos preguntas que versiones (RF-35). */
    private String notaRepeticiones(List<Pregunta> seleccionadas, Map<Integer, Double> puntajes,
                                    int totalVersiones) {
        Map<String, Integer> pool = new LinkedHashMap<>();
        for (Pregunta pregunta : seleccionadas) {
            String clave = (pregunta.temaPregunta == null ? "" : pregunta.temaPregunta) + " · "
                    + Etiquetas.de(pregunta.competenciaPregunta) + " · "
                    + Etiquetas.de(pregunta.dificultadPregunta) + " · "
                    + formatoPuntaje(puntajes.getOrDefault(pregunta.getIdPregunta(), 0.0)) + " pts";
            pool.merge(clave, 1, Integer::sum);
        }
        List<String> cortos = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : pool.entrySet()) {
            if (entrada.getValue() < totalVersiones) {
                cortos.add(entrada.getKey() + " (" + entrada.getValue() + " pregunta(s) para "
                        + totalVersiones + " versiones)");
            }
        }
        if (cortos.isEmpty()) {
            return "";
        }
        return Html.alerta(vistas, "alerta-info", "Estos tipos tienen menos preguntas que versiones, "
                + "así que alguna pregunta se repite entre versiones: " + String.join("; ", cortos) + ".");
    }

    private String accionesEstado(HttpServletRequest req, Evaluacion evaluacion) {
        int id = evaluacion.getIdEvaluacion();
        StringBuilder sb = new StringBuilder();
        if (evaluacion.estadoEvaluacion == EstadoEvaluacion.BORRADOR) {
            sb.append(boton(req, RUTA, id, "marcarLista", "Marcar lista", ""));
        }
        if (evaluacion.estadoEvaluacion != EstadoEvaluacion.ARCHIVADA) {
            sb.append(boton(req, RUTA, id, "archivar", "Archivar", "boton-contorno"));
        }
        return sb.toString();
    }

    private static String formatoPuntaje(double puntaje) {
        if (puntaje == Math.floor(puntaje)) {
            return String.valueOf((int) puntaje);
        }
        return String.valueOf(puntaje);
    }
}
