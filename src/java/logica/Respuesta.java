package logica;

import java.util.Date;

public class Respuesta {

    private int idRespuesta;
    public String contenidoRespuesta;
    public Date fechaRespuesta;
    public double puntajeRespuesta;
    public String comentarioDocenteRespuesta;
    public VersionPregunta versionPreguntaRespuesta;
    public VersionOpcion versionOpcionRespuesta;

    public int getIdRespuesta() {
        return idRespuesta;
    }

    public void setIdRespuesta(int idRespuesta) {
        this.idRespuesta = idRespuesta;
    }
}
