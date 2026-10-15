package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/** Solo POST con token CSRF: un enlace externo no puede cerrar la sesion del usuario. */
@WebServlet(name = "LogoutServlet", urlPatterns = "/app/salir")
public class LogoutServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession sesion = req.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        agregarMensaje(req, MensajeFlash.Tipo.INFO, "Sesión cerrada.");
        redirigir(req, resp, "/login");
    }
}
