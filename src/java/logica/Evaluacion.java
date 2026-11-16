package logica;

import java.util.ArrayList;

public class Evaluacion {

    private int idEvaluacion;
    public String tituloEvaluacion;
    public String descripcionEvaluacion;
    public int numeroPreguntasEvaluacion;
    public int numeroVersionesEvaluacion;
    public int duracionMinutosEvaluacion;
    public EstadoEvaluacion estadoEvaluacion;
    public ArrayList<Pregunta> preguntasEvaluacion;
    public ArrayList<VersionEvaluacion> versionesEvaluacion;

    public Evaluacion() {
        preguntasEvaluacion = new ArrayList<>();
        versionesEvaluacion = new ArrayList<>();
    }

    public int consultarIdEvaluacion() {
        return idEvaluacion;
    }

    public void establecerIdEvaluacion(int idEvaluacion) {
        this.idEvaluacion = idEvaluacion;
    }
}
