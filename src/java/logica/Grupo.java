package logica;

import java.util.ArrayList;

public class Grupo {

    private int idGrupo;
    public String nombreGrupo;
    public String periodoGrupo;
    public String codigoAccesoGrupo;
    public EstadoGrupo estadoGrupo;
    public Curso cursoGrupo;
    public ArrayList<Inscripcion> inscripcionesGrupo;
    public ArrayList<EvaluacionVirtual> evaluacionesVirtualesGrupo;

    public Grupo() {
        inscripcionesGrupo = new ArrayList<>();
        evaluacionesVirtualesGrupo = new ArrayList<>();
    }

    public int consultarIdGrupo() {
        return idGrupo;
    }

    public void establecerIdGrupo(int idGrupo) {
        this.idGrupo = idGrupo;
    }
}
