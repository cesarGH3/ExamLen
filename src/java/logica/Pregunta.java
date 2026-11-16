package logica;

import java.util.ArrayList;

public class Pregunta {

    private int idPregunta;
    public int idDocente;
    public Integer idBancoPreguntas;
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

    public int consultarIdPregunta() {
        return idPregunta;
    }

    public void establecerIdPregunta(int idPregunta) {
        this.idPregunta = idPregunta;
    }
}
