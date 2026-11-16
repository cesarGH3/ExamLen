package modelo;

import logica.Competencia;
import logica.Dificultad;
import logica.NivelIngles;
import logica.TipoGeneracion;
import logica.TipoPregunta;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Persistencia atómica del resultado de una generación con IA (Fase 7): banco (si es material),
 * material con origen GENERADO_IA, preguntas en BORRADOR con su id_solicitud_generacion, opciones
 * completas y asociaciones material-pregunta, todo en UNA transacción corta después de la llamada
 * externa. Si algo falla, no queda contenido parcial.
 */
public class PersistenciaGeneracionDAO extends DaoBase {

    public static final class OpcionPregunta {
        public final String texto;
        public final boolean correcta;

        public OpcionPregunta(String texto, boolean correcta) {
            this.texto = texto;
            this.correcta = correcta;
        }
    }

    public static final class PreguntaPersistible {
        public String enunciado;
        public TipoPregunta tipo;
        public NivelIngles nivel;
        public Dificultad dificultad;
        public Competencia competencia;
        public String tema;
        public String respuestaEsperada;
        public final List<OpcionPregunta> opciones = new ArrayList<>();
    }

    public PersistenciaGeneracionDAO(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Guarda el resultado válido de una generación y devuelve cuántas preguntas quedaron.
     * Para MATERIAL_PRACTICA crea el material. El banco destino siempre es opcional.
     */
    public int persistir(int idDocente, int idSolicitud, TipoGeneracion tipo, NivelIngles nivel,
                         Integer idBancoDestino, String descripcionMaterial, String marca,
                         List<PreguntaPersistible> preguntas) {
        return enTransaccion(c -> {
            if (consultarUnoCon(c, "SELECT id_solicitud_generacion FROM solicitud_generacion "
                    + "WHERE id_solicitud_generacion = ? AND id_docente = ? FOR UPDATE", sentencia -> {
                sentencia.setInt(1, idSolicitud); sentencia.setInt(2, idDocente);
            }, resultado -> resultado.getInt(1)).isEmpty()) {
                throw new logica.OperacionNoPermitidaException("Solicitud ajena al docente.");
            }
            if (idBancoDestino != null && consultarUnoCon(c, "SELECT id_banco_preguntas FROM banco_preguntas "
                    + "WHERE id_banco_preguntas = ? AND id_docente = ? FOR SHARE", sentencia -> {
                sentencia.setInt(1, idBancoDestino); sentencia.setInt(2, idDocente);
            }, resultado -> resultado.getInt(1)).isEmpty()) {
                throw new logica.OperacionNoPermitidaException("Banco ajeno al docente.");
            }
            Integer idBanco = idBancoDestino;
            Integer idMaterial = null;
            if (tipo == TipoGeneracion.MATERIAL_PRACTICA) {
                idMaterial = insertarCon(c, "INSERT INTO material_practica (id_docente, id_solicitud_generacion, "
                        + "titulo, descripcion, nivel, estado, origen) "
                        + "VALUES (?, ?, ?, ?, ?, 'BORRADOR', 'GENERADO_IA')", ps -> {
                    ps.setInt(1, idDocente);
                    ps.setInt(2, idSolicitud);
                    ps.setString(3, "Material generado " + marca);
                    ps.setString(4, recortar(descripcionMaterial, 500));
                    ps.setString(5, nivel == null ? null : nivel.name());
                });
            }
            int guardadas = 0;
            for (PreguntaPersistible pregunta : preguntas) {
                final Integer idMaterialFinal = idMaterial;
                int idPregunta = insertarCon(c, "INSERT INTO pregunta (id_docente, id_banco_preguntas, id_solicitud_generacion, "
                        + "enunciado, tipo, nivel, dificultad, competencia, tema, respuesta_esperada, "
                        + "estado, origen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BORRADOR', 'GENERADO_IA')", ps -> {
                    ps.setInt(1, idDocente);
                    ps.setObject(2, idBanco, java.sql.Types.INTEGER);
                    ps.setInt(3, idSolicitud);
                    ps.setString(4, pregunta.enunciado);
                    ps.setString(5, pregunta.tipo.name());
                    ps.setString(6, pregunta.nivel == null ? null : pregunta.nivel.name());
                    ps.setString(7, pregunta.dificultad == null ? null : pregunta.dificultad.name());
                    ps.setString(8, pregunta.competencia == null ? null : pregunta.competencia.name());
                    ps.setString(9, pregunta.tema);
                    ps.setString(10, pregunta.respuestaEsperada);
                });
                int orden = 1;
                for (OpcionPregunta opcion : pregunta.opciones) {
                    final int ordenOpcion = orden++;
                    insertarCon(c, "INSERT INTO opcion_respuesta (id_pregunta, texto, es_correcta) "
                            + "VALUES (?, ?, ?)", ps -> {
                        ps.setInt(1, idPregunta);
                        ps.setString(2, opcion.texto);
                        ps.setBoolean(3, opcion.correcta);
                    });
                }
                if (idMaterialFinal != null) {
                    actualizarCon(c, "INSERT INTO material_pregunta (id_material_practica, id_pregunta) "
                            + "VALUES (?, ?)", ps -> {
                        ps.setInt(1, idMaterialFinal);
                        ps.setInt(2, idPregunta);
                    });
                }
                guardadas++;
            }
            return guardadas;
        });
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.strip();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
