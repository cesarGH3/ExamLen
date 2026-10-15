package logica;

import java.util.Date;

public class EventoSupervision {

    private int idEventoSupervision;
    public TipoEventoSupervision tipoEventoSupervision;
    public String descripcionEventoSupervision;
    public Date fechaHoraEventoSupervision;

    public int getIdEventoSupervision() {
        return idEventoSupervision;
    }

    public void setIdEventoSupervision(int idEventoSupervision) {
        this.idEventoSupervision = idEventoSupervision;
    }
}
