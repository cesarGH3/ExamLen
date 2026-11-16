package servlet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Codificacion de salida para contexto HTML (texto y atributos entre comillas). */
public final class Html {

    private Html() {
    }

    public static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#x27;"); break;
                case '/': sb.append("&#x2F;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String listaErrores(Vistas vistas, List<String> errores) {
        StringBuilder elementos = new StringBuilder();
        for (String error : errores) {
            elementos.append(vistas.componer("_item-alerta.html", Map.of("texto", error)));
        }
        return vistas.componer("_lista-errores.html", Map.of("elementos", elementos.toString()));
    }

    public static String mensajesFlash(Vistas vistas, List<MensajeFlash> mensajes) {
        StringBuilder sb = new StringBuilder();
        for (MensajeFlash m : mensajes) {
            Map<String, String> datos = new HashMap<>();
            datos.put("clase", m.consultarTipo().consultarClaseCss());
            datos.put("texto", m.consultarTexto());
            sb.append(vistas.componer("_alerta.html", datos));
        }
        return sb.toString();
    }

    /** Alerta con un título y una lista de puntos. */
    public static String alertaConLista(Vistas vistas, String claseCss, String titulo, List<String> puntos) {
        StringBuilder elementos = new StringBuilder();
        for (String punto : puntos) {
            elementos.append(vistas.componer("_item-alerta.html", Map.of("texto", punto)));
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("clase", claseCss);
        datos.put("titulo", titulo);
        datos.put("elementos", elementos.toString());
        return vistas.componer("_alerta-lista.html", datos);
    }

    public static String alerta(Vistas vistas, String claseCss, String texto) {
        Map<String, String> datos = new HashMap<>();
        datos.put("clase", claseCss);
        datos.put("texto", texto);
        return vistas.componer("_alerta.html", datos);
    }

    /** Recorta un texto largo para listados, sin partir caracteres. Siempre escapa HTML. */
    public static String resumir(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        String unaLinea = texto.replace('\n', ' ').strip();
        if (unaLinea.codePointCount(0, unaLinea.length()) <= maximo) {
            return escapar(unaLinea);
        }
        int fin = unaLinea.offsetByCodePoints(0, maximo);
        return escapar(unaLinea.substring(0, fin).strip() + "…");
    }
}
