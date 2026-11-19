package servicio;

/**
 * Configuracion del programador de evaluaciones virtuales (RF-42).
 * Se lee de las propiedades de Tomcat (catalina.properties) o de una variable de entorno,
 * sin recompilar. Por defecto: 30 segundos.
 */
public final class ConfiguracionScheduler {

    /** Propiedad de Tomcat: {Catalina Base}/conf/catalina.properties. */
    public static final String PROPIEDAD_INTERVALO = "examlen.scheduler.virtual.intervalSeconds";

    /** Variable de entorno equivalente. */
    public static final String VARIABLE_INTERVALO = "EXAMLEN_SCHEDULER_VIRTUAL_INTERVAL";
    private static final String VARIABLE_INTERVALO_LEGADA = "EXAMENL_SCHEDULER_VIRTUAL_INTERVAL";

    public static final int INTERVALO_POR_DEFECTO = 30;
    public static final int INTERVALO_MINIMO = 1;

    private ConfiguracionScheduler() {
    }

    /** Intervalo en segundos entre sincronizaciones; valor por defecto si falta o es invalido. */
    public static int intervaloSegundos() {
        String valor = System.getProperty(PROPIEDAD_INTERVALO);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(VARIABLE_INTERVALO);
        }
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(VARIABLE_INTERVALO_LEGADA);
        }
        if (valor == null || valor.isBlank()) {
            return INTERVALO_POR_DEFECTO;
        }
        try {
            return Math.max(INTERVALO_MINIMO, Integer.parseInt(valor.trim()));
        } catch (NumberFormatException e) {
            return INTERVALO_POR_DEFECTO;
        }
    }
}
