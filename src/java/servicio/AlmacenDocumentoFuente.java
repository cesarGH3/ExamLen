package servicio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import logica.Docente;
import logica.DocumentoFuente;
import modelo.DocumentoFuenteDAO;

/**
 * Escribe y registra un documento fuente sin dejar archivos huerfanos.
 *
 * - Si falla la escritura, elimina el archivo parcial.
 * - Si falla el registro en MySQL, consulta si la ruta quedo referenciada: solo borra cuando
 *   la base confirma que no existe la fila. Si la consulta falla (resultado incierto), conserva
 *   el archivo y lo registra para revision manual.
 */
public final class AlmacenDocumentoFuente {

    private static final Logger LOG = Logger.getLogger(AlmacenDocumentoFuente.class.getName());

    private AlmacenDocumentoFuente() {
    }

    public static DocumentoFuente registrar(Path destino, byte[] contenido, DocumentoFuenteDAO documentoDAO,
                                            Docente docente, String nombreArchivo, String tipoArchivo)
            throws IOException {
        try {
            Files.write(destino, contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            borrarArchivo(destino);
            throw e;
        }

        try {
            return documentoDAO.cargarDocumentoFuente(docente, nombreArchivo, tipoArchivo, destino.toString());
        } catch (RuntimeException e) {
            boolean referenciado;
            try {
                referenciado = documentoDAO.estaRegistradaRuta(docente.consultarIdUsuario(), destino.toString());
            } catch (RuntimeException comprobacion) {
                LOG.log(Level.WARNING, "Registro de documento con resultado incierto; se conserva el archivo "
                        + destino + " para no borrar una fila valida.", comprobacion);
                throw e;
            }
            if (referenciado) {
                LOG.log(Level.INFO, "El documento {0} quedo referenciado pese al error; se conserva el archivo.",
                        destino);
            } else {
                borrarArchivo(destino);
            }
            throw e;
        }
    }

    private static void borrarArchivo(Path destino) {
        try {
            if (Files.isRegularFile(destino)) {
                Files.deleteIfExists(destino);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No fue posible borrar el archivo de documento: " + destino, e);
        }
    }
}
