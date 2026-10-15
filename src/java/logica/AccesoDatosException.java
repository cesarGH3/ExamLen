package logica;

/** Falla tecnica al acceder a la base de datos (conexion caida, error SQL). */
public class AccesoDatosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccesoDatosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
