package servicio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuracion SMTP del correo saliente (RF-11). Mismas fuentes que ConfiguracionIA:
 * {catalina.base}/conf/ia.properties (o -Dexamlen.ia.config) y variables de entorno.
 * Claves: correo.host, correo.puerto, correo.usuario, correo.clave, correo.remitente,
 * correo.seguridad (starttls o ssl) y correo.urlBase (opcional).
 */
public final class ConfiguracionCorreo {

    private static final Properties PROPIEDADES = cargar();

    private ConfiguracionCorreo() {
    }

    public static boolean estaConfigurado() {
        return host() != null && puerto() > 0 && remitente() != null;
    }

    public static String host() {
        return valor("correo.host", "CORREO_HOST");
    }

    public static int puerto() {
        String puerto = valor("correo.puerto", "CORREO_PUERTO");
        if (puerto == null) {
            return 0;
        }
        try {
            return Integer.parseInt(puerto);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String usuario() {
        return valor("correo.usuario", "CORREO_USUARIO");
    }

    public static String clave() {
        return valor("correo.clave", "CORREO_CLAVE");
    }

    public static String remitente() {
        return valor("correo.remitente", "CORREO_REMITENTE");
    }

    public static String seguridad() {
        String seguridad = valor("correo.seguridad", "CORREO_SEGURIDAD");
        return seguridad == null ? "starttls" : seguridad.toLowerCase(Locale.ROOT);
    }

    /** URL publica del sistema para armar el enlace del correo; si falta se deduce de la peticion. */
    public static String urlBase() {
        return valor("correo.urlBase", "CORREO_URLBASE");
    }

    public static String mensajeFaltaConfiguracion() {
        return "Falta la configuracion de correo. Agrega correo.host, correo.puerto y correo.remitente "
                + "(y correo.usuario/correo.clave si tu servidor exige autenticacion) en "
                + ConfiguracionIA.ubicacionArchivo() + ".";
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
        Path archivo = Paths.get(ConfiguracionIA.ubicacionArchivo());
        if (Files.isReadable(archivo)) {
            try (InputStream entrada = Files.newInputStream(archivo)) {
                propiedades.load(entrada);
            } catch (IOException e) {
                // Se ignora: si falta configuracion se avisa al intentar enviar.
            }
        }
        return propiedades;
    }
}
