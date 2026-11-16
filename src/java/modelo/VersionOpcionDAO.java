package modelo;

import logica.VersionOpcion;
import logica.VersionPregunta;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

public class VersionOpcionDAO extends DaoBase {

    public VersionOpcionDAO(DataSource dataSource) {
        super(dataSource);
    }

    public VersionOpcion crearVersionOpcion(VersionPregunta versionPregunta, String texto, boolean esCorrecta,
                                            int orden) {
        VersionOpcion opcion = new VersionOpcion();
        opcion.textoVersionOpcion = texto;
        opcion.esCorrectaVersionOpcion = esCorrecta;
        opcion.ordenVersionOpcion = orden;
        int id = insertar("INSERT INTO version_opcion (id_version_pregunta, texto, "
                + "es_correcta, orden) VALUES (?, ?, ?, ?)", ps -> {
            ps.setInt(1, versionPregunta.consultarIdVersionPregunta());
            ps.setString(2, texto);
            ps.setBoolean(3, esCorrecta);
            ps.setInt(4, orden);
        });
        opcion.establecerIdVersionOpcion(id);
        versionPregunta.versionOpcionesVersionPregunta.add(opcion);
        return opcion;
    }

    public void actualizarVersionOpcion(VersionOpcion versionOpcion, String texto, boolean esCorrecta, int orden) {
        actualizar("UPDATE version_opcion SET texto = ?, es_correcta = ?, "
                + "orden = ? WHERE id_version_opcion = ?", ps -> {
            ps.setString(1, texto);
            ps.setBoolean(2, esCorrecta);
            ps.setInt(3, orden);
            ps.setInt(4, versionOpcion.consultarIdVersionOpcion());
        });
        versionOpcion.textoVersionOpcion = texto;
        versionOpcion.esCorrectaVersionOpcion = esCorrecta;
        versionOpcion.ordenVersionOpcion = orden;
    }

    public void eliminarVersionOpcion(VersionOpcion versionOpcion) {
        actualizar("DELETE FROM version_opcion WHERE id_version_opcion = ?",
                ps -> ps.setInt(1, versionOpcion.consultarIdVersionOpcion()));
    }

    /** Marca una opción como correcta y desmarca las demás de la pregunta, en una transacción. */
    public void marcarCorrectaVersionOpcion(VersionPregunta versionPregunta, VersionOpcion versionOpcion) {
        enTransaccion(c -> {
            actualizarCon(c, "UPDATE version_opcion SET es_correcta = FALSE WHERE id_version_pregunta = ?",
                    ps -> ps.setInt(1, versionPregunta.consultarIdVersionPregunta()));
            actualizarCon(c, "UPDATE version_opcion SET es_correcta = TRUE WHERE id_version_opcion = ?",
                    ps -> ps.setInt(1, versionOpcion.consultarIdVersionOpcion()));
            return null;
        });
        for (VersionOpcion opcion : versionPregunta.versionOpcionesVersionPregunta) {
            opcion.esCorrectaVersionOpcion = opcion.consultarIdVersionOpcion() == versionOpcion.consultarIdVersionOpcion();
        }
        versionOpcion.esCorrectaVersionOpcion = true;
    }

    public boolean perteneceAVersionPregunta(int idVersionOpcion, int idVersionPregunta) {
        return existe("SELECT 1 FROM version_opcion WHERE id_version_opcion = ? AND id_version_pregunta = ? "
                + "LIMIT 1", ps -> {
            ps.setInt(1, idVersionOpcion);
            ps.setInt(2, idVersionPregunta);
        });
    }

    public List<VersionOpcion> listarPorVersionPregunta(int idVersionPregunta) {
        return consultarLista("SELECT id_version_opcion, texto, es_correcta, "
                + "orden FROM version_opcion WHERE id_version_pregunta = ? "
                + "ORDER BY orden", ps -> ps.setInt(1, idVersionPregunta), this::mapear);
    }

    private VersionOpcion mapear(ResultSet rs) throws SQLException {
        VersionOpcion opcion = new VersionOpcion();
        opcion.establecerIdVersionOpcion(rs.getInt("id_version_opcion"));
        opcion.textoVersionOpcion = rs.getString("texto");
        opcion.esCorrectaVersionOpcion = rs.getBoolean("es_correcta");
        opcion.ordenVersionOpcion = rs.getInt("orden");
        return opcion;
    }
}
