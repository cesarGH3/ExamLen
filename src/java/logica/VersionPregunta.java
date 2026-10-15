package logica;

import java.util.ArrayList;

public class VersionPregunta {

    private int idVersionPregunta;
    public String enunciadoVersionPregunta;
    public String respuestaEsperadaVersionPregunta;
    public int ordenVersionPregunta;
    public double puntajeVersionPregunta;
    public Pregunta preguntaVersionPregunta;
    public ArrayList<VersionOpcion> versionOpcionesVersionPregunta;

    public VersionPregunta() {
        versionOpcionesVersionPregunta = new ArrayList<>();
    }

    public int getIdVersionPregunta() {
        return idVersionPregunta;
    }

    public void setIdVersionPregunta(int idVersionPregunta) {
        this.idVersionPregunta = idVersionPregunta;
    }
}
