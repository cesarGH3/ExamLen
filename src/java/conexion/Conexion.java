package conexion;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Puerta de entrada a la base de datos MySQL.
 * Primero usa examlen.properties incluido en WEB-INF/classes; si no existe,
 * conserva el mecanismo JNDI histórico de Tomcat.
 * Los DAO del paquete modelo/ reciben esta fuente de conexiones.
 */
public final class Conexion {

    public static final String JNDI_DATASOURCE = "java:comp/env/jdbc/ExamLenDB";

    private static volatile DataSource fuente;

    private Conexion() {
    }

    public static DataSource obtenerFuente() {
        DataSource actual = fuente;
        if (actual != null) {
            return actual;
        }
        synchronized (Conexion.class) {
            if (fuente != null) {
                return fuente;
            }
            ConfiguracionBD configuracion = ConfiguracionBD.resolver();
            if (configuracion.hayCredenciales()) {
                fuente = configuracion.fuenteAplicacion();
                return fuente;
            }
            try {
                fuente = (DataSource) new InitialContext().lookup(JNDI_DATASOURCE);
                return fuente;
            } catch (NamingException e) {
                throw new IllegalStateException("No se encontró configuración de base de datos. "
                        + "Configura examlen.db.* en {catalina.base}/conf/catalina.properties o "
                        + "las variables DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD. "
                        + "No incluyas credenciales dentro del WAR.", e);
            }
        }
    }

}
