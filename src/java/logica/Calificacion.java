package logica;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Escala academica de la calificacion del intento (RF-53): 0,0 a 5,0 con una cifra decimal.
 * La nota se calcula con BigDecimal (sin errores binarios) y redondeo HALF_UP determinista.
 */
public final class Calificacion {

    /** Nota maxima de la escala exigida por el ERS. */
    public static final BigDecimal MAXIMA = new BigDecimal("5.0");

    /** Cifras decimales de la escala (coincide con intento.calificacion DECIMAL(3,1)). */
    public static final int DECIMALES = 1;

    private Calificacion() {
    }

    /**
     * Convierte puntos obtenidos sobre el maximo de la version a la escala 0,0-5,0:
     * (obtenidos x 5) / maximos, con una cifra decimal y redondeo HALF_UP.
     */
    public static BigDecimal dePuntos(BigDecimal obtenidos, BigDecimal maximos) {
        return obtenidos.multiply(MAXIMA).divide(maximos, DECIMALES, RoundingMode.HALF_UP);
    }

    /** Formatea una nota de la escala con coma decimal: "2,2". */
    public static String formato(BigDecimal nota) {
        return nota.setScale(DECIMALES, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /** Formatea una nota como "2,2 / 5,0". */
    public static String formatoConMaximo(BigDecimal nota) {
        return formato(nota) + " / " + formato(MAXIMA);
    }
}
