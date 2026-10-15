package logica;

import java.util.Date;

public class DocumentoFuente {

    private int idDocumentoFuente;
    public String nombreArchivoDocumentoFuente;
    public String tipoArchivoDocumentoFuente;
    public String rutaAlmacenamientoDocumentoFuente;
    public Date fechaCargaDocumentoFuente;

    public int getIdDocumentoFuente() {
        return idDocumentoFuente;
    }

    public void setIdDocumentoFuente(int idDocumentoFuente) {
        this.idDocumentoFuente = idDocumentoFuente;
    }
}
