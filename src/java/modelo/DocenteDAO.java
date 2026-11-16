package modelo;

import logica.Administrador;
import logica.Docente;
import logica.HashContrasena;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.util.List;
import javax.sql.DataSource;

public class DocenteDAO extends DaoBase {

    private final HashContrasena hashContrasena = new HashContrasena();

    public DocenteDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Crea la cuenta del docente con contraseña temporal; el administrador queda como quien la registra. */
    public Docente registrarDocente(Administrador administrador, String identificacion, String nombre, String correo,
                                    String institucion, String contrasenaTemporal) throws ValidacionException {
        Docente docente = new Docente();
        docente.identificacionUsuario = identificacion;
        docente.nombreUsuario = nombre;
        docente.correoUsuario = correo;
        docente.institucionUsuario = institucion == null ? "" : institucion;
        docente.contrasenaHashUsuario = hashContrasena.generarHash(contrasenaTemporal);
        docente.activoUsuario = true;
        docente.debeCambiarContrasenaDocente = true;

        Integer registradoPor = administrador.consultarIdUsuario();
        try {
            int id = enTransaccion(c -> {
                int idUsuario = insertarCon(c, "INSERT INTO usuario (identificacion, nombre, correo, "
                        + "institucion, contrasena_hash, activo) VALUES (?, ?, ?, ?, ?, TRUE)", ps -> {
                    ps.setString(1, identificacion);
                    ps.setString(2, nombre);
                    ps.setString(3, correo);
                    ps.setString(4, docente.institucionUsuario);
                    ps.setString(5, docente.contrasenaHashUsuario);
                });
                actualizarCon(c, "INSERT INTO docente (id_usuario, debe_cambiar_contrasena, id_administrador) "
                        + "VALUES (?, TRUE, ?)", ps -> {
                    ps.setInt(1, idUsuario);
                    ps.setInt(2, registradoPor);
                });
                return idUsuario;
            });
            docente.establecerIdUsuario(id);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("La identificación o el correo ya están registrados.");
        }
        return docente;
    }

    public List<Docente> listarDocentes() {
        return consultarLista("SELECT " + Mapeos.USUARIO_COLUMNAS + " FROM " + Mapeos.USUARIO_ORIGEN
                + " WHERE d.id_usuario IS NOT NULL ORDER BY u.nombre", SIN_PARAMETROS,
                rs -> (Docente) Mapeos.usuario(rs));
    }
}
