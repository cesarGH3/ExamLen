import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.*;
import java.util.*;
import logica.*;
import modelo.*;

/** Pruebas de Fase 2 sobre la instancia desechable de 127.0.0.1:33681. */
public final class Fase2Integracion {
    private static final String SERVIDOR = "jdbc:mysql://127.0.0.1:33681/";
    private static final String OPCIONES = "?allowMultiQueries=true&allowPublicKeyRetrieval=true&sslMode=DISABLED";
    private static Connection conexion;
    private static int comprobaciones;

    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length != 1 || !"INSTANCIA_DESECHABLE".equals(argumentos[0])) {
            throw new IllegalArgumentException("Requiere confirmación de instancia desechable.");
        }
        MysqlDataSource fuente = new MysqlDataSource();
        fuente.setURL(SERVIDOR + "examlen" + OPCIONES);
        fuente.setUser("root");
        fuente.setPassword("");
        conexion = fuente.getConnection();
        if (numero("SELECT COUNT(*) FROM esquema_migracion") != 20) {
            throw new IllegalStateException("Ejecuta Fase1Integracion antes que Fase2Integracion.");
        }
        try {
            ejecutar("DELETE FROM usuario WHERE identificacion LIKE 'TEST_OC_F2%'");
            ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) VALUES "
                    + "('ADMINISTRADOR','TEST_OC_F2ADM','TEST_OC_F2 Administrador','test_oc_f2_admin@examlen.local','TEST_OC','prueba',1)");
            int idAdministrador = numero("SELECT id_usuario FROM usuario WHERE identificacion='TEST_OC_F2ADM'");
            ejecutar("INSERT INTO administrador (id_usuario) VALUES (" + idAdministrador + ")");
            int idDocenteA = crearUsuario("DOCENTE", "TEST_OC_F2A", "TEST_OC_F2 Docente A");
            int idDocenteB = crearUsuario("DOCENTE", "TEST_OC_F2B", "TEST_OC_F2 Docente B");
            ejecutar("INSERT INTO docente (id_usuario,debe_cambiar_contrasena,id_administrador) VALUES "
                    + "(" + idDocenteA + ",0," + idAdministrador + "),(" + idDocenteB + ",0," + idAdministrador + ")");
            int idEstudiante = crearUsuario("ESTUDIANTE", "TEST_OC_F2E", "TEST_OC_F2 Estudiante");
            ejecutar("INSERT INTO estudiante (id_usuario,nivel_ingles,aceptacion_tratamiento_datos) VALUES ("
                    + idEstudiante + ",'B1',NOW())");

            Docente docenteA = new Docente();
            docenteA.establecerIdUsuario(idDocenteA);
            Docente docenteB = new Docente();
            docenteB.establecerIdUsuario(idDocenteB);
            Estudiante estudiante = new Estudiante();
            estudiante.establecerIdUsuario(idEstudiante);

            PreguntaDAO preguntas = new PreguntaDAO(fuente);
            OpcionRespuestaDAO opciones = new OpcionRespuestaDAO(fuente);
            MaterialPracticaDAO materiales = new MaterialPracticaDAO(fuente);
            BancoPreguntasDAO bancos = new BancoPreguntasDAO(fuente);
            CursoDAO cursos = new CursoDAO(fuente);
            GrupoDAO grupos = new GrupoDAO(fuente);
            InscripcionDAO inscripciones = new InscripcionDAO(fuente);
            EvaluacionDAO evaluaciones = new EvaluacionDAO(fuente);
            IntentoPracticaDAO intentos = new IntentoPracticaDAO(fuente);

            Curso cursoA = cursos.crearCurso(docenteA, "TEST_OC_F2 Curso", "", NivelIngles.B1);
            Grupo grupoA = grupos.abrirGrupo(cursoA, "TEST_OC_F2 Grupo", "2026-2");
            inscripciones.crearInscripcion(estudiante, grupoA);

            comprobar(listarBancosDelDocente(bancos, idDocenteA).isEmpty(), "P2-01 Docente A sin bancos");
            MaterialPractica materialA = materiales.crearMaterialPractica(docenteA, "TEST_OC_F2 Material A", "", NivelIngles.B1);
            materiales.agregarCurso(materialA, cursoA.consultarIdCurso());
            comprobar(materialA.estadoMaterialPractica == EstadoMaterial.BORRADOR
                    && materiales.listarPreguntas(materialA.consultarIdMaterialPractica()).isEmpty(), "P2-01 Material BORRADOR y vacío");

            Pregunta preguntaA = preguntas.crearEnMaterial(idDocenteA, materialA.consultarIdMaterialPractica(),
                    "TEST_OC_F2 Elige la forma correcta", TipoPregunta.SELECCION_MULTIPLE, NivelIngles.B1,
                    Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            comprobar(preguntaA.idDocente == idDocenteA && preguntaA.idBancoPreguntas == null
                    && materiales.listarPreguntas(materialA.consultarIdMaterialPractica()).size() == 1,
                    "P2-02 Pregunta propia, banco NULL y asociada al material");
            comprobar(preguntaA.estadoPregunta == EstadoPregunta.BORRADOR, "P2-03 Pregunta incompleta en BORRADOR");

            rechazar(() -> materiales.publicarMaterialPractica(materialA),
                    "P2-05 Material con pregunta incompleta no se publica");

            OpcionRespuesta correctaA = opciones.crearOpcionRespuesta(preguntaA, "went", true);
            opciones.crearOpcionRespuesta(preguntaA, "go", false);
            comprobar(preguntaA.estadoPregunta == EstadoPregunta.APROBADA, "P2-04 Manual válida autoaprobada");

            BancoPreguntas banco = bancos.crearBancoPreguntas(docenteA, "TEST_OC_F2 Banco", "");
            Pregunta preguntaBanco = preguntas.crearPregunta(idDocenteA, banco.consultarIdBancoPreguntas(),
                    "TEST_OC_F2 Pregunta con banco", TipoPregunta.SELECCION_MULTIPLE, NivelIngles.B1,
                    Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            opciones.crearOpcionRespuesta(preguntaBanco, "was", true);
            opciones.crearOpcionRespuesta(preguntaBanco, "were", false);
            materiales.agregarPregunta(materialA, preguntaBanco.consultarIdPregunta());
            comprobar(preguntaBanco.idBancoPreguntas != null
                    && materiales.listarPreguntas(materialA.consultarIdMaterialPractica()).size() == 2,
                    "P2-07 Pregunta aprobada con banco reutilizada");

            MaterialPractica materialAuxiliar = materiales.crearMaterialPractica(docenteA, "TEST_OC_F2 Material Auxiliar", "", NivelIngles.B1);
            materiales.agregarPregunta(materialAuxiliar, preguntaA.consultarIdPregunta());
            comprobar(preguntaA.idBancoPreguntas == null
                    && materiales.listarPreguntas(materialAuxiliar.consultarIdMaterialPractica()).size() == 1,
                    "P2-08 Pregunta aprobada sin banco reutilizada");

            materiales.publicarMaterialPractica(materialA);
            comprobar(materialA.estadoMaterialPractica == EstadoMaterial.PUBLICADO, "P2-06 Material válido publicado");

            Pregunta preguntaIncompleta = preguntas.crearPregunta(idDocenteA, null,
                    "TEST_OC_F2 Incompleta", TipoPregunta.SELECCION_MULTIPLE, NivelIngles.B1,
                    Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            rechazar(() -> materiales.agregarPregunta(materialAuxiliar, preguntaIncompleta.consultarIdPregunta()),
                    "P2-09 Pregunta BORRADOR no reutilizable");

            Pregunta preguntaArchivada = preguntas.crearPregunta(idDocenteA, null,
                    "TEST_OC_F2 Archivada", TipoPregunta.SELECCION_MULTIPLE, NivelIngles.B1,
                    Dificultad.BAJA, Competencia.GRAMMAR, "Pasado");
            opciones.crearOpcionRespuesta(preguntaArchivada, "a", true);
            opciones.crearOpcionRespuesta(preguntaArchivada, "b", false);
            preguntas.archivarPregunta(preguntaArchivada);
            rechazar(() -> materiales.agregarPregunta(materialAuxiliar, preguntaArchivada.consultarIdPregunta()),
                    "P2-10 Pregunta ARCHIVADA no reutilizable");

            MaterialPractica materialB = materiales.crearMaterialPractica(docenteB, "TEST_OC_F2 Material B", "", NivelIngles.B1);
            rechazar(() -> materiales.agregarPregunta(materialB, preguntaA.consultarIdPregunta()),
                    "P2-11 Docente B no usa pregunta de A");
            rechazar(() -> preguntas.crearEnMaterial(idDocenteA, materialB.consultarIdMaterialPractica(),
                    "TEST_OC_F2 Intento ajeno", TipoPregunta.SELECCION_MULTIPLE, NivelIngles.B1,
                    Dificultad.BAJA, Competencia.GRAMMAR, "Pasado"), "P2-12 Material ajeno no permite crear");

            Evaluacion evaluacionA = evaluaciones.crearEvaluacion(docenteA, "TEST_OC_F2 Evaluación", "");
            preguntas.seleccionarPregunta(preguntaA, evaluacionA);
            comprobar(preguntas.estaProtegida(preguntaA), "P2-13 Pregunta compartida protegida");
            rechazar(() -> preguntas.actualizarPregunta(preguntaA, "TEST_OC_F2 Cambio silencioso",
                    NivelIngles.B1, Dificultad.MEDIA, Competencia.GRAMMAR, "Otro"),
                    "P2-13 Original compartido no se modifica silenciosamente");

            IntentoPractica intento = intentos.crearIntentoEnCurso(estudiante, materialA);
            RespuestaPractica respuesta = new RespuestaPractica();
            respuesta.preguntaRespuestaPractica = preguntaA;
            respuesta.opcionRespuestaRespuestaPractica = correctaA;
            respuesta.contenidoRespuestaPractica = "went";
            respuesta.esCorrectaRespuestaPractica = true;
            respuesta.retroalimentacionRespuestaPractica = "TEST_OC_F2 retroalimentación";
            intentos.guardarRespuesta(intento.consultarIdIntentoPractica(), respuesta);
            Double nota = intentos.finalizarIntento(intento.consultarIdIntentoPractica());
            comprobar(nota != null, "P2-15 Intento calificado y conservado");
            comprobar(materiales.listarPublicadosDeEstudiante(idEstudiante, null).stream()
                    .anyMatch(m -> m.consultarIdMaterialPractica() == materialA.consultarIdMaterialPractica()),
                    "P2-16 Estudiante ve el material publicado");
            comprobar(intentos.listarPorEstudiante(idEstudiante).stream()
                    .anyMatch(i -> i.consultarIdIntentoPractica() == intento.consultarIdIntentoPractica()),
                    "P2-17 Historial de práctica disponible");

            Pregunta copia = preguntas.copiarParaMaterial(idDocenteA, materialA.consultarIdMaterialPractica(),
                    preguntaA.consultarIdPregunta());
            comprobar(copia.idDocente == idDocenteA && copia.idBancoPreguntas == null
                    && materiales.listarPreguntas(materialA.consultarIdMaterialPractica()).stream()
                            .anyMatch(p -> p.consultarIdPregunta() == copia.consultarIdPregunta()),
                    "P2-14 Copia contextual asociada solo al material A");
            comprobar(materiales.listarPreguntas(materialAuxiliar.consultarIdMaterialPractica()).stream()
                    .anyMatch(p -> p.consultarIdPregunta() == preguntaA.consultarIdPregunta())
                    && preguntas.listarPorEvaluacion(evaluacionA.consultarIdEvaluacion()).stream()
                            .anyMatch(p -> p.consultarIdPregunta() == preguntaA.consultarIdPregunta()),
                    "P2-14 Otras asociaciones conservan el original");
            comprobar(numero("SELECT COUNT(*) FROM respuesta_practica WHERE id_intento_practica = "
                    + intento.consultarIdIntentoPractica()) == 1
                    && "went".equals(texto("SELECT contenido FROM respuesta_practica WHERE id_intento_practica = "
                            + intento.consultarIdIntentoPractica())), "P2-15 Práctica histórica intacta tras la copia");

            System.out.println("COMPROBACIONES FASE 2 CORRECTAS: " + comprobaciones);
        } finally {
            conexion.close();
        }
    }

    private static int crearUsuario(String tipo, String identificacion, String nombre) throws SQLException {
        ejecutar("INSERT INTO usuario (tipo,identificacion,nombre,correo,institucion,contrasena_hash,activo) VALUES "
                + "('" + tipo + "','" + identificacion + "','" + nombre + "','"
                + identificacion.toLowerCase() + "@examlen.local','TEST_OC','prueba',1)");
        return numero("SELECT id_usuario FROM usuario WHERE identificacion='" + identificacion + "'");
    }

    private static List<BancoPreguntas> listarBancosDelDocente(BancoPreguntasDAO bancos, int idDocente) {
        return bancos.listarPorDocente(idDocente);
    }

    private static void comprobar(boolean cumple, String caso) {
        if (!cumple) throw new AssertionError(caso);
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
        } catch (OperacionNoPermitidaException | ValidacionException esperada) {
            comprobar(true, caso);
            return;
        }
        throw new AssertionError("Debió rechazarse: " + caso);
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

    private static String texto(String consulta) throws SQLException {
        try (Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery(consulta)) {
            resultado.next();
            return resultado.getString(1);
        }
    }
}
