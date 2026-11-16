import com.mysql.cj.jdbc.MysqlDataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import logica.*;
import modelo.*;
import servicio.ServicioEvaluacionVirtual;
import jakarta.servlet.ServletContext;

/** Pruebas de Fase 4 sobre la instancia desechable de 127.0.0.1:33681. */
public final class Fase4Integracion {
    private static final String SERVIDOR = "jdbc:mysql://127.0.0.1:33681/";
    private static final String OPCIONES = "?allowMultiQueries=true&allowPublicKeyRetrieval=true&sslMode=DISABLED";
    private static Connection conexion;
    private static int comprobaciones;

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"INSTANCIA_DESECHABLE".equals(args[0])) {
            throw new IllegalArgumentException("Requiere confirmacion de instancia desechable.");
        }
        MysqlDataSource fuente = new MysqlDataSource();
        fuente.setURL(SERVIDOR + "examlen" + OPCIONES);
        fuente.setUser("root");
        fuente.setPassword("");
        conexion = fuente.getConnection();
        if (numero("SELECT COUNT(*) FROM esquema_migracion") != 20) {
            throw new IllegalStateException("Ejecuta Fase1Integracion antes que Fase4Integracion.");
        }
        probarPolitica(fuente);
        probarVencimiento(fuente);
        probarReinicio();
        System.out.println("COMPROBACIONES FASE 4 CORRECTAS: " + comprobaciones);
    }

    private static void probarPolitica(MysqlDataSource fuente) throws Exception {
        limpiarDatos();
        Docente docente = crearDocente();
        Estudiante estudiante = crearEstudiante();
        Curso curso = new CursoDAO(fuente).crearCurso(docente, "TEST_OC_F4 Curso", "", NivelIngles.B1);
        Grupo grupo = new GrupoDAO(fuente).abrirGrupo(curso, "TEST_OC_F4 Grupo", "2026-1");
        Inscripcion inscripcion = new InscripcionDAO(fuente).crearInscripcion(estudiante, grupo);

        PreguntaDAO preguntas = new PreguntaDAO(fuente);
        OpcionRespuestaDAO opciones = new OpcionRespuestaDAO(fuente);
        EvaluacionDAO evaluaciones = new EvaluacionDAO(fuente);
        VersionEvaluacionDAO versiones = new VersionEvaluacionDAO(fuente);
        Evaluacion evaluacion = evaluaciones.crearEvaluacion(docente, "TEST_OC_F4 Evaluacion", "");
        Pregunta multiple = preguntas.crearEnEvaluacion(docente.consultarIdUsuario(),
                evaluacion.consultarIdEvaluacion(), "Selecciona la respuesta", TipoPregunta.SELECCION_MULTIPLE,
                NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
        opciones.crearOpcionRespuesta(multiple, "correcta", true);
        opciones.crearOpcionRespuesta(multiple, "incorrecta", false);
        Pregunta falsoVerdadero = preguntas.crearEnEvaluacion(docente.consultarIdUsuario(),
                evaluacion.consultarIdEvaluacion(), "El pasado de go es went", TipoPregunta.VERDADERO_FALSO,
                NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
        opciones.marcarCorrectaOpcionRespuesta(falsoVerdadero, falsoVerdadero.opcionesPregunta.get(0));
        Pregunta corta = preguntas.crearEnEvaluacion(docente.consultarIdUsuario(),
                evaluacion.consultarIdEvaluacion(), "Escribe el pasado de go", TipoPregunta.RESPUESTA_CORTA,
                NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
        preguntas.definirRespuestaEsperada(corta, "went");
        preguntas.crearEnEvaluacion(docente.consultarIdUsuario(), evaluacion.consultarIdEvaluacion(),
                "Explica el uso del pasado simple", TipoPregunta.RESPUESTA_ABIERTA,
                NivelIngles.B1, Dificultad.BAJA, Competencia.WRITING, "Pasado");
        evaluaciones.configurarEvaluacion(evaluacion, 4, 1, 30);
        evaluaciones.marcarListaEvaluacion(evaluacion);
        List<VersionEvaluacion> versionesAplicadas = versiones.generarVersionEvaluacion(evaluacion);

        EvaluacionVirtualDAO virtualDAO = new EvaluacionVirtualDAO(fuente);
        IntentoDAO intentoDAO = new IntentoDAO(fuente);
        RespuestaDAO respuestaDAO = new RespuestaDAO(fuente);
        VersionPreguntaDAO versionPreguntaDAO = new VersionPreguntaDAO(fuente);
        VersionOpcionDAO versionOpcionDAO = new VersionOpcionDAO(fuente);
        EventoSupervisionDAO eventoDAO = new EventoSupervisionDAO(fuente);
        ServicioEvaluacionVirtual servicio = new ServicioEvaluacionVirtual(virtualDAO, intentoDAO, respuestaDAO, eventoDAO);

        EvaluacionVirtual falsa = programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 1, false);
        comprobar(!falsa.consultarExigirRespuestasCompletas(), "P4-02 Politica false persistida");
        EvaluacionVirtual verdadera = programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 1, true);
        comprobar(verdadera.consultarExigirRespuestasCompletas(), "P4-03 Politica true persistida");

        Intento intento = intentoDAO.iniciarIntento(verdadera, inscripcion, versionesAplicadas);
        guardarRespuestas(intento, versionPreguntaDAO, versionOpcionDAO, respuestaDAO, -1);
        comprobar(respuestaDAO.consultarPreguntasPendientes(intento.consultarIdIntento(),
                intento.versionEvaluacionIntento.consultarIdVersionEvaluacion()).isEmpty(),
                "P4-05 Modo true con todas respondidas permite envio");
        comprobar(servicio.puedeFinalizarManualmente(intento, verdadera),
                "P4-05 Politica true con todas respondidas es finalizable");

        Intento conPendientes = intentoDAO.iniciarIntento(programar(virtualDAO, grupo, evaluacion,
                versionesAplicadas, 1, true), inscripcion, versionesAplicadas);
        comprobar(!servicio.puedeFinalizarManualmente(conPendientes,
                virtualDAO.buscarPorId(intentoDAO.idVirtualDeIntento(conPendientes.consultarIdIntento())).orElseThrow()),
                "P4-06 Modo true con pendiente bloquea envio manual");
        comprobar(servicio.consultarPreguntasPendientes(conPendientes).size() == 4,
                "P4-07 Modo true devuelve las cuatro preguntas pendientes");

        Intento soloMultiple = intentoDAO.iniciarIntento(programar(virtualDAO, grupo, evaluacion,
                versionesAplicadas, 1, true), inscripcion, versionesAplicadas);
        guardarRespuestasExcepto(soloMultiple, TipoPregunta.SELECCION_MULTIPLE,
                versionPreguntaDAO, versionOpcionDAO, respuestaDAO);
        comprobar(servicio.consultarPreguntasPendientes(soloMultiple).size() == 1,
                "P4-08 Seleccion multiple sin opcion es pendiente");

        Intento soloVf = intentoDAO.iniciarIntento(programar(virtualDAO, grupo, evaluacion,
                versionesAplicadas, 1, true), inscripcion, versionesAplicadas);
        guardarRespuestasExcepto(soloVf, TipoPregunta.VERDADERO_FALSO,
                versionPreguntaDAO, versionOpcionDAO, respuestaDAO);
        comprobar(servicio.consultarPreguntasPendientes(soloVf).size() == 1,
                "P4-09 Verdadero/falso sin opcion es pendiente");

        Intento soloCorta = intentoDAO.iniciarIntento(programar(virtualDAO, grupo, evaluacion,
                versionesAplicadas, 1, true), inscripcion, versionesAplicadas);
        guardarRespuestasExcepto(soloCorta, TipoPregunta.RESPUESTA_CORTA,
                versionPreguntaDAO, versionOpcionDAO, respuestaDAO);
        comprobar(servicio.consultarPreguntasPendientes(soloCorta).size() == 1,
                "P4-10 Respuesta corta vacia es pendiente");

        Intento soloAbierta = intentoDAO.iniciarIntento(programar(virtualDAO, grupo, evaluacion,
                versionesAplicadas, 1, true), inscripcion, versionesAplicadas);
        guardarRespuestasExcepto(soloAbierta, TipoPregunta.RESPUESTA_ABIERTA,
                versionPreguntaDAO, versionOpcionDAO, respuestaDAO);
        comprobar(servicio.consultarPreguntasPendientes(soloAbierta).size() == 1,
                "P4-11 Respuesta abierta vacia es pendiente");
        comprobar(servicio.consultarPreguntasPendientes(soloAbierta).size() > 0
                && !respuestaDAO.listarConPregunta(soloAbierta.consultarIdIntento()).isEmpty(),
                "P4-21 El mensaje de pendientes no requiere respuestas correctas");

        Intento incompleto = intentoDAO.iniciarIntento(falsa, inscripcion, versionesAplicadas);
        comprobar(servicio.puedeFinalizarManualmente(incompleto, falsa),
                "P4-13 Modo false con pendientes permite envio");

        EvaluacionVirtual falsaCompleta = programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 1, false);
        Intento completo = intentoDAO.iniciarIntento(falsaCompleta, inscripcion, versionesAplicadas);
        guardarRespuestas(completo, versionPreguntaDAO, versionOpcionDAO, respuestaDAO, -1);
        comprobar(respuestaDAO.consultarPreguntasPendientes(completo.consultarIdIntento(),
                completo.versionEvaluacionIntento.consultarIdVersionEvaluacion()).isEmpty()
                && servicio.puedeFinalizarManualmente(completo, falsaCompleta),
                "P4-12 Modo false con todas respondidas permite envio normal");
        comprobar(respuestaDAO.listarConPregunta(completo.consultarIdIntento()).size() == 4,
                "P4-22 Autoguardado servidor conserva las respuestas por intento");
        Intento reanudado = intentoDAO.iniciarIntento(falsa, inscripcion, versionesAplicadas);
        comprobar(reanudado.consultarIdIntento() == incompleto.consultarIdIntento(),
                "P4-17 Rechazo/reentrada conserva el mismo intento");

        eventoDAO.registrarEventoSupervision(incompleto, TipoEventoSupervision.PERDIDA_FOCO, "Evento F4");
        comprobar(eventoDAO.listarPorIntento(incompleto.consultarIdIntento()).size() == 1,
                "P4-23 Eventos de supervision asociados al intento");

        EvaluacionVirtual dos = programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 2, true);
        Intento primero = intentoDAO.iniciarIntento(dos, inscripcion, versionesAplicadas);
        comprobar(!servicio.puedeFinalizarManualmente(primero, dos),
                "P4-18 Intento 1 respeta politica true");
        guardarRespuestas(primero, versionPreguntaDAO, versionOpcionDAO, respuestaDAO, -1);
        comprobar(servicio.puedeFinalizarManualmente(primero, dos),
                "P4-18 Intento 1 completo permite envio");
        intentoDAO.calificarIntento(primero);
        Intento segundo = intentoDAO.iniciarIntento(dos, inscripcion, versionesAplicadas);
        comprobar(segundo.consultarNumeroIntento() == 2 && !servicio.puedeFinalizarManualmente(segundo, dos),
                "P4-19 Intento 2 respeta politica true");
        intentoDAO.finalizarIntento(segundo);
        comprobar(intentoDAO.buscarPorId(segundo.consultarIdIntento()).orElseThrow().estadoIntento
                != EstadoIntento.EN_CURSO, "P4-20 Finalizacion del primero habilita el segundo");
    }

    private static void probarVencimiento(MysqlDataSource fuente) throws Exception {
        Docente docente = new Docente();
        docente.establecerIdUsuario(numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F4D'"));
        Estudiante estudiante = new Estudiante();
        estudiante.establecerIdUsuario(numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F4E'"));
        int idGrupo = numero("SELECT id_grupo FROM grupo WHERE nombre='TEST_OC_F4 Grupo'");
        int idEvaluacion = numero("SELECT id_evaluacion FROM evaluacion WHERE titulo='TEST_OC_F4 Evaluacion'");
        Grupo grupo = new GrupoDAO(fuente).buscarPorId(idGrupo).orElseThrow();
        Evaluacion evaluacion = new EvaluacionDAO(fuente).buscarPorId(idEvaluacion).orElseThrow();
        EvaluacionVirtualDAO virtualDAO = new EvaluacionVirtualDAO(fuente);
        IntentoDAO intentoDAO = new IntentoDAO(fuente);
        RespuestaDAO respuestaDAO = new RespuestaDAO(fuente);
        VersionEvaluacionDAO versiones = new VersionEvaluacionDAO(fuente);
        List<VersionEvaluacion> listaVersiones = versiones.listarPorEvaluacion(idEvaluacion);
        EventoSupervisionDAO eventoDAO = new EventoSupervisionDAO(fuente);
        ServicioEvaluacionVirtual servicio = new ServicioEvaluacionVirtual(virtualDAO, intentoDAO, respuestaDAO, eventoDAO);
        Inscripcion inscripcion = new InscripcionDAO(fuente)
                .buscarPorGrupoYEstudiante(idGrupo, estudiante.consultarIdUsuario()).orElseThrow();

        EvaluacionVirtual virtual = programar(virtualDAO, grupo, evaluacion, listaVersiones, 2, true);
        Intento primero = intentoDAO.iniciarIntento(virtual, inscripcion, listaVersiones);
        ejecutar("UPDATE intento SET fecha_inicio = NOW() - INTERVAL 2 HOUR WHERE id_intento = "
                + primero.consultarIdIntento());
        comprobar(servicio.finalizarIntentoVencido(primero.consultarIdIntento()),
                "P4-15/P4-16 Vencimiento finaliza aunque falten respuestas");
        comprobar(intentoDAO.buscarPorId(primero.consultarIdIntento()).orElseThrow().estadoIntento
                != EstadoIntento.EN_CURSO, "P4-15 Intento vencido queda cerrado");
        Intento segundo = intentoDAO.iniciarIntento(virtual, inscripcion, listaVersiones);
        comprobar(segundo.consultarNumeroIntento() == 2,
                "P4-20 El segundo intento se habilita tras cierre automatico del primero");
        ejecutar("UPDATE evaluacion_virtual SET fecha_fin = NOW() - INTERVAL 1 MINUTE WHERE id_evaluacion_virtual = "
                + virtual.consultarIdEvaluacionVirtual());
        comprobar(servicio.finalizarIntentoVencido(segundo.consultarIdIntento()),
                "P4-16 Cierre por fecha finaliza el intento");
    }

    private static void probarReinicio() throws Exception {
        comprobar(numero("SELECT COUNT(*) FROM esquema_migracion") == 20, "P4-24 Migracion 020 una sola vez");
        ServletContext contexto = (ServletContext) Proxy.newProxyInstance(Fase4Integracion.class.getClassLoader(),
                new Class<?>[]{ServletContext.class}, (objeto, metodo, valores) ->
                        metodo.getName().equals("getResourceAsStream")
                                ? Files.newInputStream(Path.of("web" + valores[0])) : null);
        System.setProperty("examlen.db.url", SERVIDOR + "examlen");
        System.setProperty("examlen.db.user", "prueba_fase");
        System.setProperty("examlen.db.password", "SoloPruebasFase1");
        InicializadorEsquema.inicializar(contexto);
        comprobar(InicializadorEsquema.consultarDisponibilidadEsquema()
                && numero("SELECT COUNT(*) FROM esquema_migracion") == 20,
                "P4-25 Reinicio no reaplica 020");
    }

    private static EvaluacionVirtual programar(EvaluacionVirtualDAO dao, Grupo grupo, Evaluacion evaluacion,
                                               List<VersionEvaluacion> versiones, int intentos, boolean exigir)
            throws Exception {
        Date inicio = new Date(System.currentTimeMillis() - 3600000L);
        Date fin = new Date(System.currentTimeMillis() + 86400000L);
        EvaluacionVirtual virtual = dao.programarEvaluacionVirtual(grupo, evaluacion, inicio, fin,
                intentos, false, exigir);
        for (VersionEvaluacion version : versiones) {
            dao.agregarVersion(virtual.consultarIdEvaluacionVirtual(), version.consultarIdVersionEvaluacion());
        }
        if (!dao.reclamarApertura(virtual.consultarIdEvaluacionVirtual())) {
            throw new IllegalStateException("No se pudo abrir la evaluacion de prueba.");
        }
        return dao.buscarPorId(virtual.consultarIdEvaluacionVirtual()).orElseThrow();
    }

    private static void guardarRespuestas(Intento intento, VersionPreguntaDAO preguntas,
                                          VersionOpcionDAO opciones, RespuestaDAO respuestas, int omitir)
            throws Exception {
        int idVersion = intento.versionEvaluacionIntento.consultarIdVersionEvaluacion();
        for (VersionPregunta pregunta : preguntas.listarPorVersionEvaluacion(idVersion)) {
            String tipo = preguntas.tipoDeVersionPregunta(pregunta.consultarIdVersionPregunta());
            if (omitir == 0 && "SELECCION_MULTIPLE".equals(tipo)
                    || omitir == 1 && "VERDADERO_FALSO".equals(tipo)
                    || omitir == 2 && "RESPUESTA_CORTA".equals(tipo)
                    || omitir == 3 && "RESPUESTA_ABIERTA".equals(tipo)) {
                continue;
            }
            if ("SELECCION_MULTIPLE".equals(tipo) || "VERDADERO_FALSO".equals(tipo)) {
                List<VersionOpcion> lista = opciones.listarPorVersionPregunta(pregunta.consultarIdVersionPregunta());
                respuestas.guardarRespuestaVigente(intento, pregunta, "",
                        lista.get(0).consultarIdVersionOpcion());
            } else {
                respuestas.guardarRespuestaVigente(intento, pregunta, "respuesta", -1);
            }
        }
    }

    private static void guardarRespuestasExcepto(Intento intento, TipoPregunta omitido,
                                                 VersionPreguntaDAO preguntas, VersionOpcionDAO opciones,
                                                 RespuestaDAO respuestas) throws Exception {
        int idVersion = intento.versionEvaluacionIntento.consultarIdVersionEvaluacion();
        for (VersionPregunta pregunta : preguntas.listarPorVersionEvaluacion(idVersion)) {
            if (omitido.name().equals(preguntas.tipoDeVersionPregunta(pregunta.consultarIdVersionPregunta()))) {
                continue;
            }
            if (preguntas.tipoDeVersionPregunta(pregunta.consultarIdVersionPregunta()).startsWith("SELECCION")
                    || "VERDADERO_FALSO".equals(preguntas.tipoDeVersionPregunta(pregunta.consultarIdVersionPregunta()))) {
                List<VersionOpcion> lista = opciones.listarPorVersionPregunta(pregunta.consultarIdVersionPregunta());
                respuestas.guardarRespuestaVigente(intento, pregunta, "",
                        lista.get(0).consultarIdVersionOpcion());
            } else {
                respuestas.guardarRespuestaVigente(intento, pregunta, "respuesta", -1);
            }
        }
    }

    private static Docente crearDocente() throws Exception {
        ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) "
                + "VALUES ('ADMINISTRADOR','TEST_OC_F4ADM','TEST_OC_F4 Admin','test_oc_f4_admin@examlen.local','TEST_OC','prueba',1)");
        int idAdministrador = numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F4ADM'");
        ejecutar("INSERT INTO administrador (id_usuario) VALUES (" + idAdministrador + ")");
        ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) "
                + "VALUES ('DOCENTE','TEST_OC_F4D','TEST_OC_F4 Docente','test_oc_f4_docente@examlen.local','TEST_OC','prueba',1)");
        int idDocente = numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F4D'");
        ejecutar("INSERT INTO docente (id_usuario,debe_cambiar_contrasena,id_administrador) VALUES ("
                + idDocente + ",0," + idAdministrador + ")");
        Docente docente = new Docente();
        docente.establecerIdUsuario(idDocente);
        return docente;
    }

    private static Estudiante crearEstudiante() throws Exception {
        ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) "
                + "VALUES ('ESTUDIANTE','TEST_OC_F4E','TEST_OC_F4 Estudiante','test_oc_f4_estudiante@examlen.local','TEST_OC','prueba',1)");
        int idEstudiante = numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F4E'");
        ejecutar("INSERT INTO estudiante (id_usuario,nivel_ingles,aceptacion_tratamiento_datos) VALUES ("
                + idEstudiante + ",'B1',NOW())");
        Estudiante estudiante = new Estudiante();
        estudiante.establecerIdUsuario(idEstudiante);
        return estudiante;
    }

    private static void limpiarDatos() throws SQLException {
        ejecutar("DELETE FROM usuario WHERE identificacion LIKE 'TEST_OC_F4%'");
    }

    private static void comprobar(boolean cumple, String caso) {
        if (!cumple) {
            throw new AssertionError(caso);
        }
        comprobaciones++;
        System.out.println("OK " + caso);
    }

    private static void ejecutar(String consulta) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute(consulta);
        }
    }

    private static int numero(String consulta) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery(consulta)) {
            resultado.next();
            return resultado.getInt(1);
        }
    }
}
