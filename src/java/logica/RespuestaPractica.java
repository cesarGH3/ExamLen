package logica;

public class RespuestaPractica {

    private int idRespuestaPractica;
    public String contenidoRespuestaPractica;
    /** null cuando la pregunta no se califica automáticamente (abiertas y sin respuesta esperada). */
    public Boolean esCorrectaRespuestaPractica;
    public String retroalimentacionRespuestaPractica;
    public String respuestaCorrectaRespuestaPractica;
    public Pregunta preguntaRespuestaPractica;
    public OpcionRespuesta opcionRespuestaRespuestaPractica;

    public int consultarIdRespuestaPractica() {
        return idRespuestaPractica;
    }

    public void establecerIdRespuestaPractica(int idRespuestaPractica) {
        this.idRespuestaPractica = idRespuestaPractica;
    }
}
