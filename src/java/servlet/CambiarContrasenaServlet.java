package servlet;

import logica.Docente;
import logica.PoliticaContrasena;
import logica.Usuario;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet(name = "CambiarContrasenaServlet", urlPatterns = "/app/perfil/contrasena")
public class CambiarContrasenaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        mostrar(req, resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Usuario usuario = usuarioActual(req);
        String actual = req.getParameter("actual");
        String nueva = req.getParameter("nueva");
        String confirmacion = req.getParameter("confirmacion");

        List<String> errores = new ArrayList<>();
        if (!PoliticaContrasena.cumple(nueva)) {
            errores.add(PoliticaContrasena.DESCRIPCION);
        } else if (!nueva.equals(confirmacion)) {
            errores.add("La confirmación no coincide con la contraseña.");
        } else if (nueva.equals(actual)) {
            errores.add("La nueva contraseña debe ser diferente de la actual.");
        }
        if (!errores.isEmpty()) {
            mostrar(req, resp, errores);
            return;
        }
        if (!usuarioDAO.cambiarContrasenaUsuario(usuario, actual, nueva)) {
            mostrar(req, resp, List.of("La contraseña actual no es correcta."));
            return;
        }
        req.changeSessionId();
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Contraseña actualizada.");
        redirigir(req, resp, "/app/inicio");
    }

    private void mostrar(HttpServletRequest req, HttpServletResponse resp, List<String> errores) throws IOException {
        Usuario usuario = usuarioActual(req);
        String aviso = usuario instanceof Docente && ((Docente) usuario).debeCambiarContrasenaDocente
                ? Html.alerta(vistas, "alerta-info", "Estás usando una contraseña temporal. Crea una propia para continuar.")
                : "";
        renderizar(req, resp, "cambiar-contrasena.html", Map.of("aviso", aviso), errores);
    }
}
