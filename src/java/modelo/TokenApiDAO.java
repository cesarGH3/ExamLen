package modelo;

import javax.sql.DataSource;

/** Registro compartido entre instancias; nunca almacena el token Bearer. */
public final class TokenApiDAO extends DaoBase {
    public TokenApiDAO(DataSource fuente) { super(fuente); }

    public void registrar(String id, int usuario, long expiracion) {
        actualizar("INSERT INTO token_api (id_token, id_usuario, expira_en) VALUES (?, ?, ?)", ps -> {
            ps.setString(1, id); ps.setInt(2, usuario); ps.setLong(3, expiracion);
        });
    }

    public boolean vigente(String id, int usuario, long expiracion) {
        return existe("SELECT 1 FROM token_api WHERE id_token = ? AND id_usuario = ? "
                + "AND expira_en = ? AND expira_en > UNIX_TIMESTAMP() AND revocado = FALSE", ps -> {
            ps.setString(1, id); ps.setInt(2, usuario); ps.setLong(3, expiracion);
        });
    }

    public void revocar(String id, int usuario) {
        actualizar("UPDATE token_api SET revocado = TRUE WHERE id_token = ? AND id_usuario = ?", ps -> {
            ps.setString(1, id); ps.setInt(2, usuario);
        });
    }
}
