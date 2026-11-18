package api;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import modelo.TokenApiDAO;
import servlet.InicializadorAplicacion;

/** Revoca exclusivamente el Bearer presentado por el cliente autenticado. */
@WebServlet(name = "ApiRevocarTokenServlet", urlPatterns = "/api/v1/auth/revoke")
public final class ApiRevocarTokenServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private TokenApiDAO registro;

    @Override
    public void init() throws ServletException {
        registro = (TokenApiDAO) getServletContext().getAttribute(InicializadorAplicacion.TOKEN_API_DAO);
        if (registro == null) throw new ServletException("Registro de tokens no disponible.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ApiTokens.TokenValido token = (ApiTokens.TokenValido) req.getAttribute("examlen.api.token");
        resp.setHeader("Cache-Control", "no-store");
        if (token == null) {
            ApioJson.error(resp, 401, "NO_AUTENTICADO", "Se requiere un token Bearer.");
            return;
        }
        try {
            registro.revocar(token.idToken, token.idUsuario);
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (RuntimeException e) {
            getServletContext().log("Fallo interno al revocar token API (" + e.getClass().getSimpleName() + ").");
            ApioJson.error(resp, 500, "ERROR_INTERNO", "No fue posible revocar el token.");
        }
    }
}
