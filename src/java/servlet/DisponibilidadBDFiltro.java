package servlet;

import modelo.VerificadorBaseDatos;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Si MySQL no responde, ninguna pagina funciona: se devuelve 503. Solo los CSS se sirven igual. */
public class DisponibilidadBDFiltro implements Filter {

    private VerificadorBaseDatos verificador;
    private Vistas vistas;

    @Override
    public void init(FilterConfig config) throws ServletException {
        verificador = (VerificadorBaseDatos) config.getServletContext().getAttribute(InicializadorAplicacion.VERIFICADOR_BD);
        vistas = (Vistas) config.getServletContext().getAttribute(InicializadorAplicacion.VISTAS);
        if (verificador == null || vistas == null) {
            throw new ServletException("La aplicación no se inicializó correctamente.");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (req.getServletPath().startsWith("/css/")) {
            chain.doFilter(request, response);
            return;
        }
        if (!verificador.estaDisponible()) {
            resp.setHeader("Retry-After", "30");
            resp.setHeader("Cache-Control", "no-store");
            vistas.escribir(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "error.html",
                    PaginasError.datos(req.getContextPath(), HttpServletResponse.SC_SERVICE_UNAVAILABLE));
            return;
        }
        chain.doFilter(request, response);
    }
}
