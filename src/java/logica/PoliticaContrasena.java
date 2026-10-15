package logica;

/**
 * Política de contraseñas alineada al ERS (RF-02): mínimo 8 caracteres con al menos una letra
 * y al menos un número. No exige mayúscula, minúscula ni símbolo: una contraseña que cumpla
 * exactamente el ERS es válida. La regla "distinta de la actual" es contextual y se verifica
 * en el cambio de contraseña autenticado (no aplica a registro ni a restablecimientos).
 */
public final class PoliticaContrasena {

    public static final int MINIMA = 8;
    public static final int MAXIMA = Validador.MAX_CONTRASENA;

    public static final String DESCRIPCION = "La contraseña debe tener entre " + MINIMA + " y " + MAXIMA
            + " caracteres e incluir al menos una letra y al menos un número.";

    private PoliticaContrasena() {
    }

    public static boolean cumple(String contrasena) {
        if (contrasena == null || contrasena.length() < MINIMA || contrasena.length() > MAXIMA) {
            return false;
        }
        boolean letra = false;
        boolean numero = false;
        for (int i = 0; i < contrasena.length(); i++) {
            char c = contrasena.charAt(i);
            if (Character.isLetter(c)) {
                letra = true;
            } else if (c >= '0' && c <= '9') {
                numero = true;
            }
        }
        return letra && numero;
    }
}
