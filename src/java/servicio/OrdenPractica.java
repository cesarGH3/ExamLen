package servicio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Semilla estable e impredecible para barajar las preguntas de un intento de practica.
 *
 * Se apoya en la fecha de inicio ya persistida en intento_practica y en la configuracion
 * existente (EXAMLEN_ORDER_SECRET, alias historico EXAMENL_ORDER_SECRET, o el secreto de API). No requiere
 * columnas ni migraciones. Con secreto configurado el orden es estable entre reinicios y no
 * se puede predecir desde el id; sin secreto se usa un hash determinista para que al menos
 * recargar o reanudar conserve el orden dentro del despliegue.
 */
public final class OrdenPractica {

    private OrdenPractica() {
    }

    public static long semilla(int idIntentoPractica, Date fechaInicio, int idEstudiante) {
        long instante = fechaInicio == null ? 0L : fechaInicio.getTime();
        String material = idIntentoPractica + ":" + instante + ":" + idEstudiante;
        byte[] resumen;
        if (Secretos.ordenConfigurado()) {
            resumen = hmac(material);
        } else {
            resumen = sha256(material);
        }
        long semilla = 0L;
        for (int i = 0; i < 8; i++) {
            semilla = (semilla << 8) | (resumen[i] & 0xFFL);
        }
        return semilla;
    }

    private static byte[] hmac(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Secretos.claveOrden(), "HmacSHA256"));
            return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible derivar el orden de practica.", e);
        }
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }
}
