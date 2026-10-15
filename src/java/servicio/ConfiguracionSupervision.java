package servicio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Umbrales configurables de la supervisión (RF-50: «umbrales configurables»). Nunca en el código
 * fuente: se leen del archivo supervision.properties del servidor (o de variables de entorno).
 * Orden de búsqueda: -Dexamlen.supervision.config → {catalina.base}/conf/supervision.properties
 * → supervision.properties del directorio actual.
 */
public final class ConfiguracionSupervision {

    private static final String ARCHIVO = "supervision.properties";

    /** ERS RF-50: «coeficiente de similitud de texto mayor o igual a 0,85». */
    private static final double SIMILITUD_POR_DEFECTO = 0.85;

    /** ERS RF-50: «duración menor al 25 % de la mediana del grupo». */
    private static final double FRACCION_TIEMPO_POR_DEFECTO = 0.25;

    private static final Properties PROPIEDADES = cargar();

    private ConfiguracionSupervision() {
    }

    /** Coeficiente (0 a 1) a partir del cual dos respuestas se consideran excesivamente similares. */
    public static double umbralSimilitud() {
        return valorDecimal("supervision.similitud.umbral", "EXAMENL_SUPERVISION_SIMILITUD",
                SIMILITUD_POR_DEFECTO);
    }

    /** Fracción de la mediana del grupo por debajo de la cual la duración de un intento es atípica. */
    public static double fraccionMedianaTiempo() {
        return valorDecimal("supervision.tiempo.fraccion.mediana", "EXAMENL_SUPERVISION_TIEMPO",
                FRACCION_TIEMPO_POR_DEFECTO);
    }

    public static String ubicacionArchivo() {
        return rutaArchivo().toString();
    }

    /** Acepta coma o punto decimal; un valor no numérico o fuera de 0–1 vuelve al valor por defecto. */
    private static double valorDecimal(String clave, String variableEntorno, double defecto) {
        String valor = valor(clave, variableEntorno);
        if (valor == null) {
            return defecto;
        }
        try {
            double numero = Double.parseDouble(valor.replace(',', '.'));
            return numero >= 0 && numero <= 1 ? numero : defecto;
        } catch (NumberFormatException e) {
            return defecto;
        }
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
                // Se ignora: si no se puede leer, se usan los valores por defecto.
            }
        }
        return propiedades;
    }

    private static Path rutaArchivo() {
        String ruta = System.getProperty("examlen.supervision.config");
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
