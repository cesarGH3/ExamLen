package logica;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Códigos de acceso de 8 caracteres sin símbolos confusos (0/O, 1/I/L).
 * 31^8 combinaciones (unas 850 mil millones) hacen inviable adivinarlos, y además se limitan los intentos.
 */
public final class GeneradorCodigoAcceso {

    public static final int LONGITUD = 8;
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Pattern FORMATO = Pattern.compile("^[" + ALFABETO + "]{" + LONGITUD + "}$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public String generar() {
        return generarAleatorio();
    }

    /** Disponible también para que Grupo.generarCodigoAcceso() no dependa de una instancia. */
    public static String generarAleatorio() {
        StringBuilder sb = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Acepta el código con minúsculas, espacios o guiones, como suele copiarlo un estudiante. */
    public static String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        return codigo.replace(" ", "").replace("-", "").strip().toUpperCase(Locale.ROOT);
    }

    public static boolean tieneFormatoValido(String codigoNormalizado) {
        return FORMATO.matcher(codigoNormalizado).matches();
    }
}
