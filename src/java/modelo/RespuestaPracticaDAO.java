package modelo;

import logica.Competencia;
import logica.OpcionRespuesta;
import logica.Pregunta;
import logica.RespuestaPractica;
import logica.TipoPregunta;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

public class RespuestaPracticaDAO extends DaoBase {

    /** Fila del resumen de desempeño por competencia o tema (RF-62). */
    public static final class Resumen {

        public String nombre;
        public int respondidas;
        public int correctas;
    }

    public RespuestaPracticaDAO(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Evalúa automáticamente una respuesta de práctica (RF-60): las preguntas de opciones se
     * corrigen por la opción marcada y las de respuesta corta comparando con la respuesta esperada
     * (sin distinguir mayúsculas y con espacios normalizados). Las preguntas abiertas y las que no
     * tienen respuesta esperada quedan sin calificar (null).
     */
    public static Boolean evaluar(Pregunta pregunta, OpcionRespuesta opcion, String contenido) {
        if (pregunta != null && !pregunta.opcionesPregunta.isEmpty()) {
            return opcion != null && opcion.esCorrectaOpcionRespuesta;
        }
        if (pregunta == null || pregunta.tipoPregunta == TipoPregunta.RESPUESTA_ABIERTA) {
            return null;
        }
        String esperada = pregunta.respuestaEsperadaPregunta;
        if (esperada == null || esperada.isBlank()) {
            return null;
        }
        String dada = normalizar(contenido);
        if (dada.isEmpty()) {
            return false;
        }
        for (String aceptada : esperada.split(";")) {
            if (normalizar(aceptada).equals(dada)) {
                return true;
            }
        }
        return false;
    }

    /** Respuestas del intento con su pregunta, la opción marcada y la respuesta correcta (RF-60). */
    public List<RespuestaPractica> listarConPregunta(int idIntentoPractica) {
        return consultarLista("SELECT rp.id_respuesta_practica, rp.contenido, rp.es_correcta, "
                + "rp.retroalimentacion, p.id_pregunta, p.enunciado, p.tipo, p.tema, p.competencia, "
                + "p.respuesta_esperada, o.id_opcion_respuesta, o.texto, "
                + "(SELECT o2.texto FROM opcion_respuesta o2 WHERE o2.id_pregunta = p.id_pregunta "
                + "AND o2.es_correcta = TRUE ORDER BY o2.id_opcion_respuesta LIMIT 1) AS texto_correcto "
                + "FROM respuesta_practica rp JOIN pregunta p ON p.id_pregunta = rp.id_pregunta "
                + "LEFT JOIN opcion_respuesta o ON o.id_opcion_respuesta = rp.id_opcion_respuesta "
                + "WHERE rp.id_intento_practica = ? ORDER BY rp.id_respuesta_practica",
                ps -> ps.setInt(1, idIntentoPractica), rs -> {
                    RespuestaPractica respuesta = new RespuestaPractica();
                    respuesta.setIdRespuestaPractica(rs.getInt("id_respuesta_practica"));
                    respuesta.contenidoRespuestaPractica = rs.getString("contenido");
                    respuesta.esCorrectaRespuestaPractica = rs.getObject("es_correcta") == null
                            ? null : rs.getBoolean("es_correcta");
                    respuesta.retroalimentacionRespuestaPractica = rs.getString("retroalimentacion");
                    respuesta.respuestaCorrectaRespuestaPractica = rs.getString("texto_correcto");
                    Pregunta pregunta = new Pregunta();
                    pregunta.setIdPregunta(rs.getInt("id_pregunta"));
                    pregunta.enunciadoPregunta = rs.getString("enunciado");
                    pregunta.tipoPregunta = enumeracion(rs, "tipo", TipoPregunta.class);
                    pregunta.temaPregunta = rs.getString("tema");
                    pregunta.competenciaPregunta = enumeracion(rs, "competencia", Competencia.class);
                    pregunta.respuestaEsperadaPregunta = rs.getString("respuesta_esperada");
                    respuesta.preguntaRespuestaPractica = pregunta;
                    Object idOpcion = rs.getObject("id_opcion_respuesta");
                    if (idOpcion != null) {
                        OpcionRespuesta opcion = new OpcionRespuesta();
                        opcion.setIdOpcionRespuesta(rs.getInt("id_opcion_respuesta"));
                        opcion.textoOpcionRespuesta = rs.getString("texto");
                        respuesta.opcionRespuestaRespuestaPractica = opcion;
                    }
                    return respuesta;
                });
    }

    public List<RespuestaPractica> listarPorIntentoPractica(int idIntentoPractica) {
        return consultarLista("SELECT id_respuesta_practica, contenido, "
                + "es_correcta, retroalimentacion FROM respuesta_practica "
                + "WHERE id_intento_practica = ? ORDER BY id_respuesta_practica", ps -> ps.setInt(1, idIntentoPractica),
                rs -> {
                    RespuestaPractica respuesta = new RespuestaPractica();
                    respuesta.setIdRespuestaPractica(rs.getInt("id_respuesta_practica"));
                    respuesta.contenidoRespuestaPractica = rs.getString("contenido");
                    respuesta.esCorrectaRespuestaPractica = rs.getObject("es_correcta") == null
                            ? null : rs.getBoolean("es_correcta");
                    respuesta.retroalimentacionRespuestaPractica = rs.getString("retroalimentacion");
                    return respuesta;
                });
    }

    /** Desempeño del estudiante por competencia; solo cuenta las respuestas calificadas (RF-62). */
    public List<Resumen> resumenPorCompetencia(int idEstudiante) {
        return resumen("p.competencia", idEstudiante);
    }

    /** Desempeño del estudiante por tema; solo cuenta las respuestas calificadas (RF-62). */
    public List<Resumen> resumenPorTema(int idEstudiante) {
        return resumen("p.tema", idEstudiante);
    }

    private List<Resumen> resumen(String agrupador, int idEstudiante) {
        return consultarLista("SELECT " + agrupador + " AS nombre, COUNT(*) AS respondidas, "
                + "SUM(CASE WHEN rp.es_correcta = TRUE THEN 1 ELSE 0 END) AS correctas "
                + "FROM respuesta_practica rp "
                + "JOIN intento_practica ip ON ip.id_intento_practica = rp.id_intento_practica "
                + "JOIN pregunta p ON p.id_pregunta = rp.id_pregunta "
                + "WHERE ip.id_estudiante = ? AND rp.es_correcta IS NOT NULL "
                + "GROUP BY " + agrupador + " "
                + "ORDER BY (COUNT(*) - SUM(CASE WHEN rp.es_correcta = TRUE THEN 1 ELSE 0 END)) DESC, "
                + agrupador, ps -> ps.setInt(1, idEstudiante), rs -> {
                    Resumen fila = new Resumen();
                    fila.nombre = rs.getString("nombre");
                    fila.respondidas = rs.getInt("respondidas");
                    fila.correctas = rs.getInt("correctas");
                    return fila;
                });
    }

    /**
     * RF-51: ignora mayúsculas, tildes/diacríticos y espacios sobrantes de forma determinista
     * (Unicode NFD + eliminación de marcas combinantes).
     */
    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String plano = java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return plano.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
