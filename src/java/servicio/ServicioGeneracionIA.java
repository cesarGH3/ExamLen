package servicio;

import logica.DocumentoFuente;
import logica.Docente;
import logica.EstadoPregunta;
import logica.Pregunta;
import logica.TipoGeneracion;
import logica.Usuario;
import modelo.BancoPreguntasDAO;
import modelo.DocumentoFuenteDAO;
import modelo.EvaluacionDAO;
import modelo.PersistenciaGeneracionDAO;
import modelo.PreguntaDAO;
import modelo.SolicitudGeneracionDAO;
import modelo.UsuarioDAO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Procesa las generaciones con IA en segundo plano (RNF-08) con el contrato completo del módulo
 * (RF-26/27/28, CU-19 5b): reclamación condicional, lectura del documento fuera del request,
 * proveedor desde el registro (RNF-12), normalización/validación por tipo, exclusión de repetidas
 * contra la evaluación de referencia y persistencia ATÓMICA del resultado válido.
 */
public final class ServicioGeneracionIA {

    private static final Logger LOG = Logger.getLogger(ServicioGeneracionIA.class.getName());
    private static final int MAX_MENSAJE_ERROR = 1000;
    private static final long ESPERA_PARADA_SEGUNDOS = 10;
    private static final long MAX_BYTES_DOCUMENTO = 10L * 1024 * 1024;
    private static final int MAX_REFERENCIA_EN_PROMPT = 20;
    private static final int MAX_ENUNCIADO_REFERENCIA = 160;

    private final SolicitudGeneracionDAO solicitudDAO;
    private final DocumentoFuenteDAO documentoDAO;
    private final UsuarioDAO usuarioDAO;
    private final BancoPreguntasDAO bancoDAO;
    private final PreguntaDAO preguntaDAO;
    private final EvaluacionDAO evaluacionDAO;
    private final PersistenciaGeneracionDAO persistenciaDAO;
    private final int workers;
    private final int capacidadCola;

    private volatile ThreadPoolExecutor ejecutor;

    public ServicioGeneracionIA(SolicitudGeneracionDAO solicitudDAO, DocumentoFuenteDAO documentoDAO,
                                UsuarioDAO usuarioDAO, BancoPreguntasDAO bancoDAO, PreguntaDAO preguntaDAO,
                                EvaluacionDAO evaluacionDAO, PersistenciaGeneracionDAO persistenciaDAO,
                                int workers, int capacidadCola) {
        this.solicitudDAO = solicitudDAO;
        this.documentoDAO = documentoDAO;
        this.usuarioDAO = usuarioDAO;
        this.bancoDAO = bancoDAO;
        this.preguntaDAO = preguntaDAO;
        this.evaluacionDAO = evaluacionDAO;
        this.persistenciaDAO = persistenciaDAO;
        this.workers = Math.max(1, workers);
        this.capacidadCola = Math.max(1, capacidadCola);
    }

    /** Arranca el ejecutor acotado una sola vez. */
    public synchronized void iniciar() {
        if (ejecutor != null) {
            return;
        }
        ThreadFactory fabricaHilos = new ThreadFactory() {
            private final AtomicInteger contador = new AtomicInteger();

            @Override
            public Thread newThread(Runnable tarea) {
                Thread hilo = new Thread(tarea, "examlen-ia-worker-" + contador.incrementAndGet());
                hilo.setDaemon(false);
                return hilo;
            }
        };
        ejecutor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacidadCola), fabricaHilos, new ThreadPoolExecutor.AbortPolicy());
        LOG.log(Level.INFO, "Procesador de generacion IA iniciado (workers={0}, cola={1})",
                new Object[]{workers, capacidadCola});
    }

    /**
     * Encola el trabajo. Devuelve false si el servicio esta detenido o la cola esta llena
     * (nunca se ejecuta en el hilo HTTP y nunca bloquea al navegador).
     */
    public boolean encolar(TrabajoGeneracionIA trabajo) {
        ThreadPoolExecutor actual = ejecutor;
        if (actual == null || actual.isShutdown()) {
            return false;
        }
        try {
            actual.execute(() -> procesar(trabajo));
            return true;
        } catch (RejectedExecutionException e) {
            LOG.log(Level.WARNING, "Cola de generacion IA llena; solicitud {0} rechazada",
                    trabajo.idSolicitud());
            return false;
        }
    }

    /** Parada limpia: no acepta mas trabajos, espera un tiempo razonable y luego interrumpe. */
    public synchronized void detener() {
        if (ejecutor == null) {
            return;
        }
        ejecutor.shutdown();
        try {
            if (!ejecutor.awaitTermination(ESPERA_PARADA_SEGUNDOS, TimeUnit.SECONDS)) {
                List<Runnable> pendientes = ejecutor.shutdownNow();
                LOG.log(Level.WARNING, "Procesador de generacion IA interrumpido; quedaron {0} trabajos sin ejecutar",
                        pendientes.size());
            }
        } catch (InterruptedException e) {
            ejecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ejecutor = null;
        LOG.info("Procesador de generacion IA detenido.");
    }

    // ------------------------------------------------------------------ procesamiento

    private void procesar(TrabajoGeneracionIA trabajo) {
        long inicio = System.nanoTime();
        try {
            if (!solicitudDAO.reclamarSolicitudGeneracion(trabajo.idSolicitud())) {
                LOG.log(Level.INFO, "Solicitud {0} ya reclamada por otro worker; se ignora",
                        trabajo.idSolicitud());
                return;
            }
            ejecutarGeneracion(trabajo);
            solicitudDAO.completarSolicitudGeneracion(trabajo.idSolicitud());
            LOG.log(Level.INFO, "Solicitud {0} COMPLETADA (proveedor={1}, modelo={2}, {3} ms)",
                    new Object[]{trabajo.idSolicitud(), trabajo.proveedor(), trabajo.modelo(),
                        (System.nanoTime() - inicio) / 1_000_000});
        } catch (GeneracionFallidaException e) {
            LOG.log(Level.WARNING, "Solicitud {0} FALLIDA ({1}): {2}",
                    new Object[]{trabajo.idSolicitud(), e.getTipo(), e.getMessage()});
            marcarFallidaSegura(trabajo.idSolicitud(), mensajeSeguro(e.getMessage()));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Error inesperado en la generacion IA de la solicitud "
                    + trabajo.idSolicitud(), e);
            marcarFallidaSegura(trabajo.idSolicitud(),
                    "No se pudo completar la generación por un error interno. Inténtalo de nuevo.");
        }
    }

    /** El marcado de FALLIDA nunca debe ocultar la causa original ni romper el cierre. */
    private void marcarFallidaSegura(int idSolicitud, String mensaje) {
        try {
            solicitudDAO.marcarFallidaSolicitudGeneracion(idSolicitud, mensaje);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo marcar FALLIDA la solicitud " + idSolicitud
                    + " (¿apagado en curso?)", e);
        }
    }

    private void ejecutarGeneracion(TrabajoGeneracionIA trabajo) throws GeneracionFallidaException {
        Docente docente = docenteDe(trabajo);
        String contexto = leerDocumento(trabajo);
        ProveedorIAFabrica fabrica = RegistroProveedoresIA.porCodigo(trabajo.proveedor())
                .orElseThrow(() -> new GeneracionFallidaException(
                        "El proveedor de IA indicado no está registrado.", TipoErrorIA.CONFIGURACION));
        ProveedorIA proveedor = fabrica.crear(trabajo.modelo());

        if (trabajo.tipo() != TipoGeneracion.MATERIAL_PRACTICA && trabajo.idBancoDestino() != null
                && bancoDAO.buscarPorIdDeDocente(trabajo.idBancoDestino(), trabajo.idDocente()).isEmpty()) {
            throw new GeneracionFallidaException("El banco destino ya no está disponible.",
                    TipoErrorIA.DOCUMENTO);
        }
        Resultado normalizado = switch (trabajo.tipo()) {
            case PREGUNTAS -> generarPreguntas(trabajo, proveedor, contexto);
            case VARIANTES -> generarVariantes(trabajo, proveedor, contexto);
            case MATERIAL_PRACTICA -> generarMaterial(trabajo, proveedor, contexto);
        };
        if (normalizado.validas.isEmpty()) {
            registrarConteosSeguro(trabajo.idSolicitud(), trabajo.cantidad(), normalizado.recibidos, 0);
            throw new GeneracionFallidaException("La IA no devolvió preguntas válidas para guardar.",
                    TipoErrorIA.RESPUESTA);
        }
        int guardadas;
        try {
            guardadas = persistenciaDAO.persistir(trabajo.idDocente(), trabajo.idSolicitud(), trabajo.tipo(),
                    trabajo.nivel(), trabajo.idBancoDestino(), trabajo.instrucciones(), marcaTiempo(),
                    normalizado.validas);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Error de persistencia en la solicitud " + trabajo.idSolicitud(), e);
            throw new GeneracionFallidaException("No se pudo guardar el contenido generado. "
                    + "Inténtalo de nuevo.", TipoErrorIA.PERSISTENCIA, e);
        }
        registrarConteosSeguro(trabajo.idSolicitud(), trabajo.cantidad(), normalizado.recibidos, guardadas);
        LOG.log(Level.INFO, "Solicitud {0}: recibidos={1} descartados={2} guardados={3}",
                new Object[]{trabajo.idSolicitud(), normalizado.recibidos, normalizado.descartadas,
                    guardadas});
    }

    /**
     * CU-19 5b: persiste el desglose exacto de la ultima generacion (recibidas, validas, invalidas y
     * no devueltas). Nunca rompe el cierre de la solicitud si falla la escritura del desglose.
     */
    private void registrarConteosSeguro(int idSolicitud, int solicitadas, int recibidas, int validas) {
        try {
            int invalidas = Math.max(0, recibidas - validas);
            int noDevueltas = Math.max(0, solicitadas - recibidas);
            solicitudDAO.registrarConteos(idSolicitud, Math.max(0, recibidas), Math.max(0, validas),
                    invalidas, noDevueltas);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "No se pudo guardar el desglose de la solicitud " + idSolicitud, e);
        }
    }

    // ------------------------------------------------------------------ por tipo de generación

    private Resultado generarPreguntas(TrabajoGeneracionIA trabajo, ProveedorIA proveedor, String contexto)
            throws GeneracionFallidaException {
        ResultadoGeneracion recibido = proveedor.generarPreguntas(contexto, trabajo.instrucciones(),
                trabajo.cantidad(), trabajo.nivel().name());
        ValidadorPreguntasGeneradas.Resultado validadas = ValidadorPreguntasGeneradas.normalizar(
                recibido.items(), trabajo.nivel(), null, null, trabajo.competenciaElegida(),
                trabajo.temaElegido());
        Resultado resultado = new Resultado();
        resultado.recibidos = recibido.recibidos();
        resultado.descartadas = recibido.descartados() + validadas.descartadas;
        for (ValidadorPreguntasGeneradas.PreguntaNormalizada valida : validadas.validas) {
            resultado.validas.add(aPersistible(valida, trabajo.nivel()));
        }
        return resultado;
    }

    private Resultado generarVariantes(TrabajoGeneracionIA trabajo, ProveedorIA proveedor, String contexto)
            throws GeneracionFallidaException {
        List<Pregunta> bases = cargarBases(trabajo);
        int[] cupos = repartir(trabajo.cantidad(), bases.size());
        Resultado resultado = new Resultado();
        for (int i = 0; i < bases.size(); i++) {
            Pregunta base = bases.get(i);
            if (cupos[i] <= 0) {
                continue;
            }
            String instruccionesVariante = "Reformula la siguiente pregunta conservando estrictamente su "
                    + "significado, competencia, dificultad y tema, y devuelve exactamente " + cupos[i]
                    + " variante(s) del mismo tipo (" + base.tipoPregunta.name() + "). Pregunta base: \""
                    + base.enunciadoPregunta.strip() + "\". " + trabajo.instrucciones();
            ResultadoGeneracion parcial = proveedor.generarPreguntas(contexto, instruccionesVariante,
                    cupos[i], base.nivelPregunta == null ? trabajo.nivel().name() : base.nivelPregunta.name());
            ValidadorPreguntasGeneradas.Resultado validadas = ValidadorPreguntasGeneradas.normalizar(
                    parcial.items(), base.nivelPregunta, base.tipoPregunta, base.dificultadPregunta,
                    base.competenciaPregunta, base.temaPregunta);
            resultado.recibidos += parcial.recibidos();
            resultado.descartadas += parcial.descartados() + validadas.descartadas;
            for (ValidadorPreguntasGeneradas.PreguntaNormalizada valida : validadas.validas) {
                resultado.validas.add(aPersistible(valida, base.nivelPregunta == null
                        ? trabajo.nivel() : base.nivelPregunta));
            }
        }
        return resultado;
    }

    private Resultado generarMaterial(TrabajoGeneracionIA trabajo, ProveedorIA proveedor, String contexto)
            throws GeneracionFallidaException {
        Set<String> referencia = new HashSet<>();
        String instrucciones = trabajo.instrucciones();
        if (trabajo.idEvaluacionReferencia() != null) {
            logica.Evaluacion evaluacion = evaluacionDAO
                    .buscarPorIdDeDocente(trabajo.idEvaluacionReferencia(), trabajo.idDocente())
                    .orElseThrow(() -> new GeneracionFallidaException(
                            "La evaluación de referencia ya no está disponible.", TipoErrorIA.DOCUMENTO));
            List<Pregunta> fuentes = preguntaDAO.listarPorEvaluacion(evaluacion.getIdEvaluacion());
            StringBuilder lista = new StringBuilder();
            int citadas = 0;
            for (Pregunta fuente : fuentes) {
                referencia.add(ValidadorPreguntasGeneradas.normalizarEnunciado(fuente.enunciadoPregunta));
                if (citadas < MAX_REFERENCIA_EN_PROMPT) {
                    if (citadas > 0) {
                        lista.append(" | ");
                    }
                    lista.append(recortar(fuente.enunciadoPregunta, MAX_ENUNCIADO_REFERENCIA));
                    citadas++;
                }
            }
            if (!referencia.isEmpty()) {
                instrucciones = instrucciones + " No repitas ni copies estas preguntas de la evaluación "
                        + "de referencia: " + lista + ".";
            }
        }
        ResultadoGeneracion recibido = proveedor.generarPreguntas(contexto, instrucciones,
                trabajo.cantidad(), trabajo.nivel().name());
        ValidadorPreguntasGeneradas.Resultado validadas = ValidadorPreguntasGeneradas.normalizar(
                recibido.items(), trabajo.nivel(), null, null, trabajo.competenciaElegida(),
                trabajo.temaElegido());
        Resultado resultado = new Resultado();
        resultado.recibidos = recibido.recibidos();
        resultado.descartadas = recibido.descartados() + validadas.descartadas;
        for (ValidadorPreguntasGeneradas.PreguntaNormalizada valida : validadas.validas) {
            if (!referencia.isEmpty()
                    && referencia.contains(ValidadorPreguntasGeneradas.normalizarEnunciado(valida.enunciado))) {
                resultado.descartadas++;
                continue;
            }
            resultado.validas.add(aPersistible(valida, trabajo.nivel()));
        }
        return resultado;
    }

    // ------------------------------------------------------------------ apoyo

    private static final class Resultado {
        final List<PersistenciaGeneracionDAO.PreguntaPersistible> validas = new ArrayList<>();
        int recibidos;
        int descartadas;
    }

    private static PersistenciaGeneracionDAO.PreguntaPersistible aPersistible(
            ValidadorPreguntasGeneradas.PreguntaNormalizada valida, logica.NivelIngles nivel) {
        PersistenciaGeneracionDAO.PreguntaPersistible persistible =
                new PersistenciaGeneracionDAO.PreguntaPersistible();
        persistible.enunciado = valida.enunciado;
        persistible.tipo = valida.tipo;
        persistible.nivel = nivel;
        persistible.dificultad = valida.dificultad;
        persistible.competencia = valida.competencia;
        persistible.tema = valida.tema;
        persistible.respuestaEsperada = valida.respuestaEsperada;
        for (ValidadorPreguntasGeneradas.Opcion opcion : valida.opciones) {
            persistible.opciones.add(new PersistenciaGeneracionDAO.OpcionPregunta(opcion.texto, opcion.correcta));
        }
        return persistible;
    }

    private List<Pregunta> cargarBases(TrabajoGeneracionIA trabajo) throws GeneracionFallidaException {
        List<Pregunta> bases = new ArrayList<>();
        for (Integer idBase : trabajo.idsPreguntasBase()) {
            Pregunta base = preguntaDAO.buscarPorId(idBase)
                    .orElseThrow(() -> new GeneracionFallidaException(
                            "Una de las preguntas base ya no existe.", TipoErrorIA.DOCUMENTO));
            if (base.estadoPregunta == EstadoPregunta.ARCHIVADA
                    || !preguntaDAO.esDeDocente(idBase, trabajo.idDocente())) {
                throw new GeneracionFallidaException("Una de las preguntas base no está disponible.",
                        TipoErrorIA.DOCUMENTO);
            }
            bases.add(base);
        }
        return bases;
    }

    /** Reparto determinista de la cantidad total entre las bases (round-robin desde la primera). */
    static int[] repartir(int total, int partes) {
        int[] cupos = new int[partes];
        for (int i = 0; i < partes; i++) {
            cupos[i] = total / partes + (i < total % partes ? 1 : 0);
        }
        return cupos;
    }

    private Docente docenteDe(TrabajoGeneracionIA trabajo) throws GeneracionFallidaException {
        Usuario usuario = usuarioDAO.buscarPorId(trabajo.idDocente()).orElse(null);
        if (!(usuario instanceof Docente)) {
            throw new GeneracionFallidaException("La cuenta del docente ya no está disponible.",
                    TipoErrorIA.CONFIGURACION);
        }
        return (Docente) usuario;
    }

    /** El documento se revalida (propiedad, tamaño, existencia) y se lee desde la ruta persistida. */
    private String leerDocumento(TrabajoGeneracionIA trabajo) throws GeneracionFallidaException {
        if (trabajo.idDocumentoFuente() == null) {
            return "";
        }
        if (!documentoDAO.perteneceA(trabajo.idDocumentoFuente(), trabajo.idDocente())) {
            throw new GeneracionFallidaException("El documento fuente ya no está disponible. "
                    + "Vuelve a cargarlo y reintenta la solicitud.", TipoErrorIA.DOCUMENTO);
        }
        DocumentoFuente documento = documentoDAO.buscarPorId(trabajo.idDocumentoFuente())
                .orElseThrow(() -> new GeneracionFallidaException("El documento fuente ya no está disponible. "
                        + "Vuelve a cargarlo y reintenta la solicitud.", TipoErrorIA.DOCUMENTO));
        Path ruta = Paths.get(documento.rutaAlmacenamientoDocumentoFuente);
        try {
            if (!Files.isReadable(ruta)) {
                throw new GeneracionFallidaException("El archivo del documento fuente ya no está disponible. "
                        + "Vuelve a cargarlo y reintenta la solicitud.", TipoErrorIA.DOCUMENTO);
            }
            if (Files.size(ruta) > MAX_BYTES_DOCUMENTO) {
                throw new GeneracionFallidaException("El archivo del documento fuente supera el tamaño "
                        + "máximo permitido de 10 MB.", TipoErrorIA.DOCUMENTO);
            }
            return ExtractorTexto.extraer(Files.readAllBytes(ruta), documento.nombreArchivoDocumentoFuente);
        } catch (IOException e) {
            throw new GeneracionFallidaException("No se pudo leer el documento fuente. Vuelve a cargarlo "
                    + "y reintenta la solicitud.", TipoErrorIA.DOCUMENTO, e);
        } catch (RuntimeException e) {
            throw new GeneracionFallidaException("No se pudo leer el documento fuente. Vuelve a cargarlo "
                    + "y reintenta la solicitud.", TipoErrorIA.DOCUMENTO, e);
        }
    }

    /** Mensaje seguro para mensaje_error: sin claves, sin stack, recortado al tamaño de la columna. */
    static String mensajeSeguro(String mensaje) {
        String limpio = mensaje == null || mensaje.isBlank()
                ? "No se pudo completar la generación. Inténtalo de nuevo."
                : mensaje.strip();
        return limpio.length() <= MAX_MENSAJE_ERROR ? limpio : limpio.substring(0, MAX_MENSAJE_ERROR);
    }

    private static String marcaTiempo() {
        return logica.Fechas.formato("yyyy-MM-dd HH:mm", logica.Fechas.ahora());
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.strip();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
