package servicio;

import logica.AnalisisSupervision;
import logica.EvaluacionVirtual;
import logica.Intento;
import logica.TipoEventoSupervision;
import modelo.EvaluacionVirtualDAO;
import modelo.EventoSupervisionDAO;
import modelo.IntentoDAO;
import modelo.RespuestaDAO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logica de negocio compartida de las evaluaciones virtuales (RF-42, RF-47, RF-50):
 * apertura/cierre automaticos por fechas, cierre/cancelacion manuales, finalizacion de intentos
 * (por cierre de la virtual o por vencimiento individual) y analisis de supervision.
 * La usan el programador de fondo y DocenteVirtualServlet; vive en la capa de servicio.
 */
public class ServicioEvaluacionVirtual {

    private static final Logger LOG = Logger.getLogger(ServicioEvaluacionVirtual.class.getName());

    private static final int MAX_PREGUNTAS_CITADAS = 3;
    private static final int MAX_DESCRIPCION = 500;

    private final EvaluacionVirtualDAO virtualDAO;
    private final IntentoDAO intentoDAO;
    private final RespuestaDAO respuestaDAO;
    private final EventoSupervisionDAO eventoDAO;

    public ServicioEvaluacionVirtual(EvaluacionVirtualDAO virtualDAO, IntentoDAO intentoDAO,
                                     RespuestaDAO respuestaDAO, EventoSupervisionDAO eventoDAO) {
        this.virtualDAO = virtualDAO;
        this.intentoDAO = intentoDAO;
        this.respuestaDAO = respuestaDAO;
        this.eventoDAO = eventoDAO;
    }

    /**
     * Sincroniza los estados con las fechas del servidor (lo llama el programador): abre
     * PROGRAMADAS en ventana, cierra virtuales vencidas y finaliza intentos vencidos
     * individualmente (aunque su virtual siga ABIERTA). Un error en una evaluacion o intento se
     * registra y no impide procesar los demas. Devuelve cuantas operaciones efectivas hizo.
     */
    public int sincronizar() {
        int transiciones = 0;
        for (Integer id : virtualDAO.listarProgramadasEnVentana()) {
            try {
                if (virtualDAO.abrirProgramada(id)) {
                    transiciones++;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Error al abrir automaticamente la evaluacion virtual " + id, e);
            }
        }
        for (Integer id : virtualDAO.listarVencidas()) {
            try {
                if (cerrar(id)) {
                    transiciones++;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Error al cerrar automaticamente la evaluacion virtual " + id, e);
            }
        }
        for (Integer id : intentoDAO.listarVencidos()) {
            try {
                if (finalizarIntentoVencido(id)) {
                    transiciones++;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Error al finalizar el intento vencido " + id, e);
            }
        }
        if (transiciones > 0) {
            LOG.log(Level.INFO, "Evaluaciones virtuales sincronizadas: {0} operaciones", transiciones);
        }
        return transiciones;
    }

    /**
     * Cierre (manual o automatico) idempotente: reclama la transicion PROGRAMADA/ABIERTA -> CERRADA
     * de forma atomica; si otro actor ya cerro o cancelo, no hace nada. Despues finaliza los intentos
     * EN_CURSO (los ya finalizados o calificados no se tocan) y ejecuta RF-50 si hay supervision.
     */
    public boolean cerrar(int idEvaluacionVirtual) {
        if (!virtualDAO.reclamarCierre(idEvaluacionVirtual)) {
            return false;
        }
        finalizarIntentosEnCurso(idEvaluacionVirtual);
        EvaluacionVirtual virtual = virtualDAO.buscarPorId(idEvaluacionVirtual).orElse(null);
        if (virtual != null && virtual.supervisionActivaEvaluacionVirtual) {
            analizarSupervision(virtual);
        }
        return true;
    }

    /**
     * Cancelacion manual idempotente: CANCELADA es terminal para el programador (nunca vuelve a
     * ABIERTA ni CERRADA). Finaliza los intentos EN_CURSO con la logica comun.
     */
    public boolean cancelar(int idEvaluacionVirtual) {
        if (!virtualDAO.reclamarCancelacion(idEvaluacionVirtual)) {
            return false;
        }
        finalizarIntentosEnCurso(idEvaluacionVirtual);
        return true;
    }

    /** Apertura manual (RF-42): solo desde PROGRAMADA; CANCELADA y CERRADA no se reabren. */
    public boolean abrir(int idEvaluacionVirtual) {
        return virtualDAO.reclamarApertura(idEvaluacionVirtual);
    }

    /**
     * Finaliza y califica un intento cuyo limite efectivo ya paso, aunque la virtual siga ABIERTA
     * (RF-47). Idempotente: reclama EN_CURSO -> FINALIZADO con fecha_fin = limite (Fase 3B) y luego
     * califica lo automatico. No ejecuta RF-50: el analisis grupal es al cerrar la virtual.
     */
    public boolean finalizarIntentoVencido(int idIntento) {
        if (!intentoDAO.reclamarVencido(idIntento)) {
            return false;
        }
        int pendientes = respuestaDAO.calificarAutomaticas(idIntento);
        Intento intento = intentoDAO.buscarPorId(idIntento).orElse(null);
        if (intento == null) {
            return true;
        }
        if (pendientes == 0) {
            intentoDAO.calificarIntento(intento);
        } else {
            intentoDAO.finalizarIntento(intento);
        }
        return true;
    }

    /**
     * Finaliza los intentos EN_CURSO al cerrar/cancelar la virtual (RF-42): califica lo automatico
     * y deja pendientes las abiertas. Los intentos ya finalizados o calificados no se modifican.
     */
    private void finalizarIntentosEnCurso(int idEvaluacionVirtual) {
        for (Intento intento : intentoDAO.listarEnCursoPorVirtual(idEvaluacionVirtual)) {
            int pendientes = respuestaDAO.calificarAutomaticas(intento.getIdIntento());
            if (pendientes == 0) {
                intentoDAO.calificarIntento(intento);
            } else {
                intentoDAO.finalizarIntento(intento);
            }
        }
    }

    // ------------------------------------------------------------------ analisis de supervision (RF-50)

    /**
     * Al cerrarse una evaluacion virtual supervisada: tiempos atipicos y similitud de respuestas,
     * comparando cada intento contra el conjunto del grupo. Es idempotente: si la virtual ya tiene
     * eventos de esos tipos, no se vuelven a generar (vale para cierre manual y automatico).
     */
    private void analizarSupervision(EvaluacionVirtual virtual) {
        int idVirtual = virtual.getIdEvaluacionVirtual();
        List<Intento> intentos = intentoDAO.listarPorEvaluacionVirtual(idVirtual);
        if (!eventoDAO.existeEventoDeTipoEnVirtual(idVirtual, TipoEventoSupervision.TIEMPO_ATIPICO)) {
            registrarTiemposAtipicos(intentos);
        }
        if (!eventoDAO.existeEventoDeTipoEnVirtual(idVirtual, TipoEventoSupervision.SIMILITUD_RESPUESTAS)) {
            registrarSimilitudes(intentos, idVirtual);
        }
    }

    /** RF-50: duracion menor a la fraccion configurada de la mediana del grupo. */
    private void registrarTiemposAtipicos(List<Intento> intentos) {
        Map<Integer, Long> duraciones = new HashMap<>();
        for (Intento intento : intentos) {
            if (intento.fechaInicioIntento != null && intento.fechaFinIntento != null) {
                duraciones.put(intento.getIdIntento(),
                        intento.fechaFinIntento.getTime() - intento.fechaInicioIntento.getTime());
            }
        }
        long mediana = AnalisisSupervision.mediana(new ArrayList<>(duraciones.values()));
        if (duraciones.size() < 2 || mediana <= 0) {
            return;
        }
        double fraccion = ConfiguracionSupervision.fraccionMedianaTiempo();
        for (Intento intento : intentos) {
            Long duracion = duraciones.get(intento.getIdIntento());
            if (duracion != null && AnalisisSupervision.tiempoAtipico(duracion, mediana, fraccion)) {
                eventoDAO.registrarEventoSupervision(intento, TipoEventoSupervision.TIEMPO_ATIPICO,
                        "Duró " + duracionTexto(duracion) + ": menos del " + Math.round(fraccion * 100)
                        + " % de la mediana del grupo (" + duracionTexto(mediana) + ").");
            }
        }
    }

    /** RF-50: un evento por par de intentos con respuestas excesivamente similares, replicado en ambos. */
    private void registrarSimilitudes(List<Intento> intentos, int idVirtual) {
        List<RespuestaDAO.TextoRespuesta> textos = respuestaDAO.listarTextoDeEvaluacionVirtual(idVirtual);
        if (textos.isEmpty()) {
            return;
        }
        Map<Integer, Intento> intentosPorId = new HashMap<>();
        for (Intento intento : intentos) {
            intentosPorId.put(intento.getIdIntento(), intento);
        }
        Map<Integer, List<RespuestaDAO.TextoRespuesta>> porPregunta = new HashMap<>();
        for (RespuestaDAO.TextoRespuesta texto : textos) {
            porPregunta.computeIfAbsent(texto.idPregunta, clave -> new ArrayList<>()).add(texto);
        }
        double umbral = ConfiguracionSupervision.umbralSimilitud();
        Map<String, List<Coincidencia>> porPar = new LinkedHashMap<>();
        for (List<RespuestaDAO.TextoRespuesta> respuestas : porPregunta.values()) {
            for (int i = 0; i < respuestas.size(); i++) {
                for (int j = i + 1; j < respuestas.size(); j++) {
                    RespuestaDAO.TextoRespuesta a = respuestas.get(i);
                    RespuestaDAO.TextoRespuesta b = respuestas.get(j);
                    if (a.idIntento == b.idIntento || coincideConLaEsperada(a) || coincideConLaEsperada(b)) {
                        continue;
                    }
                    double similitud = AnalisisSupervision.similitud(a.contenido, b.contenido);
                    if (similitud >= umbral) {
                        String clave = Math.min(a.idIntento, b.idIntento) + ":"
                                + Math.max(a.idIntento, b.idIntento);
                        porPar.computeIfAbsent(clave, k -> new ArrayList<>())
                                .add(new Coincidencia(a, b, similitud));
                    }
                }
            }
        }
        for (List<Coincidencia> coincidencias : porPar.values()) {
            registrarPar(coincidencias, intentosPorId);
        }
    }

    /**
     * Descarta la respuesta que coincide con la respuesta esperada: quien sabe la respuesta escribe
     * lo mismo que los demas y eso no es un indicio de copia.
     */
    private static boolean coincideConLaEsperada(RespuestaDAO.TextoRespuesta respuesta) {
        return AnalisisSupervision.coincideConEsperada(respuesta.contenido, respuesta.respuestaEsperada);
    }

    private void registrarPar(List<Coincidencia> coincidencias, Map<Integer, Intento> intentosPorId) {
        coincidencias.sort((una, otra) -> Double.compare(otra.similitud, una.similitud));
        Intento intentoA = intentosPorId.get(coincidencias.get(0).a.idIntento);
        Intento intentoB = intentosPorId.get(coincidencias.get(0).b.idIntento);
        if (intentoA == null || intentoB == null) {
            return;
        }
        StringBuilder cuerpo = new StringBuilder();
        int citadas = 0;
        for (Coincidencia coincidencia : coincidencias) {
            if (citadas == MAX_PREGUNTAS_CITADAS) {
                break;
            }
            if (citadas > 0) {
                cuerpo.append("; ");
            }
            cuerpo.append("«").append(recortar(coincidencia.a.enunciado, 60)).append("» → \"")
                    .append(recortar(coincidencia.a.contenido, 70)).append("\"");
            citadas++;
        }
        if (coincidencias.size() > citadas) {
            cuerpo.append(" y ").append(coincidencias.size() - citadas).append(" más");
        }
        String inicio = "Similitud del " + Math.round(coincidencias.get(0).similitud * 100) + " % con ";
        String cola = " en " + coincidencias.size()
                + (coincidencias.size() == 1 ? " respuesta: " : " respuestas: ") + cuerpo;
        eventoDAO.registrarEventoSupervision(intentoA, TipoEventoSupervision.SIMILITUD_RESPUESTAS,
                recortar(inicio + nombreDe(intentoB.getIdIntento()) + cola, MAX_DESCRIPCION));
        eventoDAO.registrarEventoSupervision(intentoB, TipoEventoSupervision.SIMILITUD_RESPUESTAS,
                recortar(inicio + nombreDe(intentoA.getIdIntento()) + cola, MAX_DESCRIPCION));
    }

    private String nombreDe(int idIntento) {
        return intentoDAO.estudianteDeIntento(idIntento)
                .map(estudiante -> estudiante.nombreUsuario).orElse("otro intento");
    }

    private static String duracionTexto(long milisegundos) {
        long segundos = Math.max(0, milisegundos / 1000);
        if (segundos < 60) {
            return segundos + " s";
        }
        long minutos = Math.round(segundos / 60.0);
        if (minutos < 60) {
            return minutos + " min";
        }
        return (minutos / 60) + " h " + (minutos % 60) + " min";
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo).strip() + "…";
    }

    private static final class Coincidencia {
        final RespuestaDAO.TextoRespuesta a;
        final RespuestaDAO.TextoRespuesta b;
        final double similitud;

        Coincidencia(RespuestaDAO.TextoRespuesta a, RespuestaDAO.TextoRespuesta b, double similitud) {
            this.a = a;
            this.b = b;
            this.similitud = similitud;
        }
    }
}
