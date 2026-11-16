package modelo;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

/** Acceso a las claves de IA personales de cada docente. */
public final class ClaveIADocenteDAO extends DaoBase {

    public ClaveIADocenteDAO(DataSource dataSource) {
        super(dataSource);
    }

    public Optional<String> buscarCifrada(int idDocente, String proveedor) {
        return consultarUno(
                "SELECT clave_cifrada FROM clave_ia_docente WHERE id_docente = ? AND proveedor = ?",
                ps -> {
                    ps.setInt(1, idDocente);
                    ps.setString(2, proveedor);
                },
                rs -> rs.getString("clave_cifrada"));
    }

    public Set<String> listarProveedores(int idDocente) {
        Set<String> proveedores = new HashSet<>();
        consultarLista(
                "SELECT proveedor FROM clave_ia_docente WHERE id_docente = ?",
                ps -> ps.setInt(1, idDocente),
                rs -> rs.getString("proveedor")).forEach(proveedores::add);
        return proveedores;
    }

    public void guardar(int idDocente, String proveedor, String claveCifrada) {
        actualizar(
                "INSERT INTO clave_ia_docente (id_docente, proveedor, clave_cifrada, fecha_actualizacion) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON DUPLICATE KEY UPDATE clave_cifrada = VALUES(clave_cifrada), "
                + "fecha_actualizacion = CURRENT_TIMESTAMP",
                ps -> {
                    ps.setInt(1, idDocente);
                    ps.setString(2, proveedor);
                    ps.setString(3, claveCifrada);
                });
    }

    public void eliminar(int idDocente, String proveedor) {
        actualizar("DELETE FROM clave_ia_docente WHERE id_docente = ? AND proveedor = ?",
                ps -> {
                    ps.setInt(1, idDocente);
                    ps.setString(2, proveedor);
                });
    }
}
