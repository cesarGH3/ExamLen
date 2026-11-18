package api;

import java.io.IOException;
import java.util.Optional;
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

/**
 * Autenticacion de /api/v1/* con tokens Bearer de vida corta. El endpoint de emision
 * (/api/v1/auth/token) es la unica ruta publica del filtro; el resto exige un token valido.
 * La identidad y el rol se recargan de la base en cada peticion para reflejar desactivaciones
 * y cambios de rol, conservando los permisos por rol y propiedad de los servlets existentes.
 */
public class FiltroApiToken implements Filter {

    private static final String RUTA_TOKEN = "/api/v1/auth/token";

    private UsuarioDAO usuarioDAO;

    @Override
    public void init(FilterConfig config) throws ServletException {
        usuarioDAO = (UsuarioDAO) config.getServletContext().getAttribute(InicializadorAplicacion.USUARIO_DAO);
        if (usuarioDAO == null) {
            throw new ServletException("La aplicacion no se inicializo correctamente.");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String ruta = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
        if (RUTA_TOKEN.equals(ruta)) {
            chain.doFilter(request, response);
            return;
        }

        String cabecera = req.getHeader("Authorization");
        if (cabecera == null || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            noAutenticado(resp, "invalid_request", "Se requiere un token Bearer.");
            return;
        }
        Optional<ApiTokens.TokenValido> token = ApiTokens.validar(cabecera.substring(7).trim());
        if (token.isEmpty()) {
            noAutenticado(resp, "invalid_token", "El token no es valido o expiro.");
            return;
        }

        Optional<Usuario> hallado = usuarioDAO.buscarPorId(token.get().idUsuario);
        if (hallado.isEmpty() || !hallado.get().activoUsuario) {
            noAutenticado(resp, "invalid_token", "La cuenta no esta disponible.");
            return;
        }
        Usuario usuario = hallado.get();
        String rol = usuarioDAO.rolDeUsuario(usuario.consultarIdUsuario()).orElse("");
        if (rol.isBlank()) {
            noAutenticado(resp, "invalid_token", "La cuenta no tiene un rol valido.");
            return;
        }

        req.setAttribute(ApioJson.ATTR_ID_USUARIO, usuario.consultarIdUsuario());
        req.setAttribute(ApioJson.ATTR_ROL, rol);
        req.setAttribute(ApioJson.ATTR_NOMBRE, usuario.nombreUsuario);
        req.setAttribute(ApioJson.ATTR_CORREO, usuario.correoUsuario);
        req.setAttribute(ApioJson.ATTR_IDENTIFICACION, usuario.identificacionUsuario);
        resp.setHeader("Cache-Control", "no-store");
        chain.doFilter(req, resp);
    }

    private static void noAutenticado(HttpServletResponse resp, String codigo, String mensaje)
            throws IOException {
        resp.setHeader("WWW-Authenticate", ApiTokens.desafio(codigo));
        ApioJson.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO", mensaje);
    }
}
