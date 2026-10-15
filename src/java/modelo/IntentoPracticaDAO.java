package modelo;

import logica.Fechas;

import logica.Estudiante;
import logica.IntentoPractica;
import logica.MaterialPractica;
import logica.RespuestaPractica;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class IntentoPracticaDAO extends DaoBase {

    private static final String COLUMNAS = "id_intento_practica, id_material_practica, id_estudiante, "
            + "fecha_inicio, fecha_fin, calificacion";

    public IntentoPracticaDAO(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Registra el intento y todas sus respuestas en una sola transacción (RF-60, RF-61).
     * La calificación es el porcentaje de aciertos sobre las respuestas calificadas; queda null
     * cuando ninguna se califica automáticamente.
     */
    public IntentoPractica registrarIntentoPractica(Estudiante estudiante, MaterialPractica material,
            List<RespuestaPractica> respuestas) {
        Double calificacion = resultadoPorcentaje(respuestas);
        IntentoPractica intento = new IntentoPractica();
        enTransaccion(c -> {
            int idIntento = insertarCon(c, "INSERT INTO intento_practica (id_material_practica, "
                    + "id_estudiante, fecha_fin, calificacion) VALUES (?, ?, NOW(), ?)", ps -> {
                ps.setInt(1, material.getIdMaterialPractica());
                ps.setInt(2, estudiante.getIdUsuario());
                if (calificacion == null) {
                    ps.setNull(3, Types.DOUBLE);
                } else {
                    ps.setDouble(3, calificacion);
                }
            });
            intento.setIdIntentoPractica(idIntento);
            for (RespuestaPractica respuesta : respuestas) {
                int idRespuesta = insertarCon(c, "INSERT INTO respuesta_practica (id_intento_practica, "
                        + "id_pregunta, id_opcion_respuesta, contenido, es_correcta, retroalimentacion) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", ps -> {
                    ps.setInt(1, idIntento);
                    ps.setInt(2, respuesta.preguntaRespuestaPractica.getIdPregunta());
                    if (respuesta.opcionRespuestaRespuestaPractica == null) {
                        ps.setNull(3, Types.INTEGER);
                    } else {
                        ps.setInt(3, respuesta.opcionRespuestaRespuestaPractica.getIdOpcionRespuesta());
                    }
                    ps.setString(4, respuesta.contenidoRespuestaPractica);
                    ps.setObject(5, respuesta.esCorrectaRespuestaPractica, Types.BOOLEAN);
                    ps.setString(6, respuesta.retroalimentacionRespuestaPractica);
                });
                respuesta.setIdRespuestaPractica(idRespuesta);
            }
            return null;
        });
        Date ahora = Fechas.ahora();
        intento.fechaInicioIntentoPractica = ahora;
        intento.fechaFinIntentoPractica = ahora;
        intento.calificacionIntentoPractica = calificacion;
        intento.tituloMaterialPractica = material.tituloMaterialPractica;
        material.intentosPracticaMaterialPractica.add(intento);
        estudiante.intentosPracticaEstudiante.add(intento);
        return intento;
    }

    /** Porcentaje de aciertos sobre las respuestas calificadas; null si ninguna lo está. */
    private static Double resultadoPorcentaje(List<RespuestaPractica> respuestas) {
        int calificadas = 0;
        int correctas = 0;
        for (RespuestaPractica respuesta : respuestas) {
            if (respuesta.esCorrectaRespuestaPractica != null) {
                calificadas++;
                if (respuesta.esCorrectaRespuestaPractica) {
                    correctas++;
                }
            }
        }
        if (calificadas == 0) {
            return null;
        }
        return Math.round((correctas * 1000.0) / calificadas) / 10.0;
    }

    /** Intentos de práctica de un estudiante (para no eliminar cuentas con actividad, CU-07 5a). */
    /** RF-60/19: abre un intento de practica en curso (sin calificacion) para responder por pasos. */
    public IntentoPractica crearIntentoEnCurso(Estudiante estudiante, MaterialPractica material) {
        IntentoPractica intento = new IntentoPractica();
        Date ahora = Fechas.ahora();
        int id = insertar("INSERT INTO intento_practica (id_material_practica, id_estudiante, fecha_inicio) "
                + "VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, material.getIdMaterialPractica());
            ps.setInt(2, estudiante.getIdUsuario());
            ps.setTimestamp(3, new java.sql.Timestamp(ahora.getTime()));
        });
        intento.setIdIntentoPractica(id);
        intento.fechaInicioIntentoPractica = ahora;
        intento.tituloMaterialPractica = material.tituloMaterialPractica;
        return intento;
    }

    /** Persiste (o reemplaza) una respuesta del intento sin esperar al final del material. */
    public void guardarRespuesta(int idIntentoPractica, RespuestaPractica respuesta) {
        enTransaccion(c -> {
            actualizarCon(c, "DELETE FROM respuesta_practica WHERE id_intento_practica = ? AND id_pregunta = ?",
                    ps -> {
                        ps.setInt(1, idIntentoPractica);
                        ps.setInt(2, respuesta.preguntaRespuestaPractica.getIdPregunta());
                    });
            int id = insertarCon(c, "INSERT INTO respuesta_practica (id_intento_practica, "
                    + "id_pregunta, id_opcion_respuesta, contenido, es_correcta, retroalimentacion) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", ps -> {
                ps.setInt(1, idIntentoPractica);
                ps.setInt(2, respuesta.preguntaRespuestaPractica.getIdPregunta());
                if (respuesta.opcionRespuestaRespuestaPractica == null) {
                    ps.setNull(3, Types.INTEGER);
                } else {
                    ps.setInt(3, respuesta.opcionRespuestaRespuestaPractica.getIdOpcionRespuesta());
                }
                ps.setString(4, respuesta.contenidoRespuestaPractica);
                ps.setObject(5, respuesta.esCorrectaRespuestaPractica, Types.BOOLEAN);
                ps.setString(6, respuesta.retroalimentacionRespuestaPractica);
            });
            respuesta.setIdRespuestaPractica(id);
            return null;
        });
    }

    /** Cierra el intento calculando el porcentaje de aciertos sobre las respuestas calificadas. */
    public Double finalizarIntento(int idIntentoPractica) {
        return enTransaccion(c -> {
            List<Boolean> resultados = consultarListaCon(c, "SELECT es_correcta FROM respuesta_practica "
                    + "WHERE id_intento_practica = ?", ps -> ps.setInt(1, idIntentoPractica),
                    rs -> rs.getObject(1) == null ? null : rs.getBoolean(1));
            int calificadas = 0;
            int correctas = 0;
            for (Boolean resultado : resultados) {
                if (resultado != null) {
                    calificadas++;
                    if (resultado) {
                        correctas++;
                    }
                }
            }
            Double calificacion = calificadas == 0 ? null
                    : Math.round((correctas * 1000.0) / calificadas) / 10.0;
            actualizarCon(c, "UPDATE intento_practica SET calificacion = ?, fecha_fin = NOW() "
                    + "WHERE id_intento_practica = ?", ps -> {
                if (calificacion == null) {
                    ps.setNull(1, Types.DOUBLE);
                } else {
                    ps.setDouble(1, calificacion);
                }
                ps.setInt(2, idIntentoPractica);
            });
            return calificacion;
        });
    }

    /** Intentos de practica de un estudiante (para no eliminar cuentas con actividad, CU-07 5a). */
    public int contarPorEstudiante(int idEstudiante) {
        return contar("SELECT COUNT(*) FROM intento_practica WHERE id_estudiante = ?",
                ps -> ps.setInt(1, idEstudiante));
    }

    public Optional<IntentoPractica> buscarPorId(int idIntentoPractica) {
        return consultarUno("SELECT " + COLUMNAS + " FROM intento_practica WHERE id_intento_practica = ?",
                ps -> ps.setInt(1, idIntentoPractica), this::mapear);
    }

    /** Solo devuelve el intento si pertenece al estudiante indicado. */
    public Optional<IntentoPractica> buscarPorIdDeEstudiante(int idIntentoPractica, int idEstudiante) {
        return consultarUno("SELECT " + COLUMNAS + " FROM intento_practica WHERE id_intento_practica = ? "
                + "AND id_estudiante = ?", ps -> {
            ps.setInt(1, idIntentoPractica);
            ps.setInt(2, idEstudiante);
        }, this::mapear);
    }

    public int idMaterialDeIntento(int idIntentoPractica) {
        return consultarUno("SELECT id_material_practica FROM intento_practica WHERE id_intento_practica = ?",
                ps -> ps.setInt(1, idIntentoPractica), rs -> rs.getInt(1)).orElse(-1);
    }

    public String tituloMaterialDeIntento(int idIntentoPractica) {
        return consultarUno("SELECT mp.titulo FROM intento_practica ip "
                + "JOIN material_practica mp ON mp.id_material_practica = ip.id_material_practica "
                + "WHERE ip.id_intento_practica = ?", ps -> ps.setInt(1, idIntentoPractica), rs -> rs.getString(1))
                .orElse("Práctica");
    }

    public int contarPorMaterial(int idMaterialPractica) {
        return contar("SELECT COUNT(*) FROM intento_practica WHERE id_material_practica = ?",
                ps -> ps.setInt(1, idMaterialPractica));
    }

    /** Historial del estudiante con el título del material, del más reciente al más antiguo (RF-62). */
    public List<IntentoPractica> listarPorEstudiante(int idEstudiante) {
        return consultarLista("SELECT ip.id_intento_practica, ip.fecha_inicio, ip.fecha_fin, ip.calificacion, "
                + "mp.titulo FROM intento_practica ip "
                + "JOIN material_practica mp ON mp.id_material_practica = ip.id_material_practica "
                + "WHERE ip.id_estudiante = ? "
                + "ORDER BY ip.fecha_inicio DESC, ip.id_intento_practica DESC",
                ps -> ps.setInt(1, idEstudiante), rs -> {
                    IntentoPractica intento = mapear(rs);
                    intento.tituloMaterialPractica = rs.getString("titulo");
                    return intento;
                });
    }

    private IntentoPractica mapear(ResultSet rs) throws SQLException {
        IntentoPractica intento = new IntentoPractica();
        intento.setIdIntentoPractica(rs.getInt("id_intento_practica"));
        intento.fechaInicioIntentoPractica = Mapeos.fecha(rs, "fecha_inicio");
        intento.fechaFinIntentoPractica = Mapeos.fecha(rs, "fecha_fin");
        intento.calificacionIntentoPractica = rs.getObject("calificacion") == null
                ? null : rs.getDouble("calificacion");
        return intento;
    }
}
