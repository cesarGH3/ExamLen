package logica;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Validaciones de formato del lado del servidor. Replican las reglas del HTML, pero son las que mandan. */
public final class Validador {

    public static final int MAX_CONTRASENA = 128;
    private static final int MAX_CORREO = 254;

    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}][\\p{L} '\\-]{1,99}$");
    private static final Pattern IDENTIFICACION = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern CORREO = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,24}$");
    private static final Pattern PERIODO = Pattern.compile("^\\d{4}-[1-4]$");
    private static final Pattern NOMBRE_GRUPO = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} \\-]{0,49}$");

    private Validador() {
    }

    public static String limpiar(String valor) {
        return valor == null ? "" : valor.strip();
    }

    /** Para textos de varias líneas: normaliza saltos de línea y quita caracteres de control. */
    public static String limpiarTexto(String valor) {
        String texto = limpiar(valor).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String normalizarCorreo(String valor) {
        return limpiar(valor).toLowerCase(Locale.ROOT);
    }

    public static boolean esCorreoValido(String correo) {
        return correo != null && correo.length() <= MAX_CORREO && CORREO.matcher(correo).matches();
    }

    public static void validarNombre(String nombre, List<String> errores) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            errores.add("El nombre debe tener entre 2 y 100 caracteres y solo puede contener letras, espacios, apóstrofos o guiones.");
        }
    }

    public static void validarIdentificacion(String identificacion, List<String> errores) {
        if (identificacion == null || !IDENTIFICACION.matcher(identificacion).matches()) {
            errores.add("La identificación debe tener entre 5 y 20 letras o números, sin espacios.");
        }
    }

    public static void validarCorreo(String correo, List<String> errores) {
        if (!esCorreoValido(correo)) {
            errores.add("Escribe un correo electrónico válido.");
        }
    }

    public static void validarLongitud(String valor, int minimo, int maximo, String mensaje, List<String> errores) {
        int longitud = valor == null ? 0 : valor.codePointCount(0, valor.length());
        if (longitud < minimo || longitud > maximo) {
            errores.add(mensaje);
        }
    }

    public static void validarRequerido(Object valor, String mensaje, List<String> errores) {
        if (valor == null) {
            errores.add(mensaje);
        }
    }

    public static void validarPeriodo(String periodo, List<String> errores) {
        if (periodo == null || !PERIODO.matcher(periodo).matches()) {
            errores.add("El periodo debe tener el formato año-número, por ejemplo 2027-1.");
        }
    }

    public static void validarNombreGrupo(String nombre, List<String> errores) {
        if (nombre == null || !NOMBRE_GRUPO.matcher(nombre).matches()) {
            errores.add("El nombre del grupo debe tener entre 1 y 50 letras, números, espacios o guiones.");
        }
    }

    /** Devuelve el entero positivo o -1 si el valor no es válido. */
    public static int parsearId(String valor) {
        try {
            int numero = Integer.parseInt(limpiar(valor));
            return numero > 0 ? numero : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
