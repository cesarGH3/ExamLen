package servicio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Logger;

/**
 * Habilitacion explicita de la creacion del primer administrador.
 *
 * Sin EXAMLEN_BOOTSTRAP_SECRET (o examlen.bootstrap.secret / bootstrap.secret en el archivo
 * externo de secretos) la ruta publica queda deshabilitada. La credencial se envia por POST,
 * nunca por URL ni cabecera, y no se registra en logs.
 */
public final class Instalacion {

    private static final Logger LOG = Logger.getLogger(Instalacion.class.getName());
    private static final int LONGITUD_MINIMA = 32;
    private static boolean aviso;

    private Instalacion() {
    }

    /** true solo si hay una credencial externa suficientemente larga. */
    public static boolean habilitada() {
        String secreto = secreto();
        if (secreto == null) {
            avisar();
            return false;
        }
        if (secreto.length() < LONGITUD_MINIMA) {
            avisar();
            return false;
        }
        return true;
    }

    /** Comparacion en tiempo constante; no revela si el valor se parece al correcto. */
    public static boolean verificar(String candidato) {
        String secreto = secreto();
        if (secreto == null || secreto.length() < LONGITUD_MINIMA
                || candidato == null || candidato.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                candidato.strip().getBytes(StandardCharsets.UTF_8),
                secreto.getBytes(StandardCharsets.UTF_8));
    }

    public static String nombreVariable() {
        return "EXAMLEN_BOOTSTRAP_SECRET";
    }

    private static String secreto() {
        return Secretos.bootstrap();
    }

    private static synchronized void avisar() {
        if (!aviso) {
            aviso = true;
            LOG.warning("Creacion del primer administrador deshabilitada. Define "
                    + nombreVariable() + " (minimo " + LONGITUD_MINIMA
                    + " caracteres) solo durante la instalacion inicial.");
        }
    }
}
