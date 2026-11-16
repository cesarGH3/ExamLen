package servlet;

import conexion.Conexion;
import modelo.BancoPreguntasDAO;
import modelo.ClaveIADocenteDAO;
import modelo.DocumentoFuenteDAO;
import modelo.EvaluacionDAO;
import modelo.PersistenciaGeneracionDAO;
import modelo.PreguntaDAO;
import modelo.SolicitudGeneracionDAO;
import modelo.UsuarioDAO;
import servicio.ConfiguracionIA;
import servicio.ServicioGeneracionIA;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Ciclo de vida del procesador de generacion IA (RNF-08): inicia una sola vez con la
 * aplicacion, marca las solicitudes abandonadas por un reinicio anterior como FALLIDA y
 * detiene el ejecutor en el undeploy. La logica y los proveedores (RNF-12) viven en
 * {@link ServicioGeneracionIA} y el registro por ServiceLoader.
 */
@WebListener
public class ProcesadorGeneracionIA implements ServletContextListener {

    public static final String SERVICIO_IA = "examlen.servicioIA";

    static final String MENSAJE_REINICIO = "La generación fue interrumpida por un reinicio del servicio. "
            + "Puedes reintentar la solicitud.";

    private static final Logger LOG = Logger.getLogger(ProcesadorGeneracionIA.class.getName());

    private ServicioGeneracionIA servicio;

    @Override
    public void contextInitialized(ServletContextEvent evento) {
        try {
            DataSource dataSource = Conexion.obtenerFuente();
            SolicitudGeneracionDAO solicitudDAO = new SolicitudGeneracionDAO(dataSource);
            servicio = new ServicioGeneracionIA(solicitudDAO, new DocumentoFuenteDAO(dataSource),
                    new ClaveIADocenteDAO(dataSource), new UsuarioDAO(dataSource),
                    new BancoPreguntasDAO(dataSource), new PreguntaDAO(dataSource),
                    new EvaluacionDAO(dataSource), new PersistenciaGeneracionDAO(dataSource),
                    ConfiguracionIA.trabajadores(), ConfiguracionIA.capacidadCola());
            int recuperadas = solicitudDAO.marcarAbandonadasComoFallidas(MENSAJE_REINICIO);
            servicio.iniciar();
            evento.getServletContext().setAttribute(SERVICIO_IA, servicio);
            LOG.log(Level.INFO, "Procesador de generacion IA listo; solicitudes abandonadas marcadas FALLIDA: {0}",
                    recuperadas);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo iniciar el procesador de generacion IA", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent evento) {
        if (servicio != null) {
            servicio.detener();
        }
    }
}
