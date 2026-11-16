package servlet;

import logica.Usuario;
import logica.ValidacionException;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "PerfilServlet", urlPatterns = "/app/perfil")
public class PerfilServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Usuario u = usuarioActual(req);
        mostrar(req, resp, u, u.nombreUsuario, u.correoUsuario, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Usuario usuario = usuarioActual(req);
        String nombre = parametro(req, "nombre");
        String correo = parametro(req, "correo");
        List<String> errores = new ArrayList<>();
        Validador.validarNombre(nombre, errores);
        Validador.validarCorreo(Validador.normalizarCorreo(correo), errores);
        if (!errores.isEmpty()) {
            mostrar(req, resp, usuario, nombre, correo, errores);
            return;
        }
        try {
            usuarioDAO.actualizarUsuario(usuario, nombre, Validador.normalizarCorreo(correo));
            agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Datos actualizados.");
            redirigir(req, resp, "/app/perfil");
        } catch (ValidacionException e) {
            mostrar(req, resp, usuario, nombre, correo, e.consultarErrores());
        }
    }

    private void mostrar(HttpServletRequest req, HttpServletResponse resp, Usuario usuario,
                         String nombre, String correo, List<String> errores) throws IOException {
        Map<String, String> datos = new HashMap<>();
        datos.put("identificacion", usuario.identificacionUsuario);
        datos.put("tipo", Etiquetas.rol(usuario));
        datos.put("nombre", nombre);
        datos.put("correo", correo);
        renderizar(req, resp, "perfil.html", datos, errores);
    }
}
