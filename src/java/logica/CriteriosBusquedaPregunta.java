package logica;

import java.util.ArrayList;
import java.util.List;

/**
 * Criterios combinables de RF-24 (texto y filtros por banco, tipo, nivel, dificultad,
 * competencia, tema, estado y origen). Los usan la gestión de bancos/preguntas (CU-15/CU-16)
 * y la selección de evaluación (CU-20), que solo cambia el estado a APROBADA.
 */
public class CriteriosBusquedaPregunta {

    public String texto;
    public Integer idBanco;
    public TipoPregunta tipo;
    public NivelIngles nivel;
    public Dificultad dificultad;
    public Competencia competencia;
    public String tema;
    public EstadoPregunta estado;
    public OrigenContenido origen;
    public final List<Integer> excluirIds = new ArrayList<>();

    public boolean hayFiltros() {
        return (texto != null && !texto.isBlank()) || idBanco != null || tipo != null || nivel != null
                || dificultad != null || competencia != null || (tema != null && !tema.isBlank())
                || estado != null || origen != null;
    }
}
