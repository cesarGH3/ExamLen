package modelo;

import logica.Curso;
import logica.EstadoGrupo;
import logica.GeneradorCodigoAcceso;
import logica.Grupo;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class GrupoDAO extends DaoBase {

    private static final String SELECT = "SELECT " + Mapeos.GRUPO_COLUMNAS + " FROM " + Mapeos.GRUPO_ORIGEN;
    private static final int INTENTOS_CODIGO = 5;

    private final GeneradorCodigoAcceso generadorCodigo;

    public GrupoDAO(DataSource dataSource) {
        this(dataSource, new GeneradorCodigoAcceso());
    }

    public GrupoDAO(DataSource dataSource, GeneradorCodigoAcceso generadorCodigo) {
        super(dataSource);
        this.generadorCodigo = generadorCodigo;
    }

    public Grupo abrirGrupo(Curso curso, String nombre, String periodo) throws ValidacionException {
        if (!curso.activoCurso) {
            throw new ValidacionException("No puedes abrir grupos en un curso inactivo. Actívalo primero.");
        }
        if (existeEnPeriodo(curso.getIdCurso(), periodo, nombre)) {
            throw new ValidacionException("Ya existe un grupo llamado " + nombre + " en el periodo " + periodo + ".");
        }
        for (int intento = 0; intento < INTENTOS_CODIGO; intento++) {
            Grupo grupo = new Grupo();
            grupo.nombreGrupo = nombre;
            grupo.periodoGrupo = periodo;
            grupo.codigoAccesoGrupo = generadorCodigo.generar();
            grupo.estadoGrupo = EstadoGrupo.ABIERTO;
            grupo.cursoGrupo = curso;
            try {
                int id = insertar("INSERT INTO grupo (id_curso, nombre, periodo, codigo_acceso, "
                        + "estado) VALUES (?, ?, ?, ?, 'ABIERTO')", ps -> {
                    ps.setInt(1, curso.getIdCurso());
                    ps.setString(2, nombre);
                    ps.setString(3, periodo);
                    ps.setString(4, grupo.codigoAccesoGrupo);
                });
                grupo.setIdGrupo(id);
                curso.gruposCurso.add(grupo);
                return grupo;
            } catch (RegistroDuplicadoException e) {
                // Código repetido (muy improbable): se genera otro.
            }
        }
        throw new ValidacionException("No fue posible abrir el grupo. Inténtalo de nuevo.");
    }

    public String generarCodigoAccesoGrupo(Grupo grupo) throws ValidacionException {
        for (int intento = 0; intento < INTENTOS_CODIGO; intento++) {
            String codigo = generadorCodigo.generar();
            try {
                int filas = actualizar("UPDATE grupo SET codigo_acceso = ? "
                        + "WHERE id_grupo = ? AND estado = 'ABIERTO'", ps -> {
                    ps.setString(1, codigo);
                    ps.setInt(2, grupo.getIdGrupo());
                });
                if (filas == 0) {
                    throw new ValidacionException(
                            "El grupo está cerrado: no se puede regenerar su código de acceso.");
                }
                grupo.codigoAccesoGrupo = codigo;
                return codigo;
            } catch (RegistroDuplicadoException e) {
                // Código repetido: se genera otro.
            }
        }
        throw new ValidacionException("No fue posible generar un código nuevo. Inténtalo de nuevo.");
    }

    public void cerrarGrupo(Grupo grupo) throws ValidacionException {
        if (grupo.estadoGrupo != EstadoGrupo.ABIERTO) {
            throw new ValidacionException("El grupo ya está cerrado.");
        }
        actualizar("UPDATE grupo SET estado = 'CERRADO' WHERE id_grupo = ?", ps -> ps.setInt(1, grupo.getIdGrupo()));
        grupo.estadoGrupo = EstadoGrupo.CERRADO;
    }

    public void reabrirGrupo(Grupo grupo) throws ValidacionException {
        if (grupo.estadoGrupo == EstadoGrupo.ABIERTO) {
            throw new ValidacionException("El grupo ya está abierto.");
        }
        if (grupo.cursoGrupo != null && !grupo.cursoGrupo.activoCurso) {
            throw new ValidacionException("Activa el curso antes de reabrir este grupo.");
        }
        actualizar("UPDATE grupo SET estado = 'ABIERTO' WHERE id_grupo = ?", ps -> ps.setInt(1, grupo.getIdGrupo()));
        grupo.estadoGrupo = EstadoGrupo.ABIERTO;
    }

    public Optional<Grupo> buscarPorId(int idGrupo) {
        return consultarUno(SELECT + " WHERE g.id_grupo = ?", ps -> ps.setInt(1, idGrupo), Mapeos::grupo);
    }

    /** Solo devuelve el grupo si su curso pertenece al docente indicado. */
    public Optional<Grupo> buscarPorIdDeDocente(int idGrupo, int idDocente) {
        return consultarUno(SELECT + " WHERE g.id_grupo = ? AND c.id_docente = ?", ps -> {
            ps.setInt(1, idGrupo);
            ps.setInt(2, idDocente);
        }, Mapeos::grupo);
    }

    public Optional<Grupo> buscarPorCodigo(String codigoAcceso) {
        return consultarUno(SELECT + " WHERE g.codigo_acceso = ?", ps -> ps.setString(1, codigoAcceso), Mapeos::grupo);
    }

    public List<Grupo> listarPorCurso(int idCurso) {
        return consultarLista(SELECT + " WHERE g.id_curso = ? ORDER BY g.estado, g.periodo DESC, g.nombre",
                ps -> ps.setInt(1, idCurso), Mapeos::grupo);
    }

    public boolean existeEnPeriodo(int idCurso, String periodo, String nombre) {
        return existe("SELECT 1 FROM grupo WHERE id_curso = ? AND periodo = ? AND nombre = ? LIMIT 1", ps -> {
            ps.setInt(1, idCurso);
            ps.setString(2, periodo);
            ps.setString(3, nombre);
        });
    }

    public int contarAbiertosPorCurso(int idCurso) {
        return contar("SELECT COUNT(*) FROM grupo WHERE id_curso = ? AND estado = 'ABIERTO'",
                ps -> ps.setInt(1, idCurso));
    }
}
