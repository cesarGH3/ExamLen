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

    public Inscripcion crearInscripcion(Estudiante estudiante, Grupo grupo) throws ValidacionException {
        if (grupo.estadoGrupo != EstadoGrupo.ABIERTO) {
            throw new ValidacionException("Este grupo está cerrado y no admite inscripciones.");
        }
        Optional<Inscripcion> previa = buscarPorGrupoYEstudiante(grupo.getIdGrupo(), estudiante.getIdUsuario());
        if (previa.isPresent()) {
            throw new ValidacionException(previa.get().estadoInscripcion == EstadoInscripcion.ACTIVA
                    ? "Ya perteneces a este grupo."
                    : "Fuiste retirado de este grupo. Pídele a tu docente que te reincorpore.");
        }
        Inscripcion inscripcion = new Inscripcion();
        inscripcion.estudianteInscripcion = estudiante;
        inscripcion.grupoInscripcion = grupo;
        inscripcion.estadoInscripcion = EstadoInscripcion.ACTIVA;
        inscripcion.fechaInscripcion = Fechas.ahora();
        try {
            int id = insertar("INSERT INTO inscripcion (id_grupo, id_estudiante, estado) "
                    + "VALUES (?, ?, 'ACTIVA')", ps -> {
                ps.setInt(1, grupo.getIdGrupo());
                ps.setInt(2, estudiante.getIdUsuario());
            });
            inscripcion.setIdInscripcion(id);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ya perteneces a este grupo.");
        }
        grupo.inscripcionesGrupo.add(inscripcion);
        estudiante.inscripcionesEstudiante.add(inscripcion);
        return inscripcion;
    }

    /** Elimina la inscripción. Si ya tiene historial, la base de datos lo impide (RegistroEnUsoException). */
    public void eliminarInscripcion(Inscripcion inscripcion) throws ValidacionException {
        exigirGrupoAbierto(inscripcion);
        actualizar("DELETE FROM inscripcion WHERE id_inscripcion = ?",
                ps -> ps.setInt(1, inscripcion.getIdInscripcion()));
    }

    public void retirarInscripcion(Inscripcion inscripcion) throws ValidacionException {
        exigirGrupoAbierto(inscripcion);
        if (inscripcion.estadoInscripcion != EstadoInscripcion.ACTIVA) {
            throw new ValidacionException("Esta inscripción ya estaba retirada.");
        }
        actualizar("UPDATE inscripcion SET estado = 'RETIRADA' WHERE id_inscripcion = ?",
                ps -> ps.setInt(1, inscripcion.getIdInscripcion()));
        inscripcion.estadoInscripcion = EstadoInscripcion.RETIRADA;
    }

    public void reincorporarInscripcion(Inscripcion inscripcion) throws ValidacionException {
        exigirGrupoAbierto(inscripcion);
        if (inscripcion.estadoInscripcion == EstadoInscripcion.ACTIVA) {
            throw new ValidacionException("El estudiante ya está activo en el grupo.");
        }
        actualizar("UPDATE inscripcion SET estado = 'ACTIVA' WHERE id_inscripcion = ?",
                ps -> ps.setInt(1, inscripcion.getIdInscripcion()));
        inscripcion.estadoInscripcion = EstadoInscripcion.ACTIVA;
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

    private void exigirGrupoAbierto(Inscripcion inscripcion) throws ValidacionException {
        if (inscripcion.grupoInscripcion != null && inscripcion.grupoInscripcion.estadoGrupo != EstadoGrupo.ABIERTO) {
            throw new ValidacionException("El grupo está cerrado y no admite cambios en sus inscripciones.");
        }
    }
}
