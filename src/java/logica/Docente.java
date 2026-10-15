package logica;

import java.util.ArrayList;

public class Docente extends Usuario {

    public boolean debeCambiarContrasenaDocente;
    public ArrayList<Curso> cursosDocente;
    public ArrayList<BancoPreguntas> bancosPreguntasDocente;
    public ArrayList<Evaluacion> evaluacionesDocente;
    public ArrayList<MaterialPractica> materialesPracticaDocente;
    public ArrayList<DocumentoFuente> documentosFuenteDocente;
    public ArrayList<SolicitudGeneracion> solicitudesGeneracionDocente;

    public Docente() {
        cursosDocente = new ArrayList<>();
        bancosPreguntasDocente = new ArrayList<>();
        evaluacionesDocente = new ArrayList<>();
        materialesPracticaDocente = new ArrayList<>();
        documentosFuenteDocente = new ArrayList<>();
        solicitudesGeneracionDocente = new ArrayList<>();
    }
}
