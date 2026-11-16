package logica;

import java.util.ArrayList;

public class Curso {

    private int idCurso;
    public String nombreCurso;
    public String descripcionCurso;
    public NivelIngles nivelCurso;
    public boolean activoCurso;
    public Docente docenteCurso;
    public ArrayList<Grupo> gruposCurso;
    public ArrayList<MaterialPractica> materialesPracticaCurso;

    public Curso() {
        gruposCurso = new ArrayList<>();
        materialesPracticaCurso = new ArrayList<>();
    }

    public int consultarIdCurso() {
        return idCurso;
    }

    public void establecerIdCurso(int idCurso) {
        this.idCurso = idCurso;
    }
}
