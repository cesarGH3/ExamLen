package servicio;

import java.util.ArrayList;
import java.util.List;

/** Pregunta devuelta por la IA antes de guardarse en el banco. */
public class PreguntaGenerada {

    public String enunciado;
    public String tipo;
    public String dificultad;
    public String competencia;
    public String tema;
    public String respuestaEsperada;
    public List<OpcionGenerada> opciones = new ArrayList<>();

    /** Opción devuelta por la IA. */
    public static class OpcionGenerada {
        public String texto;
        public boolean correcta;
    }
}
