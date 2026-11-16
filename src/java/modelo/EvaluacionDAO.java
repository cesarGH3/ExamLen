package modelo;

import logica.Docente;
import logica.EstadoEvaluacion;
import logica.Evaluacion;
import logica.Pregunta;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class EvaluacionDAO extends DaoBase {

    private static final String COLUMNAS = "id_evaluacion, id_docente, titulo, descripcion, "
            + "numero_preguntas, numero_versiones, duracion_minutos, estado";

    public EvaluacionDAO(DataSource dataSource) {
        super(dataSource);
    }

    public Evaluacion crearEvaluacion(Docente docente, String titulo, String descripcion) {
        Evaluacion evaluacion = new Evaluacion();
        evaluacion.tituloEvaluacion = titulo;
        evaluacion.descripcionEvaluacion = descripcion;
        evaluacion.estadoEvaluacion = EstadoEvaluacion.BORRADOR;
        int id = insertar("INSERT INTO evaluacion (id_docente, titulo, descripcion, "
                + "estado) VALUES (?, ?, ?, 'BORRADOR')", ps -> {
            ps.setInt(1, docente.consultarIdUsuario());
            ps.setString(2, titulo);
            ps.setString(3, descripcion);
        });
        evaluacion.establecerIdEvaluacion(id);
        return evaluacion;
    }

    /**
     * Compatibilidad interna: crea la evaluacion ya configurada y con su primera pregunta en una sola transaccion
     * (multiplicidad 1..*): o se guardan evaluacion + asociacion, o no se crea nada.
     */
    public Evaluacion crearEvaluacionConPregunta(Docente docente, String titulo, String descripcion,
                                                 int numeroPreguntas, int numeroVersiones, int duracionMinutos,
                                                 int idPrimeraPregunta) {
        Evaluacion evaluacion = new Evaluacion();
        evaluacion.tituloEvaluacion = titulo;
        evaluacion.descripcionEvaluacion = descripcion;
        evaluacion.numeroPreguntasEvaluacion = numeroPreguntas;
        evaluacion.numeroVersionesEvaluacion = numeroVersiones;
        evaluacion.duracionMinutosEvaluacion = duracionMinutos;
        evaluacion.estadoEvaluacion = EstadoEvaluacion.BORRADOR;
        int id = enTransaccion(c -> {
            Pregunta comprobada = PreguntaDAO.cargarPregunta(c, idPrimeraPregunta, true);
            if (comprobada.idDocente != docente.consultarIdUsuario() || comprobada.estadoPregunta != logica.EstadoPregunta.APROBADA) {
                throw new logica.OperacionNoPermitidaException("Selecciona una pregunta propia aprobada.");
            }
            int idEvaluacion = insertarCon(c, "INSERT INTO evaluacion (id_docente, titulo, descripcion, "
                    + "numero_preguntas, numero_versiones, duracion_minutos, estado) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'BORRADOR')", ps -> {
                ps.setInt(1, docente.consultarIdUsuario());
                ps.setString(2, titulo);
                ps.setString(3, descripcion);
                ps.setInt(4, numeroPreguntas);
                ps.setInt(5, numeroVersiones);
                ps.setInt(6, duracionMinutos);
            });
            actualizarCon(c, "INSERT INTO evaluacion_pregunta (id_evaluacion, id_pregunta, "
                    + "orden_evaluacion_pregunta, puntaje_evaluacion_pregunta) VALUES (?, ?, 1, 0)", ps -> {
                ps.setInt(1, idEvaluacion);
                ps.setInt(2, idPrimeraPregunta);
            });
            return idEvaluacion;
        });
        evaluacion.establecerIdEvaluacion(id);
        return evaluacion;
    }

    public void configurarEvaluacion(Evaluacion evaluacion, int numeroPreguntas, int numeroVersiones,
                                     int duracionMinutos) {
        evaluacion.numeroPreguntasEvaluacion = numeroPreguntas;
        evaluacion.numeroVersionesEvaluacion = numeroVersiones;
        evaluacion.duracionMinutosEvaluacion = duracionMinutos;
        actualizar("UPDATE evaluacion SET numero_preguntas = ?, numero_versiones = ?, "
                + "duracion_minutos = ? WHERE id_evaluacion = ?", ps -> {
            ps.setInt(1, numeroPreguntas);
            ps.setInt(2, numeroVersiones);
            ps.setInt(3, duracionMinutos);
            ps.setInt(4, evaluacion.consultarIdEvaluacion());
        });
    }

    public void marcarListaEvaluacion(Evaluacion evaluacion) {
        enTransaccion(conexion -> {
            List<Integer> configuracion = consultarUnoCon(conexion,
                    "SELECT numero_preguntas, numero_versiones, duracion_minutos FROM evaluacion "
                    + "WHERE id_evaluacion = ? AND estado <> 'ARCHIVADA' FOR UPDATE",
                    sentencia -> sentencia.setInt(1, evaluacion.consultarIdEvaluacion()),
                    resultado -> List.of(resultado.getInt(1), resultado.getInt(2), resultado.getInt(3)))
                    .orElseThrow(() -> new logica.OperacionNoPermitidaException("Evaluación no editable."));
            List<Boolean> preguntas = consultarListaCon(conexion,
                    "SELECT (p.estado = 'APROBADA' AND p.id_docente = e.id_docente) FROM evaluacion_pregunta ep "
                    + "JOIN pregunta p ON p.id_pregunta = ep.id_pregunta JOIN evaluacion e ON e.id_evaluacion = ep.id_evaluacion "
                    + "WHERE ep.id_evaluacion = ? FOR UPDATE", sentencia -> sentencia.setInt(1, evaluacion.consultarIdEvaluacion()),
                    resultado -> resultado.getBoolean(1));
            if (configuracion.get(0) < 1 || configuracion.get(1) < 1 || configuracion.get(1) > 10
                    || configuracion.get(2) < 1 || preguntas.size() < configuracion.get(0)
                    || preguntas.contains(false)) {
                throw new logica.OperacionNoPermitidaException("Completa la configuración y las preguntas antes de marcar lista.");
            }
            actualizarCon(conexion, "UPDATE evaluacion SET estado = 'LISTA' WHERE id_evaluacion = ?",
                    sentencia -> sentencia.setInt(1, evaluacion.consultarIdEvaluacion()));
            return null;
        });
        evaluacion.estadoEvaluacion = EstadoEvaluacion.LISTA;
    }

    /** Editar una evaluación lista la devuelve a borrador (misma regla que las preguntas aprobadas). */
    public void volverBorrador(Evaluacion evaluacion) {
        if (evaluacion.estadoEvaluacion == EstadoEvaluacion.LISTA) {
            actualizar("UPDATE evaluacion SET estado = 'BORRADOR' WHERE id_evaluacion = ?",
                    ps -> ps.setInt(1, evaluacion.consultarIdEvaluacion()));
            evaluacion.estadoEvaluacion = EstadoEvaluacion.BORRADOR;
        }
    }

    public void archivarEvaluacion(Evaluacion evaluacion) {
        actualizar("UPDATE evaluacion SET estado = 'ARCHIVADA' WHERE id_evaluacion = ?",
                ps -> ps.setInt(1, evaluacion.consultarIdEvaluacion()));
        evaluacion.estadoEvaluacion = EstadoEvaluacion.ARCHIVADA;
    }

    public Optional<Evaluacion> buscarPorId(int idEvaluacion) {
        return consultarUno("SELECT " + COLUMNAS + " FROM evaluacion WHERE id_evaluacion = ?",
                ps -> ps.setInt(1, idEvaluacion), this::mapear);
    }

    public Optional<Evaluacion> buscarPorIdDeDocente(int idEvaluacion, int idDocente) {
        return consultarUno("SELECT " + COLUMNAS + " FROM evaluacion WHERE id_evaluacion = ? AND id_docente = ?", ps -> {
            ps.setInt(1, idEvaluacion);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    public List<Evaluacion> listarPorDocente(int idDocente) {
        return consultarLista("SELECT " + COLUMNAS + " FROM evaluacion WHERE id_docente = ? "
                + "ORDER BY estado, titulo", ps -> ps.setInt(1, idDocente), this::mapear);
    }

    private Evaluacion mapear(ResultSet rs) throws SQLException {
        Evaluacion evaluacion = new Evaluacion();
        evaluacion.establecerIdEvaluacion(rs.getInt("id_evaluacion"));
        evaluacion.tituloEvaluacion = rs.getString("titulo");
        evaluacion.descripcionEvaluacion = rs.getString("descripcion");
        evaluacion.numeroPreguntasEvaluacion = rs.getInt("numero_preguntas");
        evaluacion.numeroVersionesEvaluacion = rs.getInt("numero_versiones");
        evaluacion.duracionMinutosEvaluacion = rs.getInt("duracion_minutos");
        evaluacion.estadoEvaluacion = enumeracion(rs, "estado", EstadoEvaluacion.class);
        return evaluacion;
    }
}
