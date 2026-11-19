package modelo;

import logica.AccesoDatosException;
import logica.Administrador;
import logica.HashContrasena;
import logica.RegistroDuplicadoException;
import logica.ValidacionException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import javax.sql.DataSource;

public class AdministradorDAO extends DaoBase {

    private final HashContrasena hashContrasena = new HashContrasena();

    public AdministradorDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Crea el administrador con la contraseña indicada (alta administrativa y primer arranque). */
    public Administrador crearAdministrador(String identificacion, String nombre, String correo,
                                            String contrasenaTemporal, String institucion,
                                            java.util.Date fechaNacimiento) throws ValidacionException {
        Administrador administrador = construirAdministrador(identificacion, nombre, correo, contrasenaTemporal,
                institucion, fechaNacimiento);
        try {
            int id = enTransaccion(c -> insertarAdministrador(c, administrador, fechaNacimiento));
            administrador.establecerIdUsuario(id);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("La identificación o el correo ya están registrados.");
        }
        return administrador;
    }

    /**
     * Crea el primer administrador del sistema (pantalla de primer arranque). Dentro de la
     * transacción se bloquea la lectura del conteo (COUNT ... FOR UPDATE) para que dos
     * solicitudes concurrentes no puedan crear dos primeros administradores.
     */
    public Administrador crearPrimerAdministrador(String identificacion, String nombre, String correo,
                                                  String contrasena, String institucion,
                                                  java.util.Date fechaNacimiento) throws ValidacionException {
        Administrador administrador = construirAdministrador(identificacion, nombre, correo, contrasena,
                institucion, fechaNacimiento);
        try (Connection c = nuevaConexion()) {
            if (!adquirirBloqueoInstalacion(c)) {
                throw new AccesoDatosException("No se pudo obtener el bloqueo de instalacion.",
                        new SQLException("GET_LOCK agotado"));
            }
            try {
                c.setAutoCommit(false);
                try {
                    int existentes = consultarUnoCon(c, "SELECT COUNT(*) FROM administrador",
                            SIN_PARAMETROS, rs -> rs.getInt(1)).orElse(0);
                    if (existentes > 0) {
                        c.rollback();
                        throw new ValidacionException("Ya existe un administrador registrado.");
                    }
                    int id = insertarAdministrador(c, administrador, fechaNacimiento);
                    c.commit();
                    administrador.establecerIdUsuario(id);
                    return administrador;
                } catch (SQLException | RuntimeException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            } finally {
                liberarBloqueoInstalacion(c);
            }
        } catch (SQLException e) {
            throw traducir(e);
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("La identificación o el correo ya están registrados.");
        }
    }

    private static boolean adquirirBloqueoInstalacion(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT GET_LOCK(CONCAT('examlen:primer_administrador:', "
                        + "SUBSTRING(SHA2(DATABASE(), 256), 1, 24)), 10)");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    private static void liberarBloqueoInstalacion(Connection c) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT RELEASE_LOCK(CONCAT('examlen:primer_administrador:', "
                        + "SUBSTRING(SHA2(DATABASE(), 256), 1, 24)))")) {
            ps.execute();
        } catch (SQLException e) {
            // La conexion se cierra; MySQL libera automaticamente el bloqueo nominado.
        }
    }

    private Administrador construirAdministrador(String identificacion, String nombre, String correo,
                                                 String contrasena, String institucion,
                                                 java.util.Date fechaNacimiento) {
        Administrador administrador = new Administrador();
        administrador.identificacionUsuario = identificacion;
        administrador.nombreUsuario = nombre;
        administrador.correoUsuario = correo;
        administrador.institucionUsuario = institucion;
        administrador.fechaNacimientoAdministrador = fechaNacimiento;
        administrador.contrasenaHashUsuario = hashContrasena.generarHash(contrasena);
        administrador.activoUsuario = true;
        return administrador;
    }

    private static int insertarAdministrador(Connection c, Administrador administrador, java.util.Date fechaNacimiento)
            throws SQLException {
        int idUsuario = insertarCon(c, "INSERT INTO usuario (identificacion, nombre, correo, "
                + "institucion, contrasena_hash, activo) VALUES (?, ?, ?, ?, ?, TRUE)", ps -> {
            ps.setString(1, administrador.identificacionUsuario);
            ps.setString(2, administrador.nombreUsuario);
            ps.setString(3, administrador.correoUsuario);
            if (administrador.institucionUsuario == null) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, administrador.institucionUsuario);
            }
            ps.setString(5, administrador.contrasenaHashUsuario);
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
                    ps.setInt(3, administrador.consultarIdUsuario());
                });
                actualizarCon(c, "UPDATE administrador SET fecha_nacimiento = ? WHERE id_usuario = ?", ps -> {
                    if (fechaNacimiento == null) {
                        ps.setNull(1, Types.DATE);
                    } else {
                        ps.setDate(1, new Date(fechaNacimiento.getTime()));
                    }
                    ps.setInt(2, administrador.consultarIdUsuario());
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
                ps -> ps.setInt(1, administrador.consultarIdUsuario()));
    }
}
