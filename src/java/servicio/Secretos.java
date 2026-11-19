package servicio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Secretos de aplicacion para tokens de API, orden de practicas, bootstrap y cifrado de
 * claves de IA.
 *
 * Orden de resolucion: variable de entorno canonica EXAMLEN_* -> alias historico EXAMENL_* ->
 * propiedad del sistema examlen.* -> archivo externo {@code examlen-secrets.properties}
 * ({@code {catalina.base}/conf/} o la ruta de EXAMLEN_SECRETS_FILE / examlen.secrets.file).
 *
 * En produccion (examlen.env=produccion, valor por defecto) no se permiten claves efimeras:
 * si falta el secreto de API la aplicacion no arranca. En desarrollo se puede habilitar
 * expresamente con EXAMLEN_ALLOW_EPHEMERAL_SECRETS=true; esa clave solo vale para el proceso.
 */
public final class Secretos {

    private static final Logger LOG = Logger.getLogger(Secretos.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] CLAVE_EFIMERA = new byte[32];
    private static final String ARCHIVO_SECRETOS = "examlen-secrets.properties";
    private static final String PROPIEDAD_ARCHIVO = "examlen.secrets.file";
    private static final String VARIABLE_ARCHIVO = "EXAMLEN_SECRETS_FILE";
    private static final String PROPIEDAD_ENTORNO = "examlen.env";
    private static final String VARIABLE_ENTORNO = "EXAMLEN_ENV";
    private static final String PROPIEDAD_EFIMERAS = "examlen.allowEphemeralSecrets";
    private static final String VARIABLE_EFIMERAS = "EXAMLEN_ALLOW_EPHEMERAL_SECRETS";
    private static final String ENTORNO_DESARROLLO = "desarrollo";
    private static final int LONGITUD_MINIMA = 32;

    private static final Properties ARCHIVO = cargarArchivo();
    private static final Set<String> AVISOS_LEGADOS = ConcurrentHashMap.newKeySet();
    private static boolean avisoEfimera;

    static {
        RANDOM.nextBytes(CLAVE_EFIMERA);
    }

    private Secretos() {
    }

    /** Clave HMAC para los tokens Bearer de la API. */
    public static byte[] claveApi() {
        return derivar("api.token.secret", "EXAMLEN_API_TOKEN_SECRET", "EXAMENL_API_TOKEN_SECRET");
    }

    /** Clave para derivar el orden estable de las preguntas de practica. */
    public static byte[] claveOrden() {
        byte[] propia = derivarSiExiste("order.secret", "EXAMLEN_ORDER_SECRET", "EXAMENL_ORDER_SECRET");
        if (propia != null) {
            return propia;
        }
        byte[] api = derivarSiExiste("api.token.secret", "EXAMLEN_API_TOKEN_SECRET", "EXAMENL_API_TOKEN_SECRET");
        if (api != null) {
            return api;
        }
        return efimeraSiPermitida("EXAMLEN_ORDER_SECRET", "examlen.order.secret");
    }

    /** true si hay una clave estable configurada para el orden. */
    public static boolean ordenConfigurado() {
        return texto("order.secret", "EXAMLEN_ORDER_SECRET", "EXAMENL_ORDER_SECRET") != null
                || texto("api.token.secret", "EXAMLEN_API_TOKEN_SECRET", "EXAMENL_API_TOKEN_SECRET") != null;
    }

    /** Credencial externa para habilitar la creacion del primer administrador. */
    public static String bootstrap() {
        return texto("bootstrap.secret", "EXAMLEN_BOOTSTRAP_SECRET", "EXAMENL_BOOTSTRAP_SECRET");
    }

    /** Clave AES-GCM para las claves de IA personales de los docentes. */
    public static String cifradoIA() {
        return texto("ia.crypto.key", "EXAMLEN_IA_CRYPTO_KEY", "EXAMENL_IA_CRYPTO_KEY");
    }

    /**
     * Valida al arrancar que produccion tenga secreto persistente. No imprime valores.
     * El secreto de orden puede reutilizar el de API; el de cifrado de IA es opcional
     * (si falta, la funcion de claves personales queda deshabilitada).
     */
    public static void validarConfiguracion() {
        String api = texto("api.token.secret", "EXAMLEN_API_TOKEN_SECRET", "EXAMENL_API_TOKEN_SECRET");
        if (api == null) {
            if (produccion() || !permiteEfimeras()) {
                throw new IllegalStateException("Falta EXAMLEN_API_TOKEN_SECRET (o examlen.api.token.secret / "
                        + "api.token.secret en " + ubicacionArchivo() + "). En produccion no se usa una clave efimera.");
            }
            avisarEfimera("EXAMLEN_API_TOKEN_SECRET", "examlen.api.token.secret");
        } else if (api.length() < LONGITUD_MINIMA) {
            if (produccion()) {
                throw new IllegalStateException("EXAMLEN_API_TOKEN_SECRET debe tener al menos "
                        + LONGITUD_MINIMA + " caracteres.");
            }
            LOG.warning("EXAMLEN_API_TOKEN_SECRET es corto; usa al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        String bootstrap = bootstrap();
        if (bootstrap != null && bootstrap.length() < LONGITUD_MINIMA) {
            LOG.warning("EXAMLEN_BOOTSTRAP_SECRET es corto; la creacion del primer administrador queda deshabilitada.");
        }
    }

    /** true si el entorno configurado es produccion (valor por defecto). */
    public static boolean produccion() {
        return !ENTORNO_DESARROLLO.equalsIgnoreCase(entorno());
    }

    public static String entorno() {
        String valor = primero(System.getProperty(PROPIEDAD_ENTORNO), System.getenv(VARIABLE_ENTORNO));
        return valor == null ? "produccion" : valor;
    }

    public static boolean permiteEfimeras() {
        String valor = primero(System.getProperty(PROPIEDAD_EFIMERAS), System.getenv(VARIABLE_EFIMERAS));
        return valor != null && Boolean.parseBoolean(valor.trim());
    }

    public static String ubicacionArchivo() {
        return rutaArchivo().toString();
    }

    static String texto(String clave, String variable, String legado) {
        String valor = leerVariable(variable, false);
        if (valor == null) {
            valor = leerVariable(legado, true);
        }
        if (valor == null) {
            valor = System.getProperty("examlen." + clave);
        }
        if (valor == null) {
            valor = ARCHIVO.getProperty(clave);
        }
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static byte[] derivar(String clave, String variable, String legado) {
        String valor = texto(clave, variable, legado);
        if (valor == null) {
            return efimeraSiPermitida(variable, "examlen." + clave);
        }
        return hash(valor);
    }

    private static byte[] derivarSiExiste(String clave, String variable, String legado) {
        String valor = texto(clave, variable, legado);
        return valor == null ? null : hash(valor);
    }

    private static byte[] hash(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }

    private static byte[] efimeraSiPermitida(String variable, String propiedad) {
        if (produccion() || !permiteEfimeras()) {
            throw new IllegalStateException("Falta " + variable + " (o " + propiedad
                    + "). En produccion no se usa una clave efimera.");
        }
        avisarEfimera(variable, propiedad);
        return CLAVE_EFIMERA.clone();
    }

    private static String leerVariable(String variable, boolean legado) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        String valor = System.getenv(variable);
        if (legado && valor != null && !valor.isBlank() && AVISOS_LEGADOS.add(variable)) {
            LOG.warning("Se acepta " + variable + " por compatibilidad; migra a la variable EXAMLEN_* equivalente.");
        }
        return valor;
    }

    private static synchronized void avisarEfimera(String variable, String propiedad) {
        if (!avisoEfimera) {
            avisoEfimera = true;
            LOG.warning("Sin " + variable + " ni " + propiedad + ": se usa una clave aleatoria por proceso. "
                    + "Solo para desarrollo; configura una clave persistente para produccion.");
        }
    }

    private static Properties cargarArchivo() {
        Properties propiedades = new Properties();
        Path archivo = rutaArchivo();
        if (Files.isReadable(archivo)) {
            try (InputStream entrada = Files.newInputStream(archivo)) {
                propiedades.load(entrada);
            } catch (IOException e) {
                LOG.warning("No se pudo leer el archivo de secretos " + archivo + ": " + e.getClass().getSimpleName());
            }
        }
        return propiedades;
    }

    private static Path rutaArchivo() {
        String ruta = primero(System.getProperty(PROPIEDAD_ARCHIVO), System.getenv(VARIABLE_ARCHIVO));
        if (ruta != null && !ruta.isBlank()) {
            return Path.of(ruta.trim());
        }
        String base = System.getProperty("catalina.base");
        if (base != null && !base.isBlank()) {
            return Path.of(base, "conf", ARCHIVO_SECRETOS);
        }
        return Path.of(System.getProperty("user.dir", "."), ARCHIVO_SECRETOS);
    }

    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }
}
