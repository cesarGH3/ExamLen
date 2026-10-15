package logica;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

/**
 * Fechas y horas del sistema, siempre en la zona America/Bogota (RNF-15). El instante lo pone el
 * servidor; el desplazamiento de la base se fuerza aparte a -05:00 (equivalente fijo de Bogotá).
 */
public final class Fechas {

    /** Zona horaria fija del sistema, tal como la fija la ERS (RNF-15). */
    public static final String ZONA = "America/Bogota";

    public static final String PATRON_FECHA = "dd/MM/yyyy";
    public static final String PATRON_FECHA_HORA = "dd/MM/yyyy HH:mm";

    private static final ZoneId ZONA_HORARIA = ZoneId.of(ZONA);

    private Fechas() {
    }

    /** Instante actual según el reloj del servidor (nunca el del cliente). */
    public static Date ahora() {
        return Date.from(ZonedDateTime.now(ZONA_HORARIA).toInstant());
    }

    /** Formatea en la zona del sistema; vacío para valores nulos. */
    public static String formato(String patron, Date valor) {
        if (valor == null) {
            return "";
        }
        SimpleDateFormat formato = new SimpleDateFormat(patron);
        formato.setTimeZone(TimeZone.getTimeZone(ZONA_HORARIA));
        return formato.format(valor);
    }

    /** Interpreta el texto como hora local de Bogotá; null si está vacío o no cumple el patrón. */
    public static Date parsear(String patron, String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        SimpleDateFormat formato = new SimpleDateFormat(patron);
        formato.setTimeZone(TimeZone.getTimeZone(ZONA_HORARIA));
        formato.setLenient(false);
        try {
            return formato.parse(texto);
        } catch (ParseException e) {
            return null;
        }
    }
}
