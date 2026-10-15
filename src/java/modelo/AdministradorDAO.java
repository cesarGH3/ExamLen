package modelo;

import logica.Administrador;
import logica.HashContrasena;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.sql.Date;
import java.sql.Types;
import java.util.List;
import javax.sql.DataSource;

public class AdministradorDAO extends DaoBase {

    private final HashContrasena hashContrasena = new HashContrasena();

    public AdministradorDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Crea el administrador con la contraseña indicada. Ninguna pantalla lo usa: la cuenta inicial se crea por SQL. */
    public Administrador crearAdministrador(String identificacion, String nombre, String correo,
                                            String contrasenaTemporal, String institucion,
                                            java.util.Date fechaNacimiento) throws ValidacionException {
        Administrador admin = new Administrador();
        admin.identificacionUsuario = identificacion;
        admin.nombreUsuario = nombre;
        admin.correoUsuario = correo;
        admin.institucionUsuario = institucion;
        admin.fechaNacimientoAdministrador = fechaNacimiento;
        admin.contrasenaHashUsuario = hashContrasena.generarHash(contrasenaTemporal);
        admin.activoUsuario = true;

        try {
            int id = enTransaccion(c -> {
                int idUsuario = insertarCon(c, "INSERT INTO usuario (identificacion, nombre, correo, "
                        + "institucion, contrasena_hash, activo) VALUES (?, ?, ?, ?, ?, TRUE)", ps -> {
                    ps.setString(1, identificacion);
                    ps.setString(2, nombre);
                    ps.setString(3, correo);
                    if (institucion == null) {
                        ps.setNull(4, Types.VARCHAR);
                    } else {
                        ps.setString(4, institucion);
                    }
                    ps.setString(5, admin.contrasenaHashUsuario);
                });
                actualizarCon(c, "INSERT INTO administrador (id_usuario, fecha_nacimiento) VALUES (?, ?)", ps -> {
                    ps.setInt(1, idUsuario);
                    if (fechaNacimiento == null) {
                        ps.setNull(2, Types.DATE);
                    } else {
                        ps.setDate(2, new Date(fechaNacimiento.getTime()));
                    }
                });
                return idUsuario;
            });
            admin.setIdUsuario(id);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("La identificación o el correo ya están registrados.");
        }
        return admin;
    }

    /** Actualiza nombre, correo y fecha de nacimiento (CU-05). */
    public void actualizarAdministrador(Administrador administrador, String nombre, String correo,
                                        java.util.Date fechaNacimiento) throws ValidacionException {
        try {
            enTransaccion(c -> {
                actualizarCon(c, "UPDATE usuario u SET u.nombre = ?, u.correo = ? WHERE u.id_usuario = ? "
                        + "AND EXISTS (SELECT 1 FROM administrador a WHERE a.id_usuario = u.id_usuario)", ps -> {
                    ps.setString(1, nombre);
                    ps.setString(2, correo);
                    ps.setInt(3, administrador.getIdUsuario());
                });
                actualizarCon(c, "UPDATE administrador SET fecha_nacimiento = ? WHERE id_usuario = ?", ps -> {
                    if (fechaNacimiento == null) {
                        ps.setNull(1, Types.DATE);
                    } else {
                        ps.setDate(1, new Date(fechaNacimiento.getTime()));
                    }
                    ps.setInt(2, administrador.getIdUsuario());
                });
                return null;
            });
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ese correo ya está registrado en otra cuenta.");
        }
        administrador.nombreUsuario = nombre;
        administrador.correoUsuario = correo;
        administrador.fechaNacimientoAdministrador = fechaNacimiento;
    }

    public List<Administrador> listarAdministradores() {
        return consultarLista("SELECT " + Mapeos.USUARIO_COLUMNAS + " FROM " + Mapeos.USUARIO_ORIGEN
                + " WHERE a.id_usuario IS NOT NULL ORDER BY u.nombre", SIN_PARAMETROS,
                rs -> (Administrador) Mapeos.usuario(rs));
    }

    /** Administradores con la cuenta activa (para no eliminar al último, CU-05 5a). */
    public int contarActivos() {
        return contar("SELECT COUNT(*) FROM administrador a JOIN usuario u ON u.id_usuario = a.id_usuario "
                + "WHERE u.activo = TRUE", SIN_PARAMETROS);
    }

    public void eliminarAdministrador(Administrador administrador) {
        actualizar("DELETE FROM usuario WHERE id_usuario = ? AND EXISTS "
                + "(SELECT 1 FROM administrador a WHERE a.id_usuario = usuario.id_usuario)",
                ps -> ps.setInt(1, administrador.getIdUsuario()));
    }
}
