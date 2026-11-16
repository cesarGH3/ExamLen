package servlet;

import logica.NivelIngles;
import logica.PoliticaContrasena;
import logica.ValidacionException;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Registro publico de estudiantes. Los docentes los crea el administrador. */
@WebServlet(name = "RegistroServlet", urlPatterns = "/registro")
public class RegistroServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final int MAX_INSTITUCION = 150;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession sesion = req.getSession(false);
        if (sesion != null && sesion.getAttribute(ClavesSesion.ID_USUARIO) != null) {
            redirigir(req, resp, "/app/inicio");
            return;
        }
        renderizar(req, resp, "registrarse.html", formulario("", "", "", "", "", false), null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String identificacion = parametro(req, "identificacion");
        String nombre = parametro(req, "nombre");
        String correo = parametro(req, "correo");
        String nivel = parametro(req, "nivel");
        String institucion = parametro(req, "institucion");
        String contrasena = req.getParameter("contrasena");
        String confirmacion = req.getParameter("confirmacion");

        List<String> errores = new ArrayList<>();
        Validador.validarIdentificacion(identificacion, errores);
        Validador.validarNombre(nombre, errores);
        Validador.validarCorreo(Validador.normalizarCorreo(correo), errores);
        if (Formulario.enumeracion(NivelIngles.class, nivel) == null) {
            errores.add("Selecciona tu nivel de inglés.");
        }
        Validador.validarLongitud(institucion, 0, MAX_INSTITUCION, "La institución admite máximo 150 caracteres.", errores);
        if (!PoliticaContrasena.cumple(contrasena)) {
            errores.add(PoliticaContrasena.DESCRIPCION);
        } else if (!contrasena.equals(confirmacion)) {
            errores.add("La confirmación no coincide con la contraseña.");
        }
        if (!"true".equals(req.getParameter("acepta"))) {
            errores.add("Debes aceptar la política de tratamiento de datos personales para crear la cuenta.");
        }
        if (usuarioDAO.existeIdentificacion(identificacion)) {
            errores.add("Esa identificación ya está registrada.");
        }
        if (usuarioDAO.existeCorreo(Validador.normalizarCorreo(correo))) {
            errores.add("Ese correo ya está registrado.");
        }
        if (!errores.isEmpty()) {
            renderizar(req, resp, "registrarse.html",
                    formulario(identificacion, nombre, correo, nivel, institucion,
                            "true".equals(req.getParameter("acepta"))), errores);
            return;
        }
        try {
            estudianteDAO.crearEstudiante(identificacion, nombre, Validador.normalizarCorreo(correo), nivel, institucion, contrasena);
            agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Cuenta creada. Ya puedes iniciar sesión.");
            redirigir(req, resp, "/login");
        } catch (ValidacionException e) {
            renderizar(req, resp, "registrarse.html",
                    formulario(identificacion, nombre, correo, nivel, institucion,
                            "true".equals(req.getParameter("acepta"))), e.consultarErrores());
        }
    }

    private Map<String, String> formulario(String identificacion, String nombre, String correo, String nivel,
                                           String institucion, boolean acepta) {
        Map<String, String> datos = new HashMap<>();
        datos.put("identificacion", identificacion);
        datos.put("nombre", nombre);
        datos.put("correo", correo);
        datos.put("institucion", institucion);
        datos.put("aceptaMarcada", acepta ? "checked" : "");
        datos.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(),
                Formulario.enumeracion(NivelIngles.class, nivel), "Selecciona tu nivel"));
        return datos;
    }
}
