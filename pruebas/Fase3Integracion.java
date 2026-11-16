import com.mysql.cj.jdbc.MysqlDataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import logica.*;
import modelo.*;
import servicio.ServicioEvaluacionVirtual;
import jakarta.servlet.ServletContext;

/** Pruebas de Fase 3 exclusivamente sobre la instancia desechable de 127.0.0.1:33681. */
public final class Fase3Integracion {
    private static final String SERVIDOR = "jdbc:mysql://127.0.0.1:33681/";
    private static final String OPCIONES = "?allowMultiQueries=true&allowPublicKeyRetrieval=true&sslMode=DISABLED";
    private static Connection conexion;
    private static int comprobaciones;

    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length != 1 || !"INSTANCIA_DESECHABLE".equals(argumentos[0])) {
            throw new IllegalArgumentException("Requiere confirmacion de instancia desechable.");
        }
        for (String variable : List.of("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD")) {
            if (System.getenv(variable) != null) {
                throw new IllegalStateException("No usar configuracion de entorno: " + variable);
            }
        }
        MysqlDataSource fuente = new MysqlDataSource();
        fuente.setURL(SERVIDOR + "examlen" + OPCIONES);
        fuente.setUser("root");
        fuente.setPassword("");
        conexion = fuente.getConnection();
        if (numero("SELECT COUNT(*) FROM esquema_migracion") != 20) {
            throw new IllegalStateException("Ejecuta Fase1Integracion antes que Fase3Integracion.");
        }
        comprobar(columnasIntentos(), "P3-01/P3-02 Estructura 019 disponible");
        probarProgramacionYFlujo(fuente);
        probarConcurrencia(fuente);
        probarVencimiento(fuente);
        probarUnico();
        probarReinicio();
        System.out.println("COMPROBACIONES FASE 3 CORRECTAS: " + comprobaciones);
    }

    private static void probarProgramacionYFlujo(MysqlDataSource fuente) throws Exception {
        limpiarDatos();
        Docente docente = crearDocente();
        Estudiante estudiante = crearEstudiante();
        Curso curso = new CursoDAO(fuente).crearCurso(docente, "TEST_OC_F3 Curso", "", NivelIngles.B1);
        Grupo grupo = new GrupoDAO(fuente).abrirGrupo(curso, "TEST_OC_F3 Grupo", "2026-1");
        Inscripcion inscripcion = new InscripcionDAO(fuente).crearInscripcion(estudiante, grupo);

        PreguntaDAO preguntas = new PreguntaDAO(fuente);
        OpcionRespuestaDAO opciones = new OpcionRespuestaDAO(fuente);
        EvaluacionDAO evaluaciones = new EvaluacionDAO(fuente);
        VersionEvaluacionDAO versiones = new VersionEvaluacionDAO(fuente);
        Evaluacion evaluacion = evaluaciones.crearEvaluacion(docente, "TEST_OC_F3 Evaluacion", "");
        Pregunta pregunta = preguntas.crearEnEvaluacion(docente.consultarIdUsuario(),
                evaluacion.consultarIdEvaluacion(), "Elige la opcion correcta", TipoPregunta.SELECCION_MULTIPLE,
                NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
        opciones.crearOpcionRespuesta(pregunta, "correcta", true);
        opciones.crearOpcionRespuesta(pregunta, "incorrecta", false);
        preguntas.asignarPuntajeGrupoEnEvaluacion(evaluacion.consultarIdEvaluacion(),
                NivelIngles.B1, Competencia.GRAMMAR, Dificultad.BAJA, 2.0);
        evaluaciones.configurarEvaluacion(evaluacion, 1, 2, 30);
        evaluaciones.marcarListaEvaluacion(evaluacion);
        List<VersionEvaluacion> versionesAplicadas = versiones.generarVersionEvaluacion(evaluacion);
        if (versionesAplicadas.size() != 2) {
            throw new IllegalStateException("La evaluacion de prueba no genero dos versiones.");
        }

        EvaluacionVirtualDAO virtualDAO = new EvaluacionVirtualDAO(fuente);
        IntentoDAO intentoDAO = new IntentoDAO(fuente);
        RespuestaDAO respuestaDAO = new RespuestaDAO(fuente);
        VersionPreguntaDAO versionPreguntaDAO = new VersionPreguntaDAO(fuente);
        VersionOpcionDAO versionOpcionDAO = new VersionOpcionDAO(fuente);
        ServicioEvaluacionVirtual servicio = new ServicioEvaluacionVirtual(virtualDAO, intentoDAO, respuestaDAO,
                new EventoSupervisionDAO(fuente));

        EvaluacionVirtual una = programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 1);
        comprobar(una.consultarIntentosPermitidos() == 1, "P3-03 Docente programa con un intento");
        EvaluacionVirtual dos = programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 2);
        comprobar(dos.consultarIntentosPermitidos() == 2, "P3-04 Docente programa con dos intentos");
        rechazar(() -> programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 0),
                "P3-05 Valor 0 rechazado");
        rechazar(() -> programar(virtualDAO, grupo, evaluacion, versionesAplicadas, 3),
                "P3-06 Valor 3 rechazado");

        Intento primero = intentoDAO.iniciarIntento(dos, inscripcion, versionesAplicadas);
        comprobar(primero.consultarNumeroIntento() == 1 && primero.estadoIntento == EstadoIntento.EN_CURSO,
                "P3-07 Primer intento con numero 1");
        int idPrimero = primero.consultarIdIntento();
        Intento reanudado = intentoDAO.iniciarIntento(dos, inscripcion, versionesAplicadas);
        comprobar(reanudado.consultarIdIntento() == idPrimero, "P3-08 Reentrada reanuda el mismo intento");
        comprobar(intentoDAO.contarPorEvaluacionEInscripcion(dos.consultarIdEvaluacionVirtual(),
                inscripcion.consultarIdInscripcion()) == 1, "P3-08 Reentrada no crea otro intento");

        VersionPregunta preguntaPrimero = versionPreguntaDAO
                .listarPorVersionEvaluacion(primero.versionEvaluacionIntento.consultarIdVersionEvaluacion()).get(0);
        VersionOpcion opcionCorrecta = opcionCorrecta(versionOpcionDAO, preguntaPrimero);
        comprobar(respuestaDAO.guardarRespuestaVigente(primero, preguntaPrimero, "", opcionCorrecta.consultarIdVersionOpcion()),
                "P3-22 Autoguardado del primer intento por idIntento");
        respuestaDAO.calificarAutomaticas(primero.consultarIdIntento());
        Double notaPrimero = intentoDAO.calificarIntento(primero);
        comprobar(notaPrimero != null, "P3-18 Primer intento calificado de forma independiente");

        Intento segundo = intentoDAO.iniciarIntento(dos, inscripcion, versionesAplicadas);
        comprobar(segundo.consultarNumeroIntento() == 2 && segundo.consultarIdIntento() != idPrimero,
                "P3-12 Segundo intento distinto con numero 2");
        comprobar(primeroySegundoUsanVersionesValidas(primero, segundo, versionesAplicadas),
                "P3-15 Ambos intentos conservan versiones validas");
        Intento reanudadoSegundo = intentoDAO.iniciarIntento(dos, inscripcion, versionesAplicadas);
        comprobar(reanudadoSegundo.consultarIdIntento() == segundo.consultarIdIntento(),
                "P3-13 Reentrada al segundo intento lo reanuda");
        VersionPregunta preguntaSegundo = versionPreguntaDAO
                .listarPorVersionEvaluacion(segundo.versionEvaluacionIntento.consultarIdVersionEvaluacion()).get(0);
        VersionOpcion opcionIncorrecta = opcionIncorrecta(versionOpcionDAO, preguntaSegundo);
        comprobar(respuestaDAO.guardarRespuestaVigente(segundo, preguntaSegundo, "", opcionIncorrecta.consultarIdVersionOpcion()),
                "P3-22 Autoguardado del segundo intento por idIntento");
        respuestaDAO.calificarAutomaticas(segundo.consultarIdIntento());
        comprobar(respuestaDAO.listarConPregunta(idPrimero).size() == 1
                && respuestaDAO.listarConPregunta(segundo.consultarIdIntento()).size() == 1
                && respuestaDAO.listarConPregunta(idPrimero).get(0).versionOpcionRespuesta
                        .consultarIdVersionOpcion()
                        != respuestaDAO.listarConPregunta(segundo.consultarIdIntento()).get(0).versionOpcionRespuesta
                        .consultarIdVersionOpcion(),
                "P3-16 Respuestas separadas entre intentos");
        Double notaSegundo = intentoDAO.calificarIntento(segundo);
        comprobar(notaSegundo != null && notaPrimero.doubleValue() != notaSegundo.doubleValue(),
                "P3-18 Calificaciones independientes y conservadas");

        EventoSupervisionDAO eventos = new EventoSupervisionDAO(fuente);
        eventos.registrarEventoSupervision(primero, TipoEventoSupervision.PERDIDA_FOCO, "Evento del intento 1");
        eventos.registrarEventoSupervision(segundo, TipoEventoSupervision.CAMBIO_PESTANA, "Evento del intento 2");
        comprobar(eventos.listarPorIntento(idPrimero).size() == 1
                && eventos.listarPorIntento(segundo.consultarIdIntento()).size() == 1
                && !eventos.listarPorIntento(idPrimero).get(0).descripcionEventoSupervision
                        .equals(eventos.listarPorIntento(segundo.consultarIdIntento()).get(0).descripcionEventoSupervision),
                "P3-17 Eventos de supervision separados");

        rechazar(() -> intentoDAO.iniciarIntento(dos, inscripcion, versionesAplicadas),
                "P3-14 Tercer intento rechazado");

        Intento unico = intentoDAO.iniciarIntento(una, inscripcion, versionesAplicadas);
        comprobar(unico.consultarNumeroIntento() == 1, "P3-10 Primer intento en evaluacion de un intento");
        intentoDAO.finalizarIntento(unico);
        rechazar(() -> intentoDAO.iniciarIntento(una, inscripcion, versionesAplicadas),
                "P3-10 Nuevo inicio rechazado al consumir el unico intento");

        comprobar(intentoDAO.listarPorEvaluacionEInscripcion(dos.consultarIdEvaluacionVirtual(),
                inscripcion.consultarIdInscripcion()).size() == 2,
                "P3-11 Dos intentos finalizados habilitan el segundo");
        comprobar(servicio != null, "Servicio de evaluacion virtual disponible");
    }

    private static void probarConcurrencia(MysqlDataSource fuente) throws Exception {
        Docente docente = new Docente();
        docente.establecerIdUsuario(numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F3D'"));
        Estudiante estudiante = new Estudiante();
        estudiante.establecerIdUsuario(numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F3E'"));
        EvaluacionVirtualDAO virtualDAO = new EvaluacionVirtualDAO(fuente);
        IntentoDAO intentoDAO = new IntentoDAO(fuente);
        List<VersionEvaluacion> versiones = virtualDAO.listarVersiones(numero(
                "SELECT id_evaluacion_virtual FROM evaluacion_virtual WHERE intentos_permitidos=2 ORDER BY id_evaluacion_virtual LIMIT 1"));
        int idGrupo = numero("SELECT id_grupo FROM grupo WHERE nombre='TEST_OC_F3 Grupo'");
        int idEvaluacion = numero("SELECT id_evaluacion FROM evaluacion WHERE titulo='TEST_OC_F3 Evaluacion'");
        Grupo grupo = new GrupoDAO(fuente).buscarPorId(idGrupo).orElseThrow();
        Evaluacion evaluacion = new EvaluacionDAO(fuente).buscarPorId(idEvaluacion).orElseThrow();
        EvaluacionVirtual virtual = programar(virtualDAO, grupo, evaluacion, versiones, 2);
        Inscripcion inscripcion = new InscripcionDAO(fuente)
                .buscarPorGrupoYEstudiante(idGrupo, estudiante.consultarIdUsuario()).orElseThrow();

        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            resultados.add(ejecutor.submit(() -> {
                inicio.await(5, TimeUnit.SECONDS);
                return intentoDAO.iniciarIntento(virtual, inscripcion, versiones).consultarIdIntento();
            }));
        }
        inicio.countDown();
        int id1 = resultados.get(0).get(10, TimeUnit.SECONDS);
        int id2 = resultados.get(1).get(10, TimeUnit.SECONDS);
        ejecutor.shutdownNow();
        comprobar(id1 == id2 && intentoDAO.contarPorEvaluacionEInscripcion(
                virtual.consultarIdEvaluacionVirtual(), inscripcion.consultarIdInscripcion()) == 1,
                "P3-09 Solicitudes concurrentes crean un solo intento 1");
    }

    private static void probarVencimiento(MysqlDataSource fuente) throws Exception {
        Docente docente = new Docente();
        docente.establecerIdUsuario(numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F3D'"));
        Estudiante estudiante = new Estudiante();
        estudiante.establecerIdUsuario(numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F3E'"));
        int idGrupo = numero("SELECT id_grupo FROM grupo WHERE nombre='TEST_OC_F3 Grupo'");
        int idEvaluacion = numero("SELECT id_evaluacion FROM evaluacion WHERE titulo='TEST_OC_F3 Evaluacion'");
        Grupo grupo = new GrupoDAO(fuente).buscarPorId(idGrupo).orElseThrow();
        EvaluacionVirtualDAO virtualDAO = new EvaluacionVirtualDAO(fuente);
        List<VersionEvaluacion> versiones = new VersionEvaluacionDAO(fuente).listarPorEvaluacion(idEvaluacion);
        EvaluacionVirtual virtual = programar(virtualDAO, grupo,
                new EvaluacionDAO(fuente).buscarPorId(idEvaluacion).orElseThrow(), versiones, 2);
        Inscripcion inscripcion = new InscripcionDAO(fuente)
                .buscarPorGrupoYEstudiante(idGrupo, estudiante.consultarIdUsuario()).orElseThrow();
        IntentoDAO intentoDAO = new IntentoDAO(fuente);
        Intento intento = intentoDAO.iniciarIntento(virtual, inscripcion, versiones);
        int segundosInicio = intentoDAO.segundosRestantes(intento.consultarIdIntento());
        comprobar(segundosInicio > 1700, "P3-16/P3-21 Primer intento recibe la duracion completa");
        ejecutar("UPDATE evaluacion_virtual SET fecha_fin = NOW() - INTERVAL 1 MINUTE WHERE id_evaluacion_virtual = "
                + virtual.consultarIdEvaluacionVirtual());
        ServicioEvaluacionVirtual servicio = new ServicioEvaluacionVirtual(virtualDAO, intentoDAO,
                new RespuestaDAO(fuente), new EventoSupervisionDAO(fuente));
        comprobar(servicio.finalizarIntentoVencido(intento.consultarIdIntento()),
                "P3-21 La fecha limite finaliza el intento");
        comprobar(intentoDAO.buscarPorId(intento.consultarIdIntento()).orElseThrow().estadoIntento
                != EstadoIntento.EN_CURSO, "P3-21 Intento vencido queda cerrado y calificado");
    }

    private static void probarUnico() throws Exception {
        try (Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate("INSERT INTO intento (id_evaluacion_virtual,id_inscripcion,numero_intento,"
                    + "id_version_evaluacion,estado) SELECT id_evaluacion_virtual,id_inscripcion,numero_intento,"
                    + "id_version_evaluacion,'EN_CURSO' FROM intento WHERE numero_intento=1 LIMIT 1");
            throw new AssertionError("P3-23 La clave unica no impidio el duplicado");
        } catch (SQLException e) {
            comprobar(e.getErrorCode() == 1062, "P3-23 Restriccion UNIQUE impide duplicar numero de intento");
        }
    }

    private static void probarReinicio() throws Exception {
        comprobar(numero("SELECT COUNT(*) FROM esquema_migracion") == 20, "P3-24 Migracion 019/020 aplicada una sola vez");
        ServletContext contexto = (ServletContext) Proxy.newProxyInstance(Fase3Integracion.class.getClassLoader(),
                new Class<?>[]{ServletContext.class}, (objeto, metodo, valores) ->
                        metodo.getName().equals("getResourceAsStream")
                                ? Files.newInputStream(Path.of("web" + valores[0])) : null);
        System.setProperty("examlen.db.url", SERVIDOR + "examlen");
        System.setProperty("examlen.db.user", "prueba_fase");
        System.setProperty("examlen.db.password", "SoloPruebasFase1");
        InicializadorEsquema.inicializar(contexto);
        comprobar(InicializadorEsquema.consultarDisponibilidadEsquema()
                && numero("SELECT COUNT(*) FROM esquema_migracion") == 20,
                "P3-25 Reinicio no reaplica 019/020");
    }

    private static boolean columnasIntentos() throws SQLException {
        return numero("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='examlen' "
                + "AND TABLE_NAME='evaluacion_virtual' AND COLUMN_NAME='intentos_permitidos'") == 1
                && numero("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='examlen' "
                + "AND TABLE_NAME='intento' AND COLUMN_NAME='numero_intento'") == 1;
    }

    private static EvaluacionVirtual programar(EvaluacionVirtualDAO virtualDAO, Grupo grupo, Evaluacion evaluacion,
                                               List<VersionEvaluacion> versiones, int intentos) throws Exception {
        Date inicio = new Date(System.currentTimeMillis() - 3600000L);
        Date fin = new Date(System.currentTimeMillis() + 86400000L);
        EvaluacionVirtual virtual = virtualDAO.programarEvaluacionVirtual(grupo, evaluacion, inicio, fin,
                intentos, false);
        for (VersionEvaluacion version : versiones) {
            virtualDAO.agregarVersion(virtual.consultarIdEvaluacionVirtual(), version.consultarIdVersionEvaluacion());
        }
        if (!virtualDAO.reclamarApertura(virtual.consultarIdEvaluacionVirtual())) {
            throw new IllegalStateException("No se pudo abrir la evaluacion de prueba.");
        }
        return virtualDAO.buscarPorId(virtual.consultarIdEvaluacionVirtual()).orElseThrow();
    }

    private static VersionOpcion opcionCorrecta(VersionOpcionDAO dao, VersionPregunta pregunta) {
        for (VersionOpcion opcion : dao.listarPorVersionPregunta(pregunta.consultarIdVersionPregunta())) {
            if (opcion.esCorrectaVersionOpcion) {
                return opcion;
            }
        }
        throw new IllegalStateException("La version no tiene opcion correcta.");
    }

    private static VersionOpcion opcionIncorrecta(VersionOpcionDAO dao, VersionPregunta pregunta) {
        for (VersionOpcion opcion : dao.listarPorVersionPregunta(pregunta.consultarIdVersionPregunta())) {
            if (!opcion.esCorrectaVersionOpcion) {
                return opcion;
            }
        }
        throw new IllegalStateException("La version no tiene opcion incorrecta.");
    }

    private static boolean primeroySegundoUsanVersionesValidas(Intento primero, Intento segundo,
                                                               List<VersionEvaluacion> versiones) {
        int idPrimera = primero.versionEvaluacionIntento.consultarIdVersionEvaluacion();
        int idSegunda = segundo.versionEvaluacionIntento.consultarIdVersionEvaluacion();
        return versiones.stream().anyMatch(v -> v.consultarIdVersionEvaluacion() == idPrimera)
                && versiones.stream().anyMatch(v -> v.consultarIdVersionEvaluacion() == idSegunda);
    }

    private static Docente crearDocente() throws Exception {
        ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) "
                + "VALUES ('ADMINISTRADOR','TEST_OC_F3ADM','TEST_OC_F3 Admin','test_oc_f3_admin@examlen.local','TEST_OC','prueba',1)");
        int idAdministrador = numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F3ADM'");
        ejecutar("INSERT INTO administrador (id_usuario) VALUES (" + idAdministrador + ")");
        ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) "
                + "VALUES ('DOCENTE','TEST_OC_F3D','TEST_OC_F3 Docente','test_oc_f3_docente@examlen.local','TEST_OC','prueba',1)");
        int idDocente = numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F3D'");
        ejecutar("INSERT INTO docente (id_usuario,debe_cambiar_contrasena,id_administrador) VALUES ("
                + idDocente + ",0," + idAdministrador + ")");
        Docente docente = new Docente();
        docente.establecerIdUsuario(idDocente);
        return docente;
    }

    private static Estudiante crearEstudiante() throws Exception {
        ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) "
                + "VALUES ('ESTUDIANTE','TEST_OC_F3E','TEST_OC_F3 Estudiante','test_oc_f3_estudiante@examlen.local','TEST_OC','prueba',1)");
        int idEstudiante = numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F3E'");
        ejecutar("INSERT INTO estudiante (id_usuario,nivel_ingles,aceptacion_tratamiento_datos) VALUES ("
                + idEstudiante + ",'B1',NOW())");
        Estudiante estudiante = new Estudiante();
        estudiante.establecerIdUsuario(idEstudiante);
        return estudiante;
    }

    private static void limpiarDatos() throws SQLException {
        ejecutar("DELETE FROM usuario WHERE identificacion LIKE 'TEST_OC_F3%'");
    }

    private static void comprobar(boolean cumple, String caso) {
        if (!cumple) {
            throw new AssertionError(caso);
        }
        comprobaciones++;
        System.out.println("OK " + caso);
    }

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }

    private static void rechazar(Accion accion, String caso) throws Exception {
        try {
            accion.ejecutar();
        } catch (ValidacionException | RegistroDuplicadoException excepcion) {
            comprobar(true, caso);
            return;
        }
        throw new AssertionError("Debio rechazarse: " + caso);
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
