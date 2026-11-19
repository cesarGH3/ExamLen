package servlet;

import logica.PoliticaContrasena;
import logica.Validador;
import logica.ValidacionException;
import servicio.Instalacion;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Primer arranque: cuando aun no existe ningun administrador, permite crear
 * exactamente el primero. En cuanto existe un administrador la ruta deja de
 * estar disponible (404) y el acceso vuelve al login normal.
 */
@WebServlet(name = "PrimerAdministradorServlet", urlPatterns = "/primer-administrador")
public class PrimerAdministradorServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (usuarioDAO.contarAdministradores() > 0 || !Instalacion.habilitada()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        resp.setHeader("Cache-Control", "no-store");
        mostrar(req, resp, "", "", "", null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Cache-Control", "no-store");
        if (usuarioDAO.contarAdministradores() > 0 || !Instalacion.habilitada()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!Instalacion.verificar(req.getParameter("claveInstalacion"))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String identificacion = parametro(req, "identificacion");
        String nombre = parametro(req, "nombre");
        String correo = Validador.normalizarCorreo(parametro(req, "correo"));
        String contrasena = req.getParameter("contrasena");
        String confirmacion = req.getParameter("confirmacion");

        List<String> errores = new ArrayList<>();
        Validador.validarIdentificacion(identificacion, errores);
        Validador.validarNombre(nombre, errores);
        Validador.validarCorreo(correo, errores);
        if (!PoliticaContrasena.cumple(contrasena)) {
            errores.add(PoliticaContrasena.DESCRIPCION);
        } else if (!contrasena.equals(confirmacion)) {
            errores.add("La confirmación no coincide con la contraseña.");
        }
        if (usuarioDAO.existeIdentificacion(identificacion)) {
            errores.add("Esa identificación ya está registrada.");
        }
        if (usuarioDAO.existeCorreo(correo)) {
            errores.add("Ese correo ya está registrado.");
        }
        if (!errores.isEmpty()) {
            mostrar(req, resp, identificacion, nombre, correo, errores);
            return;
        }
        try {
            administradorDAO.crearPrimerAdministrador(identificacion, nombre, correo, contrasena, null, null);
            agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                    "Administrador creado. Inicia sesión con tu correo o identificación.");
            redirigir(req, resp, "/login");
        } catch (ValidacionException e) {
            mostrar(req, resp, identificacion, nombre, correo, e.consultarErrores());
        }
    }

    private void mostrar(HttpServletRequest req, HttpServletResponse resp, String identificacion, String nombre,
                         String correo, List<String> errores) throws IOException {
        Map<String, String> datos = new HashMap<>();
        datos.put("identificacion", identificacion);
        datos.put("nombre", nombre);
        datos.put("correo", correo);
        renderizar(req, resp, "primer-administrador.html", datos, errores);
    }
}
