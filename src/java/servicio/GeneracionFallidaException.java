package servicio;

/** Error controlado de la generación con IA: se muestra al docente y se registra en la solicitud. */
public class GeneracionFallidaException extends Exception {

    private static final long serialVersionUID = 1L;

    private final TipoErrorIA tipo;

    public GeneracionFallidaException(String mensaje) {
        this(mensaje, TipoErrorIA.OTRO, null);
    }

    public GeneracionFallidaException(String mensaje, Throwable causa) {
        this(mensaje, TipoErrorIA.OTRO, causa);
    }

    public GeneracionFallidaException(String mensaje, TipoErrorIA tipo) {
        this(mensaje, tipo, null);
    }

    public GeneracionFallidaException(String mensaje, TipoErrorIA tipo, Throwable causa) {
        super(mensaje, causa);
        this.tipo = tipo;
    }

    public TipoErrorIA getTipo() {
        return tipo;
    }
}
