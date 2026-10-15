package logica;

import java.util.ArrayList;

public class MaterialPractica {

    private int idMaterialPractica;
    public String tituloMaterialPractica;
    public String descripcionMaterialPractica;
    public NivelIngles nivelMaterialPractica;
    public EstadoMaterial estadoMaterialPractica;
    public OrigenContenido origenMaterialPractica;
    public ArrayList<Pregunta> preguntasMaterialPractica;
    public ArrayList<IntentoPractica> intentosPracticaMaterialPractica;

    public MaterialPractica() {
        preguntasMaterialPractica = new ArrayList<>();
        intentosPracticaMaterialPractica = new ArrayList<>();
    }

    public int getIdMaterialPractica() {
        return idMaterialPractica;
    }

    public void setIdMaterialPractica(int idMaterialPractica) {
        this.idMaterialPractica = idMaterialPractica;
    }
}
