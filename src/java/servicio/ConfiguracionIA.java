package servicio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Claves y carpetas de la IA. Nunca en el código fuente: se leen del archivo
 * ia.properties del servidor (o de variables de entorno).
 * Orden de búsqueda: -Dexamlen.ia.config → {catalina.base}/conf/ia.properties → ia.properties del directorio actual.
 */
public final class ConfiguracionIA {

    private static final String ARCHIVO = "ia.properties";
    private static final Properties PROPIEDADES = cargar();

    /** Propiedad de Tomcat con el numero de hilos de generacion. */
    public static final String PROPIEDAD_TRABAJADORES = "examlen.ia.workers";
    /** Variable de entorno equivalente. */
    public static final String VARIABLE_TRABAJADORES = "EXAMENL_IA_WORKERS";
    /** Propiedad de Tomcat con la capacidad de la cola de trabajos. */
    public static final String PROPIEDAD_COLA = "examlen.ia.queueCapacity";
    /** Variable de entorno equivalente. */
    public static final String VARIABLE_COLA = "EXAMENL_IA_QUEUE_CAPACITY";

    public static final int TRABAJADORES_POR_DEFECTO = 2;
    public static final int COLA_POR_DEFECTO = 50;

    private ConfiguracionIA() {
    }

    /**
     * Lista permitida de modelos del proveedor: propiedad {@code proveedor.modelos} (separados por
     * coma) o variable de entorno {@code EXAMENL_IA_MODELOS_PROVEEDOR}; si falta, solo el
     * predeterminado. Cualquier modelo fuera de la lista se rechaza antes de llamar al proveedor.
     */
    public static List<String> modelos(String proveedor, String predeterminado) {
        String valor = valor(proveedor.toLowerCase(Locale.ROOT) + ".modelos",
                "EXAMENL_IA_MODELOS_" + proveedor.toUpperCase(Locale.ROOT));
        if (valor == null || valor.isBlank()) {
            return List.of(predeterminado);
        }
        List<String> modelos = new ArrayList<>();
        for (String parte : valor.split(",")) {
            String modelo = parte.strip();
            if (!modelo.isEmpty() && !modelos.contains(modelo)) {
                modelos.add(modelo);
            }
        }
        return modelos.isEmpty() ? List.of(predeterminado) : List.copyOf(modelos);
    }

    /** Hilos de generacion en segundo plano (minimo 1). */
    public static int trabajadores() {
        return enteroPositivo(PROPIEDAD_TRABAJADORES, VARIABLE_TRABAJADORES, TRABAJADORES_POR_DEFECTO);
    }

    /** Trabajos en espera antes de rechazar nuevas solicitudes (minimo 1). */
    public static int capacidadCola() {
        return enteroPositivo(PROPIEDAD_COLA, VARIABLE_COLA, COLA_POR_DEFECTO);
    }

    private static int enteroPositivo(String propiedad, String variableEntorno, int porDefecto) {
        String valor = System.getProperty(propiedad);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(variableEntorno);
        }
        if (valor == null || valor.isBlank()) {
            return porDefecto;
        }
        try {
            return Math.max(1, Integer.parseInt(valor.trim()));
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    public static String claveGemini() {
        return valor("gemini.apiKey", "GEMINI_API_KEY");
    }

    public static String claveGroq() {
        return valor("groq.apiKey", "GROQ_API_KEY");
    }

    public static Path directorioDocumentos() {
        String ruta = valor("documentos.directorio", "EXAMENL_DOCUMENTOS");
        if (ruta == null) {
            String base = System.getProperty("catalina.base", System.getProperty("java.io.tmpdir"));
            ruta = Paths.get(base, "examlen-documentos").toString();
        }
        Path directorio = Paths.get(ruta);
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta de documentos: " + directorio, e);
        }
        return directorio;
    }

    public static String ubicacionArchivo() {
        return rutaArchivo().toString();
    }

    public static String mensajeFaltaClave() {
        return "Falta la clave de la API de IA. Crea " + ubicacionArchivo()
                + " con las propiedades gemini.apiKey y groq.apiKey, o define las variables de entorno "
                + "GEMINI_API_KEY y GROQ_API_KEY.";
    }

    private static String valor(String clave, String variableEntorno) {
        String entorno = System.getenv(variableEntorno);
        if (entorno != null && !entorno.isBlank()) {
            return entorno.strip();
        }
        String propiedad = PROPIEDADES.getProperty(clave);
        return propiedad == null || propiedad.isBlank() ? null : propiedad.strip();
    }

    private static Properties cargar() {
        Properties propiedades = new Properties();
        Path archivo = rutaArchivo();
        if (Files.isReadable(archivo)) {
            try (InputStream entrada = Files.newInputStream(archivo)) {
                propiedades.load(entrada);
            } catch (IOException e) {
                // Se ignora: si falta una clave se avisará al intentar usar la IA.
            }
        }
        return propiedades;
    }

    private static Path rutaArchivo() {
        String ruta = System.getProperty("examlen.ia.config");
        if (ruta != null && !ruta.isBlank()) {
            return Paths.get(ruta);
        }
        String base = System.getProperty("catalina.base");
        if (base != null && !base.isBlank()) {
            return Paths.get(base, "conf", ARCHIVO);
        }
        return Paths.get(System.getProperty("user.dir"), ARCHIVO);
    }
}
