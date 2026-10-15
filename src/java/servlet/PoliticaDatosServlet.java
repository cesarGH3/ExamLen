package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;

/**
 * RNF-13: política de tratamiento de datos personales. Página informativa y pública enlazada
 * desde la casilla de consentimiento del registro de estudiantes.
 */
@WebServlet(name = "PoliticaDatosServlet", urlPatterns = "/politica-datos")
public class PoliticaDatosServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        renderizar(req, resp, "politica-datos.html", new HashMap<>(), null);
    }
}
