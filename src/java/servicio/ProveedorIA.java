package servicio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/** Cliente de un proveedor de IA (Gemini, Groq o cualquier proveedor registrado). */
public interface ProveedorIA {

    String nombre();

    String modelo();

    ResultadoGeneracion generarPreguntas(String contexto, String instrucciones, int cantidad, String nivel)
            throws GeneracionFallidaException;

    /** Arma el prompt comun pidiendo un arreglo JSON estricto. */
    static String armarPrompt(String contexto, String instrucciones, int cantidad, String nivel) {
        StringBuilder sb = new StringBuilder();
        sb.append("Eres un generador de preguntas de inglés para un sistema educativo. ")
          .append("Devuelve SOLO un arreglo JSON, sin texto adicional ni bloques de código, con exactamente ")
          .append(cantidad).append(" preguntas de nivel ").append(nivel).append(". ")
          .append("Cada elemento tiene esta forma: {\"enunciado\":\"...\",")
          .append("\"tipo\":\"SELECCION_MULTIPLE|VERDADERO_FALSO|RESPUESTA_CORTA\",")
          .append("\"dificultad\":\"BAJA|MEDIA|ALTA\",\"competencia\":\"GRAMMAR|VOCABULARY|READING|WRITING\",")
          .append("\"tema\":\"...\",\"opciones\":[{\"texto\":\"...\",\"correcta\":true}],")
          .append("\"respuestaEsperada\":\"...\"}. ")
          .append("Para SELECCION_MULTIPLE incluye varias opciones (mínimo 2) con exactamente una correcta; ")
          .append("para VERDADERO_FALSO usa exactamente las opciones Verdadero y Falso marcando ")
          .append("correcta=true en una sola de ellas; ")
          .append("para RESPUESTA_CORTA deja opciones vacías y usa respuestaEsperada. ");
        if (instrucciones != null && !instrucciones.isBlank()) {
            sb.append("Instrucciones del docente: ").append(instrucciones.strip()).append(". ");
        }
        if (contexto != null && !contexto.isBlank()) {
            sb.append("Contexto del documento: ").append(contexto.strip());
        }
        return sb.toString();
    }

    /**
     * Extrae el arreglo JSON de la respuesta del modelo (tolera texto alrededor) y separa los
     * ítems válidos de los descartados por formato (CU-19 5b). Solo lanza error si no hay JSON.
     */
    static ResultadoGeneracion parsearRespuesta(String texto) throws GeneracionFallidaException {
        String limpio = texto == null ? "" : texto.strip();
        int inicio = limpio.indexOf('[');
        int fin = limpio.lastIndexOf(']');
        if (inicio < 0 || fin <= inicio) {
            throw new GeneracionFallidaException("La IA no devolvió un arreglo JSON válido.", TipoErrorIA.RESPUESTA);
        }
        try {
            JsonArray arreglo = JsonParser.parseString(limpio.substring(inicio, fin + 1)).getAsJsonArray();
            List<PreguntaGenerada> preguntas = new ArrayList<>();
            List<String> errores = new ArrayList<>();
            int descartados = 0;
            int posicion = 0;
            for (JsonElement elemento : arreglo) {
                posicion++;
                if (!elemento.isJsonObject()) {
                    descartados++;
                    errores.add("Ítem " + posicion + ": no es un objeto JSON.");
                    continue;
                }
                PreguntaGenerada pregunta = leer(elemento.getAsJsonObject());
                if (pregunta == null) {
                    descartados++;
                    errores.add("Ítem " + posicion + ": sin enunciado utilizable.");
                } else {
                    preguntas.add(pregunta);
                }
            }
            return new ResultadoGeneracion(preguntas, descartados, errores);
        } catch (RuntimeException e) {
            throw new GeneracionFallidaException("No se pudo interpretar la respuesta de la IA.",
                    TipoErrorIA.RESPUESTA, e);
        }
    }

    private static PreguntaGenerada leer(JsonObject objeto) {
        PreguntaGenerada pregunta = new PreguntaGenerada();
        pregunta.enunciado = texto(objeto, "enunciado");
        pregunta.tipo = texto(objeto, "tipo");
        pregunta.dificultad = texto(objeto, "dificultad");
        pregunta.competencia = texto(objeto, "competencia");
        pregunta.tema = texto(objeto, "tema");
        pregunta.respuestaEsperada = texto(objeto, "respuestaEsperada");
        if (objeto.has("opciones") && objeto.get("opciones").isJsonArray()) {
            for (JsonElement elemento : objeto.getAsJsonArray("opciones")) {
                if (!elemento.isJsonObject()) {
                    continue;
                }
                JsonObject opcion = elemento.getAsJsonObject();
                PreguntaGenerada.OpcionGenerada generada = new PreguntaGenerada.OpcionGenerada();
                generada.texto = texto(opcion, "texto");
                generada.correcta = opcion.has("correcta") && !opcion.get("correcta").isJsonNull()
                        && opcion.get("correcta").getAsBoolean();
                if (generada.texto != null && !generada.texto.isBlank()) {
                    pregunta.opciones.add(generada);
                }
            }
        }
        if (pregunta.enunciado == null || pregunta.enunciado.isBlank()) {
            return null;
        }
        return pregunta;
    }

    private static String texto(JsonObject objeto, String campo) {
        if (!objeto.has(campo) || objeto.get(campo).isJsonNull()) {
            return null;
        }
        try {
            return objeto.get(campo).getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
