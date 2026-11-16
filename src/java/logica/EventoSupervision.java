package logica;

import java.util.Date;

public class EventoSupervision {

    private int idEventoSupervision;
    public TipoEventoSupervision tipoEventoSupervision;
    public String descripcionEventoSupervision;
    public Date fechaHoraEventoSupervision;

    public int consultarIdEventoSupervision() {
        return idEventoSupervision;
    }

    public void establecerIdEventoSupervision(int idEventoSupervision) {
        this.idEventoSupervision = idEventoSupervision;
    }
}
