package modelo;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Elementos comunes de los PDF imprimibles (RNF-17): encabezado y pie repetidos en cada página
 * («Página N») y bloques de pregunta que no se parten entre páginas.
 */
final class PdfFormato extends PdfPageEventHelper {

    private static final Font FUENTE = new Font(Font.HELVETICA, 9, Font.NORMAL);

    private final String encabezado;

    PdfFormato(String encabezado) {
        this.encabezado = encabezado;
    }

    /** Tabla de una columna que la librería intenta no partir entre páginas. */
    static PdfPTable bloquePregunta() {
        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setKeepTogether(true);
        return bloque;
    }

    /** Celda sin borde para meter los párrafos dentro del bloque de pregunta. */
    static PdfPCell celda(Paragraph parrafo) {
        PdfPCell celda = new PdfPCell(parrafo);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(0);
        celda.setPaddingBottom(3);
        return celda;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        PdfContentByte lienzo = writer.getDirectContent();
        Rectangle pagina = document.getPageSize();
        ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT, new Phrase(encabezado, FUENTE),
                document.leftMargin(), pagina.getHeight() - 46, 0);
        lienzo.setLineWidth(0.5f);
        lienzo.moveTo(document.leftMargin(), pagina.getHeight() - 52);
        lienzo.lineTo(pagina.getWidth() - document.rightMargin(), pagina.getHeight() - 52);
        lienzo.stroke();
        ColumnText.showTextAligned(lienzo, Element.ALIGN_CENTER,
                new Phrase("Página " + writer.getPageNumber(), FUENTE),
                pagina.getWidth() / 2, 32, 0);
    }
}
