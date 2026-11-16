package servicio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente de Groq (API compatible con OpenAI chat/completions). HTTP estandar por operacion
 * (sin HttpClient estatico): no deja hilos del classloader tras el undeploy.
 */
public class ClienteGroq implements ProveedorIA {

    public static final String MODELO_PREDETERMINADO = "llama-3.3-70b-versatile";
    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    private final String modelo;
    private final String clave;

    public ClienteGroq(String modelo, String clave) {
        this.modelo = modelo == null || modelo.isBlank() ? MODELO_PREDETERMINADO : modelo.strip();
        this.clave = clave == null || clave.isBlank() ? null : clave.strip();
    }

    @Override
    public String nombre() {
        return "GROQ";
    }

    @Override
    public String modelo() {
        return modelo;
    }

    @Override
    public ResultadoGeneracion generarPreguntas(String contexto, String instrucciones, int cantidad, String nivel)
            throws GeneracionFallidaException {
        if (clave == null) {
            throw new GeneracionFallidaException("El docente no tiene configurada una clave de Groq.",
                    TipoErrorIA.CONFIGURACION);
        }
        JsonObject mensaje = new JsonObject();
        mensaje.addProperty("role", "user");
        mensaje.addProperty("content", ProveedorIA.armarPrompt(contexto, instrucciones, cantidad, nivel));
        JsonArray mensajes = new JsonArray();
        mensajes.add(mensaje);
        JsonObject cuerpo = new JsonObject();
        cuerpo.addProperty("model", modelo);
        cuerpo.add("messages", mensajes);
        cuerpo.addProperty("temperature", 0.7);

        Map<String, String> cabeceras = new HashMap<>();
        cabeceras.put("Authorization", "Bearer " + clave);
        String respuesta = HttpJson.enviarJson(URL, cabeceras, cuerpo.toString(), "Groq");
        try {
            JsonObject objeto = JsonParser.parseString(respuesta).getAsJsonObject();
            String texto = objeto.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            return ProveedorIA.parsearRespuesta(texto);
        } catch (RuntimeException e) {
            throw new GeneracionFallidaException("La IA devolvió una respuesta inesperada.", TipoErrorIA.RESPUESTA, e);
        }
    }

    /** Lista permitida de modelos (configurable con groq.modelos). */
    static List<String> modelosPermitidos() {
        return ConfiguracionIA.modelos("GROQ", MODELO_PREDETERMINADO);
    }
}
