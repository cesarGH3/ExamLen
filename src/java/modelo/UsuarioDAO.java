package modelo;

import logica.Docente;
import logica.HashContrasena;
import logica.RegistroDuplicadoException;
import logica.Usuario;
import logica.ValidacionException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class UsuarioDAO extends DaoBase {

    private static final String COLUMNAS = Mapeos.USUARIO_COLUMNAS;
    private static final String ORIGEN = Mapeos.USUARIO_ORIGEN;

    private final HashContrasena hashContrasena = new HashContrasena();
    private final String hashFicticio = hashContrasena.generarHash(UUID.randomUUID() + "Aa1!");

    public UsuarioDAO(DataSource dataSource) {
        super(dataSource);
    }

    public Optional<Usuario> buscarPorId(int idUsuario) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN + " WHERE u.id_usuario = ?",
                ps -> ps.setInt(1, idUsuario), Mapeos::usuario);
    }

    public Optional<Usuario> buscarPorCorreo(String correo) {
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN + " WHERE u.correo = ?",
                ps -> ps.setString(1, correo), Mapeos::usuario);
    }

    /**
     * RF-01: resuelve el acceso por correo o por identificacion con busqueda controlada y consultas
     * preparadas: primero correo normalizado; si no hay coincidencia, identificacion exacta.
     */
    public Optional<Usuario> buscarPorCorreoOIdentificacion(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String limpio = texto.strip();
        Optional<Usuario> porCorreo = buscarPorCorreo(limpio.toLowerCase(java.util.Locale.ROOT));
        if (porCorreo.isPresent()) {
            return porCorreo;
        }
        return consultarUno("SELECT " + COLUMNAS + " FROM " + ORIGEN + " WHERE u.identificacion = ?",
                ps -> ps.setString(1, limpio), Mapeos::usuario);
    }

    /**
     * RNF-01: si el hash almacenado es legacy (PBKDF2), tras un login correcto lo reemplaza
     * inmediatamente por BCrypt de forma atomica. Devuelve true si migro el hash.
     */
    public boolean rehashSiLegacy(Usuario usuario, String contrasenaPlano) {
        if (usuario == null || !hashContrasena.necesitaRehash(usuario.contrasenaHashUsuario)) {
            return false;
        }
        String nuevo = hashContrasena.generarHash(contrasenaPlano);
        actualizar("UPDATE usuario SET contrasena_hash = ? WHERE id_usuario = ? AND contrasena_hash = ?", ps -> {
            ps.setString(1, nuevo);
            ps.setInt(2, usuario.getIdUsuario());
            ps.setString(3, usuario.contrasenaHashUsuario);
        });
        usuario.contrasenaHashUsuario = nuevo;
        return true;
    }

    /** Cuenta hashes no conformes con RNF-01 (auditoria de la migracion legacy). */
    public int contarHashesNoBcrypt() {
        return contar("SELECT COUNT(*) FROM usuario WHERE contrasena_hash NOT LIKE '$2a$%' "
                + "AND contrasena_hash NOT LIKE '$2b$%' AND contrasena_hash NOT LIKE '$2y$%'", SIN_PARAMETROS);
    }

    /** Cuentas activas por rol (resumen del inicio). */
    public int contarActivosPorRol(String rol) {
        switch (rol) {
            case Mapeos.ROL_ADMINISTRADOR:
                return contar("SELECT COUNT(*) FROM administrador a JOIN usuario u ON u.id_usuario = a.id_usuario "
                        + "WHERE u.activo = TRUE", SIN_PARAMETROS);
            case Mapeos.ROL_DOCENTE:
                return contar("SELECT COUNT(*) FROM docente d JOIN usuario u ON u.id_usuario = d.id_usuario "
                        + "WHERE u.activo = TRUE", SIN_PARAMETROS);
            case Mapeos.ROL_ESTUDIANTE:
                return contar("SELECT COUNT(*) FROM estudiante e JOIN usuario u ON u.id_usuario = e.id_usuario "
                        + "WHERE u.activo = TRUE", SIN_PARAMETROS);
            default:
                return 0;
        }
    }

    /** Rol del usuario segun en cual tabla hija existe la fila. */
    public Optional<String> rolDeUsuario(int idUsuario) {
        return consultarUno("SELECT " + Mapeos.USUARIO_ROL + " FROM " + ORIGEN + " WHERE u.id_usuario = ?",
                ps -> ps.setInt(1, idUsuario), rs -> rs.getString("rol"));
    }

    public boolean existeCorreo(String correo) {
        return existe("SELECT 1 FROM usuario WHERE correo = ? LIMIT 1", ps -> ps.setString(1, correo));
    }

    public boolean existeIdentificacion(String identificacion) {
        return existe("SELECT 1 FROM usuario WHERE identificacion = ? LIMIT 1",
                ps -> ps.setString(1, identificacion));
    }

    /** Verifica la contraseña contra el hash; con usuario nulo compara contra el hash ficticio (mismo tiempo). */
    public boolean verificarContrasenaUsuario(Usuario usuario, String contrasena) {
        if (contrasena == null || contrasena.length() > logica.Validador.MAX_CONTRASENA) {
            hashContrasena.verificar(contrasena == null ? "" : contrasena, hashFicticio);
            return false;
        }
        String hash = usuario == null ? hashFicticio : usuario.contrasenaHashUsuario;
        return hashContrasena.verificar(contrasena, hash);
    }

    public boolean estaBloqueado(int idUsuario) {
        return existe("SELECT 1 FROM usuario WHERE id_usuario = ? AND bloqueado_hasta IS NOT NULL "
                + "AND bloqueado_hasta > NOW()", ps -> ps.setInt(1, idUsuario));
    }

    public void registrarIntentoFallido(int idUsuario, int maxIntentos, int minutosBloqueo) {
        String sql = "UPDATE usuario SET "
                + "bloqueado_hasta = CASE WHEN intentos_fallidos + 1 >= ? THEN DATE_ADD(NOW(), INTERVAL ? MINUTE) ELSE bloqueado_hasta END, "
                + "intentos_fallidos = CASE WHEN intentos_fallidos + 1 >= ? THEN 0 ELSE intentos_fallidos + 1 END "
                + "WHERE id_usuario = ?";
        actualizar(sql, ps -> {
            ps.setInt(1, maxIntentos);
            ps.setInt(2, minutosBloqueo);
            ps.setInt(3, maxIntentos);
            ps.setInt(4, idUsuario);
        });
    }

    public void reiniciarIntentos(int idUsuario) {
        actualizar("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL "
                + "WHERE id_usuario = ? AND (intentos_fallidos <> 0 OR bloqueado_hasta IS NOT NULL)",
                ps -> ps.setInt(1, idUsuario));
    }

    /** Verifica la contraseña actual, guarda la nueva y quita el cambio obligatorio (cualquier rol). */
    public boolean cambiarContrasenaUsuario(Usuario usuario, String contrasenaActual, String nuevaContrasena) {
        if (!verificarContrasenaUsuario(usuario, contrasenaActual)) {
            return false;
        }
        String hash = hashContrasena.generarHash(nuevaContrasena);
        boolean esDocente = usuario instanceof Docente;
        enTransaccion(c -> {
            actualizarCon(c, "UPDATE usuario SET contrasena_hash = ?, debe_cambiar_contrasena = FALSE "
                    + "WHERE id_usuario = ?", ps -> {
                ps.setString(1, hash);
                ps.setInt(2, usuario.getIdUsuario());
            });
            if (esDocente) {
                actualizarCon(c, "UPDATE docente SET debe_cambiar_contrasena = FALSE WHERE id_usuario = ?",
                        ps -> ps.setInt(1, usuario.getIdUsuario()));
            }
            return null;
        });
        usuario.contrasenaHashUsuario = hash;
        usuario.debeCambiarContrasena = false;
        if (esDocente) {
            ((Docente) usuario).debeCambiarContrasenaDocente = false;
        }
        return true;
    }

    public void actualizarUsuario(Usuario usuario, String nombre, String correo) throws ValidacionException {
        if (!correo.equals(usuario.correoUsuario) && existeCorreo(correo)) {
            throw new ValidacionException("Ese correo ya está registrado en otra cuenta.");
        }
        try {
            actualizar("UPDATE usuario SET nombre = ?, correo = ? WHERE id_usuario = ?", ps -> {
                ps.setString(1, nombre);
                ps.setString(2, correo);
                ps.setInt(3, usuario.getIdUsuario());
            });
        } catch (RegistroDuplicadoException e) {
            throw new ValidacionException("Ese correo ya está registrado en otra cuenta.");
        }
        usuario.nombreUsuario = nombre;
        usuario.correoUsuario = correo;
    }

    public void activarUsuario(Usuario usuario) {
        actualizar("UPDATE usuario SET activo = TRUE WHERE id_usuario = ?",
                ps -> ps.setInt(1, usuario.getIdUsuario()));
        usuario.activoUsuario = true;
    }

    public void desactivarUsuario(Usuario usuario) {
        actualizar("UPDATE usuario SET activo = FALSE WHERE id_usuario = ?",
                ps -> ps.setInt(1, usuario.getIdUsuario()));
        usuario.activoUsuario = false;
    }

    /**
     * RF-10: restablece la contraseña con una temporal. Exige el cambio en el siguiente ingreso
     * para CUALQUIER rol (marcador generico), limpia bloqueo/intentos fallidos y mantiene el
     * marcador de docente sincronizado por compatibilidad.
     */
    public void restablecerContrasenaUsuario(Usuario usuario, String contrasenaTemporal) {
        String hash = hashContrasena.generarHash(contrasenaTemporal);
        boolean esDocente = usuario instanceof Docente;
        enTransaccion(c -> {
            actualizarCon(c, "UPDATE usuario SET contrasena_hash = ?, debe_cambiar_contrasena = TRUE, "
                    + "intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id_usuario = ?", ps -> {
                ps.setString(1, hash);
                ps.setInt(2, usuario.getIdUsuario());
            });
            if (esDocente) {
                actualizarCon(c, "UPDATE docente SET debe_cambiar_contrasena = TRUE WHERE id_usuario = ?",
                        ps -> ps.setInt(1, usuario.getIdUsuario()));
            }
            return null;
        });
        usuario.contrasenaHashUsuario = hash;
        usuario.debeCambiarContrasena = true;
        if (esDocente) {
            ((Docente) usuario).debeCambiarContrasenaDocente = true;
        }
    }
}
