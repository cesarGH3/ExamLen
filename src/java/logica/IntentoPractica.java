package logica;

import java.util.ArrayList;
import java.util.Date;

public class IntentoPractica {

    private int idIntentoPractica;
    public Date fechaInicioIntentoPractica;
    public Date fechaFinIntentoPractica;
    /** Porcentaje de aciertos sobre las respuestas calificadas; null si ninguna lo está. */
    public Double calificacionIntentoPractica;
    public String tituloMaterialPractica;
    public ArrayList<RespuestaPractica> respuestasPracticaIntentoPractica;

    public IntentoPractica() {
        respuestasPracticaIntentoPractica = new ArrayList<>();
    }

    public int getIdIntentoPractica() {
        return idIntentoPractica;
    }

    public void setIdIntentoPractica(int idIntentoPractica) {
        this.idIntentoPractica = idIntentoPractica;
    }
}
