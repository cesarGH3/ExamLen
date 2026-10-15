package logica;

import java.util.ArrayList;
import java.util.Date;

public class Intento {

    private int idIntento;
    public Date fechaInicioIntento;
    public Date fechaFinIntento;
    public double calificacionIntento;
    public EstadoIntento estadoIntento;
    public VersionEvaluacion versionEvaluacionIntento;
    public ArrayList<Respuesta> respuestasIntento;
    public ArrayList<EventoSupervision> eventosSupervisionIntento;

    public Intento() {
        respuestasIntento = new ArrayList<>();
        eventosSupervisionIntento = new ArrayList<>();
    }

    public int getIdIntento() {
        return idIntento;
    }

    public void setIdIntento(int idIntento) {
        this.idIntento = idIntento;
    }
}
