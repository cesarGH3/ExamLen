package logica;

import java.util.Date;

public class DocumentoFuente {

    private int idDocumentoFuente;
    public String nombreArchivoDocumentoFuente;
    public String tipoArchivoDocumentoFuente;
    public String rutaAlmacenamientoDocumentoFuente;
    public Date fechaCargaDocumentoFuente;

    public int consultarIdDocumentoFuente() {
        return idDocumentoFuente;
    }

    public void establecerIdDocumentoFuente(int idDocumentoFuente) {
        this.idDocumentoFuente = idDocumentoFuente;
    }
}
