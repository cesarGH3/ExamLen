package logica;

/** El usuario no tiene permiso para la operacion solicitada. */
public class OperacionNoPermitidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
