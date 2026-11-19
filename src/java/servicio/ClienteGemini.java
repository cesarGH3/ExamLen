package servicio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;

/**
 * Cliente de Google Gemini (API generateContent). HTTP estandar por operacion (sin HttpClient
 * estatico): no deja hilos del classloader tras el undeploy. El modelo solo puede ser uno de
 * los permitidos por la fabrica.
 */
public class ClienteGemini implements ProveedorIA {

    public static final String MODELO_PREDETERMINADO = "gemini-3.5-flash-lite";
    public static final String MODELO_ALTERNATIVO = "gemini-flash-lite-latest";
    private final String modelo;
    private final String clave;

    public ClienteGemini(String modelo, String clave) {
        this.modelo = modelo == null || modelo.isBlank() ? MODELO_PREDETERMINADO : modelo.strip();
        this.clave = clave == null || clave.isBlank() ? null : clave.strip();
    }

    @Override
    public String nombre() {
        return "GEMINI";
    }

    @Override
    public String modelo() {
        return modelo;
    }

    @Override
    public ResultadoGeneracion generarPreguntas(String contexto, String instrucciones, int cantidad, String nivel)
            throws GeneracionFallidaException {
        if (clave == null) {
            throw new GeneracionFallidaException("El docente no tiene configurada una clave de Gemini.",
                    TipoErrorIA.CONFIGURACION);
        }
        JsonObject parte = new JsonObject();
        parte.addProperty("text", ProveedorIA.armarPrompt(contexto, instrucciones, cantidad, nivel));
        JsonArray partes = new JsonArray();
        partes.add(parte);
        JsonObject contenido = new JsonObject();
        contenido.add("parts", partes);
        JsonArray contenidos = new JsonArray();
        contenidos.add(contenido);
        JsonObject cuerpo = new JsonObject();
        cuerpo.add("contents", contenidos);

        String respuesta = HttpJson.enviarJson(GeminiApi.urlGenerar(modelo),
                GeminiApi.cabeceras(clave), cuerpo.toString(), "Gemini");
        try {
            JsonObject objeto = JsonParser.parseString(respuesta).getAsJsonObject();
            String texto = objeto.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();
            return ProveedorIA.parsearRespuesta(texto);
        } catch (RuntimeException e) {
            throw new GeneracionFallidaException("La IA devolvió una respuesta inesperada.", TipoErrorIA.RESPUESTA, e);
        }
    }

    /** Lista permitida de modelos (configurable con gemini.modelos). */
    static List<String> modelosPermitidos() {
        List<String> configurados = ConfiguracionIA.modelos("GEMINI", MODELO_PREDETERMINADO);
        if (configurados.size() == 1 && MODELO_PREDETERMINADO.equals(configurados.get(0))) {
            return List.of(MODELO_PREDETERMINADO, MODELO_ALTERNATIVO);
        }
        return configurados;
    }
}
