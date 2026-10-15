package servlet;

import logica.Administrador;
import logica.Docente;
import logica.Fechas;
import logica.OperacionNoPermitidaException;
import logica.RecursoNoEncontradoException;
import logica.Usuario;
import logica.ValidacionException;
import logica.Validador;
import modelo.BancoPreguntasDAO;
import modelo.AdministradorDAO;
import modelo.CursoDAO;
import modelo.DocenteDAO;
import modelo.DocumentoFuenteDAO;
import modelo.EstudianteDAO;
import modelo.EvaluacionDAO;
import modelo.EvaluacionVirtualDAO;
import modelo.EventoSupervisionDAO;
import modelo.GrupoDAO;
import modelo.InscripcionDAO;
import modelo.IntentoDAO;
import modelo.IntentoPracticaDAO;
import modelo.MaterialPracticaDAO;
import modelo.OpcionRespuestaDAO;
import modelo.PreguntaDAO;
import modelo.RespuestaDAO;
import modelo.RespuestaPracticaDAO;
import modelo.SolicitudGeneracionDAO;
import modelo.TokenRecuperacionDAO;
import modelo.UsuarioDAO;
import modelo.VersionEvaluacionDAO;
import modelo.VersionOpcionDAO;
import modelo.VersionPreguntaDAO;
import servicio.ServicioEvaluacionVirtual;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Servlets delgados: reciben la petición, llaman a los DAO y muestran la vista. */
public abstract class ServletBase extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String ERRORES = "examlen.errores";
    private static final String FORMULARIO = "examlen.formulario";

    @FunctionalInterface
    protected interface Accion {
        /** Devuelve el mensaje de éxito, o null si no hay que mostrar ninguno. */
        String ejecutar() throws ValidacionException;
    }

    protected transient Vistas vistas;
    protected transient UsuarioDAO usuarioDAO;
    protected transient AdministradorDAO administradorDAO;
    protected transient DocenteDAO docenteDAO;
    protected transient EstudianteDAO estudianteDAO;
    protected transient CursoDAO cursoDAO;
    protected transient GrupoDAO grupoDAO;
    protected transient InscripcionDAO inscripcionDAO;
    protected transient BancoPreguntasDAO bancoDAO;
    protected transient PreguntaDAO preguntaDAO;
    protected transient OpcionRespuestaDAO opcionDAO;
    protected transient EvaluacionDAO evaluacionDAO;
    protected transient VersionEvaluacionDAO versionDAO;
    protected transient VersionPreguntaDAO versionPreguntaDAO;
    protected transient VersionOpcionDAO versionOpcionDAO;
    protected transient EvaluacionVirtualDAO virtualDAO;
    protected transient IntentoDAO intentoDAO;
    protected transient RespuestaDAO respuestaDAO;
    protected transient EventoSupervisionDAO eventoDAO;
    protected transient MaterialPracticaDAO materialDAO;
    protected transient IntentoPracticaDAO intentoPracticaDAO;
    protected transient RespuestaPracticaDAO respuestaPracticaDAO;
    protected transient DocumentoFuenteDAO documentoDAO;
    protected transient SolicitudGeneracionDAO solicitudDAO;
    protected transient TokenRecuperacionDAO tokenDAO;
    protected transient ServicioEvaluacionVirtual servicioVirtual;

    @Override
    public void init() throws ServletException {
        ServletContext contexto = getServletContext();
        vistas = (Vistas) contexto.getAttribute(InicializadorAplicacion.VISTAS);
        usuarioDAO = (UsuarioDAO) contexto.getAttribute(InicializadorAplicacion.USUARIO_DAO);
        administradorDAO = (AdministradorDAO) contexto.getAttribute(InicializadorAplicacion.ADMINISTRADOR_DAO);
        docenteDAO = (DocenteDAO) contexto.getAttribute(InicializadorAplicacion.DOCENTE_DAO);
        estudianteDAO = (EstudianteDAO) contexto.getAttribute(InicializadorAplicacion.ESTUDIANTE_DAO);
        cursoDAO = (CursoDAO) contexto.getAttribute(InicializadorAplicacion.CURSO_DAO);
        grupoDAO = (GrupoDAO) contexto.getAttribute(InicializadorAplicacion.GRUPO_DAO);
        inscripcionDAO = (InscripcionDAO) contexto.getAttribute(InicializadorAplicacion.INSCRIPCION_DAO);
        bancoDAO = (BancoPreguntasDAO) contexto.getAttribute(InicializadorAplicacion.BANCO_DAO);
        preguntaDAO = (PreguntaDAO) contexto.getAttribute(InicializadorAplicacion.PREGUNTA_DAO);
        opcionDAO = (OpcionRespuestaDAO) contexto.getAttribute(InicializadorAplicacion.OPCION_DAO);
        evaluacionDAO = (EvaluacionDAO) contexto.getAttribute(InicializadorAplicacion.EVALUACION_DAO);
        versionDAO = (VersionEvaluacionDAO) contexto.getAttribute(InicializadorAplicacion.VERSION_DAO);
        versionPreguntaDAO = (VersionPreguntaDAO) contexto.getAttribute(InicializadorAplicacion.VERSION_PREGUNTA_DAO);
        versionOpcionDAO = (VersionOpcionDAO) contexto.getAttribute(InicializadorAplicacion.VERSION_OPCION_DAO);
        virtualDAO = (EvaluacionVirtualDAO) contexto.getAttribute(InicializadorAplicacion.VIRTUAL_DAO);
        intentoDAO = (IntentoDAO) contexto.getAttribute(InicializadorAplicacion.INTENTO_DAO);
        respuestaDAO = (RespuestaDAO) contexto.getAttribute(InicializadorAplicacion.RESPUESTA_DAO);
        eventoDAO = (EventoSupervisionDAO) contexto.getAttribute(InicializadorAplicacion.EVENTO_DAO);
        materialDAO = (MaterialPracticaDAO) contexto.getAttribute(InicializadorAplicacion.MATERIAL_DAO);
        intentoPracticaDAO = (IntentoPracticaDAO) contexto.getAttribute(InicializadorAplicacion.INTENTO_PRACTICA_DAO);
        respuestaPracticaDAO = (RespuestaPracticaDAO) contexto.getAttribute(InicializadorAplicacion.RESPUESTA_PRACTICA_DAO);
        documentoDAO = (DocumentoFuenteDAO) contexto.getAttribute(InicializadorAplicacion.DOCUMENTO_DAO);
        solicitudDAO = (SolicitudGeneracionDAO) contexto.getAttribute(InicializadorAplicacion.SOLICITUD_DAO);
        tokenDAO = (TokenRecuperacionDAO) contexto.getAttribute(InicializadorAplicacion.TOKEN_DAO);
        servicioVirtual = new ServicioEvaluacionVirtual(virtualDAO, intentoDAO, respuestaDAO, eventoDAO);
        if (vistas == null || usuarioDAO == null || cursoDAO == null) {
            throw new ServletException("La aplicación no se inicializó correctamente.");
        }
    }

    // ------------------------------------------------------------------ petición

    protected Usuario usuarioActual(HttpServletRequest req) {
        return (Usuario) req.getAttribute(AutenticacionFiltro.ATRIBUTO_USUARIO);
    }

    protected Docente docenteActual(HttpServletRequest req) {
        return (Docente) usuarioActual(req);
    }

    protected String parametro(HttpServletRequest req, String nombre) {
        return Validador.limpiar(req.getParameter(nombre));
    }

    /** Identificador obligatorio. Si falta o es inválido, la respuesta es 404. */
    protected int parametroId(HttpServletRequest req, String nombre) {
        int id = Validador.parsearId(req.getParameter(nombre));
        if (id < 0) {
            throw new RecursoNoEncontradoException("Identificador inválido");
        }
        return id;
    }

    /**
     * RNF-02: recurso existente que pertenece a otro usuario -> 403; recurso inexistente -> 404.
     * El mensaje es el mismo en ambos casos para no revelar datos de otros usuarios.
     */
    protected void verificarPropiedad(boolean esPropietario, boolean existeRecurso, String mensaje) {
        if (esPropietario) {
            return;
        }
        if (existeRecurso) {
            throw new OperacionNoPermitidaException(mensaje);
        }
        throw new RecursoNoEncontradoException(mensaje);
    }

    protected String csrf(HttpServletRequest req) {
        return TokenCsrf.obtenerOCrear(req.getSession(true));
    }

    protected String ruta(HttpServletRequest req, String ruta) {
        return req.getContextPath() + ruta;
    }

    protected String fecha(Date valor) {
        return Fechas.formato(Fechas.PATRON_FECHA, valor);
    }

    protected String fechaHora(Date valor) {
        return Fechas.formato(Fechas.PATRON_FECHA_HORA, valor);
    }

    // ------------------------------------------------------------------ respuesta

    protected void redirigir(HttpServletRequest req, HttpServletResponse resp, String ruta) throws IOException {
        resp.sendRedirect(req.getContextPath() + ruta);
    }

    /** Patrón Post/Redirect/Get: ejecuta, guarda el mensaje o los errores y redirige. */
    protected void ejecutar(HttpServletRequest req, HttpServletResponse resp, String destino,
                            Map<String, String> valoresSiFalla, Accion accion) throws IOException {
        try {
            String mensaje = accion.ejecutar();
            if (mensaje != null) {
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, mensaje);
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
            if (valoresSiFalla != null) {
                guardarFormulario(req, valoresSiFalla);
            }
        }
        redirigir(req, resp, destino);
    }

    @SuppressWarnings("unchecked")
    protected void agregarMensaje(HttpServletRequest req, MensajeFlash.Tipo tipo, String texto) {
        HttpSession sesion = req.getSession(true);
        List<MensajeFlash> mensajes = (List<MensajeFlash>) sesion.getAttribute(ClavesSesion.MENSAJES_FLASH);
        if (mensajes == null) {
            mensajes = new ArrayList<>();
        }
        mensajes.add(new MensajeFlash(tipo, texto));
        sesion.setAttribute(ClavesSesion.MENSAJES_FLASH, mensajes);
    }

    protected void agregarErrores(HttpServletRequest req, List<String> errores) {
        req.getSession(true).setAttribute(ERRORES, new ArrayList<>(errores));
    }

    /** Conserva lo que el usuario escribió para volver a mostrarlo tras un error. */
    protected void guardarFormulario(HttpServletRequest req, Map<String, String> valores) {
        req.getSession(true).setAttribute(FORMULARIO, new HashMap<>(valores));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, String> recuperarFormulario(HttpServletRequest req) {
        HttpSession sesion = req.getSession(false);
        if (sesion == null) {
            return new HashMap<>();
        }
        Object valor = sesion.getAttribute(FORMULARIO);
        sesion.removeAttribute(FORMULARIO);
        return valor instanceof Map ? new HashMap<>((Map<String, String>) valor) : new HashMap<>();
    }

    protected void renderizar(HttpServletRequest req, HttpServletResponse resp, String vista,
                              Map<String, String> datos, List<String> errores) throws IOException {
        Map<String, String> modelo = new HashMap<>(datos);
        HttpSession sesion = req.getSession(true);
        String token = TokenCsrf.obtenerOCrear(sesion);

        List<String> todosLosErrores = new ArrayList<>(consumirErrores(sesion));
        if (errores != null) {
            todosLosErrores.addAll(errores);
        }
        StringBuilder mensajes = new StringBuilder(Html.mensajesFlash(vistas, consumirMensajes(sesion)));
        if (!todosLosErrores.isEmpty()) {
            mensajes.append(Html.listaErrores(vistas, todosLosErrores));
        }

        modelo.put("ctx", req.getContextPath());
        modelo.put("csrf", token);
        modelo.put("mensajes", mensajes.toString());
        Usuario usuario = usuarioActual(req);
        modelo.put("nav", usuario == null ? "" : construirNavegacion(req, usuario, token));

        vistas.escribir(resp, HttpServletResponse.SC_OK, vista, modelo);
    }

    /** Fila de tabla vacia (colspan + mensaje) con el fragmento _fila-vacia.html. */
    protected String filaVacia(String colspan, String texto) {
        Map<String, String> datos = new HashMap<>();
        datos.put("colspan", colspan);
        datos.put("texto", texto);
        return vistas.componer("_fila-vacia.html", datos);
    }

    /** Texto de RF-10: el restablecimiento por el administrador es respaldo de RF-11. */
    protected static final String AVISO_RESPALDO_RF11 =
            "Este procedimiento es el mecanismo de respaldo de la recuperación por correo (RF-11), "
            + "para los casos en que el usuario no puede usar el método normal: por ejemplo, si perdió "
            + "el acceso a su cuenta de correo o no recibe el mensaje. Úsalo previa solicitud del "
            + "usuario por un canal institucional.";

    /**
     * Pantalla intermedia de confirmacion (sin JavaScript) para acciones delicadas:
     * dice que se va a hacer y sobre quien, y exige confirmar antes de ejecutar.
     */
    protected void confirmar(HttpServletRequest req, HttpServletResponse resp, String titulo, String mensaje,
                             String aviso, String destino, String accion, int id, String textoConfirmar,
                             String claseBoton, String volver) throws IOException {
        Map<String, String> datos = new HashMap<>();
        datos.put("titulo", titulo);
        datos.put("mensaje", mensaje);
        datos.put("aviso", aviso == null ? "" : aviso);
        datos.put("destino", destino);
        datos.put("accion", accion);
        datos.put("id", String.valueOf(id));
        datos.put("textoConfirmar", textoConfirmar);
        datos.put("claseBoton", claseBoton);
        datos.put("volver", volver);
        renderizar(req, resp, "confirmar.html", datos, null);
    }

    /**
     * CU-22: motivo por el que una versión no se puede editar, o null si es editable.
     * Solo se edita si no tiene intentos y todas sus virtuales están PROGRAMADA o canceladas
     * (una ABIERTA o CERRADA bloquea, haya o no intentos).
     */
    protected String motivoBloqueoVersion(int idVersionEvaluacion) {
        if (intentoDAO.contarPorVersionEvaluacion(idVersionEvaluacion) > 0) {
            return "Esta versión ya tiene intentos registrados: no se puede editar ni eliminar sin "
                    + "cambiar el examen de los estudiantes que ya presentaron.";
        }
        List<String> estados = virtualDAO.estadosDeAplicacionDeVersion(idVersionEvaluacion);
        if (estados.contains("ABIERTA")) {
            return "Esta versión ya está aplicada a un parcial abierto: no se puede editar mientras "
                    + "los estudiantes puedan estar presentándolo.";
        }
        if (estados.contains("CERRADA")) {
            return "Esta versión está aplicada a un parcial cerrado: se conserva como evidencia de lo "
                    + "que presentaron los estudiantes.";
        }
        return null;
    }

    /** Parrafo de ayuda con el fragmento _parrafo-ayuda.html. */
    protected String parrafoAyuda(String texto) {
        return vistas.componer("_parrafo-ayuda.html", Map.of("texto", texto));
    }

    /** Parrafo de lista vacia con el fragmento _parrafo-vacio.html. */
    protected String parrafoVacio(String texto) {
        return vistas.componer("_parrafo-vacio.html", Map.of("texto", texto));
    }

    /** Opcion de lista vacia con el fragmento _opcion-vacia.html. */
    protected String opcionVacia(String texto) {
        return vistas.componer("_opcion-vacia.html", Map.of("texto", texto));
    }

    // ------------------------------------------------------------------ fragmentos

    /** Compone una fila por elemento con un fragmento HTML (cada valor se escapa). */
    protected String componerFilas(String fragmento, List<Map<String, String>> filas, String siVacio) {
        if (filas.isEmpty()) {
            return siVacio;
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> fila : filas) {
            sb.append(vistas.componer(fragmento, fila));
        }
        return sb.toString();
    }

    /** Botón que envía una acción por POST con token CSRF. */
    protected String boton(HttpServletRequest req, String rutaDestino, int id, String accion, String texto,
                           String claseCss, String nombreExtra, String valorExtra) {
        Map<String, String> datos = new HashMap<>();
        datos.put("destino", ruta(req, rutaDestino));
        datos.put("csrf", csrf(req));
        datos.put("id", String.valueOf(id));
        datos.put("accion", accion);
        datos.put("texto", texto);
        datos.put("clase", claseCss);
        datos.put("extraNombre", nombreExtra == null ? "" : nombreExtra);
        datos.put("extraValor", valorExtra == null ? "" : valorExtra);
        return vistas.componer("_boton-accion.html", datos);
    }

    protected String boton(HttpServletRequest req, String rutaDestino, int id, String accion, String texto, String claseCss) {
        return boton(req, rutaDestino, id, accion, texto, claseCss, null, null);
    }

    private String construirNavegacion(HttpServletRequest req, Usuario usuario, String token) {
        Map<String, String> datos = new HashMap<>();
        datos.put("ctx", req.getContextPath());
        datos.put("csrf", token);
        datos.put("nombre", usuario.nombreUsuario);
        datos.put("tipo", Etiquetas.rol(usuario));
        datos.put("enlacesRol", enlacesDeRol(req.getContextPath(), usuario));
        return vistas.componer("_nav.html", datos);
    }

    /** Enlaces del menu segun el rol, compuestos con el fragmento _nav-enlace.html. */
    private String enlacesDeRol(String ctx, Usuario usuario) {
        List<Map<String, String>> enlaces = new ArrayList<>();
        if (usuario instanceof Administrador) {
            enlaces.add(enlaceDeNavegacion(ctx, "/app/admin/docentes", "Docentes"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/admin/estudiantes", "Estudiantes"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/admin/administradores", "Administradores"));
        } else if (usuario instanceof Docente) {
            enlaces.add(enlaceDeNavegacion(ctx, "/app/docente/cursos", "Mis cursos"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/docente/bancos", "Bancos de preguntas"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/docente/evaluaciones", "Mis evaluaciones"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/docente/practica", "Material de práctica"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/docente/ia", "Generación IA"));
        } else {
            enlaces.add(enlaceDeNavegacion(ctx, "/app/estudiante/grupos", "Mis grupos"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/estudiante/evaluaciones", "Evaluaciones"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/estudiante/practica", "Práctica"));
            enlaces.add(enlaceDeNavegacion(ctx, "/app/estudiante/practica/historial", "Historial de práctica"));
        }
        return componerFilas("_nav-enlace.html", enlaces, "");
    }

    private Map<String, String> enlaceDeNavegacion(String ctx, String ruta, String texto) {
        Map<String, String> datos = new HashMap<>();
        datos.put("href", ctx + ruta);
        datos.put("texto", texto);
        return datos;
    }

    @SuppressWarnings("unchecked")
    private List<MensajeFlash> consumirMensajes(HttpSession sesion) {
        Object valor = sesion.getAttribute(ClavesSesion.MENSAJES_FLASH);
        sesion.removeAttribute(ClavesSesion.MENSAJES_FLASH);
        return valor instanceof List ? (List<MensajeFlash>) valor : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> consumirErrores(HttpSession sesion) {
        Object valor = sesion.getAttribute(ERRORES);
        sesion.removeAttribute(ERRORES);
        return valor instanceof List ? (List<String>) valor : Collections.emptyList();
    }
}
