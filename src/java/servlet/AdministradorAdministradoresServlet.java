package servlet;

import logica.Administrador;
import logica.Fechas;
import logica.GeneradorContrasenaTemporal;
import logica.RecursoNoEncontradoException;
import logica.RegistroEnUsoException;
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

/** RF-06: el administrador crea, actualiza y elimina cuentas de administrador (CU-05). */
@WebServlet(name = "AdministradorAdministradoresServlet", urlPatterns = "/app/admin/administradores")
public class AdministradorAdministradoresServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/admin/administradores";

    private final transient GeneradorContrasenaTemporal generadorTemporal = new GeneradorContrasenaTemporal();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String accion = parametro(req, "accion");
        int id = Validador.parsearId(parametro(req, "id"));
        if (id > 0 && "editar".equals(accion)) {
            Administrador administrador = administradorDe(id);
            mostrar(req, resp, id, administrador.identificacionUsuario, administrador.nombreUsuario,
                    administrador.correoUsuario, fechaTexto(administrador), null);
            return;
        }
        if (id > 0 && "confirmarEliminar".equals(accion)) {
            Administrador administrador = administradorDe(id);
            confirmar(req, resp, "Eliminar administrador",
                    "Vas a eliminar la cuenta de " + administrador.nombreUsuario + " ("
                    + administrador.identificacionUsuario + "). Esta acción no se puede deshacer.",
                    null, req.getContextPath() + RUTA, "eliminar", id, "Eliminar", "boton-peligro",
                    req.getContextPath() + RUTA);
            return;
        }
        if (id > 0 && "confirmarDesactivar".equals(accion)) {
            Administrador administrador = administradorDe(id);
            confirmar(req, resp, "Desactivar administrador",
                    "Vas a desactivar la cuenta de " + administrador.nombreUsuario + " ("
                    + administrador.identificacionUsuario + "). No podrá iniciar sesión hasta que la actives "
                    + "de nuevo; su información se conserva.",
                    null, req.getContextPath() + RUTA, "desactivar", id, "Desactivar", "boton",
                    req.getContextPath() + RUTA);
            return;
        }
        if (id > 0 && "confirmarRestablecer".equals(accion)) {
            Administrador administrador = administradorDe(id);
            confirmar(req, resp, "Restablecer contraseña",
                    "Vas a asignar una contraseña temporal a " + administrador.nombreUsuario + " ("
                    + administrador.identificacionUsuario + "). Se mostrará una sola vez y deberá usarla para ingresar.",
                    Html.alerta(vistas, "alerta-info", AVISO_RESPALDO_RF11),
                    req.getContextPath() + RUTA, "restablecer", id, "Restablecer", "boton",
                    req.getContextPath() + RUTA);
            return;
        }
        mostrar(req, resp, null, "", "", "", "", null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String identificacion = parametro(req, "identificacion");
        String nombre = parametro(req, "nombre");
        String correo = parametro(req, "correo");
        String fecha = parametro(req, "fechaNacimiento");

        switch (parametro(req, "accion")) {
            case "crear": {
                List<String> errores = new ArrayList<>();
                java.util.Date fechaNacimiento = validar(identificacion, nombre, correo, fecha, errores);
                if (usuarioDAO.existeIdentificacion(identificacion)) {
                    errores.add("Esa identificación ya está registrada.");
                }
                if (usuarioDAO.existeCorreo(Validador.normalizarCorreo(correo))) {
                    errores.add("Ese correo ya está registrado.");
                }
                if (!errores.isEmpty()) {
                    mostrar(req, resp, null, identificacion, nombre, correo, fecha, errores);
                    return;
                }
                try {
                    String temporal = generadorTemporal.generar();
                    Administrador administrador = administradorDAO.crearAdministrador(identificacion, nombre,
                            Validador.normalizarCorreo(correo), temporal, null, fechaNacimiento);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Administrador " + administrador.nombreUsuario
                            + " creado. Entrégale esta contraseña temporal, no se volverá a mostrar: " + temporal);
                    redirigir(req, resp, RUTA);
                } catch (ValidacionException e) {
                    mostrar(req, resp, null, identificacion, nombre, correo, fecha, e.consultarErrores());
                }
                return;
            }
            case "actualizar": {
                int id = parametroId(req, "id");
                List<String> errores = new ArrayList<>();
                java.util.Date fechaNacimiento = validar(identificacion, nombre, correo, fecha, errores);
                if (!errores.isEmpty()) {
                    mostrar(req, resp, id, identificacion, nombre, correo, fecha, errores);
                    return;
                }
                Administrador administrador = administradorDe(id);
                try {
                    administradorDAO.actualizarAdministrador(administrador, nombre, Validador.normalizarCorreo(correo),
                            fechaNacimiento);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Administrador actualizado.");
                    redirigir(req, resp, RUTA);
                } catch (ValidacionException e) {
                    mostrar(req, resp, id, identificacion, nombre, correo, fecha, e.consultarErrores());
                }
                return;
            }
            case "activar": {
                Administrador administrador = administradorDe(parametroId(req, "id"));
                usuarioDAO.activarUsuario(administrador);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Administrador activado.");
                redirigir(req, resp, RUTA);
                return;
            }
            case "desactivar": {
                int id = parametroId(req, "id");
                if (!confirmado(req)) {
                    redirigir(req, resp, RUTA + "?accion=confirmarDesactivar&id=" + id);
                    return;
                }
                if (id == usuarioActual(req).consultarIdUsuario()) {
                    agregarMensaje(req, MensajeFlash.Tipo.ERROR, "No puedes desactivar tu propia cuenta.");
                    redirigir(req, resp, RUTA);
                    return;
                }
                Administrador administrador = administradorDe(id);
                usuarioDAO.desactivarUsuario(administrador);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                        "Administrador desactivado. Ya no puede ingresar y su información se conserva.");
                redirigir(req, resp, RUTA);
                return;
            }
            case "restablecer": {
                int id = parametroId(req, "id");
                if (!confirmado(req)) {
                    redirigir(req, resp, RUTA + "?accion=confirmarRestablecer&id=" + id);
                    return;
                }
                Administrador administrador = administradorDe(id);
                String temporal = generadorTemporal.generar();
                usuarioDAO.restablecerContrasenaUsuario(administrador, temporal);
                tokenDAO.invalidarTokensDeUsuario(id);
                SesionesActivas.cerrarSesionesDe(id);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Contraseña restablecida. Entrégale a "
                        + administrador.nombreUsuario + " esta contraseña temporal, no se volverá a mostrar: " + temporal);
                redirigir(req, resp, RUTA);
                return;
            }
            case "eliminar": {
                int id = parametroId(req, "id");
                if (!confirmado(req)) {
                    redirigir(req, resp, RUTA + "?accion=confirmarEliminar&id=" + id);
                    return;
                }
                if (id == usuarioActual(req).consultarIdUsuario()) {
                    agregarMensaje(req, MensajeFlash.Tipo.ERROR, "No puedes eliminar tu propia cuenta.");
                    redirigir(req, resp, RUTA);
                    return;
                }
                Administrador administrador = administradorDe(id);
                if (administrador.activoUsuario && administradorDAO.contarActivos() <= 1) {
                    agregarMensaje(req, MensajeFlash.Tipo.ERROR,
                            "No puedes eliminar al último administrador activo.");
                    redirigir(req, resp, RUTA);
                    return;
                }
                try {
                    administradorDAO.eliminarAdministrador(administrador);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Administrador eliminado.");
                } catch (RegistroEnUsoException e) {
                    agregarMensaje(req, MensajeFlash.Tipo.ERROR, "No se puede eliminar a "
                            + administrador.nombreUsuario + ": tiene docentes registrados u otros registros "
                            + "asociados. Desactívalo para quitarle el acceso sin perder su historial.");
                }
                redirigir(req, resp, RUTA);
                return;
            }
            default:
                redirigir(req, resp, RUTA);
        }
    }

    private java.util.Date validar(String identificacion, String nombre, String correo, String fecha,
                                   List<String> errores) {
        Validador.validarIdentificacion(identificacion, errores);
        Validador.validarNombre(nombre, errores);
        Validador.validarCorreo(Validador.normalizarCorreo(correo), errores);
        return fechaDe(fecha, errores);
    }

    /** Fecha de nacimiento opcional en formato yyyy-MM-dd (input type=date). */
    private java.util.Date fechaDe(String valor, List<String> errores) {
        String texto = valor == null ? "" : valor.strip();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(java.time.LocalDate.parse(texto));
        } catch (java.time.format.DateTimeParseException e) {
            errores.add("La fecha de nacimiento no es válida.");
            return null;
        }
    }

    private String fechaTexto(Administrador administrador) {
        return administrador.fechaNacimientoAdministrador == null ? ""
                : Fechas.formato("yyyy-MM-dd", administrador.fechaNacimientoAdministrador);
    }

    private boolean confirmado(HttpServletRequest req) {
        return "1".equals(req.getParameter("confirmado"));
    }

    private Administrador administradorDe(int id) {
        Usuario usuario = usuarioDAO.buscarPorId(id)
                .filter(u -> u instanceof Administrador)
                .orElseThrow(() -> new RecursoNoEncontradoException("El administrador indicado no existe."));
        return (Administrador) usuario;
    }

    private void mostrar(HttpServletRequest req, HttpServletResponse resp, Integer idEdicion,
                         String identificacion, String nombre, String correo, String fecha,
                         List<String> errores) throws IOException {
        String ctx = req.getContextPath();
        boolean edicion = idEdicion != null;
        Map<String, String> datos = new HashMap<>();
        datos.put("tituloForm", edicion ? "Editar administrador" : "Crear administrador");
        datos.put("accionForm", edicion ? "actualizar" : "crear");
        datos.put("id", edicion ? String.valueOf(idEdicion) : "");
        datos.put("soloLectura", edicion ? "readonly" : "");
        datos.put("identificacion", identificacion);
        datos.put("nombre", nombre);
        datos.put("correo", correo);
        datos.put("fechaNacimiento", fecha);
        datos.put("textoBoton", edicion ? "Guardar cambios" : "Crear administrador");
        datos.put("filas", construirFilas(administradorDAO.listarAdministradores(), req, csrf(req)));
        renderizar(req, resp, "admin-administradores.html", datos, errores);
    }

    private String construirFilas(List<Administrador> administradores, HttpServletRequest req, String csrf) {
        String ctx = req.getContextPath();
        if (administradores.isEmpty()) {
            return filaVacia("6", "Todavía no hay administradores.");
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Administrador a : administradores) {
            boolean activo = a.activoUsuario;
            int id = a.consultarIdUsuario();
            Map<String, String> fila = new HashMap<>();
            fila.put("identificacion", a.identificacionUsuario);
            fila.put("nombre", a.nombreUsuario);
            fila.put("correo", a.correoUsuario);
            fila.put("fechaNacimiento", a.fechaNacimientoAdministrador == null ? "—"
                    : Fechas.formato(Fechas.PATRON_FECHA, a.fechaNacimientoAdministrador));
            Map<String, String> insignia = new HashMap<>();
            insignia.put("clase", activo ? "insignia-activo" : "insignia-inactivo");
            insignia.put("texto", activo ? "Activo" : "Inactivo");
            fila.put("insignia", vistas.componer("_insignia.html", insignia));
            fila.put("urlEditar", ctx + RUTA + "?accion=editar&id=" + id);
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
        return componerFilas("_fila-administrador.html", filas, "");
    }
}
