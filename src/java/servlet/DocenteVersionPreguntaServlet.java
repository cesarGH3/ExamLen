package servlet;

import logica.Docente;
import logica.RecursoNoEncontradoException;
import logica.TipoPregunta;
import logica.ValidacionException;
import logica.Validador;
import logica.VersionEvaluacion;
import logica.VersionOpcion;
import logica.VersionPregunta;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF-38 / CU-22: edita la copia de una pregunta dentro de una versión (enunciado, respuesta
 * esperada, puntaje y opciones) sin tocar el banco. Una versión con intentos o aplicada a un
 * parcial abierto o cerrado queda bloqueada.
 */
@WebServlet(name = "DocenteVersionPreguntaServlet", urlPatterns = "/app/docente/version-pregunta")
public class DocenteVersionPreguntaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/version-pregunta";
    private static final String RUTA_EVALUACION = "/app/docente/evaluacion";
    private static final int MAX_ENUNCIADO = 4000;
    private static final int MAX_RESPUESTA = 500;
    private static final int MAX_TEXTO_OPCION = 500;
    /** El ERS no define escala de puntaje por pregunta; se mantiene el 0–1000 del código (pendiente). */
    private static final double MAX_PUNTAJE = 1000;

    private static final class Contexto {
        VersionPregunta pregunta;
        VersionEvaluacion version;
        int idEvaluacion;
        int idVersion;
        String tipo;
        boolean conOpciones;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        Contexto contexto = contexto(req, docente);
        mostrar(req, resp, contexto, null, motivoBloqueoVersion(contexto.idVersion));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        Contexto contexto = contexto(req, docente);
        String motivo = motivoBloqueoVersion(contexto.idVersion);
        if (motivo != null) {
            agregarMensaje(req, MensajeFlash.Tipo.ERROR, motivo);
            redirigir(req, resp, volver(contexto));
            return;
        }
        try {
            switch (parametro(req, "accion")) {
                case "actualizar":
                    actualizar(req, contexto);
                    break;
                case "agregarOpcion":
                    agregarOpcion(req, contexto);
                    break;
                case "actualizarOpcion":
                    actualizarOpcion(req, contexto);
                    break;
                case "eliminarOpcion":
                    eliminarOpcion(req, contexto);
                    break;
                case "marcarCorrecta":
                    marcarCorrecta(req, contexto);
                    break;
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.consultarErrores());
            guardarFormulario(req, formularioEnviado(req));
        }
        redirigir(req, resp, RUTA + "?id=" + contexto.pregunta.consultarIdVersionPregunta());
    }

    // ------------------------------------------------------------------ acciones

    private void actualizar(HttpServletRequest req, Contexto contexto) throws ValidacionException {
        String enunciado = Validador.limpiarTexto(req.getParameter("enunciado") == null
                ? "" : req.getParameter("enunciado"));
        String respuesta = Validador.limpiarTexto(req.getParameter("respuestaEsperada") == null
                ? "" : req.getParameter("respuestaEsperada"));
        double puntaje = decimal(req, "puntaje");
        List<String> errores = new ArrayList<>();
        Validador.validarLongitud(enunciado, 5, MAX_ENUNCIADO,
                "El enunciado debe tener entre 5 y 4000 caracteres.", errores);
        Validador.validarLongitud(respuesta, 0, MAX_RESPUESTA,
                "La respuesta esperada admite máximo 500 caracteres.", errores);
        if (!Double.isFinite(puntaje) || puntaje < 0 || puntaje > MAX_PUNTAJE) {
            errores.add("El puntaje debe estar entre 0 y 1000.");
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        versionPreguntaDAO.actualizar(contexto.pregunta, enunciado,
                respuesta.isEmpty() ? null : respuesta, puntaje);
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta de la versión actualizada.");
    }

    private void agregarOpcion(HttpServletRequest req, Contexto contexto) throws ValidacionException {
        String texto = Validador.limpiarTexto(req.getParameter("texto") == null ? "" : req.getParameter("texto"));
        boolean correcta = "true".equals(req.getParameter("esCorrecta"));
        List<String> errores = new ArrayList<>();
        Validador.validarLongitud(texto, 1, MAX_TEXTO_OPCION,
                "La opción debe tener entre 1 y 500 caracteres.", errores);
        List<VersionOpcion> opciones = versionOpcionDAO.listarPorVersionPregunta(
                contexto.pregunta.consultarIdVersionPregunta());
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        versionOpcionDAO.crearVersionOpcion(contexto.pregunta, texto, correcta, opciones.size() + 1);
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Opción agregada.");
    }

    private void actualizarOpcion(HttpServletRequest req, Contexto contexto) throws ValidacionException {
        String texto = Validador.limpiarTexto(req.getParameter("texto") == null ? "" : req.getParameter("texto"));
        boolean correcta = "true".equals(req.getParameter("esCorrecta"));
        List<String> errores = new ArrayList<>();
        Validador.validarLongitud(texto, 1, MAX_TEXTO_OPCION,
                "La opción debe tener entre 1 y 500 caracteres.", errores);
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        VersionOpcion opcion = opcionDe(contexto, parametroId(req, "idOpcion"));
        if (correcta) {
            versionOpcionDAO.marcarCorrectaVersionOpcion(contexto.pregunta, opcion);
        }
        versionOpcionDAO.actualizarVersionOpcion(opcion, texto, correcta, opcion.ordenVersionOpcion);
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Opción actualizada.");
    }

    private void eliminarOpcion(HttpServletRequest req, Contexto contexto) throws ValidacionException {
        VersionOpcion opcion = opcionDe(contexto, parametroId(req, "idOpcion"));
        List<VersionOpcion> opciones = versionOpcionDAO.listarPorVersionPregunta(
                contexto.pregunta.consultarIdVersionPregunta());
        if (opciones.size() <= 2) {
            throw new ValidacionException("Una pregunta de selección necesita al menos dos opciones.");
        }
        long correctas = opciones.stream().filter(o -> o.esCorrectaVersionOpcion).count();
        if (opcion.esCorrectaVersionOpcion && correctas <= 1) {
            throw new ValidacionException("Antes de eliminar la opción correcta, marca otra como correcta.");
        }
        versionOpcionDAO.eliminarVersionOpcion(opcion);
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Opción eliminada.");
    }

    private void marcarCorrecta(HttpServletRequest req, Contexto contexto) {
        VersionOpcion opcion = opcionDe(contexto, parametroId(req, "idOpcion"));
        versionOpcionDAO.marcarCorrectaVersionOpcion(contexto.pregunta, opcion);
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Opción marcada como correcta.");
    }

    private VersionOpcion opcionDe(Contexto contexto, int idOpcion) {
        return versionOpcionDAO.listarPorVersionPregunta(contexto.pregunta.consultarIdVersionPregunta()).stream()
                .filter(o -> o.consultarIdVersionOpcion() == idOpcion)
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("La opción indicada no existe."));
    }

    // ------------------------------------------------------------------ carga y pantalla

    private Contexto contexto(HttpServletRequest req, Docente docente) {
        int id = Validador.parsearId(req.getParameter("id"));
        VersionPregunta pregunta = versionPreguntaDAO.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pregunta de la versión no encontrada"));
        int idVersion = versionPreguntaDAO.idVersionEvaluacionDePregunta(id);
        java.util.Optional<VersionEvaluacion> propia = versionDAO.buscarPorIdDeDocente(idVersion,
                docente.consultarIdUsuario());
        verificarPropiedad(propia.isPresent(), versionDAO.buscarPorId(idVersion).isPresent(),
                "Pregunta de la versión no encontrada");
        VersionEvaluacion version = propia.get();
        Contexto contexto = new Contexto();
        contexto.pregunta = pregunta;
        contexto.version = version;
        contexto.idVersion = idVersion;
        contexto.idEvaluacion = versionDAO.idEvaluacionDeVersion(idVersion);
        contexto.tipo = versionPreguntaDAO.tipoDeVersionPregunta(id);
        contexto.conOpciones = contexto.tipo != null
                && DocenteBancoServlet.usaOpciones(TipoPregunta.valueOf(contexto.tipo));
        return contexto;
    }

    private String volver(Contexto contexto) {
        return RUTA_EVALUACION + "?id=" + contexto.idEvaluacion + "&version=" + contexto.idVersion;
    }

    /**
     * RF-38: si la versión ya tiene intentos o está aplicada, la pantalla se muestra en solo
     * lectura con el motivo (no se redirige) y el POST sigue bloqueado.
     */
    private void mostrar(HttpServletRequest req, HttpServletResponse resp, Contexto contexto,
                         List<String> errores, String motivo) throws IOException {
        VersionPregunta pregunta = contexto.pregunta;
        Map<String, String> formulario = recuperarFormulario(req);
        boolean soloLectura = motivo != null;
        Map<String, String> datos = new HashMap<>();
        datos.put("volver", ruta(req, volver(contexto)));
        datos.put("codigoVersion", contexto.version.codigoVersionEvaluacion);
        datos.put("tipoTexto", Etiquetas.de(TipoPregunta.valueOf(contexto.tipo)));
        datos.put("orden", String.valueOf(pregunta.ordenVersionPregunta));
        datos.put("aviso", soloLectura ? Html.alerta(vistas, "alerta-info", motivo) : "");
        datos.put("ayuda", soloLectura
                ? "Vista de solo lectura: esta versión ya tiene intentos registrados o está aplicada a un parcial."
                : "Editas la copia de esta versión (" + Etiquetas.de(TipoPregunta.valueOf(contexto.tipo))
                        + ", orden " + pregunta.ordenVersionPregunta + "): el banco de preguntas no cambia.");
        datos.put("datos", soloLectura ? seccionDatosLectura(contexto)
                : seccionDatos(req, contexto, formulario));
        datos.put("seccionOpciones", !contexto.conOpciones ? ""
                : soloLectura ? seccionOpcionesLectura(contexto) : seccionOpciones(req, contexto, formulario));
        renderizar(req, resp, "docente-version-pregunta.html", datos, errores);
    }

    /** CU-22: misma información en modo lectura, sin formularios. */
    private String seccionDatosLectura(Contexto contexto) {
        VersionPregunta pregunta = contexto.pregunta;
        boolean corta = TipoPregunta.RESPUESTA_CORTA.name().equals(contexto.tipo);
        Map<String, String> d = new HashMap<>();
        d.put("enunciado", pregunta.enunciadoVersionPregunta == null ? "" : pregunta.enunciadoVersionPregunta);
        d.put("respuestaEsperada", pregunta.respuestaEsperadaVersionPregunta == null
                ? "" : pregunta.respuestaEsperadaVersionPregunta);
        d.put("puntaje", formatoPuntaje(pregunta.puntajeVersionPregunta));
        d.put("etiquetaRespuesta", corta ? "Respuestas aceptadas" : "Respuesta esperada / guía de calificación");
        return vistas.componer("_version-pregunta-datos-lectura.html", d);
    }

    private String seccionOpcionesLectura(Contexto contexto) {
        int id = contexto.pregunta.consultarIdVersionPregunta();
        List<Map<String, String>> filas = new ArrayList<>();
        for (VersionOpcion opcion : versionOpcionDAO.listarPorVersionPregunta(id)) {
            Map<String, String> fila = new HashMap<>();
            fila.put("texto", opcion.textoVersionOpcion);
            fila.put("claseCorrecta", opcion.esCorrectaVersionOpcion ? "opcion-correcta" : "");
            fila.put("insignia", opcion.esCorrectaVersionOpcion
                    ? vistas.componer("_insignia.html", Map.of("clase", "insignia-activo", "texto", "Correcta"))
                    : "");
            fila.put("acciones", "");
            filas.add(fila);
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("ayuda", "Opciones congeladas en esta versión.");
        datos.put("filas", componerFilas("_fila-opcion.html", filas,
                opcionVacia("Esta pregunta de selección no tiene opciones.")));
        datos.put("formAgregar", "");
        return vistas.componer("_pregunta-opciones.html", datos);
    }

    private String seccionDatos(HttpServletRequest req, Contexto contexto, Map<String, String> formulario) {
        VersionPregunta pregunta = contexto.pregunta;
        boolean corta = TipoPregunta.RESPUESTA_CORTA.name().equals(contexto.tipo);
        Map<String, String> d = new HashMap<>();
        d.put("destino", ruta(req, RUTA));
        d.put("csrf", csrf(req));
        d.put("id", String.valueOf(pregunta.consultarIdVersionPregunta()));
        d.put("enunciado", formulario.getOrDefault("enunciado",
                pregunta.enunciadoVersionPregunta == null ? "" : pregunta.enunciadoVersionPregunta));
        d.put("respuestaEsperada", formulario.getOrDefault("respuestaEsperada",
                pregunta.respuestaEsperadaVersionPregunta == null ? "" : pregunta.respuestaEsperadaVersionPregunta));
        d.put("puntaje", formulario.getOrDefault("puntaje", formatoPuntaje(pregunta.puntajeVersionPregunta)));
        d.put("etiquetaRespuesta", corta ? "Respuestas aceptadas" : "Respuesta esperada / guía de calificación");
        d.put("ayudaRespuesta", corta
                ? "Se compara sin distinguir mayúsculas ni espacios extra. Si aceptas varias formas, sepáralas con punto y coma."
                : "Te sirve al calificar manualmente; los estudiantes no la ven.");
        return vistas.componer("_version-pregunta-datos.html", d);
    }

    private static Map<String, String> formularioEnviado(HttpServletRequest req) {
        Map<String, String> valores = new HashMap<>();
        if (req.getParameter("enunciado") != null) {
            valores.put("enunciado", req.getParameter("enunciado"));
        }
        if (req.getParameter("respuestaEsperada") != null) {
            valores.put("respuestaEsperada", req.getParameter("respuestaEsperada"));
        }
        if (req.getParameter("puntaje") != null) {
            valores.put("puntaje", req.getParameter("puntaje"));
        }
        if (req.getParameter("texto") != null) {
            valores.put("textoOpcion", req.getParameter("texto"));
        }
        return valores;
    }

    private String seccionOpciones(HttpServletRequest req, Contexto contexto, Map<String, String> formulario) {
        int id = contexto.pregunta.consultarIdVersionPregunta();
        List<VersionOpcion> opciones = versionOpcionDAO.listarPorVersionPregunta(id);
        int idEdicion = Validador.parsearId(parametro(req, "opcion"));
        List<Map<String, String>> filas = new ArrayList<>();
        for (VersionOpcion opcion : opciones) {
            Map<String, String> fila = new HashMap<>();
            fila.put("texto", opcion.textoVersionOpcion);
            fila.put("claseCorrecta", opcion.esCorrectaVersionOpcion ? "opcion-correcta" : "");
            fila.put("insignia", opcion.esCorrectaVersionOpcion
                    ? vistas.componer("_insignia.html", Map.of("clase", "insignia-activo", "texto", "Correcta"))
                    : "");
            StringBuilder acciones = new StringBuilder();
            String idOpcion = String.valueOf(opcion.consultarIdVersionOpcion());
            if (!opcion.esCorrectaVersionOpcion) {
                acciones.append(boton(req, RUTA, id, "marcarCorrecta", "Marcar correcta",
                        "boton-contorno boton-pequeno", "idOpcion", idOpcion));
            }
            Map<String, String> enlace = new HashMap<>();
            enlace.put("clase", "boton boton-contorno boton-pequeno");
            enlace.put("url", ruta(req, RUTA + "?id=" + id + "&opcion=" + idOpcion));
            enlace.put("texto", "Editar");
            acciones.append(vistas.componer("_enlace-accion.html", enlace));
            acciones.append(boton(req, RUTA, id, "eliminarOpcion", "Eliminar",
                    "boton-peligro boton-pequeno", "idOpcion", idOpcion));
            fila.put("acciones", acciones.toString());
            filas.add(fila);
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("ayuda", "Mínimo dos opciones, con exactamente una correcta. "
                + "Puedes editar el texto de cada una.");
        datos.put("filas", componerFilas("_fila-opcion.html", filas,
                opcionVacia("Todavía no hay opciones. Agrega al menos dos.")));
        {
            Map<String, String> form = new HashMap<>();
            form.put("destino", ruta(req, RUTA));
            form.put("csrf", csrf(req));
            form.put("id", String.valueOf(id));
            form.put("textoOpcion", formulario.getOrDefault("textoOpcion", ""));
            datos.put("formAgregar", vistas.componer("_form-agregar-opcion.html", form));
        }
        String opcionesHtml = vistas.componer("_pregunta-opciones.html", datos);

        VersionOpcion enEdicion = opciones.stream()
                .filter(o -> o.consultarIdVersionOpcion() == idEdicion)
                .findFirst().orElse(null);
        if (enEdicion == null) {
            return opcionesHtml;
        }
        Map<String, String> edicion = new HashMap<>();
        edicion.put("destino", ruta(req, RUTA));
        edicion.put("csrf", csrf(req));
        edicion.put("id", String.valueOf(id));
        edicion.put("idOpcion", String.valueOf(enEdicion.consultarIdVersionOpcion()));
        edicion.put("texto", enEdicion.textoVersionOpcion);
        edicion.put("marcada", enEdicion.esCorrectaVersionOpcion ? "checked" : "");
        edicion.put("volver", ruta(req, RUTA + "?id=" + id));
        return vistas.componer("_form-editar-opcion.html", edicion) + opcionesHtml;
    }

    private static double decimal(HttpServletRequest req, String nombre) {
        try {
            return Double.parseDouble(Validador.limpiar(req.getParameter(nombre)));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatoPuntaje(double puntaje) {
        if (puntaje == Math.floor(puntaje)) {
            return String.valueOf((int) puntaje);
        }
        return String.valueOf(puntaje);
    }
}
