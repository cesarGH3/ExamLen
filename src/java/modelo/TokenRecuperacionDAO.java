package modelo;

import logica.HashContrasena;
import logica.TokenRecuperacion;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Optional;
import javax.sql.DataSource;

/** Acceso a token_recuperacion (RF-11): crear, buscar por hash, marcar usado, invalidar y contar. */
public class TokenRecuperacionDAO extends DaoBase {

    private final HashContrasena hashContrasena = new HashContrasena();

    public TokenRecuperacionDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Invalida los tokens anteriores del usuario e inserta el nuevo, en una sola transaccion. */
    public void crearToken(TokenRecuperacion token) {
        enTransaccion(c -> {
            actualizarCon(c, "UPDATE token_recuperacion SET usado = TRUE "
                    + "WHERE id_usuario = ? AND usado = FALSE",
                    ps -> ps.setInt(1, token.usuarioTokenRecuperacion.getIdUsuario()));
            int id = insertarCon(c, "INSERT INTO token_recuperacion (token_hash, fecha_creacion, "
                    + "fecha_expiracion, usado, id_usuario) VALUES (?, ?, ?, FALSE, ?)", ps -> {
                ps.setString(1, token.tokenHashTokenRecuperacion);
                ps.setTimestamp(2, new Timestamp(token.fechaCreacionTokenRecuperacion.getTime()));
                ps.setTimestamp(3, new Timestamp(token.fechaExpiracionTokenRecuperacion.getTime()));
                ps.setInt(4, token.usuarioTokenRecuperacion.getIdUsuario());
            });
            token.setIdTokenRecuperacion(id);
            return null;
        });
    }

    /** Solicitudes del usuario en la ultima hora (limite de RNF-04). */
    public int contarSolicitudesRecientes(int idUsuario) {
        return contar("SELECT COUNT(*) FROM token_recuperacion WHERE id_usuario = ? "
                + "AND fecha_creacion >= DATE_SUB(NOW(), INTERVAL 1 HOUR)", ps -> ps.setInt(1, idUsuario));
    }

    public Optional<TokenRecuperacion> buscarPorHash(String tokenHash) {
        return consultarUno("SELECT t.id_token_recuperacion, t.token_hash, t.fecha_creacion, "
                + "t.fecha_expiracion, t.usado, " + Mapeos.USUARIO_COLUMNAS + " FROM " + Mapeos.USUARIO_ORIGEN
                + " JOIN token_recuperacion t ON t.id_usuario = u.id_usuario WHERE t.token_hash = ?",
                ps -> ps.setString(1, tokenHash), rs -> {
                    TokenRecuperacion token = new TokenRecuperacion();
                    token.setIdTokenRecuperacion(rs.getInt("id_token_recuperacion"));
                    token.tokenHashTokenRecuperacion = rs.getString("token_hash");
                    token.fechaCreacionTokenRecuperacion = Mapeos.fecha(rs, "fecha_creacion");
                    token.fechaExpiracionTokenRecuperacion = Mapeos.fecha(rs, "fecha_expiracion");
                    token.usadoTokenRecuperacion = rs.getBoolean("usado");
                    token.usuarioTokenRecuperacion = Mapeos.usuario(rs);
                    return token;
                });
    }

    public void marcarUsado(TokenRecuperacion token) {
        actualizar("UPDATE token_recuperacion SET usado = TRUE WHERE id_token_recuperacion = ?",
                ps -> ps.setInt(1, token.getIdTokenRecuperacion()));
    }

    public void invalidarTokensDeUsuario(int idUsuario) {
        actualizar("UPDATE token_recuperacion SET usado = TRUE WHERE id_usuario = ? AND usado = FALSE",
                ps -> ps.setInt(1, idUsuario));
    }

    /**
     * Consume el token (un solo uso) y guarda la contrasena nueva en una sola transaccion:
     * valida vigencia y cuenta activa con FOR UPDATE, marca el token usado, invalida los demas
     * del usuario y actualiza usuario.contrasena_hash.
     * Devuelve false si el token no existe, ya se uso, vencio o la cuenta esta desactivada.
     */
    public boolean consumirTokenYRestablecer(String tokenHash, String nuevaContrasena) {
        return enTransaccion(c -> {
            Integer idToken = null;
            Integer idUsuario = null;
            boolean valido = false;
            boolean activo = false;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t.id_token_recuperacion, t.usado, t.fecha_expiracion, u.id_usuario, u.activo "
                    + "FROM token_recuperacion t JOIN usuario u ON u.id_usuario = t.id_usuario "
                    + "WHERE t.token_hash = ? FOR UPDATE")) {
                ps.setString(1, tokenHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idToken = rs.getInt("id_token_recuperacion");
                        idUsuario = rs.getInt("id_usuario");
                        activo = rs.getBoolean("activo");
                        Timestamp expiracion = rs.getTimestamp("fecha_expiracion");
                        valido = !rs.getBoolean("usado") && expiracion != null
                                && expiracion.after(new Timestamp(System.currentTimeMillis()));
                    }
                }
            }
            if (idToken == null || !valido || !activo) {
                return false;
            }
            final int idTokenFinal = idToken;
            final int idUsuarioFinal = idUsuario;
            String hash = hashContrasena.generarHash(nuevaContrasena);
            actualizarCon(c, "UPDATE token_recuperacion SET usado = TRUE WHERE id_token_recuperacion = ?",
                    ps -> ps.setInt(1, idTokenFinal));
            actualizarCon(c, "UPDATE token_recuperacion SET usado = TRUE WHERE id_usuario = ? AND usado = FALSE",
                    ps -> ps.setInt(1, idUsuarioFinal));
            actualizarCon(c, "UPDATE usuario SET contrasena_hash = ?, intentos_fallidos = 0, "
                    + "bloqueado_hasta = NULL WHERE id_usuario = ?", ps -> {
                ps.setString(1, hash);
                ps.setInt(2, idUsuarioFinal);
            });
            return true;
        });
    }
}
