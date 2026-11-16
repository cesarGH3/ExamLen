package servicio;

import java.util.ArrayList;
import java.util.List;

/** Resultado del parser: ítems válidos, descartados por formato y errores breves (CU-19 5b). */
public final class ResultadoGeneracion {

    private final List<PreguntaGenerada> elementos;
    private final int descartados;
    private final List<String> errores;

    public ResultadoGeneracion(List<PreguntaGenerada> elementos, int descartados, List<String> errores) {
        this.elementos = List.copyOf(elementos);
        this.descartados = descartados;
        this.errores = List.copyOf(errores);
    }

    public List<PreguntaGenerada> elementos() {
        return elementos;
    }

    public int descartados() {
        return descartados;
    }

    public List<String> errores() {
        return errores;
    }

    public int recibidos() {
        return elementos.size() + descartados;
    }

    public ResultadoGeneracion sumar(ResultadoGeneracion otro) {
        List<PreguntaGenerada> todos = new ArrayList<>(elementos);
        todos.addAll(otro.elementos);
        List<String> motivos = new ArrayList<>(errores);
        motivos.addAll(otro.errores);
        return new ResultadoGeneracion(todos, descartados + otro.descartados, motivos);
    }
}
