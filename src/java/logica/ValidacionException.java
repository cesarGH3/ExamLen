package logica;

import java.util.List;

/** Datos de entrada que no cumplen las reglas de negocio. Sus mensajes son aptos para el usuario. */
public class ValidacionException extends Exception {

    private static final long serialVersionUID = 1L;

    private final List<String> errores;

    public ValidacionException(List<String> errores) {
        super("Datos inválidos");
        this.errores = List.copyOf(errores);
    }

    public ValidacionException(String error) {
        this(List.of(error));
    }

    public List<String> getErrores() {
        return errores;
    }
}
