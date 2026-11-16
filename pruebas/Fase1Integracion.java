import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.ServletContext;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import logica.*;
import modelo.*;

/** Prueba destructiva SOLO sobre una instancia desechable en 127.0.0.1:33681. */
public final class Fase1Integracion {
    private static final String SERVIDOR = "jdbc:mysql://127.0.0.1:33681/";
    private static final String OPCIONES = "?allowMultiQueries=true&allowPublicKeyRetrieval=true&sslMode=DISABLED";
    private static int comprobaciones;
    private static Connection conexion;

    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length != 1 || !"INSTANCIA_DESECHABLE".equals(argumentos[0])) {
            throw new IllegalArgumentException("Requiere confirmación de instancia desechable.");
        }
        for (String variable : List.of("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD")) {
            if (System.getenv(variable) != null) throw new IllegalStateException("No usar configuración de entorno: " + variable);
        }
        try (Connection servidor = DriverManager.getConnection(SERVIDOR + OPCIONES, "root", "");
             Statement sentencia = servidor.createStatement()) {
            try (ResultSet ubicacion = sentencia.executeQuery("SELECT @@datadir")) {
                if (!ubicacion.next() || !ubicacion.getString(1).contains("examlen-fase1-mysql-")) {
                    throw new IllegalStateException("El servidor no es la instancia temporal autorizada.");
                }
            }
            sentencia.execute("DROP DATABASE IF EXISTS examlen");
            sentencia.execute("CREATE USER IF NOT EXISTS 'prueba_fase'@'localhost' IDENTIFIED BY 'SoloPruebasFase1'"
                    + "; GRANT ALL PRIVILEGES ON *.* TO 'prueba_fase'@'localhost' WITH GRANT OPTION");
        }
        System.setProperty("examlen.db.url", SERVIDOR + "examlen");
        System.setProperty("examlen.db.user", "prueba_fase");
        System.setProperty("examlen.db.password", "SoloPruebasFase1");
        ServletContext contexto = (ServletContext) Proxy.newProxyInstance(Fase1Integracion.class.getClassLoader(),
                new Class<?>[]{ServletContext.class}, (objeto, metodo, valores) -> {
                    if (metodo.getName().equals("getResourceAsStream")) {
                        return Files.newInputStream(Path.of("web" + valores[0]));
                    }
                    return null;
                });
        InicializadorEsquema.inicializar(contexto);
        comprobar(InicializadorEsquema.consultarDisponibilidadEsquema(), "Inicialización nueva 001–020");
        MysqlDataSource fuente = new MysqlDataSource();
        fuente.setURL(SERVIDOR + "examlen" + OPCIONES);
        fuente.setUser("prueba_fase"); fuente.setPassword("SoloPruebasFase1");
        try (Connection abierta = fuente.getConnection()) {
            conexion = abierta;
            comprobar(numero("SELECT COUNT(*) FROM esquema_migracion") == 20, "20 migraciones reales");
            InicializadorEsquema.inicializar(contexto);
            comprobar(InicializadorEsquema.consultarDisponibilidadEsquema(), "Reinicio idempotente");
            ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,contrasena_hash) VALUES "
                    + "('ADMINISTRADOR','F1','Admin','admin@prueba.local','prueba'),"
                    + "('DOCENTE','F2','Docente A','a@prueba.local','prueba'),"
                    + "('DOCENTE','F3','Docente B','b@prueba.local','prueba')");
            ejecutar("INSERT INTO administrador (id_usuario) VALUES (1)");
            ejecutar("INSERT INTO docente (id_usuario,id_administrador) VALUES (2,1),(3,1)");
            Docente docente = new Docente(); docente.establecerIdUsuario(2);
            Docente ajeno = new Docente(); ajeno.establecerIdUsuario(3);
            PreguntaDAO preguntas = new PreguntaDAO(fuente);
            OpcionRespuestaDAO opciones = new OpcionRespuestaDAO(fuente);
            EvaluacionDAO evaluaciones = new EvaluacionDAO(fuente);
            VersionEvaluacionDAO versiones = new VersionEvaluacionDAO(fuente);
            BancoPreguntasDAO bancos = new BancoPreguntasDAO(fuente);
            Evaluacion evaluacion = evaluaciones.crearEvaluacion(docente, "Desde cero", "");
            comprobar(numero("SELECT COUNT(*) FROM banco_preguntas") == 0
                    && preguntas.listarPorEvaluacion(evaluacion.consultarIdEvaluacion()).isEmpty(), "Evaluación vacía sin bancos");
            rechazar(() -> evaluaciones.marcarListaEvaluacion(evaluacion), "Bloqueo de evaluación vacía");
            Pregunta pregunta = preguntas.crearEnEvaluacion(2, evaluacion.consultarIdEvaluacion(), "Elige la respuesta correcta",
                    TipoPregunta.SELECCION_MULTIPLE, NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            comprobar(pregunta.idDocente == 2 && pregunta.idBancoPreguntas == null
                    && pregunta.estadoPregunta == EstadoPregunta.BORRADOR, "Propiedad directa y borrador incompleto");
            evaluaciones.configurarEvaluacion(evaluacion, 1, 2, 30);
            rechazar(() -> evaluaciones.marcarListaEvaluacion(evaluacion), "Bloqueo de pregunta incompleta");
            OpcionRespuesta correcta = opciones.crearOpcionRespuesta(pregunta, "went", true);
            comprobar(pregunta.estadoPregunta == EstadoPregunta.BORRADOR, "Una opción no basta");
            opciones.crearOpcionRespuesta(pregunta, "go", false);
            comprobar(pregunta.estadoPregunta == EstadoPregunta.APROBADA, "Manual válida se aprueba automáticamente");
            comprobar(preguntas.listarAprobadasDeDocente(2).size() == 1
                    && preguntas.buscarDeDocente(3, new CriteriosBusquedaPregunta()).isEmpty(), "Búsqueda sin banco y aislamiento");
            BancoPreguntas banco = bancos.crearBancoPreguntas(docente, "Organización", "");
            BancoPreguntas otroBanco = bancos.crearBancoPreguntas(ajeno, "Ajeno", "");
            preguntas.organizarEnBanco(pregunta, banco.consultarIdBancoPreguntas());
            comprobar(preguntas.buscarPorId(pregunta.consultarIdPregunta()).orElseThrow().idBancoPreguntas.equals(banco.consultarIdBancoPreguntas()), "Asignar banco propio");
            rechazar(() -> preguntas.organizarEnBanco(pregunta, otroBanco.consultarIdBancoPreguntas()), "Banco ajeno rechazado");
            preguntas.organizarEnBanco(pregunta, null);
            comprobar(preguntas.buscarPorId(pregunta.consultarIdPregunta()).orElseThrow().idBancoPreguntas == null, "Quitar banco");
            rechazar(() -> ejecutar("UPDATE pregunta SET id_banco_preguntas=" + otroBanco.consultarIdBancoPreguntas()
                    + " WHERE id_pregunta=" + pregunta.consultarIdPregunta()), "FK rechaza banco ajeno");
            Evaluacion otra = evaluaciones.crearEvaluacion(ajeno, "Ajena", "");
            rechazar(() -> preguntas.seleccionarPregunta(pregunta, otra), "Asociación entre docentes rechazada");
            int cantidad = numero("SELECT COUNT(*) FROM pregunta");
            rechazar(() -> preguntas.crearEnEvaluacion(2, otra.consultarIdEvaluacion(), "Pregunta inválida por dueño",
                    TipoPregunta.RESPUESTA_ABIERTA, NivelIngles.B1, Dificultad.BAJA, Competencia.WRITING, "Pasado"), "Creación contextual ajena rechazada");
            comprobar(numero("SELECT COUNT(*) FROM pregunta") == cantidad, "No queda pregunta huérfana tras fallo");
            evaluaciones.marcarListaEvaluacion(evaluacion);
            List<VersionEvaluacion> generadas = versiones.generarVersionEvaluacion(evaluacion);
            comprobar(generadas.size() == 2 && numero("SELECT COUNT(*) FROM version_pregunta") == 2, "Generación de dos versiones");
            List<VersionEvaluacionDAO.Distribucion> distribuciones = versiones.listarDistribucionPorVersion(evaluacion.consultarIdEvaluacion());
            comprobar(distribuciones.size() == 2 && distribuciones.get(0).porTema.equals(distribuciones.get(1).porTema)
                    && distribuciones.get(0).porCompetencia.equals(distribuciones.get(1).porCompetencia)
                    && distribuciones.get(0).porDificultad.equals(distribuciones.get(1).porDificultad)
                    && distribuciones.get(0).puntajeTotal == distribuciones.get(1).puntajeTotal, "Distribución y puntaje equivalentes");
            comprobar(versiones.generarPDFVersionEvaluacion(generadas.get(0), "Curso de prueba").length > 500
                    && versiones.generarClaveRespuestasPDFVersionEvaluacion(generadas.get(0), "Curso de prueba").length > 500, "PDF y clave de respuesta conservados");
            String historico = texto("SELECT GROUP_CONCAT(enunciado ORDER BY id_version_pregunta) FROM version_pregunta");
            rechazar(() -> preguntas.actualizarPregunta(pregunta, "Cambiar original histórico", NivelIngles.B1,
                    Dificultad.MEDIA, Competencia.GRAMMAR, "Otro"), "Original histórico protegido");
            rechazar(() -> opciones.actualizarOpcionRespuesta(pregunta, correcta, "changed"), "Opción histórica protegida");
            Pregunta copia = preguntas.copiarParaEvaluacion(2, evaluacion.consultarIdEvaluacion(), pregunta.consultarIdPregunta());
            preguntas.actualizarPregunta(copia, "Enunciado actual modificado", NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            comprobar(historico.equals(texto("SELECT GROUP_CONCAT(enunciado ORDER BY id_version_pregunta) FROM version_pregunta"))
                    && preguntas.buscarPorId(pregunta.consultarIdPregunta()).orElseThrow().enunciadoPregunta.equals("Elige la respuesta correcta"), "Copia no altera originales ni versiones");
            Pregunta conBanco = preguntas.crearPregunta(2, banco.consultarIdBancoPreguntas(), "Pregunta histórica con banco",
                    TipoPregunta.SELECCION_MULTIPLE, NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            opciones.crearOpcionRespuesta(conBanco, "was", true);
            opciones.crearOpcionRespuesta(conBanco, "were", false);
            Evaluacion evalBanco = evaluaciones.crearEvaluacion(docente, "Histórica con banco", "");
            preguntas.seleccionarPregunta(conBanco, evalBanco);
            evaluaciones.configurarEvaluacion(evalBanco, 1, 1, 30);
            evaluaciones.marcarListaEvaluacion(evalBanco);
            List<VersionEvaluacion> versionBanco = versiones.generarVersionEvaluacion(evalBanco);
            comprobar(conBanco.estadoPregunta == EstadoPregunta.APROBADA && versionBanco.size() == 1
                    && versiones.generarPDFVersionEvaluacion(versionBanco.get(0), "Curso banco").length > 500
                    && versiones.generarClaveRespuestasPDFVersionEvaluacion(versionBanco.get(0), "Curso banco").length > 500,
                    "Pregunta histórica con banco conserva versión, PDF y clave");
            MaterialPracticaDAO materiales = new MaterialPracticaDAO(fuente);
            MaterialPractica material = materiales.crearMaterialPractica(docente, "Práctica", "", NivelIngles.B1);
            materiales.agregarPregunta(material, pregunta.consultarIdPregunta());
            Curso cursoMaterial = new CursoDAO(fuente).crearCurso(docente, "Curso material F1", "", NivelIngles.B1);
            materiales.agregarCurso(material, cursoMaterial.consultarIdCurso());
            materiales.publicarMaterialPractica(material);
            rechazar(() -> opciones.eliminarOpcionRespuesta(pregunta, correcta), "Práctica publicada conserva opciones");
            comprobar(materiales.listarPreguntas(material.consultarIdMaterialPractica()).get(0).enunciadoPregunta.equals("Elige la respuesta correcta"), "Práctica conserva contenido");
            ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,contrasena_hash) VALUES ('ESTUDIANTE','F4','Estudiante','est@prueba.local','prueba')");
            ejecutar("INSERT INTO estudiante (id_usuario,nivel_ingles,aceptacion_tratamiento_datos) VALUES (4,'B1',NOW())");
            ejecutar("INSERT INTO intento_practica (id_material_practica,id_estudiante) VALUES (" + material.consultarIdMaterialPractica() + ",4)");
            ejecutar("INSERT INTO respuesta_practica (id_intento_practica,id_pregunta,id_opcion_respuesta,contenido,es_correcta,retroalimentacion) VALUES (1,"
                    + pregunta.consultarIdPregunta() + "," + correcta.consultarIdOpcionRespuesta() + ",'went',TRUE,'Conservada')");
            String respuestaAnterior = texto("SELECT CONCAT(rp.contenido, '|', rp.retroalimentacion, '|', p.enunciado, '|', o.texto) "
                    + "FROM respuesta_practica rp JOIN pregunta p USING(id_pregunta) JOIN opcion_respuesta o USING(id_opcion_respuesta)");
            rechazar(() -> preguntas.definirRespuestaEsperada(pregunta, "changed"), "Respuesta histórica no editable");
            comprobar(respuestaAnterior.equals(texto("SELECT CONCAT(rp.contenido, '|', rp.retroalimentacion, '|', p.enunciado, '|', o.texto) "
                    + "FROM respuesta_practica rp JOIN pregunta p USING(id_pregunta) JOIN opcion_respuesta o USING(id_opcion_respuesta)")), "Respuesta de práctica histórica intacta");
            Pregunta corta = preguntas.crearPregunta(2, null, "Completa el pasado", TipoPregunta.RESPUESTA_CORTA,
                    NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            comprobar(corta.estadoPregunta == EstadoPregunta.BORRADOR, "Corta sin respuesta no utilizable");
            preguntas.definirRespuestaEsperada(corta, "went");
            comprobar(corta.estadoPregunta == EstadoPregunta.APROBADA, "Corta completa autoaprobada");
            preguntas.definirRespuestaEsperada(corta, "");
            comprobar(corta.estadoPregunta == EstadoPregunta.BORRADOR, "Corta incompleta vuelve a borrador");
            Pregunta verdadero = preguntas.crearPregunta(2, null, "El pasado de go es went", TipoPregunta.VERDADERO_FALSO,
                    NivelIngles.B1, Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            opciones.marcarCorrectaOpcionRespuesta(verdadero, verdadero.opcionesPregunta.get(0));
            comprobar(verdadero.estadoPregunta == EstadoPregunta.APROBADA, "Verdadero/falso completo autoaprobado");
            SolicitudGeneracion solicitud = new SolicitudGeneracionDAO(fuente).crearSolicitudGeneracion(docente,
                    TipoGeneracion.PREGUNTAS, "Prueba sin proveedor externo", 1, NivelIngles.B1, "prueba", null, null);
            PersistenciaGeneracionDAO.PreguntaPersistible generada = new PersistenciaGeneracionDAO.PreguntaPersistible();
            generada.enunciado = "Escribe una oración"; generada.tipo = TipoPregunta.RESPUESTA_ABIERTA;
            generada.nivel = NivelIngles.B1; generada.dificultad = Dificultad.BAJA;
            generada.competencia = Competencia.WRITING; generada.tema = "Pasado";
            new PersistenciaGeneracionDAO(fuente).persistir(2, solicitud.consultarIdSolicitudGeneracion(), TipoGeneracion.PREGUNTAS,
                    NivelIngles.B1, null, "", "prueba", List.of(generada));
            Pregunta inteligencia = preguntas.buscarPorId(numero("SELECT MAX(id_pregunta) FROM pregunta")).orElseThrow();
            comprobar(inteligencia.estadoPregunta == EstadoPregunta.BORRADOR && inteligencia.idBancoPreguntas == null, "IA sin banco conserva borrador");
            rechazar(() -> preguntas.seleccionarPregunta(inteligencia, evaluacion), "IA sin aprobar no se reutiliza");
            preguntas.actualizarPregunta(inteligencia, "Escribe dos oraciones", NivelIngles.B1, Dificultad.BAJA, Competencia.WRITING, "Pasado");
            comprobar(inteligencia.estadoPregunta == EstadoPregunta.BORRADOR, "Editar IA no la autoaprueba");
            preguntas.aprobarPregunta(inteligencia);
            comprobar(inteligencia.estadoPregunta == EstadoPregunta.APROBADA, "IA requiere aprobación expresa");
            preguntas.seleccionarPregunta(inteligencia, evaluacion);
            Pregunta copiaIa = preguntas.copiarParaEvaluacion(2, evaluacion.consultarIdEvaluacion(), inteligencia.consultarIdPregunta());
            comprobar(copiaIa.origenPregunta == OrigenContenido.GENERADO_IA && copiaIa.estadoPregunta == EstadoPregunta.BORRADOR, "Copia IA no elude revisión");
            MaterialPractica borradorIa = materiales.crearMaterialPractica(docente, "IA en revisión", "", NivelIngles.B1);
            ejecutar("INSERT INTO material_pregunta (id_material_practica,id_pregunta) VALUES (" + borradorIa.consultarIdMaterialPractica() + "," + inteligencia.consultarIdPregunta() + ")");
            preguntas.actualizarPregunta(inteligencia, "Texto IA revisado en material borrador", NivelIngles.B1, Dificultad.BAJA, Competencia.WRITING, "Pasado");
            comprobar(inteligencia.estadoPregunta == EstadoPregunta.BORRADOR, "Material IA sin uso permite revisar y mantiene borrador");
            Pregunta concurrente = preguntas.crearEnEvaluacion(2, evaluacion.consultarIdEvaluacion(), "Original concurrente",
                    TipoPregunta.RESPUESTA_ABIERTA, NivelIngles.B1, Dificultad.BAJA, Competencia.WRITING, "Pasado");
            Evaluacion segunda = evaluaciones.crearEvaluacion(docente, "Segunda referencia", "");
            java.util.concurrent.ExecutorService ejecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try (Connection bloqueo = fuente.getConnection()) {
                bloqueo.setAutoCommit(false);
                try (Statement sentencia = bloqueo.createStatement()) {
                    sentencia.executeQuery("SELECT id_pregunta FROM pregunta WHERE id_pregunta=" + concurrente.consultarIdPregunta() + " FOR UPDATE").close();
                    sentencia.executeUpdate("INSERT INTO evaluacion_pregunta (id_evaluacion,id_pregunta) VALUES (" + segunda.consultarIdEvaluacion() + "," + concurrente.consultarIdPregunta() + ")");
                    java.util.concurrent.CountDownLatch inicio = new java.util.concurrent.CountDownLatch(1);
                    java.util.concurrent.Future<Boolean> resultado = ejecutor.submit(() -> {
                        inicio.countDown();
                        try {
                            preguntas.actualizarPregunta(concurrente, "Cambio no permitido tras compartir", NivelIngles.B1,
                                    Dificultad.BAJA, Competencia.WRITING, "Pasado");
                            return false;
                        } catch (OperacionNoPermitidaException esperada) { return true; }
                    });
                    inicio.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    bloqueo.commit();
                    comprobar(resultado.get(10, java.util.concurrent.TimeUnit.SECONDS), "Edición concurrente revalida referencias tras bloqueo");
                    comprobar("Original concurrente".equals(preguntas.buscarPorId(concurrente.consultarIdPregunta()).orElseThrow().enunciadoPregunta), "Concurrencia conserva original compartido");
                }
            } finally { ejecutor.shutdownNow(); }
            Evaluacion archivable = evaluaciones.crearEvaluacion(docente, "Archivable", "");
            Pregunta preguntaAbierta = preguntas.crearEnEvaluacion(2, archivable.consultarIdEvaluacion(),
                    "Explica el uso del pasado simple", TipoPregunta.RESPUESTA_ABIERTA, NivelIngles.B1,
                    Dificultad.BAJA, Competencia.WRITING, "Pasado");
            comprobar(preguntaAbierta.estadoPregunta == EstadoPregunta.APROBADA, "Abierta manual válida autoaprobada");
            evaluaciones.configurarEvaluacion(archivable, 1, 1, 30);
            evaluaciones.marcarListaEvaluacion(archivable);
            preguntas.archivarPregunta(preguntaAbierta);
            comprobar(evaluaciones.buscarPorIdDeDocente(archivable.consultarIdEvaluacion(), 2).orElseThrow()
                    .estadoEvaluacion == EstadoEvaluacion.BORRADOR && preguntaAbierta.estadoPregunta == EstadoPregunta.ARCHIVADA,
                    "Archivar pregunta devuelve la evaluación LISTA a BORRADOR");
            System.out.println("COMPROBACIONES CORRECTAS: " + comprobaciones);
        }
    }

    private static void comprobar(boolean cumple, String caso) {
        if (!cumple) throw new AssertionError(caso);
        comprobaciones++; System.out.println("OK " + caso);
    }
    @FunctionalInterface private interface Accion { void ejecutar() throws Exception; }
    private static void rechazar(Accion accion, String caso) throws Exception {
        try { accion.ejecutar(); } catch (OperacionNoPermitidaException | ValidacionException | SQLException excepcion) {
            comprobar(true, caso); return;
        }
        throw new AssertionError("Debió rechazarse: " + caso);
    }
    private static void ejecutar(String consulta) throws SQLException {
        try (Statement sentencia = conexion.createStatement()) { sentencia.execute(consulta); }
    }
    private static int numero(String consulta) throws SQLException {
        try (Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(consulta)) {
            resultado.next(); return resultado.getInt(1);
        }
    }
    private static String texto(String consulta) throws SQLException {
        try (Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(consulta)) {
            resultado.next(); return resultado.getString(1);
        }
    }
}
