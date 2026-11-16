package logica;

import java.util.ArrayList;
import java.util.Date;

public class Inscripcion {

    private int idInscripcion;
    public Date fechaInscripcion;
    public EstadoInscripcion estadoInscripcion;
    public Estudiante estudianteInscripcion;
    public Grupo grupoInscripcion;
    public ArrayList<Intento> intentosInscripcion;

    public Inscripcion() {
        intentosInscripcion = new ArrayList<>();
    }

    public int consultarIdInscripcion() {
        return idInscripcion;
    }

    public void establecerIdInscripcion(int idInscripcion) {
        this.idInscripcion = idInscripcion;
    }
}
