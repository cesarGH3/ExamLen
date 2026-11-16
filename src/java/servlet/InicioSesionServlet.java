package servlet;

import logica.AutenticacionFallidaException;
import logica.Docente;
import logica.Usuario;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet(name = "InicioSesionServlet", urlPatterns = "/login")
public class InicioSesionServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;
    private static final String MENSAJE_GENERICO =
            "Correo o contraseña incorrectos, o la cuenta no está disponible en este momento.";
    private static final String MENSAJE_DESACTIVADA =
            "La cuenta está desactivada. Contacte al administrador.";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (usuarioDAO.contarAdministradores() == 0) {
            redirigir(req, resp, "/primer-administrador");
            return;
        }
        HttpSession sesion = req.getSession(false);
        if (sesion != null && sesion.getAttribute(ClavesSesion.ID_USUARIO) != null) {
            redirigir(req, resp, "/app/inicio");
            return;
        }
        renderizar(req, resp, "login.html", Map.of("acceso", ""), null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (usuarioDAO.contarAdministradores() == 0) {
            redirigir(req, resp, "/primer-administrador");
            return;
        }
        String acceso = parametro(req, "acceso");
        if (acceso.isBlank()) {
            acceso = parametro(req, "correo");
        }
        String contrasena = req.getParameter("contrasena");
        try {
            Usuario usuario = usuarioDAO.buscarPorCorreoOIdentificacion(acceso).orElse(null);
            boolean contrasenaCorrecta = usuarioDAO.verificarContrasenaUsuario(usuario, contrasena);
            if (usuario == null) {
                throw new AutenticacionFallidaException(MENSAJE_GENERICO);
            }
            if (usuarioDAO.estaBloqueado(usuario.consultarIdUsuario())) {
                throw new AutenticacionFallidaException(MENSAJE_GENERICO);
            }
            if (!contrasenaCorrecta) {
                usuarioDAO.registrarIntentoFallido(usuario.consultarIdUsuario(), MAX_INTENTOS, MINUTOS_BLOQUEO);
                throw new AutenticacionFallidaException(MENSAJE_GENERICO);
            }
            // Credenciales correctas: recien aqui se informa el estado de la cuenta (CU-01).
            if (!usuario.activoUsuario) {
                throw new AutenticacionFallidaException(MENSAJE_DESACTIVADA);
            }
            usuarioDAO.reiniciarIntentos(usuario.consultarIdUsuario());
            usuarioDAO.recalcularHashSiEsAnterior(usuario, contrasena);

            // Nueva sesion al autenticar: evita fijacion de sesion.
            HttpSession anterior = req.getSession(false);
            if (anterior != null) {
                anterior.invalidate();
            }
            HttpSession sesion = req.getSession(true);
            sesion.setAttribute(ClavesSesion.ID_USUARIO, usuario.consultarIdUsuario());
            SesionesActivas.registrar(usuario.consultarIdUsuario(), sesion);

            boolean debeCambiar = usuario.debeCambiarContrasena
                    || (usuario instanceof Docente && ((Docente) usuario).debeCambiarContrasenaDocente);
            redirigir(req, resp, debeCambiar ? "/app/perfil/contrasena" : "/app/inicio");
        } catch (AutenticacionFallidaException e) {
            renderizar(req, resp, "login.html", Map.of("acceso", acceso), List.of(e.getMessage()));
        }
    }
}
