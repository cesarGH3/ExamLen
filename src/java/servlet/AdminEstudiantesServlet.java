package servlet;

import logica.Estudiante;
import logica.GeneradorContrasenaTemporal;
import logica.NivelIngles;
import logica.RecursoNoEncontradoException;
import logica.Usuario;
import logica.ValidacionException;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** RF-08: el administrador consulta estudiantes, corrige su nivel y elimina cuentas sin actividad. */
@WebServlet(name = "AdminEstudiantesServlet", urlPatterns = "/app/admin/estudiantes")
public class AdminEstudiantesServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/admin/estudiantes";

    private final transient GeneradorContrasenaTemporal generadorTemporal = new GeneradorContrasenaTemporal();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String accion = parametro(req, "accion");
        int id = Validador.parsearId(parametro(req, "id"));
        if (id > 0 && "confirmarEliminar".equals(accion)) {
            Estudiante estudiante = estudianteDe(id);
            confirmar(req, resp, "Eliminar estudiante",
                    "Vas a eliminar la cuenta de " + estudiante.nombreUsuario + " ("
                    + estudiante.identificacionUsuario + "). Esta acción no se puede deshacer.",
                    null, req.getContextPath() + RUTA, "eliminar", id, "Eliminar", "boton-peligro",
                    req.getContextPath() + RUTA);
            return;
        }
        if (id > 0 && "confirmarDesactivar".equals(accion)) {
            Estudiante estudiante = estudianteDe(id);
            confirmar(req, resp, "Desactivar estudiante",
                    "Vas a desactivar la cuenta de " + estudiante.nombreUsuario + " ("
                    + estudiante.identificacionUsuario + "). No podrá iniciar sesión hasta que la "
                    + "actives de nuevo; su información se conserva.",
                    null, req.getContextPath() + RUTA, "desactivar", id, "Desactivar", "boton",
                    req.getContextPath() + RUTA);
            return;
        }
        if (id > 0 && "confirmarRestablecer".equals(accion)) {
            Estudiante estudiante = estudianteDe(id);
            confirmar(req, resp, "Restablecer contraseña",
                    "Vas a asignar una contraseña temporal a " + estudiante.nombreUsuario + " ("
                    + estudiante.identificacionUsuario + "). Se mostrará una sola vez y deberá usarla "
                    + "para ingresar.",
                    Html.alerta(vistas, "alerta-info", AVISO_RESPALDO_RF11),
                    req.getContextPath() + RUTA, "restablecer", id, "Restablecer", "boton",
                    req.getContextPath() + RUTA);
            return;
        }
        mostrar(req, resp, parametro(req, "buscar"), null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        switch (parametro(req, "accion")) {
            case "actualizarNivel": {
                int id = parametroId(req, "id");
                Estudiante estudiante = estudianteDe(id);
                NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
                if (nivel == null) {
                    agregarMensaje(req, MensajeFlash.Tipo.ERROR, "Selecciona el nivel de inglés.");
                    redirigir(req, resp, RUTA);
                    return;
                }
                estudianteDAO.actualizarEstudiante(estudiante, nivel.name());
                agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                        "Nivel de " + estudiante.nombreUsuario + " actualizado a " + Etiquetas.de(nivel) + ".");
                redirigir(req, resp, RUTA);
                return;
            }
            case "activar": {
                Estudiante estudiante = estudianteDe(parametroId(req, "id"));
                usuarioDAO.activarUsuario(estudiante);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Estudiante activado.");
                redirigir(req, resp, RUTA);
                return;
            }
            case "desactivar": {
                int id = parametroId(req, "id");
                if (!confirmado(req)) {
                    redirigir(req, resp, RUTA + "?accion=confirmarDesactivar&id=" + id);
                    return;
                }
                Estudiante estudiante = estudianteDe(id);
                usuarioDAO.desactivarUsuario(estudiante);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                        "Estudiante desactivado. Ya no puede ingresar y su información se conserva.");
                redirigir(req, resp, RUTA);
                return;
            }
            case "restablecer": {
                int id = parametroId(req, "id");
                if (!confirmado(req)) {
                    redirigir(req, resp, RUTA + "?accion=confirmarRestablecer&id=" + id);
                    return;
                }
                Estudiante estudiante = estudianteDe(id);
                String temporal = generadorTemporal.generar();
                usuarioDAO.restablecerContrasenaUsuario(estudiante, temporal);
                tokenDAO.invalidarTokensDeUsuario(id);
                SesionesActivas.cerrarSesionesDe(id);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Contraseña restablecida. Entrégale a "
                        + estudiante.nombreUsuario + " esta contraseña temporal, no se volverá a mostrar: " + temporal);
                redirigir(req, resp, RUTA);
                return;
            }
            case "eliminar": {
                int id = parametroId(req, "id");
                if (!confirmado(req)) {
                    redirigir(req, resp, RUTA + "?accion=confirmarEliminar&id=" + id);
                    return;
                }
                Estudiante estudiante = estudianteDe(id);
                int intentos = intentoDAO.contarPorEstudiante(id) + intentoPracticaDAO.contarPorEstudiante(id);
                if (intentos > 0) {
                    agregarMensaje(req, MensajeFlash.Tipo.ERROR, "No se puede eliminar a "
                            + estudiante.nombreUsuario + ": tiene intentos registrados. Desactívalo para "
                            + "quitarle el acceso sin perder su historial.");
                    redirigir(req, resp, RUTA);
                    return;
                }
                try {
                    estudianteDAO.eliminarEstudiante(estudiante);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Estudiante eliminado.");
                } catch (logica.RegistroEnUsoException e) {
                    agregarMensaje(req, MensajeFlash.Tipo.ERROR, "No se puede eliminar a "
                            + estudiante.nombreUsuario + ": tiene inscripciones u otros registros "
                            + "asociados. Desactívalo para quitarle el acceso sin perder su historial.");
                }
                redirigir(req, resp, RUTA);
                return;
            }
            default:
                redirigir(req, resp, RUTA);
        }
    }

    private boolean confirmado(HttpServletRequest req) {
        return "1".equals(req.getParameter("confirmado"));
    }

    private Estudiante estudianteDe(int id) {
        Usuario usuario = usuarioDAO.buscarPorId(id)
                .filter(u -> u instanceof Estudiante)
                .orElseThrow(() -> new RecursoNoEncontradoException("El estudiante indicado no existe."));
        return (Estudiante) usuario;
    }

    private void mostrar(HttpServletRequest req, HttpServletResponse resp, String buscar,
                         List<String> errores) throws IOException {
        String ctx = req.getContextPath();
        Map<String, String> datos = new HashMap<>();
        datos.put("buscar", buscar);
        datos.put("filas", construirFilas(estudianteDAO.listarEstudiantes(buscar), req, csrf(req)));
        renderizar(req, resp, "admin-estudiantes.html", datos, errores);
    }

    private String construirFilas(List<Estudiante> estudiantes, HttpServletRequest req, String csrf) {
        String ctx = req.getContextPath();
        if (estudiantes.isEmpty()) {
            return filaVacia("6", "No hay estudiantes que coincidan con la búsqueda.");
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Estudiante e : estudiantes) {
            boolean activo = e.activoUsuario;
            int id = e.getIdUsuario();
            Map<String, String> fila = new HashMap<>();
            fila.put("identificacion", e.identificacionUsuario);
            fila.put("nombre", e.nombreUsuario);
            fila.put("correo", e.correoUsuario);
            fila.put("destino", ctx + RUTA);
            fila.put("csrf", csrf);
            fila.put("id", String.valueOf(id));
            fila.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(),
                    Formulario.enumeracion(NivelIngles.class, e.nivelInglesEstudiante), null));
            Map<String, String> insignia = new HashMap<>();
            insignia.put("clase", activo ? "insignia-activo" : "insignia-inactivo");
            insignia.put("texto", activo ? "Activo" : "Inactivo");
            fila.put("insignia", vistas.componer("_insignia.html", insignia));
            fila.put("urlRestablecer", ctx + RUTA + "?accion=confirmarRestablecer&id=" + id);
            fila.put("urlEliminar", ctx + RUTA + "?accion=confirmarEliminar&id=" + id);
            if (activo) {
                Map<String, String> enlace = new HashMap<>();
                enlace.put("clase", "boton boton-contorno boton-pequeno");
                enlace.put("url", ctx + RUTA + "?accion=confirmarDesactivar&id=" + id);
                enlace.put("texto", "Desactivar");
                fila.put("accionEstado", vistas.componer("_enlace-accion.html", enlace));
            } else {
                fila.put("accionEstado", boton(req, RUTA, id, "activar", "Activar", "boton-contorno boton-pequeno"));
            }
            filas.add(fila);
        }
        return componerFilas("_fila-estudiante.html", filas, "");
    }
}
