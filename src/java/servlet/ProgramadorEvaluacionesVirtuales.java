package servlet;

import conexion.Conexion;
import modelo.EventoSupervisionDAO;
import modelo.EvaluacionVirtualDAO;
import modelo.IntentoDAO;
import modelo.RespuestaDAO;
import servicio.ConfiguracionScheduler;
import servicio.ServicioEvaluacionVirtual;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import javax.sql.DataSource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Programador de evaluaciones virtuales (RF-42): sincroniza periodicamente los estados con las
 * fechas del servidor (abre PROGRAMADAS en ventana, cierra vencidas, finaliza intentos y ejecuta
 * RF-50) aunque nadie haga peticiones. Un unico hilo nombrado; se detiene con la aplicacion.
 */
@WebListener
public class ProgramadorEvaluacionesVirtuales implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(ProgramadorEvaluacionesVirtuales.class.getName());
    private static final long RETARDO_INICIAL_SEGUNDOS = 5;

    private ScheduledExecutorService ejecutor;

    @Override
    public void contextInitialized(ServletContextEvent evento) {
        try {
            DataSource dataSource = Conexion.obtenerFuente();
            ServicioEvaluacionVirtual servicio = new ServicioEvaluacionVirtual(
                    new EvaluacionVirtualDAO(dataSource), new IntentoDAO(dataSource),
                    new RespuestaDAO(dataSource), new EventoSupervisionDAO(dataSource));
            int intervalo = ConfiguracionScheduler.intervaloSegundos();
            ejecutor = Executors.newSingleThreadScheduledExecutor(tarea -> {
                Thread hilo = new Thread(tarea, "examlen-virtual-scheduler");
                hilo.setDaemon(true);
                return hilo;
            });
            ejecutor.scheduleWithFixedDelay(() -> sincronizar(servicio), RETARDO_INICIAL_SEGUNDOS,
                    intervalo, TimeUnit.SECONDS);
            LOG.log(Level.INFO, "Programador de evaluaciones virtuales iniciado (cada {0} s)", intervalo);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo iniciar el programador de evaluaciones virtuales", e);
        }
    }

    /** Una ejecucion; cualquier error se registra y no cancela las ejecuciones futuras. */
    private void sincronizar(ServicioEvaluacionVirtual servicio) {
        try {
            servicio.sincronizar();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Error en la sincronizacion automatica de evaluaciones virtuales", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent evento) {
        if (ejecutor == null) {
            return;
        }
        ejecutor.shutdownNow();
        try {
            if (!ejecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warning("El programador de evaluaciones virtuales no termino a tiempo.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("Programador de evaluaciones virtuales detenido.");
    }
}
