package modelo;

import logica.VersionOpcion;
import logica.VersionPregunta;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;

public class VersionPreguntaDAO extends DaoBase {

    public VersionPreguntaDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Compara la respuesta: si hay opción elegida manda su marca; si no, contra las respuestas aceptadas. */
    public boolean esRespuestaCorrectaVersionPregunta(VersionPregunta versionPregunta, String contenido,
                                                      VersionOpcion opcion) {
        if (opcion != null) {
            return opcion.esCorrectaVersionOpcion;
        }
        String dada = normalizar(contenido);
        if (dada.isEmpty() || versionPregunta.respuestaEsperadaVersionPregunta == null) {
            return false;
        }
        for (String aceptada : versionPregunta.respuestaEsperadaVersionPregunta.split(";")) {
            if (normalizar(aceptada).equals(dada)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Intercambia la posicion de dos preguntas de la misma version en una transaccion. Con
     * uk_version_pregunta_orden (migracion 026) dos UPDATE directos chocarian; por eso la primera
     * pasa por una posicion libre (MAX + 1). Antes se bloquea la fila de la version en
     * version_evaluacion: todos los intercambios de una misma version quedan en fila, de modo que
     * dos transacciones nunca calculan a la vez la misma posicion temporal.
     */
    public void intercambiarOrden(VersionPregunta a, VersionPregunta b) {
        int[] ordenes = enTransaccion(c -> {
            // Primero se bloquea SOLO la fila de la version (lectura sin bloqueo para conocer su id):
            // asi todas las transacciones adquieren los bloqueos en el mismo orden (version y luego
            // preguntas) y no pueden interbloquearse.
            int idVersion = consultarUnoCon(c, "SELECT id_version_evaluacion FROM version_pregunta "
                    + "WHERE id_version_pregunta = ?", ps -> ps.setInt(1, a.consultarIdVersionPregunta()),
                    rs -> rs.getInt(1)).orElseThrow(() -> new SQLException("La pregunta de la version no existe."));
            consultarListaCon(c, "SELECT id_version_evaluacion FROM version_evaluacion "
                    + "WHERE id_version_evaluacion = ? FOR UPDATE", ps -> ps.setInt(1, idVersion), rs -> rs.getInt(1));
            List<int[]> filas = consultarListaCon(c, "SELECT id_version_pregunta, id_version_evaluacion, orden "
                    + "FROM version_pregunta WHERE id_version_pregunta IN (?, ?) FOR UPDATE", ps -> {
                        ps.setInt(1, a.consultarIdVersionPregunta());
                        ps.setInt(2, b.consultarIdVersionPregunta());
                    }, rs -> new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)});
            if (filas.size() != 2 || filas.get(0)[1] != filas.get(1)[1]) {
                throw new SQLException("Las preguntas no pertenecen a la misma version.");
            }
            int ordenA = filas.get(0)[0] == a.consultarIdVersionPregunta() ? filas.get(0)[2] : filas.get(1)[2];
            int ordenB = filas.get(0)[0] == a.consultarIdVersionPregunta() ? filas.get(1)[2] : filas.get(0)[2];
            int libre = consultarUnoCon(c, "SELECT COALESCE(MAX(orden), 0) + 1 FROM version_pregunta "
                    + "WHERE id_version_evaluacion = ? FOR UPDATE", ps -> ps.setInt(1, filas.get(0)[1]),
                    rs -> rs.getInt(1)).orElse(1);
            actualizarCon(c, "UPDATE version_pregunta SET orden = ? WHERE id_version_pregunta = ?", ps -> {
                ps.setInt(1, libre);
                ps.setInt(2, a.consultarIdVersionPregunta());
            });
            actualizarCon(c, "UPDATE version_pregunta SET orden = ? WHERE id_version_pregunta = ?", ps -> {
                ps.setInt(1, ordenA);
                ps.setInt(2, b.consultarIdVersionPregunta());
            });
            actualizarCon(c, "UPDATE version_pregunta SET orden = ? WHERE id_version_pregunta = ?", ps -> {
                ps.setInt(1, ordenB);
                ps.setInt(2, a.consultarIdVersionPregunta());
            });
            return new int[]{ordenB, ordenA};
        });
        a.ordenVersionPregunta = ordenes[0];
        b.ordenVersionPregunta = ordenes[1];
    }

    /** Cambia el orden de impresión de una pregunta dentro de su versión. */
    public void actualizarOrden(VersionPregunta versionPregunta, int orden) {
        actualizar("UPDATE version_pregunta SET orden = ? WHERE id_version_pregunta = ?", ps -> {
            ps.setInt(1, orden);
            ps.setInt(2, versionPregunta.consultarIdVersionPregunta());
        });
        versionPregunta.ordenVersionPregunta = orden;
    }

    /** RF-38: edita la copia de la versión (enunciado, respuesta esperada y puntaje); no toca el banco. */
    public void actualizar(VersionPregunta versionPregunta, String enunciado, String respuestaEsperada,
                           double puntaje) {
        actualizar("UPDATE version_pregunta SET enunciado = ?, respuesta_esperada = ?, puntaje = ? "
                + "WHERE id_version_pregunta = ?", ps -> {
            ps.setString(1, enunciado);
            ps.setString(2, respuestaEsperada);
            ps.setDouble(3, puntaje);
            ps.setInt(4, versionPregunta.consultarIdVersionPregunta());
        });
        versionPregunta.enunciadoVersionPregunta = enunciado;
        versionPregunta.respuestaEsperadaVersionPregunta = respuestaEsperada;
        versionPregunta.puntajeVersionPregunta = puntaje;
    }

    public int idVersionEvaluacionDePregunta(int idVersionPregunta) {
        return consultarUno("SELECT id_version_evaluacion FROM version_pregunta WHERE id_version_pregunta = ?",
                ps -> ps.setInt(1, idVersionPregunta), rs -> rs.getInt("id_version_evaluacion")).orElse(0);
    }

    /** Tipo de la pregunta del banco de la que salió esta copia (para saber si lleva opciones). */
    public String tipoDeVersionPregunta(int idVersionPregunta) {
        return consultarUno("SELECT p.tipo FROM version_pregunta vp "
                + "JOIN pregunta p ON p.id_pregunta = vp.id_pregunta WHERE vp.id_version_pregunta = ?",
                ps -> ps.setInt(1, idVersionPregunta), rs -> rs.getString("tipo")).orElse(null);
    }

    public boolean perteneceAVersion(int idVersionPregunta, int idVersionEvaluacion) {
        return existe("SELECT 1 FROM version_pregunta WHERE id_version_pregunta = ? "
                + "AND id_version_evaluacion = ? LIMIT 1", ps -> {
            ps.setInt(1, idVersionPregunta);
            ps.setInt(2, idVersionEvaluacion);
        });
    }

    public Optional<VersionPregunta> buscarPorId(int idVersionPregunta) {
        return consultarUno("SELECT id_version_pregunta, id_version_evaluacion, id_pregunta, "
                + "enunciado, respuesta_esperada, orden, "
                + "puntaje FROM version_pregunta WHERE id_version_pregunta = ?",
                ps -> ps.setInt(1, idVersionPregunta), this::mapear);
    }

    public List<VersionPregunta> listarPorVersionEvaluacion(int idVersionEvaluacion) {
        return consultarLista("SELECT id_version_pregunta, id_version_evaluacion, id_pregunta, "
                + "enunciado, respuesta_esperada, orden, "
                + "puntaje FROM version_pregunta WHERE id_version_evaluacion = ? "
                + "ORDER BY orden", ps -> ps.setInt(1, idVersionEvaluacion), this::mapear);
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private VersionPregunta mapear(ResultSet rs) throws SQLException {
        VersionPregunta versionPregunta = new VersionPregunta();
        versionPregunta.establecerIdVersionPregunta(rs.getInt("id_version_pregunta"));
        versionPregunta.enunciadoVersionPregunta = rs.getString("enunciado");
        versionPregunta.respuestaEsperadaVersionPregunta = rs.getString("respuesta_esperada");
        versionPregunta.ordenVersionPregunta = rs.getInt("orden");
        versionPregunta.puntajeVersionPregunta = rs.getDouble("puntaje");
        return versionPregunta;
    }
}
