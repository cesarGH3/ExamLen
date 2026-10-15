package servlet;

import java.util.HashMap;
import java.util.Map;

/** Utilidades para leer y dibujar controles de formulario de forma segura. */
public final class Formulario {

    private Formulario() {
    }

    /** Convierte el parámetro en un valor de la enumeración; null si no es un valor permitido. */
    public static <E extends Enum<E>> E enumeracion(Class<E> tipo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Etiquetas option para un select, con el valor elegido marcado. */
    public static <E extends Enum<E>> String opciones(Vistas vistas, E[] valores, E seleccionado, String textoVacio) {
        StringBuilder sb = new StringBuilder();
        if (textoVacio != null) {
            sb.append(opcion(vistas, "", textoVacio, seleccionado == null));
        }
        for (E valor : valores) {
            sb.append(opcion(vistas, valor.name(), Etiquetas.de(valor), valor == seleccionado));
        }
        return sb.toString();
    }

    /** Etiqueta option suelta (para listas que no son enumeraciones). */
    static String opcion(Vistas vistas, String valor, String texto, boolean seleccionada) {
        Map<String, String> datos = new HashMap<>();
        datos.put("valor", valor);
        datos.put("texto", texto);
        datos.put("selected", seleccionada ? " selected" : "");
        return vistas.componer("_opcion.html", datos);
    }
}
