package logica;

public class VersionOpcion {

    private int idVersionOpcion;
    public String textoVersionOpcion;
    public boolean esCorrectaVersionOpcion;
    public int ordenVersionOpcion;

    public int consultarIdVersionOpcion() {
        return idVersionOpcion;
    }

    public void establecerIdVersionOpcion(int idVersionOpcion) {
        this.idVersionOpcion = idVersionOpcion;
    }
}
