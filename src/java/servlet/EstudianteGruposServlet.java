package servlet;

import logica.CodigoAccesoInvalidoException;
import logica.Estudiante;
import logica.EstadoGrupo;
import logica.GeneradorCodigoAcceso;
import logica.Grupo;
import logica.Inscripcion;
import logica.RecursoNoEncontradoException;
import logica.RegistroEnUsoException;
import logica.ValidacionException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-14: unirse a un grupo exige validar el codigo, mostrar curso/grupo/periodo/docente y
 * confirmar antes de crear la inscripcion. El servidor revalida al confirmar (codigo, grupo
 * ABIERTO, curso activo, sin duplicado) y no confia en datos ocultos del formulario.
 */
@WebServlet(name = "EstudianteGruposServlet", urlPatterns = "/app/estudiante/grupos")
public class EstudianteGruposServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/estudiante/grupos";
    private static final String CLAVE_FALLOS = "examlen.codigoFallos";
    private static final int MAX_FALLOS = 10;
    private static final long VENTANA_MS = 15L * 60 * 1000;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        if ("confirmarUnirse".equals(parametro(req, "accion"))) {
            mostrarConfirmacion(req, resp, estudiante);
            return;
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Inscripcion inscripcion : inscripcionDAO.listarActivasPorEstudiante(estudiante.consultarIdUsuario())) {
            Grupo grupo = inscripcion.grupoInscripcion;
            Map<String, String> fila = new HashMap<>();
            fila.put("curso", grupo.cursoGrupo.nombreCurso);
            fila.put("nivel", Etiquetas.de(grupo.cursoGrupo.nivelCurso));
            fila.put("grupo", grupo.nombreGrupo);
            fila.put("periodo", grupo.periodoGrupo);
            fila.put("docente", grupo.cursoGrupo.docenteCurso.nombreUsuario);
            fila.put("estado", Etiquetas.de(grupo.estadoGrupo));
            fila.put("claseEstado", grupo.estadoGrupo == EstadoGrupo.ABIERTO ? "insignia-activo" : "insignia-neutra");
            fila.put("accion", grupo.estadoGrupo == EstadoGrupo.ABIERTO
                    ? boton(req, RUTA, inscripcion.consultarIdInscripcion(), "salir", "Salir", "boton-peligro boton-pequeno")
                    : "");
            filas.add(fila);
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-mi-grupo.html", filas,
                filaVacia("5", "Aún no perteneces a ningún grupo. Pídele el código a tu docente.")));
        renderizar(req, resp, "estudiante-grupos.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        switch (parametro(req, "accion")) {
            case "unirse":
                prepararUnion(req, resp, estudiante);
                break;
            case "confirmarUnirse":
                confirmarUnion(req, resp, estudiante);
                break;
            case "salir": {
                int idInscripcion = parametroId(req, "id");
                ejecutar(req, resp, RUTA, null, () -> {
                    Inscripcion inscripcion = inscripcionDAO.buscarPorIdDeEstudiante(idInscripcion, estudiante.consultarIdUsuario())
                            .orElseThrow(() -> new RecursoNoEncontradoException("Inscripción no encontrada"));
                    try {
                        inscripcionDAO.eliminarInscripcion(inscripcion);
                        return "Saliste del grupo.";
                    } catch (RegistroEnUsoException e) {
                        inscripcionDAO.retirarInscripcion(inscripcion);
                        return "Saliste del grupo. Tu historial en él se conserva.";
                    }
                });
                break;
            }
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /** Primer paso: valida el código y lleva a la pantalla de confirmación (no inscribe). */
    private void prepararUnion(HttpServletRequest req, HttpServletResponse resp, Estudiante estudiante)
            throws IOException {
        String codigo = parametro(req, "codigo");
        try {
            validarCodigo(req, estudiante, codigo);
            redirigir(req, resp, RUTA + "?accion=confirmarUnirse&codigo=" + url(codigo));
        } catch (ValidacionException e) {
            agregarErrores(req, e.consultarErrores());
            redirigir(req, resp, RUTA);
        }
    }

    /** Segundo paso: revalida todo y solo entonces crea la inscripción ACTIVA. */
    private void confirmarUnion(HttpServletRequest req, HttpServletResponse resp, Estudiante estudiante)
            throws IOException {
        String codigo = parametro(req, "codigo");
        if (!"1".equals(req.getParameter("confirmado"))) {
            redirigir(req, resp, RUTA + "?accion=confirmarUnirse&codigo=" + url(codigo));
            return;
        }
        try {
            Grupo grupo = validarCodigo(req, estudiante, codigo);
            Inscripcion inscripcion = inscripcionDAO.crearInscripcion(estudiante, grupo);
            Grupo unido = inscripcion.grupoInscripcion;
            agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                    "Te uniste a " + unido.cursoGrupo.nombreCurso + ", " + unido.nombreGrupo + " ("
                    + unido.periodoGrupo + ").");
        } catch (ValidacionException e) {
            agregarErrores(req, e.consultarErrores());
        }
        redirigir(req, resp, RUTA);
    }

    /** Pantalla de confirmación con los datos reales del grupo (releídos por código). */
    private void mostrarConfirmacion(HttpServletRequest req, HttpServletResponse resp, Estudiante estudiante)
            throws IOException {
        String codigo = parametro(req, "codigo");
        try {
            Grupo grupo = validarCodigo(req, estudiante, codigo);
            Map<String, String> datos = new HashMap<>();
            datos.put("curso", grupo.cursoGrupo.nombreCurso);
            datos.put("nivel", Etiquetas.de(grupo.cursoGrupo.nivelCurso));
            datos.put("grupo", grupo.nombreGrupo);
            datos.put("periodo", grupo.periodoGrupo);
            datos.put("docente", grupo.cursoGrupo.docenteCurso.nombreUsuario);
            datos.put("codigo", GeneradorCodigoAcceso.normalizar(codigo));
            renderizar(req, resp, "estudiante-confirmar-grupo.html", datos, null);
        } catch (ValidacionException e) {
            agregarErrores(req, e.consultarErrores());
            redirigir(req, resp, RUTA);
        }
    }

    /**
     * Valida el código y devuelve el grupo. Los códigos incorrectos cuentan para el límite por
     * sesión (anti fuerza bruta). El grupo devuelto ya cumple ABIERTO, curso activo y sin duplicado.
     */
    private Grupo validarCodigo(HttpServletRequest req, Estudiante estudiante, String codigo)
            throws CodigoAccesoInvalidoException, ValidacionException {
        HttpSession sesion = req.getSession(true);
        long ahora = System.currentTimeMillis();
        long[] fallos = (long[]) sesion.getAttribute(CLAVE_FALLOS);
        if (fallos == null || ahora - fallos[1] > VENTANA_MS) {
            fallos = new long[]{0, ahora};
        }
        if (fallos[0] >= MAX_FALLOS) {
            throw new ValidacionException("Demasiados códigos incorrectos. Espera 15 minutos e inténtalo de nuevo.");
        }
        try {
            String normalizado = GeneradorCodigoAcceso.normalizar(codigo);
            if (!GeneradorCodigoAcceso.tieneFormatoValido(normalizado)) {
                throw new CodigoAccesoInvalidoException("El código debe tener 8 letras o números. Revísalo con tu docente.");
            }
            Grupo grupo = grupoDAO.buscarPorCodigo(normalizado)
                    .orElseThrow(() -> new CodigoAccesoInvalidoException(
                            "No hay ningún grupo con ese código. Revísalo con tu docente."));
            if (grupo.estadoGrupo != EstadoGrupo.ABIERTO) {
                throw new ValidacionException("Este grupo está cerrado y no admite inscripciones.");
            }
            if (!grupo.cursoGrupo.activoCurso) {
                throw new ValidacionException("Ese grupo no está recibiendo inscripciones.");
            }
            if (inscripcionDAO.buscarPorGrupoYEstudiante(grupo.consultarIdGrupo(), estudiante.consultarIdUsuario()).isPresent()) {
                throw new ValidacionException("Ya perteneces a este grupo o fuiste retirado de él; pídele a tu "
                        + "docente que te reincorpore.");
            }
            return grupo;
        } catch (CodigoAccesoInvalidoException e) {
            fallos[0]++;
            sesion.setAttribute(CLAVE_FALLOS, fallos);
            throw e;
        }
    }

    private static String url(String valor) {
        return java.net.URLEncoder.encode(valor == null ? "" : valor, java.nio.charset.StandardCharsets.UTF_8);
    }
}
