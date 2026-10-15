package servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/** Toda peticion que modifica datos debe traer el token CSRF de la sesion. */
public class CsrfFiltro implements Filter {

    private static final Set<String> METODOS_PROTEGIDOS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        if (METODOS_PROTEGIDOS.contains(req.getMethod())) {
            String token;
            try {
                token = req.getParameter(TokenCsrf.PARAMETRO);
            } catch (IllegalStateException e) {
                // Cuerpo multipart mayor que maxRequestSize: rechazo controlado, sin 500.
                ((HttpServletResponse) response).sendError(413,
                        "El archivo supera el tamaño máximo permitido de 10 MB.");
                return;
            }
            if (!TokenCsrf.esValido(req.getSession(false), token)) {
                ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
