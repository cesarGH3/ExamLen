package logica;

import java.util.ArrayList;

public class BancoPreguntas {

    private int idBancoPreguntas;
    public String nombreBancoPreguntas;
    public String descripcionBancoPreguntas;
    public ArrayList<Pregunta> preguntasBancoPregunta;

    public BancoPreguntas() {
        preguntasBancoPregunta = new ArrayList<>();
    }

    public int getIdBancoPreguntas() {
        return idBancoPreguntas;
    }

    public void setIdBancoPreguntas(int idBancoPreguntas) {
        this.idBancoPreguntas = idBancoPreguntas;
    }
}
