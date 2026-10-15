package modelo;

import logica.Calificacion;
import logica.Fechas;

import logica.EstadoIntento;
import logica.Estudiante;
import logica.EvaluacionVirtual;
import logica.Inscripcion;
import logica.Intento;
import logica.RecursoNoEncontradoException;
import logica.VersionEvaluacion;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class IntentoDAO extends DaoBase {

    private static final String COLUMNAS = "i.id_intento, i.id_evaluacion_virtual, i.id_inscripcion, "
            + "i.id_version_evaluacion, i.fecha_inicio, i.fecha_fin, i.calificacion, "
            + "i.estado, v.codigo";
    private static final String ORIGEN = "intento i "
            + "JOIN version_evaluacion v ON v.id_version_evaluacion = i.id_version_evaluacion";

    public IntentoDAO(DataSource dataSource) {
        super(dataSource);
    }

    public Intento crearIntento(EvaluacionVirtual evaluacionVirtual, Inscripcion inscripcion,
                                VersionEvaluacion versionEvaluacion) {
        Intento intento = new Intento();
        intento.fechaInicioIntento = Fechas.ahora();
        intento.estadoIntento = EstadoIntento.EN_CURSO;
        intento.versionEvaluacionIntento = versionEvaluacion;
        int id = insertar("INSERT INTO intento (id_evaluacion_virtual, id_inscripcion, id_version_evaluacion, "
                + "estado) VALUES (?, ?, ?, 'EN_CURSO')", ps -> {
            ps.setInt(1, evaluacionVirtual.getIdEvaluacionVirtual());
            ps.setInt(2, inscripcion.getIdInscripcion());
            ps.setInt(3, versionEvaluacion.getIdVersionEvaluacion());
        });
        intento.setIdIntento(id);
        evaluacionVirtual.intentosEvaluacionVirtual.add(intento);
        inscripcion.intentosInscripcion.add(intento);
        return intento;
    }

    /**
     * Definicion canonica del limite efectivo del intento (RF-47): el menor entre la duracion
     * individual y la fecha de fin de la evaluacion virtual. Hora del servidor (RNF-15).
     * La usan el cierre, los segundos restantes, la deteccion de vencidos y el guardado autoritativo.
     */
    static final String LIMITE_EFECTIVO =
            "LEAST(ev.fecha_fin, DATE_ADD(i.fecha_inicio, INTERVAL e.duracion_minutos MINUTE))";

    static final String LIMITE_JOIN = "intento i "
            + "JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = i.id_evaluacion_virtual "
            + "JOIN evaluacion e ON e.id_evaluacion = ev.id_evaluacion";

    /**
     * Fija fecha_fin sin sobrescribirla: el menor entre el momento actual y el limite efectivo.
     */
    private static final String CIERRE_FECHA_FIN =
            "i.fecha_fin = COALESCE(i.fecha_fin, LEAST(NOW(), " + LIMITE_EFECTIVO + "))";

    private static final String CIERRE_JOIN = LIMITE_JOIN;

    /**
     * RF-48/RF-47: finaliza un intento EN_CURSO fijando fecha_fin con la hora del servidor.
     * Sirve para la entrega voluntaria (NOW() es anterior al limite), el vencimiento detectado al
     * volver el estudiante (queda el limite efectivo) y el cierre/cancelacion manual (el menor
     * entre el cierre y los limites). Nunca sobrescribe una fecha ya registrada.
     */
    public void finalizarIntento(Intento intento) {
        enTransaccion(c -> {
            actualizarCon(c, "UPDATE " + CIERRE_JOIN + " SET " + CIERRE_FECHA_FIN + ", "
                    + "i.estado = 'FINALIZADO' WHERE i.id_intento = ? AND i.estado = 'EN_CURSO'",
                    ps -> ps.setInt(1, intento.getIdIntento()));
            sincronizarCierre(c, intento);
            return null;
        });
    }

    /** Refleja en memoria la fecha_fin y el estado reales del intento (sin sobrescribir la fecha). */
    private static void sincronizarCierre(Connection c, Intento intento) throws SQLException {
        consultarUnoCon(c, "SELECT fecha_fin, estado FROM intento WHERE id_intento = ?",
                ps -> ps.setInt(1, intento.getIdIntento()), rs -> {
                    intento.fechaFinIntento = Mapeos.fecha(rs, "fecha_fin");
                    intento.estadoIntento = enumeracion(rs, "estado", EstadoIntento.class);
                    return null;
                });
    }

    private static final Logger LOG = Logger.getLogger(IntentoDAO.class.getName());

    /** Puntos obtenidos y maximos de la version del intento (para la vista del docente). */
    public static final class Puntos {
        public double obtenidos;
        public double maximos;
    }

    /**
     * RF-53: marca el intento CALIFICADO y guarda su calificacion final en escala 0,0-5,0.
     * Devuelve la nota guardada o null si los datos son inconsistentes (maximo 0 o puntos
     * obtenidos mayores al maximo): en ese caso no se guarda nota falsa, el intento queda
     * FINALIZADO y el problema se registra para el docente.
     */
    public Double calificarIntento(Intento intento) {
        return recalcularCalificacion(intento, true);
    }

    /**
     * Recalcula la calificacion sin marcar el intento como calificado (se usa al ajustar una
     * respuesta mientras quedan pendientes, RF-52). Devuelve la nota o null si no se guardo.
     */
    public Double actualizarCalificacion(Intento intento) {
        return recalcularCalificacion(intento, false);
    }

    /**
     * Nota final = (SUM(respuesta.puntaje) x 5) / SUM(version_pregunta.puntaje de la version del
     * intento), con una cifra decimal y HALF_UP. El denominador sale siempre del snapshot de la
     * version (nunca de evaluacion_pregunta ni del banco). Todo ocurre en una transaccion que
     * bloquea la fila del intento.
     */
    private Double recalcularCalificacion(Intento intento, boolean marcarCalificado) {
        Double nota = enTransaccion(c -> {
            List<Integer> versiones = consultarListaCon(c,
                    "SELECT id_version_evaluacion FROM intento WHERE id_intento = ? FOR UPDATE",
                    ps -> ps.setInt(1, intento.getIdIntento()), rs -> rs.getInt(1));
            if (versiones.isEmpty()) {
                throw new RecursoNoEncontradoException("Intento no encontrado");
            }
            int idVersion = versiones.get(0);
            BigDecimal obtenidos = consultarUnoCon(c,
                    "SELECT COALESCE(SUM(puntaje), 0) FROM respuesta WHERE id_intento = ?",
                    ps -> ps.setInt(1, intento.getIdIntento()), rs -> rs.getBigDecimal(1))
                    .orElse(BigDecimal.ZERO);
            BigDecimal maximos = consultarUnoCon(c,
                    "SELECT COALESCE(SUM(puntaje), 0) FROM version_pregunta WHERE id_version_evaluacion = ?",
                    ps -> ps.setInt(1, idVersion), rs -> rs.getBigDecimal(1))
                    .orElse(BigDecimal.ZERO);
            if (maximos.signum() <= 0) {
                LOG.log(Level.SEVERE, "Intento sin maximo de puntos: id={0}, version={1}; no se guarda calificacion",
                        new Object[]{intento.getIdIntento(), idVersion});
                if (marcarCalificado && intento.estadoIntento != EstadoIntento.CALIFICADO) {
                    finalizarSinNota(c, intento);
                }
                if (marcarCalificado) {
                    sincronizarCierre(c, intento);
                }
                return null;
            }
            if (obtenidos.compareTo(maximos) > 0) {
                LOG.log(Level.SEVERE, "Inconsistencia de puntos: intento={0} obtenidos={1} maximos={2}; "
                        + "no se guarda calificacion",
                        new Object[]{intento.getIdIntento(), obtenidos, maximos});
                if (marcarCalificado && intento.estadoIntento != EstadoIntento.CALIFICADO) {
                    finalizarSinNota(c, intento);
                }
                if (marcarCalificado) {
                    sincronizarCierre(c, intento);
                }
                return null;
            }
            BigDecimal notaFinal = Calificacion.dePuntos(obtenidos, maximos);
            if (marcarCalificado) {
                // La calificacion no decide cuando termino el intento: fecha_fin se fija aqui solo
                // si aun no existia (COALESCE), con el limite efectivo. Una recalificacion no la toca.
                actualizarCon(c, "UPDATE " + CIERRE_JOIN + " SET " + CIERRE_FECHA_FIN + ", "
                        + "i.calificacion = ?, i.estado = 'CALIFICADO' WHERE i.id_intento = ?", ps -> {
                    ps.setBigDecimal(1, notaFinal);
                    ps.setInt(2, intento.getIdIntento());
                });
                sincronizarCierre(c, intento);
            } else {
                actualizarCon(c, "UPDATE intento SET calificacion = ? WHERE id_intento = ?", ps -> {
                    ps.setBigDecimal(1, notaFinal);
                    ps.setInt(2, intento.getIdIntento());
                });
            }
            return notaFinal.doubleValue();
        });
        if (nota != null) {
            intento.calificacionIntento = nota;
        }
        return nota;
    }

    /** Cierra el intento sin nota cuando la calificacion no puede calcularse (dato inconsistente). */
    private static void finalizarSinNota(Connection c, Intento intento) throws SQLException {
        actualizarCon(c, "UPDATE " + CIERRE_JOIN + " SET " + CIERRE_FECHA_FIN + ", "
                + "i.estado = 'FINALIZADO' WHERE i.id_intento = ? AND i.estado = 'EN_CURSO'",
                ps -> ps.setInt(1, intento.getIdIntento()));
    }

    /** Puntos obtenidos y maximos de la version del intento (solo lectura, para la vista del docente). */
    public Puntos puntosDeIntento(int idIntento) {
        return consultarUno("SELECT "
                + "(SELECT COALESCE(SUM(r.puntaje), 0) FROM respuesta r WHERE r.id_intento = i.id_intento) AS obtenidos, "
                + "(SELECT COALESCE(SUM(vp.puntaje), 0) FROM version_pregunta vp "
                + "WHERE vp.id_version_evaluacion = i.id_version_evaluacion) AS maximos "
                + "FROM intento i WHERE i.id_intento = ?", ps -> ps.setInt(1, idIntento), rs -> {
                    Puntos puntos = new Puntos();
                    puntos.obtenidos = rs.getDouble("obtenidos");
                    puntos.maximos = rs.getDouble("maximos");
                    return puntos;
                }).orElse(null);
    }

    public Optional<Intento> buscarPorId(int idIntento) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN + " WHERE i.id_intento = ?",
                ps -> ps.setInt(1, idIntento), this::mapear);
    }

    /** Solo devuelve el intento si pertenece al estudiante indicado. */
    public Optional<Intento> buscarPorIdDeEstudiante(int idIntento, int idEstudiante) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN
                + " JOIN inscripcion n ON n.id_inscripcion = i.id_inscripcion "
                + "WHERE i.id_intento = ? AND n.id_estudiante = ?", ps -> {
            ps.setInt(1, idIntento);
            ps.setInt(2, idEstudiante);
        }, this::mapear);
    }

    /** Solo devuelve el intento si su grupo pertenece al docente indicado. */
    public Optional<Intento> buscarPorIdDeDocente(int idIntento, int idDocente) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN
                + " JOIN inscripcion n ON n.id_inscripcion = i.id_inscripcion "
                + "JOIN grupo g ON g.id_grupo = n.id_grupo JOIN curso c ON c.id_curso = g.id_curso "
                + "WHERE i.id_intento = ? AND c.id_docente = ?", ps -> {
            ps.setInt(1, idIntento);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    public Optional<Intento> buscarPorEvaluacionEInscripcion(int idEvaluacionVirtual, int idInscripcion) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN
                + " WHERE i.id_evaluacion_virtual = ? AND i.id_inscripcion = ?", ps -> {
            ps.setInt(1, idEvaluacionVirtual);
            ps.setInt(2, idInscripcion);
        }, this::mapear);
    }

    public List<Intento> listarPorInscripcion(int idInscripcion) {
        return consultarLista("SELECT " + COLUMNAS + " FROM " + ORIGEN + " WHERE i.id_inscripcion = ? "
                + "ORDER BY i.fecha_inicio DESC", ps -> ps.setInt(1, idInscripcion), this::mapear);
    }

    public List<Intento> listarEnCursoPorVirtual(int idEvaluacionVirtual) {
        return consultarLista("SELECT " + COLUMNAS + " FROM " + ORIGEN
                + " WHERE i.id_evaluacion_virtual = ? AND i.estado = 'EN_CURSO'",
                ps -> ps.setInt(1, idEvaluacionVirtual), this::mapear);
    }

    /** Todos los intentos de una evaluación virtual, para el análisis de supervisión al cerrar (RF-50). */
    public List<Intento> listarPorEvaluacionVirtual(int idEvaluacionVirtual) {
        return consultarLista("SELECT " + COLUMNAS + " FROM " + ORIGEN
                + " WHERE i.id_evaluacion_virtual = ? ORDER BY i.id_intento",
                ps -> ps.setInt(1, idEvaluacionVirtual), this::mapear);
    }

    /** Para repartir las versiones de forma equilibrada al iniciar intentos. */
    public int contarPorVersion(int idEvaluacionVirtual, int idVersionEvaluacion) {
        return contar("SELECT COUNT(*) FROM intento WHERE id_evaluacion_virtual = ? AND id_version_evaluacion = ?",
                ps -> {
            ps.setInt(1, idEvaluacionVirtual);
            ps.setInt(2, idVersionEvaluacion);
        });
    }

    /** Intentos de un estudiante (para no eliminar cuentas con actividad, CU-07 5a). */
    public int contarPorEstudiante(int idEstudiante) {
        return contar("SELECT COUNT(*) FROM intento i "
                + "JOIN inscripcion n ON n.id_inscripcion = i.id_inscripcion WHERE n.id_estudiante = ?",
                ps -> ps.setInt(1, idEstudiante));
    }

    /** Intentos de una versión (CU-22: una versión con intentos queda en solo lectura). */
    public int contarPorVersionEvaluacion(int idVersionEvaluacion) {
        return contar("SELECT COUNT(*) FROM intento WHERE id_version_evaluacion = ?",
                ps -> ps.setInt(1, idVersionEvaluacion));
    }

    /** Estudiante dueño del intento (para las pantallas del docente). */
    public Optional<Estudiante> estudianteDeIntento(int idIntento) {
        return consultarUno("SELECT e.id_usuario, e.identificacion, e.nombre, e.correo, "
                + "es.nivel_ingles FROM intento i "
                + "JOIN inscripcion n ON n.id_inscripcion = i.id_inscripcion "
                + "JOIN usuario e ON e.id_usuario = n.id_estudiante "
                + "JOIN estudiante es ON es.id_usuario = e.id_usuario WHERE i.id_intento = ?",
                ps -> ps.setInt(1, idIntento), rs -> {
                    Estudiante estudiante = new Estudiante();
                    estudiante.setIdUsuario(rs.getInt("id_usuario"));
                    estudiante.identificacionUsuario = rs.getString("identificacion");
                    estudiante.nombreUsuario = rs.getString("nombre");
                    estudiante.correoUsuario = rs.getString("correo");
                    estudiante.nivelInglesEstudiante = rs.getString("nivel_ingles");
                    return estudiante;
                });
    }

    public Optional<String> tituloEvaluacionDeIntento(int idIntento) {
        return consultarUno("SELECT e.titulo FROM intento i "
                + "JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = i.id_evaluacion_virtual "
                + "JOIN evaluacion e ON e.id_evaluacion = ev.id_evaluacion WHERE i.id_intento = ?",
                ps -> ps.setInt(1, idIntento), rs -> rs.getString(1));
    }

    public int idVirtualDeIntento(int idIntento) {
        return consultarUno("SELECT id_evaluacion_virtual FROM intento WHERE id_intento = ?",
                ps -> ps.setInt(1, idIntento), rs -> rs.getInt(1)).orElse(-1);
    }

    /**
     * Segundos que le quedan al intento: los que faltan para el limite efectivo (duracion individual
     * o fecha de fin de la virtual, lo primero). Negativo si ya vencio. Autoridad: hora de la BD.
     */
    public int segundosRestantes(int idIntento) {
        return consultarUno("SELECT TIMESTAMPDIFF(SECOND, NOW(), " + LIMITE_EFECTIVO + ") FROM " + LIMITE_JOIN
                + " WHERE i.id_intento = ?", ps -> ps.setInt(1, idIntento), rs -> rs.getInt(1)).orElse(0);
    }

    /**
     * Ids de intentos EN_CURSO cuyo limite efectivo ya paso (los finaliza el programador, aunque la
     * evaluacion virtual siga ABIERTA). La consulta esta acotada a EN_CURSO.
     */
    public List<Integer> listarVencidos() {
        return consultarLista("SELECT i.id_intento FROM " + LIMITE_JOIN
                + " WHERE i.estado = 'EN_CURSO' AND " + LIMITE_EFECTIVO + " <= NOW()",
                SIN_PARAMETROS, rs -> rs.getInt(1));
    }

    /**
     * Reclama la finalizacion de un intento vencido de forma atomica (idempotente): solo pasa de
     * EN_CURSO a FINALIZADO si el limite efectivo ya paso, y fija fecha_fin al limite (Fase 3B).
     * Devuelve true si esta ejecucion hizo la transicion.
     */
    public boolean reclamarVencido(int idIntento) {
        return actualizar("UPDATE " + LIMITE_JOIN + " SET " + CIERRE_FECHA_FIN + ", "
                + "i.estado = 'FINALIZADO' WHERE i.id_intento = ? AND i.estado = 'EN_CURSO' "
                + "AND " + LIMITE_EFECTIVO + " <= NOW()", ps -> ps.setInt(1, idIntento)) > 0;
    }

    private Intento mapear(ResultSet rs) throws SQLException {
        Intento intento = new Intento();
        intento.setIdIntento(rs.getInt("id_intento"));
        intento.fechaInicioIntento = Mapeos.fecha(rs, "fecha_inicio");
        intento.fechaFinIntento = Mapeos.fecha(rs, "fecha_fin");
        intento.calificacionIntento = rs.getDouble("calificacion");
        intento.estadoIntento = enumeracion(rs, "estado", EstadoIntento.class);
        VersionEvaluacion version = new VersionEvaluacion();
        version.setIdVersionEvaluacion(rs.getInt("id_version_evaluacion"));
        version.codigoVersionEvaluacion = rs.getString("codigo");
        intento.versionEvaluacionIntento = version;
        return intento;
    }
}
