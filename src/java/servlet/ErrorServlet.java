package servlet;

import logica.AccesoDatosException;
import logica.OperacionNoPermitidaException;
import logica.RecursoNoEncontradoException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Pagina de error unica. El detalle tecnico va al log; el usuario solo ve un mensaje claro. */
@WebServlet(name = "ErrorServlet", urlPatterns = "/error")
public class ErrorServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(ErrorServlet.class.getName());

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Throwable excepcion = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Integer codigo = (Integer) req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int estado = codigo == null ? HttpServletResponse.SC_NOT_FOUND : codigo;

        if (excepcion != null) {
            if (contiene(excepcion, RecursoNoEncontradoException.class)) {
                estado = HttpServletResponse.SC_NOT_FOUND;
            } else if (contiene(excepcion, OperacionNoPermitidaException.class)) {
                estado = HttpServletResponse.SC_FORBIDDEN;
            } else if (contiene(excepcion, AccesoDatosException.class)) {
                estado = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
                LOG.log(Level.SEVERE, "Error de base de datos en " + req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), excepcion);
            } else {
                estado = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                LOG.log(Level.SEVERE, "Error en " + req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI), excepcion);
            }
        }

        if (resp.isCommitted()) {
            return;
        }
        Vistas vistas = (Vistas) getServletContext().getAttribute(InicializadorAplicacion.VISTAS);
        resp.setHeader("Cache-Control", "no-store");
        vistas.escribir(resp, estado, "error.html", PaginasError.datos(req.getContextPath(), estado));
    }

    private boolean contiene(Throwable t, Class<? extends Throwable> tipo) {
        for (Throwable actual = t; actual != null; actual = actual.getCause()) {
            if (tipo.isInstance(actual)) {
                return true;
            }
        }
        return false;
    }
}
