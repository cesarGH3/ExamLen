package logica;

import java.util.ArrayList;
import java.util.Date;

public class EvaluacionVirtual {

    private int idEvaluacionVirtual;
    private int intentosPermitidos;
    private boolean exigirRespuestasCompletas;
    public Date fechaInicioEvaluacionVirtual;
    public Date fechaFinEvaluacionVirtual;
    public boolean supervisionActivaEvaluacionVirtual;
    public EstadoEvaluacionVirtual estadoEvaluacionVirtual;
    public Evaluacion evaluacionEvaluacionVirtual;
    public ArrayList<Intento> intentosEvaluacionVirtual;
    public ArrayList<VersionEvaluacion> versionesEvaluacionEvaluacionVirtual;

    public EvaluacionVirtual() {
        intentosPermitidos = 1;
        exigirRespuestasCompletas = false;
        intentosEvaluacionVirtual = new ArrayList<>();
        versionesEvaluacionEvaluacionVirtual = new ArrayList<>();
    }

    public int consultarIdEvaluacionVirtual() {
        return idEvaluacionVirtual;
    }

    public void establecerIdEvaluacionVirtual(int idEvaluacionVirtual) {
        this.idEvaluacionVirtual = idEvaluacionVirtual;
    }

    public int consultarIntentosPermitidos() {
        return intentosPermitidos;
    }

    public void establecerIntentosPermitidos(int intentosPermitidos) {
        this.intentosPermitidos = intentosPermitidos;
    }

    public boolean consultarExigirRespuestasCompletas() {
        return exigirRespuestasCompletas;
    }

    public void establecerExigirRespuestasCompletas(boolean exigirRespuestasCompletas) {
        this.exigirRespuestasCompletas = exigirRespuestasCompletas;
    }
}
