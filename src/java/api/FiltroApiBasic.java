package api;

import logica.Usuario;
import modelo.UsuarioDAO;
import servlet.InicializadorAplicacion;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Autenticacion HTTP Basic para /api/v1/* reutilizando EXACTAMENTE la logica de login de ExamLen
 * (correo o identificacion, hash BCrypt o legacy con rehash, bloqueo 5/15, cuenta inactiva).
 * No crea sesion: adjunta la identidad al request. No registra credenciales en logs.
 */
public class FiltroApiBasic implements Filter {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 15;
    private static final String MENSAJE_GENERICO = "Usuario o contraseña incorrectos.";
    private static final String MENSAJE_DESACTIVADA = "La cuenta está desactivada. Contacte al administrador.";

    private UsuarioDAO usuarioDAO;

    @Override
    public void init(FilterConfig config) throws ServletException {
        usuarioDAO = (UsuarioDAO) config.getServletContext().getAttribute(InicializadorAplicacion.USUARIO_DAO);
        if (usuarioDAO == null) {
            throw new ServletException("La aplicación no se inicializó correctamente.");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String cabecera = req.getHeader("Authorization");
        if (cabecera == null || !cabecera.regionMatches(true, 0, "Basic ", 0, 6)) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO",
                    "Se requiere autenticación HTTP Basic.");
            return;
        }

        String credenciales;
        try {
            byte[] decodificado = Base64.getDecoder().decode(cabecera.substring(6).trim());
            credenciales = new String(decodificado, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
            return;
        }
        int separador = credenciales.indexOf(':');
        if (separador <= 0) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
            return;
        }
        String identificador = credenciales.substring(0, separador).strip();
        String contrasena = credenciales.substring(separador + 1);

        Optional<Usuario> hallado = usuarioDAO.buscarPorCorreoOIdentificacion(identificador);
        Usuario usuario = hallado.orElse(null);
        boolean contrasenaCorrecta = usuarioDAO.verificarContrasenaUsuario(usuario, contrasena);
        if (usuario == null) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
            return;
        }
        if (usuarioDAO.estaBloqueado(usuario.consultarIdUsuario())) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
            return;
        }
        if (!contrasenaCorrecta) {
            usuarioDAO.registrarIntentoFallido(usuario.consultarIdUsuario(), MAX_INTENTOS, MINUTOS_BLOQUEO);
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
            return;
        }
        if (!usuario.activoUsuario) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "CUENTA_DESACTIVADA", MENSAJE_DESACTIVADA);
            return;
        }
        usuarioDAO.reiniciarIntentos(usuario.consultarIdUsuario());
        usuarioDAO.recalcularHashSiEsAnterior(usuario, contrasena);

        String rol = usuarioDAO.rolDeUsuario(usuario.consultarIdUsuario()).orElse("");
        if (rol.isBlank()) {
            ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "CREDENCIALES_INVALIDAS", MENSAJE_GENERICO);
            return;
        }
        req.setAttribute(ApioJson.ATTR_ID_USUARIO, usuario.consultarIdUsuario());
        req.setAttribute(ApioJson.ATTR_ROL, rol);
        req.setAttribute(ApioJson.ATTR_NOMBRE, usuario.nombreUsuario);
        req.setAttribute(ApioJson.ATTR_CORREO, usuario.correoUsuario);
        req.setAttribute(ApioJson.ATTR_IDENTIFICACION, usuario.identificacionUsuario);
        chain.doFilter(req, resp);
    }
}
