package servlet;

import logica.Docente;
import logica.RecursoNoEncontradoException;
import logica.Usuario;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * RF-09/CU-08: activar o desactivar docentes. La desactivacion exige confirmacion previa
 * (confirmado=1) y se revalida en servidor; la activacion es directa.
 */
@WebServlet(name = "AdminEstadoDocenteServlet", urlPatterns = "/app/admin/docentes/estado")
public class AdminEstadoDocenteServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String accion = parametro(req, "accion");
        int idDocente = logica.Validador.parsearId(req.getParameter("idDocente"));
        if (idDocente < 0) {
            idDocente = parametroId(req, "id");
        }
        boolean activar = "activar".equals(accion) || "true".equals(req.getParameter("activo"));
        boolean desactivar = "desactivar".equals(accion) || "false".equals(req.getParameter("activo"));
        if (!activar && !desactivar) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (desactivar && !"1".equals(req.getParameter("confirmado"))) {
            redirigir(req, resp, "/app/admin/docentes?accion=confirmarDesactivar&id=" + idDocente);
            return;
        }
        Usuario usuario = usuarioDAO.buscarPorId(idDocente)
                .filter(u -> u instanceof Docente)
                .orElseThrow(() -> new RecursoNoEncontradoException("El docente indicado no existe."));
        if (activar) {
            usuarioDAO.activarUsuario(usuario);
        } else {
            usuarioDAO.desactivarUsuario(usuario);
        }
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, activar ? "Docente activado."
                : "Docente desactivado. Ya no puede ingresar.");
        redirigir(req, resp, "/app/admin/docentes");
    }
}
