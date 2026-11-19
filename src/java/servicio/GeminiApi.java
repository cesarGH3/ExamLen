package servicio;

import java.util.Map;

/** Endpoints y autenticacion de Gemini: la clave va en x-goog-api-key, nunca en la URL. */
final class GeminiApi {

    private static final String BASE_POR_DEFECTO = "https://generativelanguage.googleapis.com/v1beta";

    private GeminiApi() {
    }

    static String urlModelos() {
        return base() + "/models";
    }

    static String urlGenerar(String modelo) {
        return base() + "/models/" + modelo + ":generateContent";
    }

    static Map<String, String> cabeceras(String clave) {
        return Map.of("x-goog-api-key", clave);
    }

    /**
     * La base solo se puede cambiar por propiedad del sistema para pruebas aisladas;
     * no se acepta entrada de usuarios ni de formularios.
     */
    private static String base() {
        String valor = System.getProperty("examlen.gemini.baseUrl");
        if (valor == null || valor.isBlank()) {
            return BASE_POR_DEFECTO;
        }
        return valor.strip().replaceAll("/+$", "");
    }
}
