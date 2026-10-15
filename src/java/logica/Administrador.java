package logica;

import java.util.ArrayList;
import java.util.Date;

public class Administrador extends Usuario {

    public Date fechaNacimientoAdministrador;
    public ArrayList<Docente> docentesAdministrador;

    public Administrador() {
        docentesAdministrador = new ArrayList<>();
    }
}
