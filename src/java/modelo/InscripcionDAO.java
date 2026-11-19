package modelo;

import logica.Fechas;

import logica.EstadoGrupo;
import logica.EstadoInscripcion;
import logica.Estudiante;
import logica.Grupo;
import logica.Inscripcion;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class InscripcionDAO extends DaoBase {

    private static final String SELECT = "SELECT " + Mapeos.INSCRIPCION_COLUMNAS + " FROM " + Mapeos.INSCRIPCION_ORIGEN;

    public InscripcionDAO(DataSource dataSource) {
        super(dataSource);
    }

    private static final String GRUPO_CERRADO = "Este grupo está cerrado y no admite inscripciones.";
    private static final String GRUPO_CERRADO_CAMBIOS = "El grupo está cerrado y no admite cambios en sus inscripciones.";

    /**
     * Comprobacion y alta en una sola transaccion: la fila del grupo se bloquea con FOR UPDATE,
     * de modo que un cierre concurrente espera o se aplica antes de la lectura, nunca entre la
     * comprobacion y el INSERT. La base lo refuerza con trg_inscripcion_bi (migracion 025).
     */
    public Inscripcion crearInscripcion(Estudiante estudiante, Grupo grupo) throws ValidacionException {
        Inscripcion inscripcion = new Inscripcion();
        inscripcion.estudianteInscripcion = estudiante;
        inscripcion.grupoInscripcion = grupo;
        inscripcion.estadoInscripcion = EstadoInscripcion.ACTIVA;
        inscripcion.fechaInscripcion = Fechas.ahora();
        String rechazo;
        try {
            rechazo = enTransaccion(c -> {
                List<String> estadoGrupo = consultarListaCon(c, "SELECT estado FROM grupo WHERE id_grupo = ? FOR UPDATE",
                        ps -> ps.setInt(1, grupo.consultarIdGrupo()), rs -> rs.getString(1));
                if (estadoGrupo.isEmpty() || !EstadoGrupo.ABIERTO.name().equals(estadoGrupo.get(0))) {
                    return GRUPO_CERRADO;
                }
                List<String> previa = consultarListaCon(c, "SELECT estado FROM inscripcion "
                        + "WHERE id_grupo = ? AND id_estudiante = ? FOR UPDATE", ps -> {
                            ps.setInt(1, grupo.consultarIdGrupo());
                            ps.setInt(2, estudiante.consultarIdUsuario());
                        }, rs -> rs.getString(1));
                if (!previa.isEmpty()) {
                    return EstadoInscripcion.ACTIVA.name().equals(previa.get(0))
                            ? "Ya perteneces a este grupo."
                            : "Fuiste retirado de este grupo. Pídele a tu docente que te reincorpore.";
                }
                inscripcion.establecerIdInscripcion(insertarCon(c, "INSERT INTO inscripcion (id_grupo, id_estudiante, estado) "
                        + "VALUES (?, ?, 'ACTIVA')", ps -> {
                            ps.setInt(1, grupo.consultarIdGrupo());
                            ps.setInt(2, estudiante.consultarIdUsuario());
                        }));
                return null;
            });
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ya perteneces a este grupo.");
        }
        if (rechazo != null) {
            if (GRUPO_CERRADO.equals(rechazo)) {
                grupo.estadoGrupo = EstadoGrupo.CERRADO;
            }
            throw new ValidacionException(rechazo);
        }
        grupo.inscripcionesGrupo.add(inscripcion);
        estudiante.inscripcionesEstudiante.add(inscripcion);
        return inscripcion;
    }

    /** Elimina la inscripción. Si ya tiene historial, la base de datos lo impide (RegistroEnUsoException). */
    public void eliminarInscripcion(Inscripcion inscripcion) throws ValidacionException {
        cambiarEnGrupoAbierto(inscripcion, null, "DELETE FROM inscripcion WHERE id_inscripcion = ?");
        // Mantiene coherentes los objetos ya cargados que se reutilicen tras la eliminacion.
        int id = inscripcion.consultarIdInscripcion();
        if (inscripcion.grupoInscripcion != null) {
            inscripcion.grupoInscripcion.inscripcionesGrupo.removeIf(i -> i.consultarIdInscripcion() == id);
        }
        if (inscripcion.estudianteInscripcion != null) {
            inscripcion.estudianteInscripcion.inscripcionesEstudiante.removeIf(i -> i.consultarIdInscripcion() == id);
        }
    }

    public void retirarInscripcion(Inscripcion inscripcion) throws ValidacionException {
        cambiarEnGrupoAbierto(inscripcion, EstadoInscripcion.ACTIVA,
                "UPDATE inscripcion SET estado = 'RETIRADA' WHERE id_inscripcion = ?");
        inscripcion.estadoInscripcion = EstadoInscripcion.RETIRADA;
    }

    public void reincorporarInscripcion(Inscripcion inscripcion) throws ValidacionException {
        cambiarEnGrupoAbierto(inscripcion, EstadoInscripcion.RETIRADA,
                "UPDATE inscripcion SET estado = 'ACTIVA' WHERE id_inscripcion = ?");
        inscripcion.estadoInscripcion = EstadoInscripcion.ACTIVA;
    }

    /**
     * Bloquea inscripcion y grupo (FOR UPDATE), valida con el estado vigente en la base y aplica
     * el cambio en la misma transaccion: el resultado no depende del objeto leido antes.
     */
    private void cambiarEnGrupoAbierto(Inscripcion inscripcion, EstadoInscripcion estadoRequerido, String sql)
            throws ValidacionException {
        String rechazo = enTransaccion(c -> {
            List<String[]> estado = consultarListaCon(c, "SELECT g.estado, i.estado FROM inscripcion i "
                    + "JOIN grupo g ON g.id_grupo = i.id_grupo WHERE i.id_inscripcion = ? FOR UPDATE",
                    ps -> ps.setInt(1, inscripcion.consultarIdInscripcion()),
                    rs -> new String[]{rs.getString(1), rs.getString(2)});
            if (estado.isEmpty()) {
                return "La inscripción ya no existe.";
            }
            if (!EstadoGrupo.ABIERTO.name().equals(estado.get(0)[0])) {
                return GRUPO_CERRADO_CAMBIOS;
            }
            if (estadoRequerido != null && !estadoRequerido.name().equals(estado.get(0)[1])) {
                return estadoRequerido == EstadoInscripcion.ACTIVA
                        ? "Esta inscripción ya estaba retirada."
                        : "El estudiante ya está activo en el grupo.";
            }
            actualizarCon(c, sql, ps -> ps.setInt(1, inscripcion.consultarIdInscripcion()));
            return null;
        });
        if (rechazo != null) {
            throw new ValidacionException(rechazo);
        }
    }

    public Optional<Inscripcion> buscarPorId(int idInscripcion) {
        return consultarUno(SELECT + " WHERE i.id_inscripcion = ?", ps -> ps.setInt(1, idInscripcion), Mapeos::inscripcion);
    }

    /** Solo devuelve la inscripción si su grupo pertenece al docente indicado. */
    public Optional<Inscripcion> buscarPorIdDeDocente(int idInscripcion, int idDocente) {
        return consultarUno(SELECT + " WHERE i.id_inscripcion = ? AND c.id_docente = ?", ps -> {
            ps.setInt(1, idInscripcion);
            ps.setInt(2, idDocente);
        }, Mapeos::inscripcion);
    }

    /** Solo devuelve la inscripción si pertenece al estudiante indicado. */
    public Optional<Inscripcion> buscarPorIdDeEstudiante(int idInscripcion, int idEstudiante) {
        return consultarUno(SELECT + " WHERE i.id_inscripcion = ? AND i.id_estudiante = ?", ps -> {
            ps.setInt(1, idInscripcion);
            ps.setInt(2, idEstudiante);
        }, Mapeos::inscripcion);
    }

    public Optional<Inscripcion> buscarPorGrupoYEstudiante(int idGrupo, int idEstudiante) {
        return consultarUno(SELECT + " WHERE i.id_grupo = ? AND i.id_estudiante = ?", ps -> {
            ps.setInt(1, idGrupo);
            ps.setInt(2, idEstudiante);
        }, Mapeos::inscripcion);
    }

    public List<Inscripcion> listarPorGrupo(int idGrupo) {
        return consultarLista(SELECT + " WHERE i.id_grupo = ? ORDER BY e.nombre",
                ps -> ps.setInt(1, idGrupo), Mapeos::inscripcion);
    }

    public List<Inscripcion> listarActivasPorEstudiante(int idEstudiante) {
        return consultarLista(SELECT + " WHERE i.id_estudiante = ? AND i.estado = 'ACTIVA' "
                + "ORDER BY g.estado, g.periodo DESC, c.nombre",
                ps -> ps.setInt(1, idEstudiante), Mapeos::inscripcion);
    }

    public int contarActivosPorGrupo(int idGrupo) {
        return contar("SELECT COUNT(*) FROM inscripcion WHERE id_grupo = ? AND estado = 'ACTIVA'",
                ps -> ps.setInt(1, idGrupo));
    }
}
