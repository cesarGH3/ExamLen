package servicio;

import java.nio.charset.StandardCharsets;

/**
 * Hoja de práctica en Word: convierte a bytes el documento HTML (extensión .doc)
 * que el servlet compone con el motor de plantillas y sus fragmentos.
 */
public class GeneradorWordPractica {

    public byte[] generar(String documentoHtml) {
        return documentoHtml.getBytes(StandardCharsets.UTF_8);
    }
}
