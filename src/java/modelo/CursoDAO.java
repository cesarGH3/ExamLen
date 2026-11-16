package modelo;

import logica.Curso;
import logica.Docente;
import logica.NivelIngles;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class CursoDAO extends DaoBase {

    private static final String SELECT = "SELECT " + Mapeos.CURSO_COLUMNAS + " FROM " + Mapeos.CURSO_ORIGEN;

    public CursoDAO(DataSource dataSource) {
        super(dataSource);
    }

    public Curso crearCurso(Docente docente, String nombre, String descripcion, NivelIngles nivel)
            throws ValidacionException {
        Curso curso = new Curso();
        curso.nombreCurso = nombre;
        curso.descripcionCurso = descripcion;
        curso.nivelCurso = nivel;
        curso.activoCurso = true;
        curso.docenteCurso = docente;
        try {
            int id = insertar("INSERT INTO curso (id_docente, nombre, descripcion, nivel, activo) "
                    + "VALUES (?, ?, ?, ?, TRUE)", ps -> {
                ps.setInt(1, docente.consultarIdUsuario());
                ps.setString(2, nombre);
                ps.setString(3, descripcion);
                ps.setString(4, nivel.name());
            });
            curso.establecerIdCurso(id);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ya tienes un curso con ese nombre.");
        }
        return curso;
    }

    public void actualizarCurso(Curso curso, String nombre, String descripcion) throws ValidacionException {
        try {
            actualizar("UPDATE curso SET nombre = ?, descripcion = ? WHERE id_curso = ?", ps -> {
                ps.setString(1, nombre);
                ps.setString(2, descripcion);
                ps.setInt(3, curso.consultarIdCurso());
            });
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ya tienes un curso con ese nombre.");
        }
        curso.nombreCurso = nombre;
        curso.descripcionCurso = descripcion;
    }

    public void activarCurso(Curso curso) throws ValidacionException {
        if (curso.activoCurso) {
            throw new ValidacionException("El curso ya está activo.");
        }
        actualizar("UPDATE curso SET activo = TRUE WHERE id_curso = ?", ps -> ps.setInt(1, curso.consultarIdCurso()));
        curso.activoCurso = true;
    }

    /**
     * CU-11 5a: desactivar NO se bloquea por tener grupos abiertos. Los grupos existentes siguen
     * funcionando; solo se impide abrir grupos nuevos mientras el curso este inactivo.
     */
    public void desactivarCurso(Curso curso) throws ValidacionException {
        if (!curso.activoCurso) {
            throw new ValidacionException("El curso ya está inactivo.");
        }
        actualizar("UPDATE curso SET activo = FALSE WHERE id_curso = ?", ps -> ps.setInt(1, curso.consultarIdCurso()));
        curso.activoCurso = false;
    }

    public Optional<Curso> buscarPorId(int idCurso) {
        return consultarUno(SELECT + " WHERE c.id_curso = ?", ps -> ps.setInt(1, idCurso), Mapeos::curso);
    }

    /** Solo devuelve el curso si pertenece al docente indicado. */
    public Optional<Curso> buscarPorIdDeDocente(int idCurso, int idDocente) {
        return consultarUno(SELECT + " WHERE c.id_curso = ? AND c.id_docente = ?", ps -> {
            ps.setInt(1, idCurso);
            ps.setInt(2, idDocente);
        }, Mapeos::curso);
    }

    public List<Curso> listarPorDocente(int idDocente) {
        return consultarLista(SELECT + " WHERE c.id_docente = ? ORDER BY c.activo DESC, c.nombre",
                ps -> ps.setInt(1, idDocente), Mapeos::curso);
    }
}
