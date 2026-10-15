package servicio;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Almacenamiento tecnico de la ultima medicion por entorno (LOCAL / NGROK) en archivos JSON
 * fuera del directorio publico web/. Escritura por archivo temporal y reemplazo atomico.
 */
public final class ArchivoRendimiento {

    public static final String LOCAL = "local";
    public static final String NGROK = "ngrok";

    private ArchivoRendimiento() {
    }

    /** Umbral relativo de clasificacion: EXAMLEN_RENDIMIENTO_UMBRAL_PCT, por defecto 50. */
    public static int umbralPct() {
        String valor = System.getenv("EXAMLEN_RENDIMIENTO_UMBRAL_PCT");
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty("examlen.rendimiento.umbralPct");
        }
        if (valor == null || valor.isBlank()) {
            return 50;
        }
        try {
            return Math.max(0, Integer.parseInt(valor.trim()));
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    public static Path directorio() {
        String valor = System.getProperty("examlen.rendimiento.dir");
        if (valor == null || valor.isBlank()) {
            valor = System.getenv("EXAMLEN_RENDIMIENTO_DIR");
        }
        if (valor == null || valor.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".examlen-rendimiento");
        }
        return Paths.get(valor);
    }

    public static Path archivo(String entorno) {
        return directorio().resolve("rendimiento-" + entorno + ".json");
    }

    /** Guarda la lista de filas del entorno mediante temporal + reemplazo atomico. */
    public static String guardar(String entorno, JsonArray filas) throws IOException {
        Files.createDirectories(directorio());
        Path destino = archivo(entorno);
        Path temporal = directorio().resolve("rendimiento-" + entorno + ".tmp");
        Files.writeString(temporal, filas.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        JsonArray comprobado = JsonParser.parseString(Files.readString(temporal, StandardCharsets.UTF_8))
                .getAsJsonArray();
        if (comprobado.isEmpty()) {
            Files.deleteIfExists(temporal);
            throw new IOException("El archivo de rendimiento quedaria vacio");
        }
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        return destino.toString();
    }

    /** Ultima medicion del entorno, o null si aun no existe o esta corrupta. */
    public static JsonArray leer(String entorno) {
        Path archivo = archivo(entorno);
        if (!Files.isRegularFile(archivo)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(archivo, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean existe(String entorno) {
        return Files.isRegularFile(archivo(entorno));
    }
}
