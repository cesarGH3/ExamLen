package logica;

/** Inicio de sesion rechazado. El mensaje es generico a proposito. */
public class AutenticacionFallidaException extends Exception {

    private static final long serialVersionUID = 1L;

    public AutenticacionFallidaException(String mensaje) {
        super(mensaje);
    }
}
