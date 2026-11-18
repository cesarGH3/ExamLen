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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Cabeceras defensivas: CSP estricta, anti-clickjacking, anti-sniffing.
 * RNF-03: HTTPS es obligatorio por defecto y el rechazo de HTTP ocurre antes de que cualquier
 * filtro procese credenciales. HSTS solo se envia sobre HTTPS real.
 *
 * Detras de un proxy inverso (examlen.behind.proxy=true / EXAMLEN_BEHIND_PROXY=true) solo se
 * acepta X-Forwarded-Proto=https si la conexion inmediata viene de una IP declarada en
 * examlen.trusted.proxies / EXAMLEN_TRUSTED_PROXIES. Sin allowlist la cabecera se ignora por
 * completo: un cliente directo no puede fabricar una conexion segura.
 *
 * Desarrollo local: solo de forma explicita con examlen.https.required=false o
 * EXAMLEN_HTTPS_REQUIRED=false. Nunca debe usarse en produccion.
 */
public class CabecerasSeguridadFiltro implements Filter {

    private static final Logger LOG = Logger.getLogger(CabecerasSeguridadFiltro.class.getName());

    private static final String CSP = "default-src 'none'; script-src 'self'; connect-src 'self'; "
            + "style-src 'self'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

    private static final Pattern IP_LITERAL = Pattern.compile(
            "^(?:\\d{1,3}(?:\\.\\d{1,3}){3}|[0-9A-Fa-f:]+)$");

    private boolean httpsRequerido;
    private boolean detrasDeProxy;
    private List<RangoIp> proxiesConfiables = Collections.emptyList();

    @Override
    public void init(FilterConfig config) {
        httpsRequerido = leerBooleano("EXAMLEN_HTTPS_REQUIRED", "examlen.https.required", true);
        detrasDeProxy = leerBooleano("EXAMLEN_BEHIND_PROXY", "examlen.behind.proxy", false);
        proxiesConfiables = leerProxies("EXAMLEN_TRUSTED_PROXIES", "examlen.trusted.proxies");

        if (!httpsRequerido) {
            LOG.warning("HTTPS obligatorio desactivado (examlen.https.required=false). "
                    + "Usese solo en desarrollo local.");
        }
        if (detrasDeProxy && proxiesConfiables.isEmpty()) {
            LOG.warning("examlen.behind.proxy=true sin examlen.trusted.proxies: "
                    + "X-Forwarded-Proto se ignorara para impedir suplantacion.");
        }
    }

    private static boolean leerBooleano(String variable, String propiedad, boolean porDefecto) {
        String valor = System.getenv(variable);
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty(propiedad);
        }
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        if ("true".equalsIgnoreCase(valor.trim()) || "1".equals(valor.trim())) return true;
        if ("false".equalsIgnoreCase(valor.trim()) || "0".equals(valor.trim())) return false;
        throw new IllegalArgumentException("Configuracion booleana invalida: " + propiedad);
    }

    private static List<RangoIp> leerProxies(String variable, String propiedad) {
        String valor = System.getenv(variable);
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty(propiedad);
        }
        if (valor == null || valor.isBlank()) {
            return Collections.emptyList();
        }
        List<RangoIp> rangos = new ArrayList<>();
        for (String parte : valor.split(",")) {
            String texto = parte.trim();
            if (texto.isEmpty()) {
                continue;
            }
            RangoIp rango = RangoIp.parsear(texto);
            if (rango == null) {
                LOG.warning("Entrada invalida en la allowlist de proxies: " + texto);
            } else {
                rangos.add(rango);
            }
        }
        return List.copyOf(rangos);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        boolean seguro = req.isSecure()
                || (detrasDeProxy && esProxyConfiable(req.getRemoteAddr())
                        && "https".equalsIgnoreCase(protoForwarded(req.getHeader("X-Forwarded-Proto"))));
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

    private boolean esProxyConfiable(String direccion) {
        if (direccion == null || proxiesConfiables.isEmpty()) {
            return false;
        }
        for (RangoIp rango : proxiesConfiables) {
            if (rango.contiene(direccion)) {
                return true;
            }
        }
        return false;
    }

    private static String protoForwarded(String cabecera) {
        if (cabecera == null) {
            return "";
        }
        int coma = cabecera.indexOf(',');
        return (coma >= 0 ? cabecera.substring(0, coma) : cabecera).trim();
    }

    private static final class RangoIp {
        private final byte[] red;
        private final int bits;

        private RangoIp(byte[] red, int bits) {
            this.red = red;
            this.bits = bits;
        }

        static RangoIp parsear(String texto) {
            String[] partes = texto.split("/", -1);
            if (partes.length > 2 || partes[0].isBlank() || !IP_LITERAL.matcher(partes[0]).matches()) {
                return null;
            }
            try {
                InetAddress direccion = InetAddress.getByName(partes[0]);
                byte[] bytes = direccion.getAddress();
                int maximo = bytes.length * 8;
                int prefijo = maximo;
                if (partes.length == 2) {
                    prefijo = Integer.parseInt(partes[1]);
                }
                if (prefijo < 0 || prefijo > maximo) {
                    return null;
                }
                return new RangoIp(bytes, prefijo);
            } catch (NumberFormatException | UnknownHostException e) {
                return null;
            }
        }

        boolean contiene(String direccion) {
            if (direccion == null || !IP_LITERAL.matcher(direccion).matches()) {
                return false;
            }
            try {
                byte[] candidata = InetAddress.getByName(direccion).getAddress();
                if (candidata.length != red.length) {
                    return false;
                }
                int bytesCompletos = bits / 8;
                int bitsRestantes = bits % 8;
                for (int i = 0; i < bytesCompletos; i++) {
                    if (candidata[i] != red[i]) {
                        return false;
                    }
                }
                if (bitsRestantes == 0) {
                    return true;
                }
                int mascara = 0xFF << (8 - bitsRestantes);
                return (candidata[bytesCompletos] & mascara) == (red[bytesCompletos] & mascara);
            } catch (UnknownHostException e) {
                return false;
            }
        }
    }
}
