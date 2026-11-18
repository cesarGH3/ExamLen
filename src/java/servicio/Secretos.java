package servicio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * Secretos de aplicacion para tokens de API y orden de practicas.
 *
 * Orden de resolucion: variable de entorno -> propiedad del sistema. Si no hay valor se usa
 * una clave aleatoria por proceso (los tokens dejan de valer al reiniciar y el orden de una
 * practica cambia al reiniciar). En produccion conviene configurar al menos una de las dos
 * propiedades para que ambos comportamientos sean estables entre reinicios.
 */
public final class Secretos {

    private static final Logger LOG = Logger.getLogger(Secretos.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] CLAVE_EFIMERA = new byte[32];
    private static boolean avisoEmitido;

    static {
        RANDOM.nextBytes(CLAVE_EFIMERA);
    }

    private Secretos() {
    }

    /** Clave para firmar tokens Bearer de vida corta. */
    public static byte[] claveApi() {
        byte[] configurada = leer("EXAMLEN_API_TOKEN_SECRET", "examlen.api.token.secret");
        if (configurada != null) {
            return configurada;
        }
        avisarSinSecreto("EXAMENL_API_TOKEN_SECRET", "examlen.api.token.secret");
        return CLAVE_EFIMERA.clone();
    }

    /**
     * Clave para derivar el orden estable de las preguntas de practica. Se puede fijar una
     * clave propia; si no existe se reutiliza la de la API. Si tampoco existe, se usa la clave
     * efimera del proceso.
     */
    public static byte[] claveOrden() {
        byte[] configurada = leer("EXAMENL_ORDER_SECRET", "examlen.order.secret");
        if (configurada != null) {
            return configurada;
        }
        byte[] api = leer("EXAMENL_API_TOKEN_SECRET", "examlen.api.token.secret");
        if (api != null) {
            return api;
        }
        return CLAVE_EFIMERA.clone();
    }

    /** true si hay una clave configurada para el orden (estable entre reinicios). */
    public static boolean ordenConfigurado() {
        return leer("EXAMENL_ORDER_SECRET", "examlen.order.secret") != null
                || leer("EXAMENL_API_TOKEN_SECRET", "examlen.api.token.secret") != null;
    }

    private static byte[] leer(String variable, String propiedad) {
        String valor = System.getenv(variable);
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty(propiedad);
        }
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(valor.trim().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }

    private static synchronized void avisarSinSecreto(String variable, String propiedad) {
        if (!avisoEmitido) {
            avisoEmitido = true;
            LOG.warning("Sin " + variable + " ni " + propiedad + ": se usa una clave aleatoria "
                    + "por proceso. Configura una clave fija para produccion.");
        }
    }
}
