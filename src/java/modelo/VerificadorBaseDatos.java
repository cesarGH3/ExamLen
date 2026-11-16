package modelo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Comprueba que MySQL responde. Se usa en cada peticion: sin base de datos, la aplicacion no opera. */
public class VerificadorBaseDatos {

    private static final Logger LOG = Logger.getLogger(VerificadorBaseDatos.class.getName());
    private static final int SEGUNDOS_ESPERA = 2;

    private final DataSource dataSource;

    public VerificadorBaseDatos(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean estaDisponible() {
        if (!InicializadorEsquema.consultarDisponibilidadEsquema()) return false;
        try (Connection conexion = dataSource.getConnection()) {
            return conexion.isValid(SEGUNDOS_ESPERA);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Base de datos no disponible: {0}", e.getMessage());
            return false;
        }
    }
}
