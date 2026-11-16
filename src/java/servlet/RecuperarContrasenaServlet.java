package servlet;

import logica.TokenRecuperacion;
import logica.Usuario;
import logica.Validador;
import servicio.ConfiguracionCorreo;
import servicio.ServicioCorreo;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * RF-11: solicitud del enlace de recuperacion. Responde siempre el mismo mensaje,
 * exista o no el correo (RNF-04).
 */
@WebServlet(name = "RecuperarContrasenaServlet", urlPatterns = "/recuperar")
public class RecuperarContrasenaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(RecuperarContrasenaServlet.class.getName());
    private static final int MAX_SOLICITUDES_POR_HORA = 3;
    /**
     * RF-11/RNF-04: una sola respuesta externa para todos los casos (correo registrado, no
     * registrado, cuenta inactiva, límite superado o fallo de SMTP). El último tramo orienta sin
     * enumerar: reintentar más tarde o pedir el restablecimiento al administrador (RF-10).
     */
    private static final String MENSAJE_GENERICO =
            "Si el correo está registrado y la cuenta está activa, recibirás un enlace de recuperación. "
            + "Revisa también la carpeta de spam. Si no llega en unos minutos, vuelve a intentarlo más tarde "
            + "o solicita al administrador el restablecimiento de tu contraseña.";

    private final ServicioCorreo servicioCorreo = new ServicioCorreo();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession sesion = req.getSession(false);
        if (sesion != null && sesion.getAttribute(ClavesSesion.ID_USUARIO) != null) {
            redirigir(req, resp, "/app/inicio");
            return;
        }
        renderizar(req, resp, "recuperar.html", Map.of("correo", ""), null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String correo = parametro(req, "correo");
        String correoNormalizado = Validador.normalizarCorreo(correo);
        if (!Validador.esCorreoValido(correoNormalizado)) {
            renderizar(req, resp, "recuperar.html", Map.of("correo", correo),
                    List.of("Escribe un correo electrónico válido."));
            return;
        }
        solicitarRecuperacion(req, correoNormalizado);
        agregarMensaje(req, MensajeFlash.Tipo.INFO, MENSAJE_GENERICO);
        redirigir(req, resp, "/recuperar");
    }

    private void solicitarRecuperacion(HttpServletRequest req, String correo) {
        Usuario usuario = usuarioDAO.buscarPorCorreo(correo).orElse(null);
        if (usuario == null || !usuario.activoUsuario) {
            return;
        }
        if (tokenDAO.contarSolicitudesRecientes(usuario.consultarIdUsuario()) >= MAX_SOLICITUDES_POR_HORA) {
            LOG.warning("Solicitud de recuperación bloqueada por el límite de 3 por hora (usuario "
                    + usuario.consultarIdUsuario() + ")");
            return;
        }
        TokenRecuperacion token = TokenRecuperacion.generarTokenRecuperacion(usuario);
        tokenDAO.crearToken(token);
        String enlace = enlaceRecuperacion(req, token.consultarTokenPlanoTokenRecuperacion());
        if (!servicioCorreo.enviarEnlaceRecuperacion(usuario.correoUsuario, enlace)) {
            // CU-10 5a: si el envío falla, se compensa invalidando el token recién creado para no
            // dejar enlaces utilizables sin posibilidad razonable de entrega. El mensaje externo no
            // cambia (no enumera) e indica reintentar o pedir el restablecimiento al administrador.
            LOG.warning("El correo de recuperación no se pudo enviar; se invalida el token recién creado "
                    + "para la cuenta " + usuario.consultarIdUsuario() + ".");
            tokenDAO.invalidarTokensDeUsuario(usuario.consultarIdUsuario());
        }
    }

    private String enlaceRecuperacion(HttpServletRequest req, String tokenPlano) {
        String base = ConfiguracionCorreo.urlBase();
        if (base == null) {
            base = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort()
                    + req.getContextPath();
        } else if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/restablecer?token=" + tokenPlano;
    }
}
