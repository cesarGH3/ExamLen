package servlet;

import logica.Administrador;
import logica.Docente;
import logica.Estudiante;
import logica.Usuario;
import modelo.UsuarioDAO;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;

/**
 * Protege /app/*: exige sesion, recarga el usuario (si fue desactivado pierde el acceso de inmediato),
 * aplica autorizacion por tipo de usuario y obliga a cambiar la contrasena temporal.
 */
public class AutenticacionFiltro implements Filter {

    public static final String ATRIBUTO_USUARIO = "examlen.usuarioActual";
    private static final String RUTA_CAMBIO_CONTRASENA = "/app/perfil/contrasena";
    private static final String RUTA_SALIR = "/app/salir";

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
        String ctx = req.getContextPath();

        HttpSession sesion = req.getSession(false);
        Object id = sesion == null ? null : sesion.getAttribute(ClavesSesion.ID_USUARIO);
        if (!(id instanceof Integer)) {
            resp.sendRedirect(ctx + "/login");
            return;
        }

        Optional<Usuario> encontrado = usuarioDAO.buscarPorId((Integer) id);
        if (encontrado.isEmpty() || !encontrado.get().activoUsuario) {
            sesion.invalidate();
            resp.sendRedirect(ctx + "/login");
            return;
        }
        Usuario usuario = encontrado.get();

        // Ruta ya normalizada por el contenedor (evita saltos con ;parametros o codificaciones).
        String ruta = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
        if (!estaAutorizado(usuario, ruta)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        boolean debeCambiar = usuario.debeCambiarContrasena
                || (usuario instanceof Docente && ((Docente) usuario).debeCambiarContrasenaDocente);
        if (debeCambiar && !ruta.equals(RUTA_CAMBIO_CONTRASENA) && !ruta.equals(RUTA_SALIR)) {
            resp.sendRedirect(ctx + RUTA_CAMBIO_CONTRASENA);
            return;
        }

        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
        req.setAttribute(ATRIBUTO_USUARIO, usuario);
        chain.doFilter(req, resp);
    }

    private boolean estaAutorizado(Usuario usuario, String ruta) {
        if (ruta.startsWith("/app/admin/")) {
            return usuario instanceof Administrador;
        }
        if (ruta.startsWith("/app/docente/")) {
            return usuario instanceof Docente;
        }
        if (ruta.startsWith("/app/estudiante/")) {
            return usuario instanceof Estudiante;
        }
        return true;
    }
}
