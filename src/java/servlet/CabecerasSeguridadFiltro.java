package servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Cabeceras defensivas: CSP estricta, anti-clickjacking, anti-sniffing.
 * RNF-03: con EXAMLEN_HTTPS_REQUIRED=true (despliegue) se rechazan las conexiones HTTP sin
 * redirigir; HSTS solo se envía sobre HTTPS real. Detrás de un proxy configurado
 * (EXAMLEN_BEHIND_PROXY=true) se acepta X-Forwarded-Proto=https como confiable; nunca se confía
 * en esa cabecera si el proxy no está declarado.
 */
public class CabecerasSeguridadFiltro implements Filter {

    private static final String CSP = "default-src 'none'; script-src 'self'; connect-src 'self'; "
            + "style-src 'self'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

    private boolean httpsRequerido;
    private boolean detrasDeProxy;

    @Override
    public void init(FilterConfig config) {
        httpsRequerido = leerBooleano("EXAMLEN_HTTPS_REQUIRED", "examlen.https.required", false);
        detrasDeProxy = leerBooleano("EXAMLEN_BEHIND_PROXY", "examlen.behind.proxy", false);
    }

    private static boolean leerBooleano(String variable, String propiedad, boolean porDefecto) {
        String valor = System.getenv(variable);
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty(propiedad);
        }
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        return "true".equalsIgnoreCase(valor.trim()) || "1".equals(valor.trim());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        boolean seguro = req.isSecure()
                || (detrasDeProxy && "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto")));
        if (httpsRequerido && !seguro) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "ExamLen requiere HTTPS.");
            return;
        }
        resp.setHeader("Content-Security-Policy", CSP);
        if (seguro) {
            resp.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Referrer-Policy", "no-referrer");
        resp.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        chain.doFilter(request, response);
    }
}
