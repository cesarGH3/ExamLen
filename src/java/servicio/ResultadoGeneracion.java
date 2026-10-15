package servicio;

import java.util.ArrayList;
import java.util.List;

/** Resultado del parser: ítems válidos, descartados por formato y errores breves (CU-19 5b). */
public final class ResultadoGeneracion {

    private final List<PreguntaGenerada> items;
    private final int descartados;
    private final List<String> errores;

    public ResultadoGeneracion(List<PreguntaGenerada> items, int descartados, List<String> errores) {
        this.items = List.copyOf(items);
        this.descartados = descartados;
        this.errores = List.copyOf(errores);
    }

    public List<PreguntaGenerada> items() {
        return items;
    }

    public int descartados() {
        return descartados;
    }

    public List<String> errores() {
        return errores;
    }

    public int recibidos() {
        return items.size() + descartados;
    }

    public ResultadoGeneracion sumar(ResultadoGeneracion otro) {
        List<PreguntaGenerada> todos = new ArrayList<>(items);
        todos.addAll(otro.items);
        List<String> motivos = new ArrayList<>(errores);
        motivos.addAll(otro.errores);
        return new ResultadoGeneracion(todos, descartados + otro.descartados, motivos);
    }
}
