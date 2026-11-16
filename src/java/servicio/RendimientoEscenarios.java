package servicio;

import logica.Administrador;
import logica.BancoPreguntas;
import logica.Competencia;
import logica.Dificultad;
import logica.Docente;
import logica.DocumentoFuente;
import logica.Estudiante;
import logica.Evaluacion;
import logica.EvaluacionVirtual;
import logica.Fechas;
import logica.Grupo;
import logica.Inscripcion;
import logica.NivelIngles;
import logica.OpcionRespuesta;
import logica.Pregunta;
import logica.Respuesta;
import logica.RespuestaPractica;
import logica.TipoGeneracion;
import logica.TipoPregunta;
import logica.TokenRecuperacion;
import logica.VersionEvaluacion;
import logica.VersionPregunta;
import modelo.AdministradorDAO;
import modelo.BancoPreguntasDAO;
import modelo.CursoDAO;
import modelo.DocenteDAO;
import modelo.DocumentoFuenteDAO;
import modelo.EstudianteDAO;
import modelo.EvaluacionDAO;
import modelo.EvaluacionVirtualDAO;
import modelo.EventoSupervisionDAO;
import modelo.GrupoDAO;
import modelo.InscripcionDAO;
import modelo.IntentoDAO;
import modelo.IntentoPracticaDAO;
import modelo.MaterialPracticaDAO;
import modelo.OpcionRespuestaDAO;
import modelo.PreguntaDAO;
import modelo.RespuestaDAO;
import modelo.RespuestaPracticaDAO;
import modelo.SolicitudGeneracionDAO;
import modelo.TokenRecuperacionDAO;
import modelo.UsuarioDAO;
import modelo.VersionEvaluacionDAO;
import modelo.VersionPreguntaDAO;
import java.util.List;

/**
 * Escenarios controlados de rendimiento para los 34 CU (Fase benchmark). Cada ejecución prepara
 * datos PERF_ reutilizables, ejecuta la lógica real del CU y verifica un resultado mínimo; la
 * limpieza de PERF_ la hace RendimientoDAO. No persiste nada en tablas nuevas.
 */
public final class RendimientoEscenarios {

    public static final class Resultado {

        public boolean verificado;
        public String observacion = "";
        public Integer solicitudId;
        public Double aceptacionMs;
        public String estadoSolicitud;
    }

    public static final class AceptacionIA {

        public int idSolicitud;
        public boolean encolada;
        public boolean proveedorDisponible;
    }

    private final AdministradorDAO administradorDAO;
    private final UsuarioDAO usuarioDAO;
    private final DocenteDAO docenteDAO;
    private final EstudianteDAO estudianteDAO;
    private final CursoDAO cursoDAO;
    private final GrupoDAO grupoDAO;
    private final InscripcionDAO inscripcionDAO;
    private final BancoPreguntasDAO bancoDAO;
    private final PreguntaDAO preguntaDAO;
    private final OpcionRespuestaDAO opcionDAO;
    private final DocumentoFuenteDAO documentoDAO;
    private final TokenRecuperacionDAO tokenDAO;
    private final EvaluacionDAO evaluacionDAO;
    private final VersionEvaluacionDAO versionDAO;
    private final VersionPreguntaDAO versionPreguntaDAO;
    private final EvaluacionVirtualDAO virtualDAO;
    private final IntentoDAO intentoDAO;
    private final RespuestaDAO respuestaDAO;
    private final EventoSupervisionDAO eventoDAO;
    private final MaterialPracticaDAO materialDAO;
    private final IntentoPracticaDAO intentoPracticaDAO;
    private final RespuestaPracticaDAO respuestaPracticaDAO;
    private final SolicitudGeneracionDAO solicitudDAO;
    private final ServicioEvaluacionVirtual servicioVirtual;
    private final ServicioGeneracionIA servicioIA;

    private int secuencia;
    private boolean listo;
    private Administrador administrador;
    private Docente docente;
    private Estudiante estudiante;
    private logica.Curso curso;
    private Grupo grupo;
    private Inscripcion inscripcion;
    private BancoPreguntas banco;
    private Pregunta pregunta;
    private OpcionRespuesta opcion;
    private Evaluacion evaluacion;
    private VersionEvaluacion version;
    private VersionPregunta versionPregunta;

    public RendimientoEscenarios(AdministradorDAO administradorDAO, UsuarioDAO usuarioDAO, DocenteDAO docenteDAO,
                                 EstudianteDAO estudianteDAO, CursoDAO cursoDAO, GrupoDAO grupoDAO,
                                 InscripcionDAO inscripcionDAO, BancoPreguntasDAO bancoDAO, PreguntaDAO preguntaDAO,
                                 OpcionRespuestaDAO opcionDAO, DocumentoFuenteDAO documentoDAO,
                                 TokenRecuperacionDAO tokenDAO, EvaluacionDAO evaluacionDAO,
                                 VersionEvaluacionDAO versionDAO, VersionPreguntaDAO versionPreguntaDAO,
                                 EvaluacionVirtualDAO virtualDAO, IntentoDAO intentoDAO, RespuestaDAO respuestaDAO,
                                 EventoSupervisionDAO eventoDAO, MaterialPracticaDAO materialDAO,
                                 IntentoPracticaDAO intentoPracticaDAO, RespuestaPracticaDAO respuestaPracticaDAO,
                                 SolicitudGeneracionDAO solicitudDAO, ServicioEvaluacionVirtual servicioVirtual,
                                 ServicioGeneracionIA servicioIA) {
        this.administradorDAO = administradorDAO;
        this.usuarioDAO = usuarioDAO;
        this.docenteDAO = docenteDAO;
        this.estudianteDAO = estudianteDAO;
        this.cursoDAO = cursoDAO;
        this.grupoDAO = grupoDAO;
        this.inscripcionDAO = inscripcionDAO;
        this.bancoDAO = bancoDAO;
        this.preguntaDAO = preguntaDAO;
        this.opcionDAO = opcionDAO;
        this.documentoDAO = documentoDAO;
        this.tokenDAO = tokenDAO;
        this.evaluacionDAO = evaluacionDAO;
        this.versionDAO = versionDAO;
        this.versionPreguntaDAO = versionPreguntaDAO;
        this.virtualDAO = virtualDAO;
        this.intentoDAO = intentoDAO;
        this.respuestaDAO = respuestaDAO;
        this.eventoDAO = eventoDAO;
        this.materialDAO = materialDAO;
        this.intentoPracticaDAO = intentoPracticaDAO;
        this.respuestaPracticaDAO = respuestaPracticaDAO;
        this.solicitudDAO = solicitudDAO;
        this.servicioVirtual = servicioVirtual;
        this.servicioIA = servicioIA;
    }

    // ------------------------------------------------------------------ fixtures

    private synchronized void fixture() throws Exception {
        if (listo) {
            return;
        }
        int paso = 0;
        try {
            paso = 1;
            administrador = administradorDAO.listarAdministradores().get(0);
            paso = 2;
            docente = docenteDAO.registrarDocente(administrador, "PERF-DOC", "PERF Docente", "perf.docente@examlen.local",
                    "ExamLen", "Perf#20261");
            paso = 3;
            estudiante = estudianteDAO.crearEstudiante("PERF-EST", "PERF Estudiante", "perf.est@examlen.local", "B1",
                    "ExamLen", "Perf#20261");
            paso = 4;
            curso = cursoDAO.crearCurso(docente, "PERF_CURSO", "", NivelIngles.B1);
            paso = 5;
            grupo = grupoDAO.abrirGrupo(curso, "PERF-G", "2026-1");
            paso = 6;
            inscripcion = inscripcionDAO.crearInscripcion(estudiante, grupo);
            paso = 7;
            banco = bancoDAO.crearBancoPreguntas(docente, "PERF_BANCO", "");
            paso = 8;
            pregunta = preguntaDAO.crearPregunta(docente.consultarIdUsuario(), banco.consultarIdBancoPreguntas(), "PERF pregunta base", TipoPregunta.SELECCION_MULTIPLE,
                    NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "PERF");
            paso = 9;
            opcion = opcionDAO.crearOpcionRespuesta(pregunta, "PERF opcion correcta", true);
            opcionDAO.crearOpcionRespuesta(pregunta, "PERF opcion incorrecta", false);
            paso = 10;
            // La pregunta manual válida ya está aprobada (RF-22).
            paso = 11;
            evaluacion = evaluacionDAO.crearEvaluacionConPregunta(docente, "PERF_EVALUACION", "", 1, 1, 30,
                    pregunta.consultarIdPregunta());
            evaluacionDAO.marcarListaEvaluacion(evaluacion);
            paso = 12;
            List<VersionEvaluacion> versiones = versionDAO.generarVersionEvaluacion(evaluacion);
            version = versiones.get(0);
            paso = 13;
            versionPregunta = versionPreguntaDAO.listarPorVersionEvaluacion(version.consultarIdVersionEvaluacion()).get(0);
            paso = 14;
            versionPreguntaDAO.actualizar(versionPregunta, versionPregunta.enunciadoVersionPregunta,
                    versionPregunta.respuestaEsperadaVersionPregunta, 1);
            listo = true;
        } catch (Exception e) {
            throw new Exception("fixture paso " + paso + ": " + e.getClass().getSimpleName() + " - "
                    + e.getMessage(), e);
        }
    }

    public synchronized void reiniciar() {
        listo = false;
    }

    /** Ejecuta el escenario del CU pedido. Devuelve null si el CU no existe. */
    public Resultado ejecutar(String cuId) throws Exception {
        fixture();
        switch (cuId) {
            case "CU-01":
                return verificado(usuarioDAO.verificarContrasenaUsuario(docente, "Perf#20261"));
            case "CU-02": {
                boolean cambio = usuarioDAO.cambiarContrasenaUsuario(docente, "Perf#20261", "Perf#20262");
                usuarioDAO.restablecerContrasenaUsuario(docente, "Perf#20261");
                Docente recargado = (Docente) usuarioDAO.buscarPorId(docente.consultarIdUsuario()).orElse(docente);
                return verificado(cambio && usuarioDAO.verificarContrasenaUsuario(recargado, "Perf#20261"));
            }
            case "CU-03": {
                usuarioDAO.actualizarUsuario(estudiante, "PERF Estudiante", "perf.est@examlen.local");
                return verificado("PERF Estudiante".equals(
                        usuarioDAO.buscarPorId(estudiante.consultarIdUsuario()).orElseThrow().nombreUsuario));
            }
            case "CU-04": {
                int n = ++secuencia;
                Estudiante nuevo = estudianteDAO.crearEstudiante("PERF-EST2-" + n, "PERF Estudiante 2",
                        "perf.est2." + n + "@examlen.local", "A2", "ExamLen", "Perf#20261");
                return verificado(nuevo.consultarIdUsuario() > 0);
            }
            case "CU-05": {
                int n = ++secuencia;
                Administrador nuevo = administradorDAO.crearAdministrador("PERF-ADM2-" + n, "PERF Admin 2",
                        "perf.adm2." + n + "@examlen.local", "Perf#20261", "ExamLen", new java.util.Date());
                return verificado(nuevo.consultarIdUsuario() > 0);
            }
            case "CU-06": {
                int n = ++secuencia;
                Docente nuevo = docenteDAO.registrarDocente(administrador, "PERF-DOC2-" + n, "PERF Docente 2",
                        "perf.doc2." + n + "@examlen.local", "ExamLen", "Perf#20261");
                return verificado(nuevo.consultarIdUsuario() > 0);
            }
            case "CU-07":
                return verificado(!estudianteDAO.listarEstudiantes("PERF").isEmpty());
            case "CU-08": {
                usuarioDAO.desactivarUsuario(estudiante);
                usuarioDAO.activarUsuario(estudiante);
                return verificado(usuarioDAO.buscarPorId(estudiante.consultarIdUsuario()).orElseThrow().activoUsuario);
            }
            case "CU-09": {
                usuarioDAO.restablecerContrasenaUsuario(estudiante, "Perf#20263");
                usuarioDAO.restablecerContrasenaUsuario(estudiante, "Perf#20261");
                return verificado(true);
            }
            case "CU-10": {
                TokenRecuperacion token = TokenRecuperacion.generarTokenRecuperacion(estudiante);
                tokenDAO.crearToken(token);
                boolean consumido = tokenDAO.consumirTokenYRestablecer(
                        TokenRecuperacion.hash(token.consultarTokenPlanoTokenRecuperacion()), "Perf#20264");
                usuarioDAO.restablecerContrasenaUsuario(estudiante, "Perf#20261");
                Resultado r = verificado(consumido);
                r.observacion = "SMTP_STUB_BENCHMARK (no se envia correo real)";
                return r;
            }
            case "CU-11": {
                int n = ++secuencia;
                logica.Curso nuevo = cursoDAO.crearCurso(docente, "PERF_CURSO2-" + n, "", NivelIngles.A2);
                cursoDAO.actualizarCurso(nuevo, "PERF_CURSO2-" + n, "editado");
                return verificado("editado".equals(cursoDAO.buscarPorId(nuevo.consultarIdCurso()).orElseThrow()
                        .descripcionCurso));
            }
            case "CU-12": {
                int n = ++secuencia;
                Grupo nuevo = grupoDAO.abrirGrupo(curso, "PERF-G2-" + n, "2026-2");
                grupoDAO.cerrarGrupo(nuevo);
                grupoDAO.reabrirGrupo(nuevo);
                return verificado("ABIERTO".equals(grupoDAO.buscarPorId(nuevo.consultarIdGrupo()).orElseThrow()
                        .estadoGrupo.name()));
            }
            case "CU-13": {
                inscripcionDAO.retirarInscripcion(inscripcion);
                inscripcionDAO.reincorporarInscripcion(inscripcion);
                return verificado("ACTIVA".equals(inscripcionDAO.buscarPorId(inscripcion.consultarIdInscripcion())
                        .orElseThrow().estadoInscripcion.name()));
            }
            case "CU-14": {
                int n = ++secuencia;
                Estudiante otro = estudianteDAO.crearEstudiante("PERF-EST3-" + n, "PERF Estudiante 3",
                        "perf.est3." + n + "@examlen.local", "A1", "ExamLen", "Perf#20261");
                Inscripcion nueva = inscripcionDAO.crearInscripcion(otro, grupo);
                return verificado(nueva.consultarIdInscripcion() > 0);
            }
            case "CU-15": {
                int n = ++secuencia;
                BancoPreguntas nuevo = bancoDAO.crearBancoPreguntas(docente, "PERF_BANCO2-" + n, "");
                bancoDAO.actualizarBancoPreguntas(nuevo, "PERF_BANCO2-" + n, "editado");
                return verificado(true);
            }
            case "CU-16": {
                Pregunta nueva = preguntaDAO.crearPregunta(docente.consultarIdUsuario(), banco.consultarIdBancoPreguntas(), "PERF pregunta 2", TipoPregunta.SELECCION_MULTIPLE,
                        NivelIngles.B1, Dificultad.MEDIA, Competencia.VOCABULARY, "PERF");
                OpcionRespuesta o1 = opcionDAO.crearOpcionRespuesta(nueva, "PERF op 1", true);
                opcionDAO.crearOpcionRespuesta(nueva, "PERF op 2", false);
                opcionDAO.actualizarOpcionRespuesta(nueva, o1, "PERF op 1 editada");
                preguntaDAO.actualizarPregunta(nueva, "PERF pregunta 2 editada", NivelIngles.B1, Dificultad.MEDIA,
                        Competencia.VOCABULARY, "PERF");
                return verificado(true);
            }
            case "CU-17": {
                Pregunta nueva = preguntaDAO.crearPregunta(docente.consultarIdUsuario(), banco.consultarIdBancoPreguntas(), "PERF pregunta 3", TipoPregunta.RESPUESTA_ABIERTA,
                        NivelIngles.B1, Dificultad.BAJA, Competencia.WRITING, "PERF");
                // Aprobación automática de la pregunta manual.
                preguntaDAO.archivarPregunta(nueva);
                preguntaDAO.restaurarPregunta(nueva);
                return verificado("APROBADA".equals(preguntaDAO.buscarPorId(nueva.consultarIdPregunta()).orElseThrow()
                        .estadoPregunta.name()));
            }
            case "CU-18": {
                DocumentoFuente documento = documentoDAO.cargarDocumentoFuente(docente, "perf.txt", "TXT",
                        java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "perf.txt").toString());
                documentoDAO.eliminar(documento);
                return verificado(documentoDAO.buscarPorId(documento.consultarIdDocumentoFuente()).isEmpty());
            }
            case "CU-19": {
                AceptacionIA aceptacion = aceptarIA();
                Resultado r = verificado(aceptacion.idSolicitud > 0);
                r.solicitudId = aceptacion.idSolicitud;
                r.observacion = (aceptacion.encolada ? "Solicitud encolada (asincrona)" : "Servicio ocupado: solicitud FALLIDA")
                        + "; IA_REAL";
                return r;
            }
            case "CU-20": {
                Evaluacion nueva = evaluacionDAO.crearEvaluacionConPregunta(docente, "PERF_EVALUACION2", "", 1, 1,
                        30, pregunta.consultarIdPregunta());
                return verificado(nueva.consultarIdEvaluacion() > 0
                        && evaluacionDAO.buscarPorIdDeDocente(nueva.consultarIdEvaluacion(), docente.consultarIdUsuario())
                                .isPresent());
            }
            case "CU-21": {
                List<VersionEvaluacion> generadas = versionDAO.generarVersionEvaluacion(evaluacion);
                return verificado(!generadas.isEmpty());
            }
            case "CU-22": {
                versionPreguntaDAO.actualizar(versionPregunta, "PERF versión editada",
                        versionPregunta.respuestaEsperadaVersionPregunta, versionPregunta.puntajeVersionPregunta);
                return verificado("PERF versión editada".equals(versionPreguntaDAO
                        .buscarPorId(versionPregunta.consultarIdVersionPregunta()).orElseThrow().enunciadoVersionPregunta));
            }
            case "CU-23": {
                byte[] pdf = versionDAO.generarPDFVersionEvaluacion(version, null);
                return verificado(pdf.length > 100);
            }
            case "CU-24": {
                byte[] pdf = versionDAO.generarClaveRespuestasPDFVersionEvaluacion(version, null);
                return verificado(pdf.length > 100);
            }
            case "CU-25": {
                EvaluacionVirtual virtual = programarVirtual();
                virtualDAO.agregarVersion(virtual.consultarIdEvaluacionVirtual(), version.consultarIdVersionEvaluacion());
                return verificado(virtualDAO.listarVersiones(virtual.consultarIdEvaluacionVirtual()).size() == 1);
            }
            case "CU-26": {
                EvaluacionVirtual virtual = programarVirtual();
                virtualDAO.agregarVersion(virtual.consultarIdEvaluacionVirtual(), version.consultarIdVersionEvaluacion());
                boolean abierta = servicioVirtual.abrir(virtual.consultarIdEvaluacionVirtual());
                boolean cerrada = servicioVirtual.cerrar(virtual.consultarIdEvaluacionVirtual());
                return verificado(abierta && cerrada);
            }
            case "CU-27": {
                EvaluacionVirtual virtual = virtualAbierta();
                logica.Intento intento = intentoDAO.crearIntento(virtual, inscripcion, version);
                boolean guardada = respuestaDAO.guardarRespuestaVigente(intento, versionPregunta, "PERF respuesta", -1);
                return verificado(intento.consultarIdIntento() > 0 && guardada);
            }
            case "CU-28": {
                EvaluacionVirtual virtual = virtualAbierta();
                logica.Intento intento = intentoDAO.crearIntento(virtual, inscripcion, version);
                eventoDAO.registrarEventoSupervision(intento, logica.TipoEventoSupervision.CAMBIO_PESTANA,
                        "PERF evento");
                return verificado(eventoDAO.contarPorIntento(intento.consultarIdIntento()) >= 1);
            }
            case "CU-29": {
                EvaluacionVirtual virtual = virtualAbierta();
                logica.Intento intento = intentoDAO.crearIntento(virtual, inscripcion, version);
                respuestaDAO.guardarRespuestaVigente(intento, versionPregunta, "PERF respuesta", -1);
                Respuesta respuesta = respuestaDAO.listarConPregunta(intento.consultarIdIntento()).get(0);
                respuestaDAO.calificarManualmenteRespuesta(respuesta, 1, "PERF comentario");
                Double nota = intentoDAO.calificarIntento(intento);
                return verificado(nota != null);
            }
            case "CU-30": {
                EvaluacionVirtual virtual = virtualAbierta();
                logica.Intento intento = intentoDAO.crearIntento(virtual, inscripcion, version);
                eventoDAO.registrarEventoSupervision(intento, logica.TipoEventoSupervision.PERDIDA_FOCO,
                        "PERF evento 2");
                return verificado(!eventoDAO.contarPorTipo(intento.consultarIdIntento()).isEmpty()
                        && !eventoDAO.listarPorIntento(intento.consultarIdIntento()).isEmpty());
            }
            case "CU-31": {
                EvaluacionVirtual virtual = virtualAbierta();
                logica.Intento intento = intentoDAO.crearIntento(virtual, inscripcion, version);
                respuestaDAO.guardarRespuestaVigente(intento, versionPregunta, "PERF respuesta 31", -1);
                return verificado(!respuestaDAO.listarConPregunta(intento.consultarIdIntento()).isEmpty()
                        && intentoDAO.buscarPorIdDeDocente(intento.consultarIdIntento(), docente.consultarIdUsuario()).isPresent());
            }
            case "CU-32": {
                logica.MaterialPractica material = materialDAO.crearMaterialPractica(docente, "PERF_MATERIAL",
                        "", NivelIngles.B1);
                materialDAO.agregarPregunta(material, pregunta.consultarIdPregunta());
                materialDAO.publicarMaterialPractica(material);
                return verificado("PUBLICADO".equals(materialDAO.buscarPorId(material.consultarIdMaterialPractica())
                        .orElseThrow().estadoMaterialPractica.name()));
            }
            case "CU-33": {
                logica.MaterialPractica material = materialPublicado();
                byte[] pdf = materialDAO.generarPDFMaterialPractica(material);
                byte[] pdfConRespuestas = materialDAO.generarPDFMaterialPracticaConRespuestas(material);
                return verificado(pdf.length > 100 && pdfConRespuestas.length > 100);
            }
            case "CU-34": {
                logica.MaterialPractica material = materialPublicado();
                logica.IntentoPractica intento = intentoPracticaDAO.crearIntentoEnCurso(estudiante, material);
                RespuestaPractica respuesta = new RespuestaPractica();
                respuesta.preguntaRespuestaPractica = pregunta;
                respuesta.opcionRespuestaRespuestaPractica = opcion;
                respuesta.contenidoRespuestaPractica = opcion.textoOpcionRespuesta;
                respuesta.esCorrectaRespuestaPractica = true;
                intentoPracticaDAO.guardarRespuesta(intento.consultarIdIntentoPractica(), respuesta);
                Double nota = intentoPracticaDAO.finalizarIntento(intento.consultarIdIntentoPractica());
                return verificado(nota != null && nota == 100.0);
            }
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ IA asíncrona

    public AceptacionIA aceptarIA() throws Exception {
        fixture();
        AceptacionIA aceptacion = new AceptacionIA();
        String modelo = RegistroProveedoresIA.modeloEfectivo("GEMINI", null);
        logica.SolicitudGeneracion solicitud = solicitudDAO.crearSolicitudGeneracion(docente,
                TipoGeneracion.PREGUNTAS, "Tema: PERF. Competencia: GRAMMAR. Benchmark", 1, NivelIngles.B1, modelo,
                null, banco.consultarIdBancoPreguntas());
        aceptacion.idSolicitud = solicitud.consultarIdSolicitudGeneracion();
        TrabajoGeneracionIA trabajo = new TrabajoGeneracionIA(solicitud.consultarIdSolicitudGeneracion(),
                docente.consultarIdUsuario(), TipoGeneracion.PREGUNTAS, "Tema: PERF. Competencia: GRAMMAR. Benchmark",
                1, NivelIngles.B1, "GEMINI", modelo, null, banco.consultarIdBancoPreguntas(), "PERF", Competencia.GRAMMAR,
                List.of(), null);
        aceptacion.proveedorDisponible = RegistroProveedoresIA.porCodigo("GEMINI")
                .map(f -> f.disponible()).orElse(false);
        aceptacion.encolada = servicioIA != null && servicioIA.encolar(trabajo);
        if (!aceptacion.encolada) {
            solicitudDAO.marcarFallidaSolicitudGeneracion(solicitud.consultarIdSolicitudGeneracion(),
                    "Servicio de generación no disponible.");
        }
        return aceptacion;
    }

    public String estadoIA(int idSolicitud) {
        return solicitudDAO.buscarPorId(idSolicitud)
                .map(s -> s.estadoSolicitudGeneracion.name()).orElse("DESCONOCIDA");
    }

    // ------------------------------------------------------------------ archivos reales

    private static final List<String> IMPLEMENTADOS = List.of(
            "CU-01", "CU-02", "CU-03", "CU-04", "CU-05", "CU-06", "CU-07", "CU-08", "CU-09", "CU-10", "CU-11",
            "CU-12", "CU-13", "CU-14", "CU-15", "CU-16", "CU-17", "CU-18", "CU-19", "CU-20", "CU-21", "CU-22",
            "CU-23", "CU-24", "CU-25", "CU-26", "CU-27", "CU-28", "CU-29", "CU-30", "CU-31", "CU-32", "CU-33",
            "CU-34");

    /** Escenarios realmente implementados (se compara contra el catálogo en la coherencia). */
    public static List<String> escenarios() {
        return IMPLEMENTADOS;
    }

    public byte[] pdfVersion() throws Exception {
        fixture();
        return versionDAO.generarPDFVersionEvaluacion(version, null);
    }

    public byte[] pdfClaveVersion() throws Exception {
        fixture();
        return versionDAO.generarClaveRespuestasPDFVersionEvaluacion(version, null);
    }

    public byte[] pdfPractica(boolean conRespuestas) throws Exception {
        fixture();
        logica.MaterialPractica material = materialPublicado();
        return conRespuestas ? materialDAO.generarPDFMaterialPracticaConRespuestas(material)
                : materialDAO.generarPDFMaterialPractica(material);
    }


    // ------------------------------------------------------------------ apoyo

    private EvaluacionVirtual programarVirtual() throws Exception {
        java.util.Date ahora = Fechas.ahora();
        EvaluacionVirtual virtual = virtualDAO.programarEvaluacionVirtual(grupo, evaluacion,
                new java.util.Date(ahora.getTime() - 3600_000L), new java.util.Date(ahora.getTime() + 3600_000L),
                true);
        return virtual;
    }

    private EvaluacionVirtual virtualAbierta() throws Exception {
        EvaluacionVirtual virtual = programarVirtual();
        virtualDAO.agregarVersion(virtual.consultarIdEvaluacionVirtual(), version.consultarIdVersionEvaluacion());
        servicioVirtual.abrir(virtual.consultarIdEvaluacionVirtual());
        return virtual;
    }

    private logica.MaterialPractica materialPublicado() throws Exception {
        logica.MaterialPractica material = materialDAO.crearMaterialPractica(docente, "PERF_MATERIAL",
                "", NivelIngles.B1);
        materialDAO.agregarPregunta(material, pregunta.consultarIdPregunta());
        materialDAO.publicarMaterialPractica(material);
        return material;
    }

    private static Resultado verificado(boolean ok) {
        Resultado r = new Resultado();
        r.verificado = ok;
        r.observacion = ok ? "" : "Verificación mínima fallida";
        return r;
    }
}
