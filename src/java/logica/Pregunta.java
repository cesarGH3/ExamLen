package logica;

import java.util.ArrayList;

public class Pregunta {

    private int idPregunta;
    public String enunciadoPregunta;
    public TipoPregunta tipoPregunta;
    public NivelIngles nivelPregunta;
    public Dificultad dificultadPregunta;
    public Competencia competenciaPregunta;
    public String temaPregunta;
    public String respuestaEsperadaPregunta;
    public EstadoPregunta estadoPregunta;
    public OrigenContenido origenPregunta;
    public ArrayList<OpcionRespuesta> opcionesPregunta;

    public Pregunta() {
        opcionesPregunta = new ArrayList<>();
    }

    public int getIdPregunta() {
        return idPregunta;
    }

    public void setIdPregunta(int idPregunta) {
        this.idPregunta = idPregunta;
    }
}
