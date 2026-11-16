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

    public int consultarIdRespuesta() {
        return idRespuesta;
    }

    public void establecerIdRespuesta(int idRespuesta) {
        this.idRespuesta = idRespuesta;
    }
}
