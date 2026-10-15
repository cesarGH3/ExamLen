package logica;

/**
 * El recurso no existe o no pertenece al usuario. Ambos casos responden 404
 * para no revelar si existen recursos de otros docentes.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
