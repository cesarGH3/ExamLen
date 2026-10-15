package servicio;

/** Clasificación de los fallos de generación con IA (mensaje y log según el caso). */
public enum TipoErrorIA {
    CONFIGURACION,
    TIEMPO_AGOTADO,
    CUOTA,
    HTTP,
    RESPUESTA,
    DOCUMENTO,
    PERSISTENCIA,
    OTRO
}
