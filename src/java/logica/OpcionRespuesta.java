package logica;

public class OpcionRespuesta {

    private int idOpcionRespuesta;
    public String textoOpcionRespuesta;
    public boolean esCorrectaOpcionRespuesta;

    public int consultarIdOpcionRespuesta() {
        return idOpcionRespuesta;
    }

    public void establecerIdOpcionRespuesta(int idOpcionRespuesta) {
        this.idOpcionRespuesta = idOpcionRespuesta;
    }
}
