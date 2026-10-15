package conexion;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Única puerta de entrada a la base de datos MySQL.
 * Las credenciales viven en web/META-INF/context.xml (JNDI), nunca en el código.
 * Los DAO del paquete modelo/ reciben esta fuente de conexiones.
 */
public final class Conexion {

    public static final String JNDI_DATASOURCE = "java:comp/env/jdbc/ExamLenDB";

    private Conexion() {
    }

    public static DataSource obtenerFuente() {
        try {
            return (DataSource) new InitialContext().lookup(JNDI_DATASOURCE);
        } catch (NamingException e) {
            throw new IllegalStateException("No se encontró el DataSource " + JNDI_DATASOURCE
                    + ". Revisa META-INF/context.xml y conf/catalina.properties de Tomcat.", e);
        }
    }
}
