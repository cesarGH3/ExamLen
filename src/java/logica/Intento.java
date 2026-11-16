package logica;

import java.util.ArrayList;
import java.util.Date;

public class Intento {

    private int idIntento;
    private int numeroIntento;
    public Date fechaInicioIntento;
    public Date fechaFinIntento;
    public double calificacionIntento;
    public EstadoIntento estadoIntento;
    public VersionEvaluacion versionEvaluacionIntento;
    public ArrayList<Respuesta> respuestasIntento;
    public ArrayList<EventoSupervision> eventosSupervisionIntento;

    public Intento() {
        numeroIntento = 1;
        respuestasIntento = new ArrayList<>();
        eventosSupervisionIntento = new ArrayList<>();
    }

    public int consultarIdIntento() {
        return idIntento;
    }

    public void establecerIdIntento(int idIntento) {
        this.idIntento = idIntento;
    }

    public int consultarNumeroIntento() {
        return numeroIntento;
    }

    public void establecerNumeroIntento(int numeroIntento) {
        this.numeroIntento = numeroIntento;
    }
}
