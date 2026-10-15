package servlet;

import logica.PoliticaContrasena;
import logica.TokenRecuperacion;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF-11: fija la contrasena nueva con un token de un solo uso (CU-10, pasos 6-8).
 */
@WebServlet(name = "RestablecerContrasenaServlet", urlPatterns = "/restablecer")
public class RestablecerContrasenaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String MENSAJE_INVALIDO = "El enlace ya no es válido. Solicita uno nuevo.";
    private static final String MENSAJE_INACTIVA = "La cuenta está desactivada. Contacta al administrador.";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        mostrarFormulario(req, resp, parametro(req, "token"), null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String tokenPlano = parametro(req, "token");
        String nueva = req.getParameter("nueva");
        String confirmacion = req.getParameter("confirmacion");

        List<String> errores = new ArrayList<>();
        if (tokenPlano.isEmpty()) {
            errores.add(MENSAJE_INVALIDO);
        } else if (!PoliticaContrasena.cumple(nueva)) {
            errores.add(PoliticaContrasena.DESCRIPCION);
        } else if (!nueva.equals(confirmacion)) {
            errores.add("La confirmación no coincide con la contraseña.");
        }
        if (!errores.isEmpty()) {
            mostrarFormulario(req, resp, tokenPlano, errores);
            return;
        }

        TokenRecuperacion token = tokenValido(tokenPlano);
        if (token == null) {
            mostrarFormulario(req, resp, tokenPlano, null);
            return;
        }
        if (!tokenDAO.consumirTokenYRestablecer(TokenRecuperacion.hash(tokenPlano), nueva)) {
            mostrarFormulario(req, resp, tokenPlano, null);
            return;
        }
        SesionesActivas.cerrarSesionesDe(token.usuarioTokenRecuperacion.getIdUsuario());
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Contraseña actualizada. Ya puedes iniciar sesión.");
        redirigir(req, resp, "/login");
    }

    private void mostrarFormulario(HttpServletRequest req, HttpServletResponse resp, String tokenPlano,
                                   List<String> errores) throws IOException {
        TokenRecuperacion token = tokenValido(tokenPlano);
        if (token == null) {
            agregarMensaje(req, MensajeFlash.Tipo.ERROR, MENSAJE_INVALIDO);
            renderizar(req, resp, "restablecer.html", Map.of("formulario", ""), null);
            return;
        }
        if (!token.usuarioTokenRecuperacion.activoUsuario) {
            agregarMensaje(req, MensajeFlash.Tipo.ERROR, MENSAJE_INACTIVA);
            renderizar(req, resp, "restablecer.html", Map.of("formulario", ""), null);
            return;
        }
        Map<String, String> fragmento = new HashMap<>();
        fragmento.put("ctx", req.getContextPath());
        fragmento.put("csrf", csrf(req));
        fragmento.put("token", tokenPlano);
        Map<String, String> datos = new HashMap<>();
        datos.put("formulario", vistas.componer("_formulario-restablecer.html", fragmento));
        renderizar(req, resp, "restablecer.html", datos, errores);
    }

    /** Devuelve el token si existe y esta vigente (sin importar si la cuenta esta activa). */
    private TokenRecuperacion tokenValido(String tokenPlano) {
        if (tokenPlano == null || tokenPlano.isEmpty()) {
            return null;
        }
        TokenRecuperacion token = tokenDAO.buscarPorHash(TokenRecuperacion.hash(tokenPlano)).orElse(null);
        return token != null && token.esValidoTokenRecuperacion() ? token : null;
    }
}
