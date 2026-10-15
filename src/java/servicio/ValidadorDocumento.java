package servicio;

import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validación de contenido de los documentos fuente (RF-25): no basta la extensión.
 * Límites de seguridad del parser DOCX (no son límites académicos del ERS):
 * máximo 512 entradas, máximo 20 MB descomprimidos por entrada y máximo 40 MB en total
 * (4 veces el máximo de subida). Se cuentan los bytes realmente leídos, no las cabeceras.
 */
public final class ValidadorDocumento {

    static final int MAX_ENTRADAS_DOCX = 512;
    static final long MAX_BYTES_ENTRADA_DOCX = 20L * 1024 * 1024;
    static final long MAX_BYTES_TOTAL_DOCX = 40L * 1024 * 1024;
    private static final String CONTENT_TYPES = "[Content_Types].xml";
    private static final String DOCUMENT_XML = "word/document.xml";

    private ValidadorDocumento() {
    }

    /** Valida el contenido según la extensión (pdf, docx, txt); lanza error controlado si no lo es. */
    public static void validar(byte[] contenido, String extension) throws GeneracionFallidaException {
        switch (extension.toLowerCase(java.util.Locale.ROOT)) {
            case "pdf":
                validarPdf(contenido);
                return;
            case "docx":
                validarDocx(contenido);
                return;
            case "txt":
                validarTxt(contenido);
                return;
            default:
                throw new GeneracionFallidaException("Formato no soportado. Usa PDF, DOCX o TXT.",
                        TipoErrorIA.DOCUMENTO);
        }
    }

    private static void validarPdf(byte[] contenido) throws GeneracionFallidaException {
        if (contenido.length < 5 || contenido[0] != '%' || contenido[1] != 'P' || contenido[2] != 'D'
                || contenido[3] != 'F' || contenido[4] != '-') {
            throw new GeneracionFallidaException("El archivo no es un PDF válido.", TipoErrorIA.DOCUMENTO);
        }
        try (PDDocument documento = PDDocument.load(new ByteArrayInputStream(contenido))) {
            if (documento.getNumberOfPages() < 1) {
                throw new GeneracionFallidaException("El PDF no tiene páginas legibles.", TipoErrorIA.DOCUMENTO);
            }
        } catch (IOException e) {
            throw new GeneracionFallidaException("El PDF está dañado o no se puede leer.", TipoErrorIA.DOCUMENTO, e);
        }
    }

    private static void validarDocx(byte[] contenido) throws GeneracionFallidaException {
        boolean tieneContentTypes = false;
        boolean tieneDocumento = false;
        int entradas = 0;
        long total = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                entradas++;
                if (entradas > MAX_ENTRADAS_DOCX) {
                    throw new GeneracionFallidaException("El DOCX supera el límite de seguridad de entradas internas.",
                            TipoErrorIA.DOCUMENTO);
                }
                if (CONTENT_TYPES.equals(entrada.getName())) {
                    tieneContentTypes = true;
                }
                if (DOCUMENT_XML.equals(entrada.getName())) {
                    tieneDocumento = true;
                }
                long deEntrada = 0;
                int leidos;
                while ((leidos = zip.read(buffer)) > 0) {
                    deEntrada += leidos;
                    total += leidos;
                    if (deEntrada > MAX_BYTES_ENTRADA_DOCX || total > MAX_BYTES_TOTAL_DOCX) {
                        throw new GeneracionFallidaException("El DOCX supera los límites de descompresión seguros.",
                                TipoErrorIA.DOCUMENTO);
                    }
                }
            }
        } catch (IOException e) {
            throw new GeneracionFallidaException("El archivo no es un DOCX válido.", TipoErrorIA.DOCUMENTO, e);
        }
        if (!tieneContentTypes || !tieneDocumento) {
            throw new GeneracionFallidaException("El archivo no es un DOCX válido (faltan partes OOXML).",
                    TipoErrorIA.DOCUMENTO);
        }
    }

    private static void validarTxt(byte[] contenido) throws GeneracionFallidaException {
        for (byte dato : contenido) {
            if (dato == 0) {
                throw new GeneracionFallidaException("El archivo de texto contiene datos binarios.",
                        TipoErrorIA.DOCUMENTO);
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido));
        } catch (CharacterCodingException e) {
            throw new GeneracionFallidaException("El archivo de texto no está en una codificación válida (UTF-8).",
                    TipoErrorIA.DOCUMENTO, e);
        }
    }
}
