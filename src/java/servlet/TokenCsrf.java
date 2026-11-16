package servlet;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Token sincronizador por sesion contra CSRF. */
public final class TokenCsrf {

    public static final String PARAMETRO = "_csrf";
    private static final String ATRIBUTO = "examlen.csrf";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenCsrf() {
    }

    public static String obtenerOCrear(HttpSession sesion) {
        String token = (String) sesion.getAttribute(ATRIBUTO);
        if (token == null) {
            byte[] bytes = new byte[32];
            ALEATORIO.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            sesion.setAttribute(ATRIBUTO, token);
        }
        return token;
    }

    public static boolean esValido(HttpSession sesion, String recibido) {
        if (sesion == null || recibido == null) {
            return false;
        }
        String esperado = (String) sesion.getAttribute(ATRIBUTO);
        return esperado != null && MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }
}
