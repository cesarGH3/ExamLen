package servlet;

import java.io.Serializable;

/** Mensaje que sobrevive a una redireccion (patron Post/Redirect/Get) y se muestra una sola vez. */
public final class MensajeFlash implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Tipo {
        EXITO("alerta-exito"), ERROR("alerta-error"), INFO("alerta-info");

        private final String claseCss;

        Tipo(String claseCss) {
            this.claseCss = claseCss;
        }

        public String getClaseCss() {
            return claseCss;
        }
    }

    private final Tipo tipo;
    private final String texto;

    public MensajeFlash(Tipo tipo, String texto) {
        this.tipo = tipo;
        this.texto = texto;
    }

    public Tipo getTipo() { return tipo; }
    public String getTexto() { return texto; }
}
