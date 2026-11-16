package servicio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * POST JSON con la API estandar de Java (HttpURLConnection): conexion por operacion, sin hilos
 * internos ni estado estatico que amarre el classloader (RNF-12/25). Nunca incluye la clave ni
 * la URL completa en los mensajes.
 */
final class HttpJson {

    private static final int CONEXION_MS = 15000;
    private static final int LECTURA_MS = 60000;
    private static final int MAX_INTENTOS = 3;
    private static final long ESPERA_REINTENTO_MS = 1000L;

    private HttpJson() {
    }

    static String enviarJson(String url, Map<String, String> cabeceras, String cuerpo, String proveedor)
            throws GeneracionFallidaException {
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            HttpURLConnection conexion = null;
            try {
                conexion = (HttpURLConnection) new URL(url).openConnection();
                conexion.setConnectTimeout(CONEXION_MS);
                conexion.setReadTimeout(LECTURA_MS);
                conexion.setRequestMethod("POST");
                conexion.setDoOutput(true);
                conexion.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conexion.setRequestProperty("Accept", "application/json");
                if (cabeceras != null) {
                    for (Map.Entry<String, String> cabecera : cabeceras.entrySet()) {
                        conexion.setRequestProperty(cabecera.getKey(), cabecera.getValue());
                    }
                }
                byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
                conexion.setFixedLengthStreamingMode(datos.length);
                try (OutputStream salida = conexion.getOutputStream()) {
                    salida.write(datos);
                }
                int codigo = conexion.getResponseCode();
                InputStream flujo = codigo >= 400 ? conexion.getErrorStream() : conexion.getInputStream();
                String respuesta = leer(flujo);
                if (esTransitorio(codigo) && intento < MAX_INTENTOS) {
                    esperar(intento);
                    continue;
                }
                if (codigo == 429) {
                    throw new GeneracionFallidaException("El proveedor " + proveedor
                            + " alcanzó su límite de uso; inténtalo más tarde.", TipoErrorIA.CUOTA);
                }
                if (codigo == 401 || codigo == 403) {
                    throw new GeneracionFallidaException("El proveedor " + proveedor
                            + " rechazó la clave configurada (HTTP " + codigo + ").", TipoErrorIA.CONFIGURACION);
                }
                if (esTransitorio(codigo)) {
                    throw new GeneracionFallidaException("El proveedor " + proveedor
                            + " está temporalmente saturado (HTTP " + codigo
                            + "). Inténtalo de nuevo en unos minutos.", TipoErrorIA.HTTP);
                }
                if (codigo >= 400) {
                    throw new GeneracionFallidaException("El proveedor " + proveedor
                            + " respondió con estado " + codigo + ".", TipoErrorIA.HTTP);
                }
                return respuesta;
            } catch (SocketTimeoutException e) {
                throw new GeneracionFallidaException("El proveedor " + proveedor
                        + " no respondió a tiempo.", TipoErrorIA.TIEMPO_AGOTADO, e);
            } catch (IOException e) {
                throw new GeneracionFallidaException("No se pudo contactar al proveedor " + proveedor + ".",
                        TipoErrorIA.HTTP, e);
            } finally {
                if (conexion != null) {
                    conexion.disconnect();
                }
            }
        }
        throw new GeneracionFallidaException("El proveedor " + proveedor
                + " no respondió después de varios intentos.", TipoErrorIA.HTTP);
    }

    private static boolean esTransitorio(int codigo) {
        return codigo == 502 || codigo == 503 || codigo == 504;
    }

    private static void esperar(int intento) throws GeneracionFallidaException {
        try {
            Thread.sleep(ESPERA_REINTENTO_MS * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneracionFallidaException("La generación fue interrumpida.", TipoErrorIA.OTRO, e);
        }
    }

    private static String leer(InputStream flujo) throws IOException {
        if (flujo == null) {
            return "";
        }
        try (InputStream entrada = flujo) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
