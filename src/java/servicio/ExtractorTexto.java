package servicio;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extrae el texto de documentos PDF, DOCX y TXT para usarlo como contexto de la IA. */
public final class ExtractorTexto {

    private static final int MAX_CARACTERES = 20000;

    private ExtractorTexto() {
    }

    public static String extraer(byte[] contenido, String nombreArchivo) throws GeneracionFallidaException {
        String nombre = nombreArchivo == null ? "" : nombreArchivo.toLowerCase();
        try {
            String texto;
            if (nombre.endsWith(".txt")) {
                texto = new String(contenido, StandardCharsets.UTF_8);
            } else if (nombre.endsWith(".pdf")) {
                texto = extraerPdf(contenido);
            } else if (nombre.endsWith(".docx")) {
                texto = extraerDocx(contenido);
            } else {
                throw new GeneracionFallidaException("Formato no soportado. Usa PDF, DOCX o TXT.");
            }
            texto = texto.replaceAll("\\s+", " ").strip();
            if (texto.length() > MAX_CARACTERES) {
                texto = texto.substring(0, MAX_CARACTERES);
            }
            if (texto.isEmpty()) {
                throw new GeneracionFallidaException("No se pudo extraer texto del documento.");
            }
            return texto;
        } catch (IOException e) {
            throw new GeneracionFallidaException("No se pudo leer el documento.", e);
        }
    }

    private static String extraerPdf(byte[] contenido) throws IOException {
        try (PDDocument documento = PDDocument.load(new ByteArrayInputStream(contenido))) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private static String extraerDocx(byte[] contenido) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entrada.getName())) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.replace("</w:p>", "\n").replaceAll("<[^>]+>", " ");
                }
            }
        }
        throw new IOException("El archivo DOCX no contiene word/document.xml");
    }
}
