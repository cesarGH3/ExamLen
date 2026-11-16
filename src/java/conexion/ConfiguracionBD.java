package conexion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuracion de conexion a MySQL para la inicializacion automatica del esquema
 * (creacion de la base y aplicacion de migraciones al arrancar ExamLen).
 *
 * Orden de resolucion por campo: variable de entorno -> propiedad del sistema ->
 * archivo examlen.properties incluido en WEB-INF/classes ->
 * archivo {catalina.base}/conf/catalina.properties (compatibilidad) ->
 * valor por defecto (solo host, puerto y nombre de base admiten defecto).
 * La contrasena nunca tiene valor por defecto y jamas se registra en logs.
 *
 * El nombre de la base se valida como identificador seguro antes de usarse.
 * allowMultiQueries solo se agrega a las URLs de esta clase (conexion interna del
 * inicializador); el DataSource JNDI de la aplicacion no se modifica.
 */
public final class ConfiguracionBD {

    private static final String PROPIEDAD_URL = "examlen.db.url";
    private static final String PROPIEDAD_USUARIO = "examlen.db.user";
    private static final String PROPIEDAD_CONTRASENA = "examlen.db.password";
    private static final String ARCHIVO_APP = "examlen.properties";
    private static final Properties PROPIEDADES_APP = leerPropiedadesApp();

    private static final String HOST_POR_DEFECTO = "gitua.site";
    private static final int PUERTO_POR_DEFECTO = 3306;
    private static final String BASE_POR_DEFECTO = "examlen";

    private static final Pattern BASE_VALIDA = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern URL_JDBC = Pattern.compile("jdbc:mysql://([^:/]+):(\\d+)/([^?]+)(?:\\?.*)?");

    private final String host;
    private final int puerto;
    private final String base;
    private final String usuario;
    private final String contrasena;

    private ConfiguracionBD(String host, int puerto, String base, String usuario, String contrasena) {
        this.host = host;
        this.puerto = puerto;
        this.base = base;
        this.usuario = usuario;
        this.contrasena = contrasena;
    }

    public static ConfiguracionBD resolver() {
        Properties catalina = leerCatalinaProperties();

        String url = primero(System.getProperty(PROPIEDAD_URL), PROPIEDADES_APP.getProperty(PROPIEDAD_URL),
                catalina.getProperty(PROPIEDAD_URL));
        String hostUrl = null;
        String puertoUrl = null;
        String baseUrl = null;
        if (url != null) {
            Matcher m = URL_JDBC.matcher(url.trim());
            if (m.matches()) {
                hostUrl = m.group(1);
                puertoUrl = m.group(2);
                baseUrl = m.group(3);
            }
        }

        String host = primero(System.getenv("DB_HOST"), hostUrl, HOST_POR_DEFECTO);
        String puertoTexto = primero(System.getenv("DB_PORT"), puertoUrl);
        String base = primero(System.getenv("DB_NAME"), baseUrl, BASE_POR_DEFECTO);

        if (!BASE_VALIDA.matcher(base).matches()) {
            throw new IllegalArgumentException("El nombre de la base de datos no es un identificador valido (A-Z, a-z, 0-9, _): " + base);
        }

        String usuario = primero(System.getenv("DB_USER"), System.getProperty(PROPIEDAD_USUARIO),
                PROPIEDADES_APP.getProperty(PROPIEDAD_USUARIO), catalina.getProperty(PROPIEDAD_USUARIO));
        String contrasena = primero(System.getenv("DB_PASSWORD"), System.getProperty(PROPIEDAD_CONTRASENA),
                PROPIEDADES_APP.getProperty(PROPIEDAD_CONTRASENA), catalina.getProperty(PROPIEDAD_CONTRASENA));

        int puerto = PUERTO_POR_DEFECTO;
        if (puertoTexto != null && !puertoTexto.isBlank()) {
            try {
                puerto = Integer.parseInt(puertoTexto.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El puerto de MySQL no es numerico: " + puertoTexto);
            }
        }

        return new ConfiguracionBD(host, puerto, base, usuario, contrasena);
    }

    /** true si hay usuario y contrasena configurados (sin ellos no se intenta inicializar). */
    public boolean hayCredenciales() {
        return usuario != null && !usuario.isBlank() && contrasena != null;
    }

    public String host() { return host; }

    public int puerto() { return puerto; }

    public String base() { return base; }

    public String usuario() { return usuario; }

    public String contrasena() { return contrasena; }

    /** URL sin base de datos (para CREATE DATABASE). Solo uso interno del inicializador. */
    public String urlServidor() {
        return construirUrl("");
    }

    /** URL con base de datos (para migraciones). Solo uso interno del inicializador. */
    public String urlBase() {
        return construirUrl("/" + base);
    }

    /** Descripcion sin contrasena, apta para logs. */
    public String descripcion() {
        return host + ":" + puerto + "/" + base + " (usuario " + usuario + ")";
    }

    private String construirUrl(String rutaBase) {
        return "jdbc:mysql://" + host + ":" + puerto + rutaBase
                + "?sslMode=PREFERRED&allowPublicKeyRetrieval=true"
                + "&connectionTimeZone=America/Bogota&sessionVariables=time_zone='-05:00'"
                + "&connectTimeout=3000&socketTimeout=10000&characterEncoding=UTF-8"
                + "&allowMultiQueries=true";
    }

    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }

    private static Properties leerPropiedadesApp() {
        Properties propiedades = new Properties();
        try (InputStream entrada = ConfiguracionBD.class.getClassLoader().getResourceAsStream(ARCHIVO_APP)) {
            if (entrada != null) {
                propiedades.load(entrada);
            }
        } catch (IOException e) {
            // Sin archivo de aplicacion se conserva el mecanismo externo de Tomcat.
        }
        return propiedades;
    }

    private static Properties leerCatalinaProperties() {
        Properties propiedades = new Properties();
        String cat = System.getProperty("catalina.base");
        if (cat == null) {
            cat = System.getProperty("catalina.home");
        }
        if (cat == null) {
            return propiedades;
        }
        Path archivo = Path.of(cat, "conf", "catalina.properties");
        if (!Files.isReadable(archivo)) {
            return propiedades;
        }
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
        } catch (IOException e) {
            // Sin archivo legible simplemente se continua con entorno/propiedades/defectos.
        }
        return propiedades;
    }
}
