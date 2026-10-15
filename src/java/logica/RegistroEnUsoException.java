package logica;

/** No se puede eliminar porque otros registros lo referencian (clave foránea). */
public class RegistroEnUsoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegistroEnUsoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
