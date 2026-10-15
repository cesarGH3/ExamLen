package modelo;

import javax.sql.DataSource;

/** Limpieza exclusiva de los datos de benchmark PERF_ (no toca datos reales del dominio). */
public class RendimientoDAO extends DaoBase {

    public RendimientoDAO(DataSource dataSource) {
        super(dataSource);
    }

    private static final String DOCENTE_PERF = "SELECT id_usuario FROM usuario WHERE identificacion LIKE 'PERF-%'";
    private static final String CURSO_PERF = "SELECT c.id_curso FROM curso c JOIN usuario u ON u.id_usuario=c.id_docente "
            + "WHERE u.identificacion LIKE 'PERF-%'";
    private static final String BANCO_PERF = "SELECT b.id_banco_preguntas FROM banco_preguntas b JOIN usuario u "
            + "ON u.id_usuario=b.id_docente WHERE u.identificacion LIKE 'PERF-%'";
    private static final String EVAL_PERF = "SELECT e.id_evaluacion FROM evaluacion e JOIN usuario u "
            + "ON u.id_usuario=e.id_docente WHERE u.identificacion LIKE 'PERF-%'";
    private static final String VIRTUAL_PERF = "SELECT v.id_evaluacion_virtual FROM evaluacion_virtual v "
            + "WHERE v.id_evaluacion IN (" + EVAL_PERF + ")";
    private static final String MATERIAL_PERF = "SELECT m.id_material_practica FROM material_practica m JOIN usuario u "
            + "ON u.id_usuario=m.id_docente WHERE u.identificacion LIKE 'PERF-%'";

    /** Elimina en orden de dependencias todo dato con identificacion/correo PERF-. Devuelve filas restantes. */
    public int limpiar() {
        ejecutar("DELETE e FROM evento_supervision e JOIN intento i ON i.id_intento=e.id_intento "
                + "WHERE i.id_evaluacion_virtual IN (" + VIRTUAL_PERF + ")");
        ejecutar("DELETE r FROM respuesta r JOIN intento i ON i.id_intento=r.id_intento "
                + "WHERE i.id_evaluacion_virtual IN (" + VIRTUAL_PERF + ")");
        ejecutar("DELETE i FROM intento i WHERE i.id_evaluacion_virtual IN (" + VIRTUAL_PERF + ")");
        ejecutar("DELETE evv FROM evaluacion_virtual_version evv WHERE evv.id_evaluacion_virtual IN (" + VIRTUAL_PERF + ")");
        ejecutar("DELETE FROM evaluacion_virtual WHERE id_evaluacion IN (" + EVAL_PERF + ")");
        ejecutar("DELETE vo FROM version_opcion vo JOIN version_pregunta vp ON vp.id_version_pregunta=vo.id_version_pregunta "
                + "JOIN version_evaluacion ve ON ve.id_version_evaluacion=vp.id_version_evaluacion "
                + "WHERE ve.id_evaluacion IN (" + EVAL_PERF + ")");
        ejecutar("DELETE vp FROM version_pregunta vp JOIN version_evaluacion ve "
                + "ON ve.id_version_evaluacion=vp.id_version_evaluacion WHERE ve.id_evaluacion IN (" + EVAL_PERF + ")");
        ejecutar("DELETE FROM version_evaluacion WHERE id_evaluacion IN (" + EVAL_PERF + ")");
        ejecutar("DELETE ep FROM evaluacion_pregunta ep WHERE ep.id_evaluacion IN (" + EVAL_PERF + ")");
        ejecutar("DELETE FROM evaluacion WHERE id_evaluacion IN (SELECT id_evaluacion FROM ("
                + EVAL_PERF + ") AS perf_eval)");
        ejecutar("DELETE rp FROM respuesta_practica rp JOIN intento_practica ip "
                + "ON ip.id_intento_practica=rp.id_intento_practica WHERE ip.id_material_practica IN ("
                + MATERIAL_PERF + ")");
        ejecutar("DELETE ip FROM intento_practica ip WHERE ip.id_material_practica IN (" + MATERIAL_PERF + ")");
        ejecutar("DELETE mp FROM material_pregunta mp WHERE mp.id_material_practica IN (" + MATERIAL_PERF + ")");
        ejecutar("DELETE cm FROM curso_material cm WHERE cm.id_material_practica IN (" + MATERIAL_PERF + ")");
        ejecutar("DELETE FROM material_practica WHERE id_docente IN (" + DOCENTE_PERF + ")");
        ejecutar("DELETE o FROM opcion_respuesta o JOIN pregunta p ON p.id_pregunta=o.id_pregunta "
                + "WHERE p.id_banco_preguntas IN (" + BANCO_PERF + ")");
        ejecutar("DELETE FROM pregunta WHERE id_banco_preguntas IN (" + BANCO_PERF + ")");
        ejecutar("DELETE FROM solicitud_generacion WHERE id_docente IN (" + DOCENTE_PERF + ")");
        ejecutar("DELETE FROM banco_preguntas WHERE id_docente IN (" + DOCENTE_PERF + ")");
        ejecutar("DELETE FROM documento_fuente WHERE id_docente IN (" + DOCENTE_PERF + ")");
        ejecutar("DELETE ins FROM inscripcion ins JOIN grupo g ON g.id_grupo=ins.id_grupo "
                + "WHERE g.id_curso IN (" + CURSO_PERF + ")");
        ejecutar("DELETE FROM grupo WHERE id_curso IN (" + CURSO_PERF + ")");
        ejecutar("DELETE FROM curso WHERE id_docente IN (" + DOCENTE_PERF + ")");
        ejecutar("DELETE t FROM token_recuperacion t JOIN usuario u ON u.id_usuario=t.id_usuario "
                + "WHERE u.identificacion LIKE 'PERF-%'");
        ejecutar("DELETE FROM estudiante WHERE id_usuario IN (SELECT id_usuario FROM usuario "
                + "WHERE identificacion LIKE 'PERF-%')");
        ejecutar("DELETE FROM docente WHERE id_usuario IN (SELECT id_usuario FROM usuario "
                + "WHERE identificacion LIKE 'PERF-%')");
        ejecutar("DELETE FROM administrador WHERE id_usuario IN (SELECT id_usuario FROM usuario "
                + "WHERE identificacion LIKE 'PERF-%')");
        ejecutar("DELETE FROM usuario WHERE identificacion LIKE 'PERF-%' OR correo LIKE 'perf.%'");
        return contar("SELECT COUNT(*) FROM usuario WHERE identificacion LIKE 'PERF-%' OR correo LIKE 'perf.%'",
                SIN_PARAMETROS);
    }

    private void ejecutar(String sql) {
        actualizar(sql, SIN_PARAMETROS);
    }
}
