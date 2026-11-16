package conexion;

import com.mysql.cj.jdbc.MysqlDataSource;
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
                fuente = crearFuente(configuracion);
                return fuente;
            }
            try {
                fuente = (DataSource) new InitialContext().lookup(JNDI_DATASOURCE);
                return fuente;
            } catch (NamingException e) {
                throw new IllegalStateException("No se encontró configuración de base de datos. "
                        + "Crea WEB-INF/classes/examlen.properties o revisa META-INF/context.xml "
                        + "y conf/catalina.properties de Tomcat.", e);
            }
        }
    }

    private static DataSource crearFuente(ConfiguracionBD configuracion) {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(configuracion.urlBase());
        dataSource.setUser(configuracion.usuario());
        dataSource.setPassword(configuracion.contrasena());
        return dataSource;
    }
}
