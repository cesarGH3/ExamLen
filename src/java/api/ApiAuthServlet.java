package api;

import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * GET /api/v1/auth/validar: el filtro Basic ya autentico al usuario; aqui solo se confirma
 * la identidad con los datos minimos (sin hash, sin contrasena, sin tokens).
 */
@WebServlet(name = "ApiAuthServlet", urlPatterns = "/api/v1/auth/validar")
public class ApiAuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject usuario = new JsonObject();
        usuario.addProperty("id", ApioJson.idUsuario(req));
        usuario.addProperty("nombre", ApioJson.nombre(req));
        usuario.addProperty("correo", ApioJson.correo(req));
        usuario.addProperty("rol", ApioJson.rol(req));

        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("autenticado", true);
        cuerpo.add("usuario", usuario);
        ApioJson.ok(resp, cuerpo);
    }
}
