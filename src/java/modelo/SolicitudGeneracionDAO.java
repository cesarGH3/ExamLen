package modelo;

import logica.Fechas;

import logica.AccesoDatosException;
import logica.Docente;
import logica.EstadoSolicitud;
import logica.NivelIngles;
import logica.SolicitudGeneracion;
import logica.TipoGeneracion;
import logica.ValidacionException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class SolicitudGeneracionDAO extends DaoBase {

    private static final String COLUMNAS = "id_solicitud_generacion, id_docente, tipo, "
            + "instrucciones, cantidad, nivel, "
            + "modelo, estado, fecha, "
            + "mensaje_error, id_documento_fuente, id_banco_destino, "
            + "cantidad_recibida, cantidad_valida, cantidad_invalida, cantidad_no_devuelta, "
            + "proveedor, tema, competencia, id_evaluacion_referencia, id_preguntas_base";

    public SolicitudGeneracionDAO(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Inserta la solicitud PENDIENTE. Si el documento o el banco desaparecieron entre la
     * validación y el INSERT (carrera con "eliminar documento"), la FK falla y se traduce en un
     * error de validación controlado: nunca queda una solicitud apuntando a algo inexistente.
     */
    public SolicitudGeneracion crearSolicitudGeneracion(Docente docente, TipoGeneracion tipo, String instrucciones,
                                                        int cantidad, NivelIngles nivel, String modelo,
                                                        Integer idDocumentoFuente, Integer idBancoPreguntas)
            throws ValidacionException {
        return crearSolicitudGeneracion(docente, tipo, instrucciones, cantidad, nivel, modelo, idDocumentoFuente,
                idBancoPreguntas, null, null, null, null, null);
    }

    /** Variante con contexto de reintento (RF-29): proveedor, tema, competencia, referencia y bases. */
    public SolicitudGeneracion crearSolicitudGeneracion(Docente docente, TipoGeneracion tipo, String instrucciones,
                                                        int cantidad, NivelIngles nivel, String modelo,
                                                        Integer idDocumentoFuente, Integer idBancoPreguntas,
                                                        String proveedor, String tema, logica.Competencia competencia,
                                                        Integer idEvaluacionReferencia, List<Integer> idsPreguntasBase)
            throws ValidacionException {
        SolicitudGeneracion solicitud = new SolicitudGeneracion();
        solicitud.tipoSolicitudGeneracion = tipo;
        solicitud.instruccionesSolicitudGeneracion = instrucciones;
        solicitud.cantidadSolicitudGeneracion = cantidad;
        solicitud.nivelSolicitudGeneracion = nivel;
        solicitud.modeloSolicitudGeneracion = modelo;
        solicitud.estadoSolicitudGeneracion = EstadoSolicitud.PENDIENTE;
        solicitud.fechaSolicitudGeneracion = Fechas.ahora();
        String basesCsv = idsPreguntasBase == null || idsPreguntasBase.isEmpty() ? null
                : idsPreguntasBase.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        int id;
        try {
            id = insertar("INSERT INTO solicitud_generacion (id_docente, id_documento_fuente, id_banco_destino, "
                + "tipo, instrucciones, cantidad, "
                + "nivel, modelo, estado, proveedor, tema, competencia, id_evaluacion_referencia, id_preguntas_base) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?, ?, ?)", ps -> {
            ps.setInt(1, docente.getIdUsuario());
            if (idDocumentoFuente == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, idDocumentoFuente);
            }
            if (idBancoPreguntas == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, idBancoPreguntas);
            }
            ps.setString(4, tipo.name());
            ps.setString(5, instrucciones);
            ps.setInt(6, cantidad);
            if (nivel == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, nivel.name());
            }
            if (modelo == null || modelo.isBlank()) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, modelo);
            }
            if (proveedor == null || proveedor.isBlank()) {
                ps.setNull(9, Types.VARCHAR);
            } else {
                ps.setString(9, proveedor);
            }
            if (tema == null || tema.isBlank()) {
                ps.setNull(10, Types.VARCHAR);
            } else {
                ps.setString(10, tema);
            }
            if (competencia == null) {
                ps.setNull(11, Types.VARCHAR);
            } else {
                ps.setString(11, competencia.name());
            }
            if (idEvaluacionReferencia == null || idEvaluacionReferencia <= 0) {
                ps.setNull(12, Types.INTEGER);
            } else {
                ps.setInt(12, idEvaluacionReferencia);
            }
            if (basesCsv == null) {
                ps.setNull(13, Types.VARCHAR);
            } else {
                ps.setString(13, basesCsv);
            }
        });
        } catch (AccesoDatosException e) {
            if (esFkRota(e)) {
                throw new ValidacionException("El documento o el banco indicado ya no está disponible. "
                        + "Actualiza la página y vuelve a intentarlo.");
            }
            throw e;
        }
        solicitud.setIdSolicitudGeneracion(id);
        return solicitud;
    }

    /** CU-19 5b: guarda los conteos exactos de la ultima generacion (recibidas/validas/invalidas/no devueltas). */
    public void registrarConteos(int idSolicitudGeneracion, int recibidas, int validas, int invalidas,
                                 int noDevueltas) {
        actualizar("UPDATE solicitud_generacion SET cantidad_recibida = ?, cantidad_valida = ?, "
                + "cantidad_invalida = ?, cantidad_no_devuelta = ? WHERE id_solicitud_generacion = ?", ps -> {
            ps.setInt(1, recibidas);
            ps.setInt(2, validas);
            ps.setInt(3, invalidas);
            ps.setInt(4, noDevueltas);
            ps.setInt(5, idSolicitudGeneracion);
        });
    }

    /** true si la causa raíz es una violación de clave foránea (ERROR 1452 / SQLState 23000). */
    private static boolean esFkRota(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            if (actual instanceof SQLException) {
                SQLException sql = (SQLException) actual;
                if (sql.getErrorCode() == 1452 || "23000".equals(sql.getSQLState())) {
                    return true;
                }
            }
            actual = actual.getCause();
        }
        return false;
    }

    /**
     * Reclama la solicitud para el worker: PENDIENTE -> EN_PROCESO solo si sigue PENDIENTE.
     * Devuelve false si otro worker ya la tomo (evita ejecuciones duplicadas).
     */
    public boolean reclamarSolicitudGeneracion(int idSolicitudGeneracion) {
        return actualizar("UPDATE solicitud_generacion SET estado = 'EN_PROCESO' "
                + "WHERE id_solicitud_generacion = ? AND estado = 'PENDIENTE'",
                ps -> ps.setInt(1, idSolicitudGeneracion)) == 1;
    }

    /** EN_PROCESO -> COMPLETADA (solo si esta EN_PROCESO). */
    public void completarSolicitudGeneracion(int idSolicitudGeneracion) {
        actualizar("UPDATE solicitud_generacion SET estado = 'COMPLETADA' "
                + "WHERE id_solicitud_generacion = ? AND estado = 'EN_PROCESO'",
                ps -> ps.setInt(1, idSolicitudGeneracion));
    }

    /** PENDIENTE o EN_PROCESO -> FALLIDA; nunca toca COMPLETADA ni FALLIDA. */
    public void marcarFallidaSolicitudGeneracion(int idSolicitudGeneracion, String mensaje) {
        actualizar("UPDATE solicitud_generacion SET estado = 'FALLIDA', mensaje_error = ? "
                + "WHERE id_solicitud_generacion = ? AND estado IN ('PENDIENTE', 'EN_PROCESO')", ps -> {
            if (mensaje == null) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, mensaje);
            }
            ps.setInt(2, idSolicitudGeneracion);
        });
    }

    /**
     * Al arrancar la aplicacion: las solicitudes PENDIENTE o EN_PROCESO de un proceso anterior
     * no pueden reanudarse (el proveedor y los parametros derivados no estan todos persistidos),
     * asi que quedan FALLIDA con un mensaje controlado. Devuelve cuantas se marcaron.
     */
    public int marcarAbandonadasComoFallidas(String mensaje) {
        return actualizar("UPDATE solicitud_generacion SET estado = 'FALLIDA', mensaje_error = ? "
                + "WHERE estado IN ('PENDIENTE', 'EN_PROCESO')", ps -> {
            if (mensaje == null) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, mensaje);
            }
        });
    }

    public Optional<SolicitudGeneracion> buscarPorId(int idSolicitudGeneracion) {
        return consultarUno("SELECT " + COLUMNAS + " FROM solicitud_generacion WHERE id_solicitud_generacion = ?",
                ps -> ps.setInt(1, idSolicitudGeneracion), this::mapear);
    }

    /** Solo devuelve la solicitud si pertenece al docente indicado. */
    public Optional<SolicitudGeneracion> buscarPorIdDeDocente(int idSolicitudGeneracion, int idDocente) {
        return consultarUno("SELECT " + COLUMNAS + " FROM solicitud_generacion "
                + "WHERE id_solicitud_generacion = ? AND id_docente = ?", ps -> {
            ps.setInt(1, idSolicitudGeneracion);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    public List<SolicitudGeneracion> listarPorDocente(int idDocente) {
        return consultarLista("SELECT " + COLUMNAS + " FROM solicitud_generacion WHERE id_docente = ? "
                + "ORDER BY fecha DESC", ps -> ps.setInt(1, idDocente), this::mapear);
    }

    private SolicitudGeneracion mapear(ResultSet rs) throws SQLException {
        SolicitudGeneracion solicitud = new SolicitudGeneracion();
        solicitud.setIdSolicitudGeneracion(rs.getInt("id_solicitud_generacion"));
        solicitud.tipoSolicitudGeneracion = enumeracion(rs, "tipo", TipoGeneracion.class);
        solicitud.instruccionesSolicitudGeneracion = rs.getString("instrucciones");
        solicitud.cantidadSolicitudGeneracion = rs.getInt("cantidad");
        solicitud.nivelSolicitudGeneracion = enumeracion(rs, "nivel", logica.NivelIngles.class);
        solicitud.modeloSolicitudGeneracion = rs.getString("modelo");
        solicitud.estadoSolicitudGeneracion = enumeracion(rs, "estado", EstadoSolicitud.class);
        solicitud.fechaSolicitudGeneracion = Mapeos.fecha(rs, "fecha");
        solicitud.mensajeErrorSolicitudGeneracion = rs.getString("mensaje_error");
        int idDocumento = rs.getInt("id_documento_fuente");
        if (!rs.wasNull()) {
            logica.DocumentoFuente documento = new logica.DocumentoFuente();
            documento.setIdDocumentoFuente(idDocumento);
            solicitud.documentoFuenteSolicitudGeneracion = documento;
        }
        int idBanco = rs.getInt("id_banco_destino");
        if (!rs.wasNull()) {
            logica.BancoPreguntas banco = new logica.BancoPreguntas();
            banco.setIdBancoPreguntas(idBanco);
            solicitud.bancoPreguntaSolicitudGeneracion = banco;
        }
        solicitud.cantidadRecibidaSolicitudGeneracion = enteroOpcional(rs, "cantidad_recibida");
        solicitud.cantidadValidaSolicitudGeneracion = enteroOpcional(rs, "cantidad_valida");
        solicitud.cantidadInvalidaSolicitudGeneracion = enteroOpcional(rs, "cantidad_invalida");
        solicitud.cantidadNoDevueltaSolicitudGeneracion = enteroOpcional(rs, "cantidad_no_devuelta");
        solicitud.proveedorSolicitudGeneracion = rs.getString("proveedor");
        solicitud.temaSolicitudGeneracion = rs.getString("tema");
        solicitud.competenciaSolicitudGeneracion = rs.getString("competencia");
        solicitud.idEvaluacionReferenciaSolicitudGeneracion = enteroOpcional(rs, "id_evaluacion_referencia");
        solicitud.idPreguntasBaseSolicitudGeneracion = rs.getString("id_preguntas_base");
        return solicitud;
    }

    private static Integer enteroOpcional(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }
}
