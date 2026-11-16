package modelo;

import logica.Curso;
import logica.Evaluacion;
import logica.EstadoEvaluacionVirtual;
import logica.EvaluacionVirtual;
import logica.Grupo;
import logica.ValidacionException;
import logica.VersionEvaluacion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class EvaluacionVirtualDAO extends DaoBase {

    private static final String COLUMNAS = "ev.id_evaluacion_virtual, ev.id_grupo, ev.id_evaluacion, "
            + "ev.fecha_inicio, ev.fecha_fin, "
            + "ev.supervision_activa, ev.intentos_permitidos, ev.exigir_respuestas_completas, ev.estado, "
            + "e.titulo, e.descripcion, e.numero_preguntas, e.duracion_minutos";
    private static final String ORIGEN = "evaluacion_virtual ev "
            + "JOIN evaluacion e ON e.id_evaluacion = ev.id_evaluacion";

    public EvaluacionVirtualDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** RF-41: la supervisión la elige el docente y se persiste tal cual. */
    public EvaluacionVirtual programarEvaluacionVirtual(Grupo grupo, Evaluacion evaluacion,
                                                       java.util.Date fechaInicio, java.util.Date fechaFin,
                                                       boolean supervision)
            throws ValidacionException {
        return programarEvaluacionVirtual(grupo, evaluacion, fechaInicio, fechaFin, 1, supervision, false);
    }

    /** Compatibilidad con Fase 3: sin politica explicita se permite enviar incompleto. */
    public EvaluacionVirtual programarEvaluacionVirtual(Grupo grupo, Evaluacion evaluacion,
                                                       java.util.Date fechaInicio, java.util.Date fechaFin,
                                                       int intentosPermitidos, boolean supervision)
            throws ValidacionException {
        return programarEvaluacionVirtual(grupo, evaluacion, fechaInicio, fechaFin, intentosPermitidos,
                supervision, false);
    }

    /** RF-41/Fase 4: intentos, supervision y politica de respuestas completas. */
    public EvaluacionVirtual programarEvaluacionVirtual(Grupo grupo, Evaluacion evaluacion,
                                                       java.util.Date fechaInicio, java.util.Date fechaFin,
                                                       int intentosPermitidos, boolean supervision,
                                                       boolean exigirRespuestasCompletas)
            throws ValidacionException {
        if (fechaInicio == null || fechaFin == null || !fechaInicio.before(fechaFin)) {
            throw new ValidacionException("La fecha de inicio debe ser anterior a la fecha de fin.");
        }
        if (intentosPermitidos != 1 && intentosPermitidos != 2) {
            throw new ValidacionException("Los intentos permitidos deben ser 1 o 2.");
        }
        EvaluacionVirtual virtual = new EvaluacionVirtual();
        virtual.fechaInicioEvaluacionVirtual = fechaInicio;
        virtual.fechaFinEvaluacionVirtual = fechaFin;
        virtual.supervisionActivaEvaluacionVirtual = supervision;
        virtual.establecerIntentosPermitidos(intentosPermitidos);
        virtual.establecerExigirRespuestasCompletas(exigirRespuestasCompletas);
        virtual.estadoEvaluacionVirtual = EstadoEvaluacionVirtual.PROGRAMADA;
        virtual.evaluacionEvaluacionVirtual = evaluacion;
        int id = insertar("INSERT INTO evaluacion_virtual (id_grupo, id_evaluacion, "
                + "fecha_inicio, fecha_fin, "
                + "supervision_activa, intentos_permitidos, exigir_respuestas_completas, estado) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PROGRAMADA')", ps -> {
            ps.setInt(1, grupo.consultarIdGrupo());
            ps.setInt(2, evaluacion.consultarIdEvaluacion());
            ps.setTimestamp(3, new java.sql.Timestamp(fechaInicio.getTime()));
            ps.setTimestamp(4, new java.sql.Timestamp(fechaFin.getTime()));
            ps.setBoolean(5, supervision);
            ps.setInt(6, intentosPermitidos);
            ps.setBoolean(7, exigirRespuestasCompletas);
        });
        virtual.establecerIdEvaluacionVirtual(id);
        return virtual;
    }

    /** Las transiciones detalladas (quién y cuándo) se definen con la pantalla de la Entrega 3. */
    public void abrirEvaluacionVirtual(EvaluacionVirtual virtual) {
        cambiarEstado(virtual, "ABIERTA", EstadoEvaluacionVirtual.ABIERTA);
    }

    public void cerrarEvaluacionVirtual(EvaluacionVirtual virtual) {
        cambiarEstado(virtual, "CERRADA", EstadoEvaluacionVirtual.CERRADA);
    }

    public void cancelarEvaluacionVirtual(EvaluacionVirtual virtual) {
        cambiarEstado(virtual, "CANCELADA", EstadoEvaluacionVirtual.CANCELADA);
    }

    public boolean estaDisponibleEvaluacionVirtual(EvaluacionVirtual virtual) {
        java.util.Date ahora = new java.util.Date();
        return virtual.estadoEvaluacionVirtual == EstadoEvaluacionVirtual.ABIERTA
                && virtual.fechaInicioEvaluacionVirtual != null && virtual.fechaFinEvaluacionVirtual != null
                && !ahora.before(virtual.fechaInicioEvaluacionVirtual)
                && !ahora.after(virtual.fechaFinEvaluacionVirtual);
    }

    public Optional<EvaluacionVirtual> buscarPorId(int idEvaluacionVirtual) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN + " WHERE ev.id_evaluacion_virtual = ?",
                ps -> ps.setInt(1, idEvaluacionVirtual), this::mapear);
    }

    /** Solo devuelve la evaluación virtual si su grupo pertenece al docente indicado. */
    public Optional<EvaluacionVirtual> buscarPorIdDeDocente(int idEvaluacionVirtual, int idDocente) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN
                + " JOIN grupo g ON g.id_grupo = ev.id_grupo JOIN curso c ON c.id_curso = g.id_curso "
                + "WHERE ev.id_evaluacion_virtual = ? AND c.id_docente = ?", ps -> {
            ps.setInt(1, idEvaluacionVirtual);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    public List<EvaluacionVirtual> listarPorGrupo(int idGrupo) {
        return consultarLista("SELECT " + COLUMNAS + " FROM " + ORIGEN + " WHERE ev.id_grupo = ? "
                + "ORDER BY ev.fecha_inicio DESC", ps -> ps.setInt(1, idGrupo), this::mapear);
    }

    public List<EvaluacionVirtual> listarPorGrupoDeDocente(int idGrupo, int idDocente) {
        return consultarLista("SELECT " + COLUMNAS + " FROM " + ORIGEN
                + " JOIN grupo g ON g.id_grupo = ev.id_grupo JOIN curso c ON c.id_curso = g.id_curso "
                + "WHERE ev.id_grupo = ? AND c.id_docente = ? ORDER BY ev.fecha_inicio DESC", ps -> {
            ps.setInt(1, idGrupo);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    // ------------------------------------------------------------------ versiones aplicadas

    public void agregarVersion(int idEvaluacionVirtual, int idVersionEvaluacion) {
        actualizar("INSERT IGNORE INTO evaluacion_virtual_version (id_evaluacion_virtual, id_version_evaluacion) "
                + "VALUES (?, ?)", ps -> {
            ps.setInt(1, idEvaluacionVirtual);
            ps.setInt(2, idVersionEvaluacion);
        });
    }

    public void quitarVersion(int idEvaluacionVirtual, int idVersionEvaluacion) {
        actualizar("DELETE FROM evaluacion_virtual_version WHERE id_evaluacion_virtual = ? "
                + "AND id_version_evaluacion = ?", ps -> {
            ps.setInt(1, idEvaluacionVirtual);
            ps.setInt(2, idVersionEvaluacion);
        });
    }

    public List<VersionEvaluacion> listarVersiones(int idEvaluacionVirtual) {
        return consultarLista("SELECT v.id_version_evaluacion, v.codigo, "
                + "v.fecha_generacion FROM evaluacion_virtual_version evv "
                + "JOIN version_evaluacion v ON v.id_version_evaluacion = evv.id_version_evaluacion "
                + "WHERE evv.id_evaluacion_virtual = ? ORDER BY v.codigo",
                ps -> ps.setInt(1, idEvaluacionVirtual), rs -> {
                    VersionEvaluacion version = new VersionEvaluacion();
                    version.establecerIdVersionEvaluacion(rs.getInt("id_version_evaluacion"));
                    version.codigoVersionEvaluacion = rs.getString("codigo");
                    version.fechaGeneracionVersionEvaluacion = Mapeos.fecha(rs, "fecha_generacion");
                    return version;
                });
    }

    public boolean tieneVersion(int idEvaluacionVirtual, int idVersionEvaluacion) {
        return existe("SELECT 1 FROM evaluacion_virtual_version WHERE id_evaluacion_virtual = ? "
                + "AND id_version_evaluacion = ? LIMIT 1", ps -> {
            ps.setInt(1, idEvaluacionVirtual);
            ps.setInt(2, idVersionEvaluacion);
        });
    }

    /**
     * Estados de los parciales virtuales donde está aplicada una versión (CU-22): solo se puede
     * editar si todos están PROGRAMADA (o cancelados) y no hay intentos.
     */
    public List<String> estadosDeAplicacionDeVersion(int idVersionEvaluacion) {
        return consultarLista("SELECT ev.estado FROM evaluacion_virtual_version evv "
                + "JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = evv.id_evaluacion_virtual "
                + "WHERE evv.id_version_evaluacion = ?",
                ps -> ps.setInt(1, idVersionEvaluacion), rs -> rs.getString("estado"));
    }

    public int contarIntentos(int idEvaluacionVirtual) {
        return contar("SELECT COUNT(*) FROM intento WHERE id_evaluacion_virtual = ?",
                ps -> ps.setInt(1, idEvaluacionVirtual));
    }

    public int idGrupoDeVirtual(int idEvaluacionVirtual) {
        return consultarUno("SELECT id_grupo FROM evaluacion_virtual WHERE id_evaluacion_virtual = ?",
                ps -> ps.setInt(1, idEvaluacionVirtual), rs -> rs.getInt(1)).orElse(-1);
    }

    /** Grupo al que pertenece la evaluación virtual (con su curso), para las pantallas del docente. */
    public Optional<Grupo> grupoDeVirtual(int idEvaluacionVirtual) {
        return consultarUno("SELECT g.id_grupo, g.nombre, g.periodo, g.codigo_acceso, "
                + "g.estado, c.id_curso, c.nombre FROM evaluacion_virtual ev "
                + "JOIN grupo g ON g.id_grupo = ev.id_grupo JOIN curso c ON c.id_curso = g.id_curso "
                + "WHERE ev.id_evaluacion_virtual = ?", ps -> ps.setInt(1, idEvaluacionVirtual), rs -> {
                    Grupo grupo = new Grupo();
                    grupo.establecerIdGrupo(rs.getInt("id_grupo"));
                    grupo.nombreGrupo = rs.getString("nombre");
                    grupo.periodoGrupo = rs.getString("periodo");
                    grupo.codigoAccesoGrupo = rs.getString("codigo_acceso");
                    grupo.estadoGrupo = logica.EstadoGrupo.valueOf(rs.getString("estado"));
                    Curso curso = new Curso();
                    curso.establecerIdCurso(rs.getInt("id_curso"));
                    curso.nombreCurso = rs.getString("nombre");
                    grupo.cursoGrupo = curso;
                    return grupo;
                });
    }

    // ------------------------------------------------------------------ transiciones automaticas (RF-42)

    /** Ids de virtuales PROGRAMADAS cuya ventana ya empezo (las abre el programador). */
    public List<Integer> listarProgramadasEnVentana() {
        return consultarLista("SELECT id_evaluacion_virtual FROM evaluacion_virtual "
                + "WHERE estado = 'PROGRAMADA' AND fecha_inicio <= NOW() AND fecha_fin > NOW()",
                SIN_PARAMETROS, rs -> rs.getInt(1));
    }

    /** Ids de virtuales PROGRAMADAS o ABIERTAS cuya fecha_fin ya paso (las cierra el programador). */
    public List<Integer> listarVencidas() {
        return consultarLista("SELECT id_evaluacion_virtual FROM evaluacion_virtual "
                + "WHERE estado IN ('PROGRAMADA','ABIERTA') AND fecha_fin <= NOW()",
                SIN_PARAMETROS, rs -> rs.getInt(1));
    }

    /**
     * Apertura automatica condicional (idempotente y segura ante concurrencia): solo cambia si
     * sigue PROGRAMADA y la ventana esta vigente. Devuelve true si esta ejecucion la abrio.
     */
    public boolean abrirProgramada(int idEvaluacionVirtual) {
        return actualizar("UPDATE evaluacion_virtual SET estado = 'ABIERTA' "
                + "WHERE id_evaluacion_virtual = ? AND estado = 'PROGRAMADA' "
                + "AND fecha_inicio <= NOW() AND fecha_fin > NOW()",
                ps -> ps.setInt(1, idEvaluacionVirtual)) > 0;
    }

    /** Apertura manual (RF-42): solo desde PROGRAMADA. */
    public boolean reclamarApertura(int idEvaluacionVirtual) {
        return actualizar("UPDATE evaluacion_virtual SET estado = 'ABIERTA' "
                + "WHERE id_evaluacion_virtual = ? AND estado = 'PROGRAMADA'",
                ps -> ps.setInt(1, idEvaluacionVirtual)) > 0;
    }

    /**
     * Cierre condicional (manual o automatico): solo desde PROGRAMADA o ABIERTA; CANCELADA nunca
     * se cierra y CERRADA es terminal. Devuelve true si esta ejecucion hizo la transicion.
     */
    public boolean reclamarCierre(int idEvaluacionVirtual) {
        return actualizar("UPDATE evaluacion_virtual SET estado = 'CERRADA' "
                + "WHERE id_evaluacion_virtual = ? AND estado IN ('PROGRAMADA','ABIERTA')",
                ps -> ps.setInt(1, idEvaluacionVirtual)) > 0;
    }

    /** Cancelacion condicional: nunca re-cancela; CANCELADA queda fuera del programador. */
    public boolean reclamarCancelacion(int idEvaluacionVirtual) {
        return actualizar("UPDATE evaluacion_virtual SET estado = 'CANCELADA' "
                + "WHERE id_evaluacion_virtual = ? AND estado <> 'CANCELADA'",
                ps -> ps.setInt(1, idEvaluacionVirtual)) > 0;
    }

    /** Disponibilidad con la hora de la base de datos (autoridad de tiempo). */
    public boolean disponibleAhora(int idEvaluacionVirtual) {
        return existe("SELECT 1 FROM evaluacion_virtual WHERE id_evaluacion_virtual = ? "
                + "AND estado = 'ABIERTA' "
                + "AND NOW() BETWEEN fecha_inicio AND fecha_fin",
                ps -> ps.setInt(1, idEvaluacionVirtual));
    }

    private void cambiarEstado(EvaluacionVirtual virtual, String estadoBd, EstadoEvaluacionVirtual estado) {
        actualizar("UPDATE evaluacion_virtual SET estado = ? WHERE id_evaluacion_virtual = ?", ps -> {
            ps.setString(1, estadoBd);
            ps.setInt(2, virtual.consultarIdEvaluacionVirtual());
        });
        virtual.estadoEvaluacionVirtual = estado;
    }

    private EvaluacionVirtual mapear(ResultSet rs) throws SQLException {
        EvaluacionVirtual virtual = new EvaluacionVirtual();
        virtual.establecerIdEvaluacionVirtual(rs.getInt("id_evaluacion_virtual"));
        virtual.fechaInicioEvaluacionVirtual = Mapeos.fecha(rs, "fecha_inicio");
        virtual.fechaFinEvaluacionVirtual = Mapeos.fecha(rs, "fecha_fin");
        virtual.supervisionActivaEvaluacionVirtual = rs.getBoolean("supervision_activa");
        virtual.establecerIntentosPermitidos(rs.getInt("intentos_permitidos"));
        virtual.establecerExigirRespuestasCompletas(rs.getBoolean("exigir_respuestas_completas"));
        virtual.estadoEvaluacionVirtual = enumeracion(rs, "estado", EstadoEvaluacionVirtual.class);
        Evaluacion evaluacion = new Evaluacion();
        evaluacion.establecerIdEvaluacion(rs.getInt("id_evaluacion"));
        evaluacion.tituloEvaluacion = rs.getString("titulo");
        evaluacion.descripcionEvaluacion = rs.getString("descripcion");
        evaluacion.numeroPreguntasEvaluacion = rs.getInt("numero_preguntas");
        evaluacion.duracionMinutosEvaluacion = rs.getInt("duracion_minutos");
        virtual.evaluacionEvaluacionVirtual = evaluacion;
        return virtual;
    }
}
