package modelo;

import logica.Administrador;
import logica.Curso;
import logica.Docente;
import logica.Estudiante;
import logica.EstadoGrupo;
import logica.EstadoInscripcion;
import logica.Grupo;
import logica.Inscripcion;
import logica.NivelIngles;
import logica.Usuario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

/** Columnas y mapeos compartidos (nombres de columna alineados al diagrama v3). */
final class Mapeos {

    /** Roles del sistema: el rol se deduce de en cual tabla hija existe la fila. */
    static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    static final String ROL_DOCENTE = "DOCENTE";
    static final String ROL_ESTUDIANTE = "ESTUDIANTE";

    static final String USUARIO_ROL = "CASE WHEN a.id_usuario IS NOT NULL THEN '" + ROL_ADMINISTRADOR
            + "' WHEN d.id_usuario IS NOT NULL THEN '" + ROL_DOCENTE
            + "' WHEN es.id_usuario IS NOT NULL THEN '" + ROL_ESTUDIANTE
            + "' END AS rol";

    static final String USUARIO_COLUMNAS = "u.id_usuario, u.identificacion, u.nombre, u.correo, "
            + "u.institucion, u.contrasena_hash, u.activo, u.debe_cambiar_contrasena, " + USUARIO_ROL + ", "
            + "a.fecha_nacimiento, d.debe_cambiar_contrasena AS debe_cambiar_contrasena_docente, es.nivel_ingles";

    static final String USUARIO_ORIGEN = "usuario u "
            + "LEFT JOIN administrador a ON a.id_usuario = u.id_usuario "
            + "LEFT JOIN docente d ON d.id_usuario = u.id_usuario "
            + "LEFT JOIN estudiante es ON es.id_usuario = u.id_usuario";

    static final String CURSO_COLUMNAS = "c.id_curso, c.id_docente, c.nombre AS c_nombre, "
            + "c.descripcion AS c_descripcion, c.nivel AS c_nivel, c.activo AS c_activo, "
            + "d.id_usuario AS d_id, d.nombre AS d_nombre";

    static final String CURSO_ORIGEN = "curso c JOIN usuario d ON d.id_usuario = c.id_docente";

    static final String GRUPO_COLUMNAS = CURSO_COLUMNAS + ", g.id_grupo, g.nombre AS g_nombre, "
            + "g.periodo AS g_periodo, g.codigo_acceso AS g_codigo, g.estado AS g_estado";

    static final String GRUPO_ORIGEN = "grupo g JOIN curso c ON c.id_curso = g.id_curso "
            + "JOIN usuario d ON d.id_usuario = c.id_docente";

    static final String INSCRIPCION_COLUMNAS = GRUPO_COLUMNAS + ", i.id_inscripcion, "
            + "i.fecha AS i_fecha, i.estado AS i_estado, e.id_usuario AS e_id, "
            + "e.identificacion AS e_identificacion, e.nombre AS e_nombre, e.correo AS e_correo";

    static final String INSCRIPCION_ORIGEN = "inscripcion i JOIN usuario e ON e.id_usuario = i.id_estudiante "
            + "JOIN grupo g ON g.id_grupo = i.id_grupo JOIN curso c ON c.id_curso = g.id_curso "
            + "JOIN usuario d ON d.id_usuario = c.id_docente";

    private Mapeos() {
    }

    static Date fecha(ResultSet rs, String columna) throws SQLException {
        Timestamp valor = rs.getTimestamp(columna);
        return valor == null ? null : new Date(valor.getTime());
    }

    static Usuario usuario(ResultSet rs) throws SQLException {
        String rol = rs.getString("rol");
        Usuario u;
        if (ROL_ADMINISTRADOR.equals(rol)) {
            Administrador administrador = new Administrador();
            administrador.fechaNacimientoAdministrador = rs.getDate("fecha_nacimiento");
            u = administrador;
        } else if (ROL_DOCENTE.equals(rol)) {
            Docente docente = new Docente();
            docente.debeCambiarContrasenaDocente = rs.getBoolean("debe_cambiar_contrasena_docente");
            u = docente;
        } else if (ROL_ESTUDIANTE.equals(rol)) {
            Estudiante estudiante = new Estudiante();
            estudiante.nivelInglesEstudiante = rs.getString("nivel_ingles");
            u = estudiante;
        } else {
            throw new SQLException("El usuario no tiene fila en ninguna tabla de rol");
        }
        u.establecerIdUsuario(rs.getInt("id_usuario"));
        u.identificacionUsuario = rs.getString("identificacion");
        u.nombreUsuario = rs.getString("nombre");
        u.correoUsuario = rs.getString("correo");
        u.institucionUsuario = rs.getString("institucion");
        u.contrasenaHashUsuario = rs.getString("contrasena_hash");
        u.activoUsuario = rs.getBoolean("activo");
        u.debeCambiarContrasena = rs.getBoolean("debe_cambiar_contrasena");
        return u;
    }

    static Curso curso(ResultSet rs) throws SQLException {
        Curso curso = new Curso();
        curso.establecerIdCurso(rs.getInt("id_curso"));
        curso.nombreCurso = rs.getString("c_nombre");
        curso.descripcionCurso = rs.getString("c_descripcion");
        curso.nivelCurso = NivelIngles.valueOf(rs.getString("c_nivel"));
        curso.activoCurso = rs.getBoolean("c_activo");
        Docente docente = new Docente();
        docente.establecerIdUsuario(rs.getInt("d_id"));
        docente.nombreUsuario = rs.getString("d_nombre");
        curso.docenteCurso = docente;
        return curso;
    }

    static Grupo grupo(ResultSet rs) throws SQLException {
        Grupo grupo = new Grupo();
        grupo.establecerIdGrupo(rs.getInt("id_grupo"));
        grupo.nombreGrupo = rs.getString("g_nombre");
        grupo.periodoGrupo = rs.getString("g_periodo");
        grupo.codigoAccesoGrupo = rs.getString("g_codigo");
        grupo.estadoGrupo = EstadoGrupo.valueOf(rs.getString("g_estado"));
        grupo.cursoGrupo = curso(rs);
        return grupo;
    }

    static Inscripcion inscripcion(ResultSet rs) throws SQLException {
        Estudiante estudiante = new Estudiante();
        estudiante.establecerIdUsuario(rs.getInt("e_id"));
        estudiante.identificacionUsuario = rs.getString("e_identificacion");
        estudiante.nombreUsuario = rs.getString("e_nombre");
        estudiante.correoUsuario = rs.getString("e_correo");

        Inscripcion inscripcion = new Inscripcion();
        inscripcion.establecerIdInscripcion(rs.getInt("id_inscripcion"));
        inscripcion.fechaInscripcion = fecha(rs, "i_fecha");
        inscripcion.estadoInscripcion = EstadoInscripcion.valueOf(rs.getString("i_estado"));
        inscripcion.estudianteInscripcion = estudiante;
        inscripcion.grupoInscripcion = grupo(rs);
        return inscripcion;
    }
}
