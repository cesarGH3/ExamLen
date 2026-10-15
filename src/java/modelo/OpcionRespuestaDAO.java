package modelo;

import logica.OpcionRespuesta;
import logica.Pregunta;
import logica.TipoPregunta;
import logica.ValidacionException;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

public class OpcionRespuestaDAO extends DaoBase {

    public OpcionRespuestaDAO(DataSource dataSource) {
        super(dataSource);
    }

    public OpcionRespuesta crearOpcionRespuesta(Pregunta pregunta, String texto, boolean esCorrecta)
            throws ValidacionException {
        if (pregunta.tipoPregunta != TipoPregunta.SELECCION_MULTIPLE) {
            throw new ValidacionException("Solo las preguntas de selección múltiple admiten opciones nuevas.");
        }
        String normalizado = texto.toLowerCase(Locale.ROOT);
        for (OpcionRespuesta existente : pregunta.opcionesPregunta) {
            if (existente.textoOpcionRespuesta.toLowerCase(Locale.ROOT).equals(normalizado)) {
                throw new ValidacionException("Ya existe una opción con ese texto.");
            }
        }
        PreguntaDAO.exigirEditable(pregunta);

        OpcionRespuesta opcion = new OpcionRespuesta();
        opcion.textoOpcionRespuesta = texto;
        opcion.esCorrectaOpcionRespuesta = false;
        enTransaccion(c -> {
            PreguntaDAO.desaprobarSiAprobada(c, pregunta);
            int id = insertarCon(c, "INSERT INTO opcion_respuesta (id_pregunta, texto, "
                    + "es_correcta) VALUES (?, ?, FALSE)", ps -> {
                ps.setInt(1, pregunta.getIdPregunta());
                ps.setString(2, texto);
            });
            opcion.setIdOpcionRespuesta(id);
            if (esCorrecta) {
                actualizarCon(c, "UPDATE opcion_respuesta SET es_correcta = FALSE WHERE id_pregunta = ?",
                        ps -> ps.setInt(1, pregunta.getIdPregunta()));
                actualizarCon(c, "UPDATE opcion_respuesta SET es_correcta = TRUE WHERE id_opcion_respuesta = ?",
                        ps -> ps.setInt(1, id));
            }
            return null;
        });
        pregunta.opcionesPregunta.add(opcion);
        if (esCorrecta) {
            for (OpcionRespuesta o : pregunta.opcionesPregunta) {
                o.esCorrectaOpcionRespuesta = o == opcion;
            }
        }
        return opcion;
    }

    public void eliminarOpcionRespuesta(Pregunta pregunta, OpcionRespuesta opcion) throws ValidacionException {
        if (pregunta.tipoPregunta != TipoPregunta.SELECCION_MULTIPLE) {
            throw new ValidacionException("Las opciones de verdadero o falso son fijas.");
        }
        exigirPertenece(pregunta, opcion);
        PreguntaDAO.exigirEditable(pregunta);
        enTransaccion(c -> {
            PreguntaDAO.desaprobarSiAprobada(c, pregunta);
            actualizarCon(c, "DELETE FROM opcion_respuesta WHERE id_opcion_respuesta = ? AND id_pregunta = ?", ps -> {
                ps.setInt(1, opcion.getIdOpcionRespuesta());
                ps.setInt(2, pregunta.getIdPregunta());
            });
            return null;
        });
        pregunta.opcionesPregunta.remove(opcion);
    }

    public void marcarCorrectaOpcionRespuesta(Pregunta pregunta, OpcionRespuesta opcion) throws ValidacionException {
        if (!PreguntaDAO.usaOpciones(pregunta.tipoPregunta)) {
            throw new ValidacionException("Este tipo de pregunta no usa opciones.");
        }
        exigirPertenece(pregunta, opcion);
        PreguntaDAO.exigirEditable(pregunta);
        enTransaccion(c -> {
            PreguntaDAO.desaprobarSiAprobada(c, pregunta);
            actualizarCon(c, "UPDATE opcion_respuesta SET es_correcta = FALSE WHERE id_pregunta = ?",
                    ps -> ps.setInt(1, pregunta.getIdPregunta()));
            actualizarCon(c, "UPDATE opcion_respuesta SET es_correcta = TRUE WHERE id_opcion_respuesta = ?",
                    ps -> ps.setInt(1, opcion.getIdOpcionRespuesta()));
            return null;
        });
        for (OpcionRespuesta o : pregunta.opcionesPregunta) {
            o.esCorrectaOpcionRespuesta = o == opcion;
        }
    }

    /**
     * Edición del texto de una opción (UI en la pantalla de la pregunta). La modificación y la
     * regla APROBADA → BORRADOR ocurren en la misma transacción (Fase 1B).
     */
    public void actualizarOpcionRespuesta(Pregunta pregunta, OpcionRespuesta opcion, String texto)
            throws ValidacionException {
        exigirPertenece(pregunta, opcion);
        PreguntaDAO.exigirEditable(pregunta);
        enTransaccion(c -> {
            PreguntaDAO.desaprobarSiAprobada(c, pregunta);
            actualizarCon(c, "UPDATE opcion_respuesta SET texto = ? WHERE id_opcion_respuesta = ? AND id_pregunta = ?",
                    ps -> {
                        ps.setString(1, texto);
                        ps.setInt(2, opcion.getIdOpcionRespuesta());
                        ps.setInt(3, pregunta.getIdPregunta());
                    });
            return null;
        });
        opcion.textoOpcionRespuesta = texto;
    }

    public List<OpcionRespuesta> listarPorPregunta(int idPregunta) {
        return consultarLista("SELECT id_opcion_respuesta, texto, es_correcta "
                + "FROM opcion_respuesta WHERE id_pregunta = ? ORDER BY id_opcion_respuesta",
                ps -> ps.setInt(1, idPregunta), PreguntaDAO::mapearOpcion);
    }

    private void exigirPertenece(Pregunta pregunta, OpcionRespuesta opcion) throws ValidacionException {
        if (!pregunta.opcionesPregunta.contains(opcion)) {
            throw new ValidacionException("La opción indicada no pertenece a esta pregunta.");
        }
    }
}
