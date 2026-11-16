package servlet;

import logica.Administrador;
import logica.Docente;
import logica.GeneradorContrasenaTemporal;
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

@WebServlet(name = "AdministradorDocentesServlet", urlPatterns = "/app/admin/docentes")
public class AdministradorDocentesServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final int MAX_INSTITUCION = 150;

    private final transient GeneradorContrasenaTemporal generadorTemporal = new GeneradorContrasenaTemporal();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("confirmarRestablecer".equals(parametro(req, "accion"))) {
            int id = parametroId(req, "id");
            Docente docente = usuarioDAO.buscarPorId(id)
                    .filter(u -> u instanceof Docente)
                    .map(u -> (Docente) u)
                    .orElseThrow(() -> new logica.RecursoNoEncontradoException("El docente indicado no existe."));
            confirmar(req, resp, "Restablecer contraseña",
                    "Vas a asignar una contraseña temporal a " + docente.nombreUsuario + " ("
                    + docente.identificacionUsuario + "). Se mostrará una sola vez y deberá cambiarla en su "
                    + "siguiente ingreso.",
                    Html.alerta(vistas, "alerta-info", AVISO_RESPALDO_RF11),
                    req.getContextPath() + "/app/admin/docentes", "restablecer", id, "Restablecer", "boton",
                    req.getContextPath() + "/app/admin/docentes");
            return;
        }
        if ("confirmarDesactivar".equals(parametro(req, "accion"))) {
            int id = parametroId(req, "id");
            Docente docente = usuarioDAO.buscarPorId(id)
                    .filter(u -> u instanceof Docente)
                    .map(u -> (Docente) u)
                    .orElseThrow(() -> new logica.RecursoNoEncontradoException("El docente indicado no existe."));
            confirmar(req, resp, "Desactivar docente",
                    "¿Confirma que desea desactivar a " + docente.nombreUsuario + " ("
                    + docente.identificacionUsuario + ")? No podrá ingresar y su información se conserva.",
                    "", req.getContextPath() + "/app/admin/docentes/estado", "desactivar", id,
                    "Desactivar", "boton-peligro", req.getContextPath() + "/app/admin/docentes");
            return;
        }
        mostrar(req, resp, "", "", "", "", null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("restablecer".equals(parametro(req, "accion"))) {
            int id = parametroId(req, "id");
            if (!"1".equals(req.getParameter("confirmado"))) {
                redirigir(req, resp, "/app/admin/docentes?accion=confirmarRestablecer&id=" + id);
                return;
            }
            Docente docente = usuarioDAO.buscarPorId(id)
                    .filter(u -> u instanceof Docente)
                    .map(u -> (Docente) u)
                    .orElseThrow(() -> new logica.RecursoNoEncontradoException("El docente indicado no existe."));
            String temporal = generadorTemporal.generar();
            usuarioDAO.restablecerContrasenaUsuario(docente, temporal);
            tokenDAO.invalidarTokensDeUsuario(id);
            SesionesActivas.cerrarSesionesDe(id);
            agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Contraseña restablecida. Entrégale a "
                    + docente.nombreUsuario + " esta contraseña temporal, no se volverá a mostrar: " + temporal);
            redirigir(req, resp, "/app/admin/docentes");
            return;
        }
        String identificacion = parametro(req, "identificacion");
        String nombre = parametro(req, "nombre");
        String correo = parametro(req, "correo");
        String institucion = parametro(req, "institucion");

        List<String> errores = new ArrayList<>();
        Validador.validarIdentificacion(identificacion, errores);
        Validador.validarNombre(nombre, errores);
        Validador.validarCorreo(Validador.normalizarCorreo(correo), errores);
        Validador.validarLongitud(institucion, 0, MAX_INSTITUCION, "La institución admite máximo 150 caracteres.", errores);
        if (usuarioDAO.existeIdentificacion(identificacion)) {
            errores.add("Esa identificación ya está registrada.");
        }
        if (usuarioDAO.existeCorreo(Validador.normalizarCorreo(correo))) {
            errores.add("Ese correo ya está registrado.");
        }
        if (!errores.isEmpty()) {
            mostrar(req, resp, identificacion, nombre, correo, institucion, errores);
            return;
        }
        try {
            String temporal = generadorTemporal.generar();
            Administrador administrador = (Administrador) usuarioActual(req);
            Docente docente = docenteDAO.registrarDocente(administrador, identificacion, nombre,
                    Validador.normalizarCorreo(correo), institucion, temporal);
            agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Docente " + docente.nombreUsuario
                    + " registrado. Entrégale esta contraseña temporal, no se volverá a mostrar: " + temporal);
            redirigir(req, resp, "/app/admin/docentes");
        } catch (ValidacionException e) {
            mostrar(req, resp, identificacion, nombre, correo, institucion, e.consultarErrores());
        }
    }

    private void mostrar(HttpServletRequest req, HttpServletResponse resp, String identificacion,
                         String nombre, String correo, String institucion, List<String> errores) throws IOException {
        String csrf = csrf(req);
        Map<String, String> datos = new HashMap<>();
        datos.put("identificacion", identificacion);
        datos.put("nombre", nombre);
        datos.put("correo", correo);
        datos.put("institucion", institucion);
        datos.put("filas", construirFilas(docenteDAO.listarDocentes(), req.getContextPath(), csrf));
        renderizar(req, resp, "admin-docentes.html", datos, errores);
    }

    private String construirFilas(List<Docente> docentes, String ctx, String csrf) {
        if (docentes.isEmpty()) {
            return filaVacia("5", "Todavia no hay docentes. Registra el primero con el formulario.");
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Docente d : docentes) {
            boolean activo = d.activoUsuario;
            Map<String, String> fila = new HashMap<>();
            fila.put("identificacion", d.identificacionUsuario);
            fila.put("nombre", d.nombreUsuario);
            fila.put("correo", d.correoUsuario);
            Map<String, String> insignia = new HashMap<>();
            insignia.put("clase", activo ? "insignia-activo" : "insignia-inactivo");
            insignia.put("texto", activo ? "Activo" : "Inactivo");
            fila.put("insignia", vistas.componer("_insignia.html", insignia));
            String desactivarUrl = ctx + "/app/admin/docentes?accion=confirmarDesactivar&id=" + d.consultarIdUsuario();
            if (activo) {
                fila.put("accionEstado", "<a class=\"boton boton-peligro boton-pequeno\" href=\""
                        + desactivarUrl + "\">Desactivar</a>");
            } else {
                Map<String, String> accion = new HashMap<>();
                accion.put("destino", ctx + "/app/admin/docentes/estado");
                accion.put("csrf", csrf);
                accion.put("id", String.valueOf(d.consultarIdUsuario()));
                accion.put("accion", "activar");
                accion.put("texto", "Activar");
                accion.put("clase", "boton-contorno boton-pequeno");
                accion.put("extraNombre", "");
                accion.put("extraValor", "");
                fila.put("accionEstado", vistas.componer("_boton-accion.html", accion));
            }
            fila.put("restablecerUrl", ctx + "/app/admin/docentes?accion=confirmarRestablecer&id=" + d.consultarIdUsuario());
            filas.add(fila);
        }
        return componerFilas("_fila-docente.html", filas, "");
    }
}
