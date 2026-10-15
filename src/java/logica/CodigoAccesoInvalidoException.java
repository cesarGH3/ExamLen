package logica;

/** Código de acceso inexistente o mal formado. Se distingue para limitar intentos. */
public class CodigoAccesoInvalidoException extends ValidacionException {

    private static final long serialVersionUID = 1L;

    public CodigoAccesoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
