package logica;

import java.util.ArrayList;
import java.util.Date;

public class VersionEvaluacion {

    private int idVersionEvaluacion;
    public String codigoVersionEvaluacion;
    public Date fechaGeneracionVersionEvaluacion;
    public ArrayList<VersionPregunta> versionPreguntasVersionEvaluacion;

    public VersionEvaluacion() {
        versionPreguntasVersionEvaluacion = new ArrayList<>();
    }

    public int consultarIdVersionEvaluacion() {
        return idVersionEvaluacion;
    }

    public void establecerIdVersionEvaluacion(int idVersionEvaluacion) {
        this.idVersionEvaluacion = idVersionEvaluacion;
    }
}
