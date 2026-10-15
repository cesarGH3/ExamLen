package servicio;

import logica.Competencia;
import logica.NivelIngles;
import logica.TipoGeneracion;
import java.util.List;

/**
 * Comando inmutable con todo lo necesario para procesar una generación FUERA del hilo HTTP.
 * No guarda referencias a HttpServletRequest, HttpServletResponse, HttpSession, Part ni streams:
 * solo valores planos persistidos o resueltos antes de responder.
 */
public final class TrabajoGeneracionIA {

    private final int idSolicitud;
    private final int idDocente;
    private final TipoGeneracion tipo;
    private final String instrucciones;
    private final int cantidad;
    private final NivelIngles nivel;
    private final String proveedor;
    private final String modelo;
    private final Integer idDocumentoFuente;
    private final Integer idBancoDestino;
    private final String temaElegido;
    private final Competencia competenciaElegida;
    private final List<Integer> idsPreguntasBase;
    private final Integer idEvaluacionReferencia;

    public TrabajoGeneracionIA(int idSolicitud, int idDocente, TipoGeneracion tipo, String instrucciones,
                               int cantidad, NivelIngles nivel, String proveedor, String modelo,
                               Integer idDocumentoFuente, Integer idBancoDestino,
                               String temaElegido, Competencia competenciaElegida,
                               List<Integer> idsPreguntasBase, Integer idEvaluacionReferencia) {
        this.idSolicitud = idSolicitud;
        this.idDocente = idDocente;
        this.tipo = tipo;
        this.instrucciones = instrucciones;
        this.cantidad = cantidad;
        this.nivel = nivel;
        this.proveedor = proveedor;
        this.modelo = modelo;
        this.idDocumentoFuente = idDocumentoFuente;
        this.idBancoDestino = idBancoDestino;
        this.temaElegido = temaElegido;
        this.competenciaElegida = competenciaElegida;
        this.idsPreguntasBase = idsPreguntasBase == null ? List.of() : List.copyOf(idsPreguntasBase);
        this.idEvaluacionReferencia = idEvaluacionReferencia;
    }

    public int idSolicitud() {
        return idSolicitud;
    }

    public int idDocente() {
        return idDocente;
    }

    public TipoGeneracion tipo() {
        return tipo;
    }

    public String instrucciones() {
        return instrucciones;
    }

    public int cantidad() {
        return cantidad;
    }

    public NivelIngles nivel() {
        return nivel;
    }

    public String proveedor() {
        return proveedor;
    }

    public String modelo() {
        return modelo;
    }

    public Integer idDocumentoFuente() {
        return idDocumentoFuente;
    }

    public Integer idBancoDestino() {
        return idBancoDestino;
    }

    public String temaElegido() {
        return temaElegido;
    }

    public Competencia competenciaElegida() {
        return competenciaElegida;
    }

    public List<Integer> idsPreguntasBase() {
        return idsPreguntasBase;
    }

    public Integer idEvaluacionReferencia() {
        return idEvaluacionReferencia;
    }
}
