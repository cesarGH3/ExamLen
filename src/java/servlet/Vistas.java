package servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor minimo de plantillas sobre archivos HTML en /html (web/html).
 *   {{clave}}   se reemplaza ESCAPADO (por defecto, seguro contra XSS)
 *   {{{clave}}} se reemplaza sin escapar; solo para fragmentos que el servidor ya construyo escapando.
 */
public final class Vistas {

    private static final String RUTA = "/html/";
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[a-z0-9_\\-]+\\.html$");
    private static final Pattern MARCADOR =
            Pattern.compile("\\{\\{\\{([A-Za-z0-9_]+)\\}\\}\\}|\\{\\{([A-Za-z0-9_]+)\\}\\}");

    private final ServletContext contexto;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public Vistas(ServletContext contexto) {
        this.contexto = contexto;
    }

    public String componer(String nombre, Map<String, String> datos) {
        String plantilla = cache.computeIfAbsent(nombre, this::cargar);
        Matcher m = MARCADOR.matcher(plantilla);
        StringBuilder sb = new StringBuilder(plantilla.length() + 256);
        while (m.find()) {
            String valor = m.group(1) != null
                    ? Objects.toString(datos.get(m.group(1)), "")
                    : Html.escapar(datos.get(m.group(2)));
            m.appendReplacement(sb, Matcher.quoteReplacement(valor));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public void escribir(HttpServletResponse resp, int estado, String nombre, Map<String, String> datos)
            throws IOException {
        String html = componer(nombre, datos);
        resp.setStatus(estado);
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().write(html);
    }

    private String cargar(String nombre) {
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            throw new IllegalArgumentException("Nombre de vista inválido: " + nombre);
        }
        try (InputStream in = contexto.getResourceAsStream(RUTA + nombre)) {
            if (in == null) {
                throw new IllegalStateException("Vista no encontrada: " + nombre);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
