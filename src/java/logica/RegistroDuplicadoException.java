package logica;

/** Se violo una restriccion unica de la base de datos. */
public class RegistroDuplicadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegistroDuplicadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
