package modelo;

import logica.Docente;
import logica.EstadoEvaluacion;
import logica.Evaluacion;
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
            ps.setInt(1, docente.getIdUsuario());
            ps.setString(2, titulo);
            ps.setString(3, descripcion);
        });
        evaluacion.setIdEvaluacion(id);
        return evaluacion;
    }

    /**
     * RF-31: crea la evaluacion ya configurada y con su primera pregunta en una sola transaccion
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
            int idEvaluacion = insertarCon(c, "INSERT INTO evaluacion (id_docente, titulo, descripcion, "
                    + "numero_preguntas, numero_versiones, duracion_minutos, estado) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'BORRADOR')", ps -> {
                ps.setInt(1, docente.getIdUsuario());
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
        evaluacion.setIdEvaluacion(id);
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
            ps.setInt(4, evaluacion.getIdEvaluacion());
        });
    }

    public void marcarListaEvaluacion(Evaluacion evaluacion) {
        actualizar("UPDATE evaluacion SET estado = 'LISTA' WHERE id_evaluacion = ?",
                ps -> ps.setInt(1, evaluacion.getIdEvaluacion()));
        evaluacion.estadoEvaluacion = EstadoEvaluacion.LISTA;
    }

    /** Editar una evaluación lista la devuelve a borrador (misma regla que las preguntas aprobadas). */
    public void volverBorrador(Evaluacion evaluacion) {
        if (evaluacion.estadoEvaluacion == EstadoEvaluacion.LISTA) {
            actualizar("UPDATE evaluacion SET estado = 'BORRADOR' WHERE id_evaluacion = ?",
                    ps -> ps.setInt(1, evaluacion.getIdEvaluacion()));
            evaluacion.estadoEvaluacion = EstadoEvaluacion.BORRADOR;
        }
    }

    public void archivarEvaluacion(Evaluacion evaluacion) {
        actualizar("UPDATE evaluacion SET estado = 'ARCHIVADA' WHERE id_evaluacion = ?",
                ps -> ps.setInt(1, evaluacion.getIdEvaluacion()));
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
        evaluacion.setIdEvaluacion(rs.getInt("id_evaluacion"));
        evaluacion.tituloEvaluacion = rs.getString("titulo");
        evaluacion.descripcionEvaluacion = rs.getString("descripcion");
        evaluacion.numeroPreguntasEvaluacion = rs.getInt("numero_preguntas");
        evaluacion.numeroVersionesEvaluacion = rs.getInt("numero_versiones");
        evaluacion.duracionMinutosEvaluacion = rs.getInt("duracion_minutos");
        evaluacion.estadoEvaluacion = enumeracion(rs, "estado", EstadoEvaluacion.class);
        return evaluacion;
    }
}
