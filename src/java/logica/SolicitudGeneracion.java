package logica;

import java.util.ArrayList;
import java.util.Date;

public class SolicitudGeneracion {

    private int idSolicitudGeneracion;
    public TipoGeneracion tipoSolicitudGeneracion;
    public String instruccionesSolicitudGeneracion;
    public int cantidadSolicitudGeneracion;
    public NivelIngles nivelSolicitudGeneracion;
    public String modeloSolicitudGeneracion;
    public EstadoSolicitud estadoSolicitudGeneracion;
    public Date fechaSolicitudGeneracion;
    public String mensajeErrorSolicitudGeneracion;
    public DocumentoFuente documentoFuenteSolicitudGeneracion;
    public BancoPreguntas bancoPreguntaSolicitudGeneracion;
    /** Conteos exactos de la generacion IA (CU-19 5b): null en solicitudes historicas sin dato. */
    public Integer cantidadRecibidaSolicitudGeneracion;
    public Integer cantidadValidaSolicitudGeneracion;
    public Integer cantidadInvalidaSolicitudGeneracion;
    public Integer cantidadNoDevueltaSolicitudGeneracion;
    /** Contexto para el reintento con los mismos datos (RF-29). */
    public String proveedorSolicitudGeneracion;
    public String temaSolicitudGeneracion;
    public String competenciaSolicitudGeneracion;
    public Integer idEvaluacionReferenciaSolicitudGeneracion;
    public String idPreguntasBaseSolicitudGeneracion;
    public ArrayList<Pregunta> preguntasSolicitudGeneracion;
    public ArrayList<MaterialPractica> materialesPracticaSolicitudGeneracion;

    public SolicitudGeneracion() {
        preguntasSolicitudGeneracion = new ArrayList<>();
        materialesPracticaSolicitudGeneracion = new ArrayList<>();
    }

    public int consultarIdSolicitudGeneracion() {
        return idSolicitudGeneracion;
    }

    public void establecerIdSolicitudGeneracion(int idSolicitudGeneracion) {
        this.idSolicitudGeneracion = idSolicitudGeneracion;
    }
}
