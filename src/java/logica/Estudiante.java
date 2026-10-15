package logica;

import java.util.ArrayList;
import java.util.Date;

public class Estudiante extends Usuario {

    public String nivelInglesEstudiante;
    /** Fecha de aceptación de la política de tratamiento de datos (RNF-13); null en cuentas anteriores. */
    public Date aceptacionTratamientoDatos;
    public ArrayList<Inscripcion> inscripcionesEstudiante;
    public ArrayList<IntentoPractica> intentosPracticaEstudiante;

    public Estudiante() {
        inscripcionesEstudiante = new ArrayList<>();
        intentosPracticaEstudiante = new ArrayList<>();
    }
}
