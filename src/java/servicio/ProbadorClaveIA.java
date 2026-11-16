package servicio;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Valida una clave candidata contra el endpoint de modelos del proveedor. */
public final class ProbadorClaveIA {

    private static final int CONEXION_MS = 10000;
    private static final int LECTURA_MS = 20000;

    private ProbadorClaveIA() {
    }

    public static void probar(String proveedor, String clave) throws GeneracionFallidaException {
        String codigo = proveedor == null ? "" : proveedor.trim().toUpperCase();
        if (clave == null || clave.isBlank()) {
            throw new GeneracionFallidaException("Escribe una clave de IA.", TipoErrorIA.CONFIGURACION);
        }
        if ("GEMINI".equals(codigo)) {
            probarGemini(clave.strip());
        } else if ("GROQ".equals(codigo)) {
            probarGroq(clave.strip());
        } else {
            throw new GeneracionFallidaException("Proveedor de IA no soportado.", TipoErrorIA.CONFIGURACION);
        }
    }

    private static void probarGemini(String clave) throws GeneracionFallidaException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key="
                + URLEncoder.encode(clave, StandardCharsets.UTF_8);
        consultar(url, null, "Gemini");
    }

    private static void probarGroq(String clave) throws GeneracionFallidaException {
        consultar("https://api.groq.com/openai/v1/models", "Bearer " + clave, "Groq");
    }

    private static void consultar(String url, String authorization, String proveedor)
            throws GeneracionFallidaException {
        HttpURLConnection conexion = null;
        try {
            conexion = (HttpURLConnection) new URL(url).openConnection();
            conexion.setConnectTimeout(CONEXION_MS);
            conexion.setReadTimeout(LECTURA_MS);
            conexion.setRequestMethod("GET");
            conexion.setRequestProperty("Accept", "application/json");
            if (authorization != null) {
                conexion.setRequestProperty("Authorization", authorization);
            }
            int codigo = conexion.getResponseCode();
            if (codigo >= 200 && codigo < 300) {
                try (InputStream in = conexion.getInputStream()) {
                    in.readAllBytes();
                }
                return;
            }
            if (codigo == 401 || codigo == 403) {
                throw new GeneracionFallidaException("El proveedor " + proveedor
                        + " rechazó la clave (HTTP " + codigo + ").", TipoErrorIA.CONFIGURACION);
            }
            if (codigo == 429) {
                throw new GeneracionFallidaException("El proveedor " + proveedor
                        + " alcanzó su límite temporal de uso (HTTP 429).", TipoErrorIA.CUOTA);
            }
            throw new GeneracionFallidaException("El proveedor " + proveedor
                    + " respondió con HTTP " + codigo + ".", TipoErrorIA.HTTP);
        } catch (GeneracionFallidaException e) {
            throw e;
        } catch (IOException e) {
            throw new GeneracionFallidaException("No se pudo contactar al proveedor " + proveedor + ".",
                    TipoErrorIA.HTTP, e);
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }
}
