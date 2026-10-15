package logica;

import java.util.ArrayList;
import java.util.Date;

public class EvaluacionVirtual {

    private int idEvaluacionVirtual;
    public Date fechaInicioEvaluacionVirtual;
    public Date fechaFinEvaluacionVirtual;
    public boolean supervisionActivaEvaluacionVirtual;
    public EstadoEvaluacionVirtual estadoEvaluacionVirtual;
    public Evaluacion evaluacionEvaluacionVirtual;
    public ArrayList<Intento> intentosEvaluacionVirtual;
    public ArrayList<VersionEvaluacion> versionesEvaluacionEvaluacionVirtual;

    public EvaluacionVirtual() {
        intentosEvaluacionVirtual = new ArrayList<>();
        versionesEvaluacionEvaluacionVirtual = new ArrayList<>();
    }

    public int getIdEvaluacionVirtual() {
        return idEvaluacionVirtual;
    }

    public void setIdEvaluacionVirtual(int idEvaluacionVirtual) {
        this.idEvaluacionVirtual = idEvaluacionVirtual;
    }
}
