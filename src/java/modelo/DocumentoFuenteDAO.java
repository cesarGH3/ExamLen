package modelo;

import logica.Fechas;

import logica.Docente;
import logica.DocumentoFuente;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class DocumentoFuenteDAO extends DaoBase {

    private static final String COLUMNAS = "id_documento_fuente, id_docente, nombre_archivo, "
            + "tipo_archivo, ruta_almacenamiento, fecha_carga";

    public DocumentoFuenteDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Registra el documento. El almacenamiento físico del archivo se implementa en la Entrega 4. */
    public DocumentoFuente cargarDocumentoFuente(Docente docente, String nombreArchivo, String tipoArchivo,
                                                 String rutaAlmacenamiento) {
        DocumentoFuente documento = new DocumentoFuente();
        documento.nombreArchivoDocumentoFuente = nombreArchivo;
        documento.tipoArchivoDocumentoFuente = tipoArchivo;
        documento.rutaAlmacenamientoDocumentoFuente = rutaAlmacenamiento;
        documento.fechaCargaDocumentoFuente = Fechas.ahora();
        int id = insertar("INSERT INTO documento_fuente (id_docente, nombre_archivo, "
                + "tipo_archivo, ruta_almacenamiento) VALUES (?, ?, ?, ?)", ps -> {
            ps.setInt(1, docente.getIdUsuario());
            ps.setString(2, nombreArchivo);
            ps.setString(3, tipoArchivo);
            ps.setString(4, rutaAlmacenamiento);
        });
        documento.setIdDocumentoFuente(id);
        return documento;
    }

    public Optional<DocumentoFuente> buscarPorId(int idDocumentoFuente) {
        return consultarUno("SELECT " + COLUMNAS + " FROM documento_fuente WHERE id_documento_fuente = ?",
                ps -> ps.setInt(1, idDocumentoFuente), this::mapear);
    }

    public boolean perteneceA(int idDocumentoFuente, int idDocente) {
        return existe("SELECT 1 FROM documento_fuente WHERE id_documento_fuente = ? AND id_docente = ? LIMIT 1",
                ps -> {
                    ps.setInt(1, idDocumentoFuente);
                    ps.setInt(2, idDocente);
                });
    }

    public void eliminar(DocumentoFuente documento) {
        actualizar("DELETE FROM documento_fuente WHERE id_documento_fuente = ?",
                ps -> ps.setInt(1, documento.getIdDocumentoFuente()));
    }

    /** Resultado de intentar eliminar un documento fuente. */
    public enum ResultadoEliminacion {
        ELIMINADO, REFERENCIADO, NO_ENCONTRADO
    }

    /**
     * Elimina el documento SOLO si no está referenciado por ninguna solicitud de generación.
     * La fila se bloquea (FOR UPDATE) dentro de la transacción: una solicitud concurrente que
     * intente insertar la referencia espera al commit o falla, de modo que nunca queda una
     * solicitud apuntando a un documento inexistente.
     */
    public ResultadoEliminacion eliminarSiNoReferenciado(int idDocumentoFuente, int idDocente) {
        return enTransaccion(c -> {
            List<Integer> bloqueados = consultarListaCon(c, "SELECT id_documento_fuente FROM documento_fuente "
                    + "WHERE id_documento_fuente = ? AND id_docente = ? FOR UPDATE",
                    ps -> {
                        ps.setInt(1, idDocumentoFuente);
                        ps.setInt(2, idDocente);
                    }, rs -> rs.getInt(1));
            if (bloqueados.isEmpty()) {
                return ResultadoEliminacion.NO_ENCONTRADO;
            }
            List<Integer> referencias = consultarListaCon(c, "SELECT id_solicitud_generacion "
                    + "FROM solicitud_generacion WHERE id_documento_fuente = ? LIMIT 1",
                    ps -> ps.setInt(1, idDocumentoFuente), rs -> rs.getInt(1));
            if (!referencias.isEmpty()) {
                return ResultadoEliminacion.REFERENCIADO;
            }
            actualizarCon(c, "DELETE FROM documento_fuente WHERE id_documento_fuente = ?",
                    ps -> ps.setInt(1, idDocumentoFuente));
            return ResultadoEliminacion.ELIMINADO;
        });
    }

    public List<DocumentoFuente> listarPorDocente(int idDocente) {
        return consultarLista("SELECT " + COLUMNAS + " FROM documento_fuente WHERE id_docente = ? "
                + "ORDER BY fecha_carga DESC", ps -> ps.setInt(1, idDocente), this::mapear);
    }

    private DocumentoFuente mapear(ResultSet rs) throws SQLException {
        DocumentoFuente documento = new DocumentoFuente();
        documento.setIdDocumentoFuente(rs.getInt("id_documento_fuente"));
        documento.nombreArchivoDocumentoFuente = rs.getString("nombre_archivo");
        documento.tipoArchivoDocumentoFuente = rs.getString("tipo_archivo");
        documento.rutaAlmacenamientoDocumentoFuente = rs.getString("ruta_almacenamiento");
        documento.fechaCargaDocumentoFuente = Mapeos.fecha(rs, "fecha_carga");
        return documento;
    }
}
