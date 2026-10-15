package modelo;

import logica.Fechas;

import logica.Intento;
import logica.Respuesta;
import logica.TipoPregunta;
import logica.VersionOpcion;
import logica.VersionPregunta;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;

public class RespuestaDAO extends DaoBase {

    private static final String COLUMNAS = "id_respuesta, id_intento, id_version_pregunta, id_version_opcion, "
            + "contenido, fecha, puntaje, comentario_docente";

    public RespuestaDAO(DataSource dataSource) {
        super(dataSource);
    }

    public Respuesta crearRespuesta(Intento intento, VersionPregunta versionPregunta, String contenido) {
        Respuesta respuesta = new Respuesta();
        respuesta.contenidoRespuesta = contenido;
        respuesta.fechaRespuesta = Fechas.ahora();
        respuesta.versionPreguntaRespuesta = versionPregunta;
        int id = insertar("INSERT INTO respuesta (id_intento, id_version_pregunta, contenido) "
                + "VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, intento.getIdIntento());
            ps.setInt(2, versionPregunta.getIdVersionPregunta());
            ps.setString(3, contenido);
        });
        respuesta.setIdRespuesta(id);
        intento.respuestasIntento.add(respuesta);
        return respuesta;
    }

    public void modificarRespuesta(Respuesta respuesta, String contenido) {
        actualizar("UPDATE respuesta SET contenido = ? WHERE id_respuesta = ?", ps -> {
            ps.setString(1, contenido);
            ps.setInt(2, respuesta.getIdRespuesta());
        });
        respuesta.contenidoRespuesta = contenido;
    }

    /** Puntaje según la opción marcada o las respuestas aceptadas. Reglas finas en la Entrega 3. */
    public double calificarAutomaticamenteRespuesta(Respuesta respuesta) {
        VersionPregunta versionPregunta = respuesta.versionPreguntaRespuesta;
        boolean correcta = false;
        if (versionPregunta != null) {
            if (respuesta.versionOpcionRespuesta != null) {
                correcta = respuesta.versionOpcionRespuesta.esCorrectaVersionOpcion;
            } else {
                String dada = normalizar(respuesta.contenidoRespuesta);
                if (!dada.isEmpty() && versionPregunta.respuestaEsperadaVersionPregunta != null) {
                    for (String aceptada : versionPregunta.respuestaEsperadaVersionPregunta.split(";")) {
                        if (normalizar(aceptada).equals(dada)) {
                            correcta = true;
                            break;
                        }
                    }
                }
            }
        }
        double puntaje = correcta ? versionPregunta.puntajeVersionPregunta : 0.0;
        actualizar("UPDATE respuesta SET puntaje = ? WHERE id_respuesta = ?", ps -> {
            ps.setDouble(1, puntaje);
            ps.setInt(2, respuesta.getIdRespuesta());
        });
        respuesta.puntajeRespuesta = puntaje;
        return puntaje;
    }

    public void calificarManualmenteRespuesta(Respuesta respuesta, double puntaje, String comentario) {
        actualizar("UPDATE respuesta SET puntaje = ?, comentario_docente = ? "
                + "WHERE id_respuesta = ?", ps -> {
            ps.setDouble(1, puntaje);
            if (comentario == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, comentario);
            }
            ps.setInt(3, respuesta.getIdRespuesta());
        });
        respuesta.puntajeRespuesta = puntaje;
        respuesta.comentarioDocenteRespuesta = comentario;
    }

    public Optional<Respuesta> buscarPorId(int idRespuesta) {
        return consultarUno("SELECT " + COLUMNAS + " FROM respuesta WHERE id_respuesta = ?",
                ps -> ps.setInt(1, idRespuesta), this::mapear);
    }

    public List<Respuesta> listarPorIntento(int idIntento) {
        return consultarLista("SELECT " + COLUMNAS + " FROM respuesta WHERE id_intento = ? ORDER BY id_respuesta",
                ps -> ps.setInt(1, idIntento), this::mapear);
    }

    // ------------------------------------------------------------------ Entrega 3

    private static final String SELECT_CON_PREGUNTA = "SELECT r.id_respuesta, r.contenido, "
            + "r.fecha, r.puntaje, r.comentario_docente, vp.id_version_pregunta, "
            + "vp.enunciado, vp.respuesta_esperada, vp.puntaje AS puntaje_version, "
            + "vo.id_version_opcion, vo.texto, vo.es_correcta "
            + "FROM respuesta r JOIN version_pregunta vp ON vp.id_version_pregunta = r.id_version_pregunta "
            + "LEFT JOIN version_opcion vo ON vo.id_version_opcion = r.id_version_opcion";

    /** Respuesta del intento para una pregunta concreta (autoguardado). */
    public Optional<Respuesta> buscarPorIntentoYVersionPregunta(int idIntento, int idVersionPregunta) {
        return consultarUno(SELECT_CON_PREGUNTA + " WHERE r.id_intento = ? AND r.id_version_pregunta = ?", ps -> {
            ps.setInt(1, idIntento);
            ps.setInt(2, idVersionPregunta);
        }, this::mapearConPregunta);
    }

    /** Respuestas del intento con su pregunta y opción (revisión y calificación). */
    public List<Respuesta> listarConPregunta(int idIntento) {
        return consultarLista(SELECT_CON_PREGUNTA + " WHERE r.id_intento = ? ORDER BY vp.orden",
                ps -> ps.setInt(1, idIntento), this::mapearConPregunta);
    }

    /** Respuesta con su pregunta y opción, para recalificar o ajustar su puntaje (RF-52). */
    public Optional<Respuesta> buscarConPregunta(int idRespuesta) {
        return consultarUno(SELECT_CON_PREGUNTA + " WHERE r.id_respuesta = ?",
                ps -> ps.setInt(1, idRespuesta), this::mapearConPregunta);
    }

    /**
     * Guarda (crea o modifica) la respuesta de una pregunta solo si el intento sigue EN_CURSO y
     * NOW() es anterior al limite efectivo (RF-47). Bloquea la fila del intento dentro de la
     * transaccion: el programador no puede finalizarlo entre la comprobacion y la escritura, y
     * ninguna escritura confirmada despues del limite queda persistida.
     * Devuelve true si la respuesta quedo guardada.
     */
    public boolean guardarRespuestaVigente(Intento intento, VersionPregunta pregunta, String contenido, int idOpcion) {
        return enTransaccion(c -> {
            List<String> vigencia = consultarListaCon(c,
                    "SELECT i.estado FROM " + IntentoDAO.LIMITE_JOIN + " WHERE i.id_intento = ? "
                    + "AND i.estado = 'EN_CURSO' AND " + IntentoDAO.LIMITE_EFECTIVO + " > NOW() FOR UPDATE",
                    ps -> ps.setInt(1, intento.getIdIntento()), rs -> rs.getString(1));
            if (vigencia.isEmpty()) {
                return false;
            }
            List<Integer> existentes = consultarListaCon(c, "SELECT id_respuesta FROM respuesta "
                    + "WHERE id_intento = ? AND id_version_pregunta = ?", ps -> {
                        ps.setInt(1, intento.getIdIntento());
                        ps.setInt(2, pregunta.getIdVersionPregunta());
                    }, rs -> rs.getInt(1));
            final int idRespuesta;
            if (existentes.isEmpty()) {
                idRespuesta = insertarCon(c, "INSERT INTO respuesta (id_intento, id_version_pregunta, "
                        + "contenido) VALUES (?, ?, ?)", ps -> {
                            ps.setInt(1, intento.getIdIntento());
                            ps.setInt(2, pregunta.getIdVersionPregunta());
                            ps.setString(3, contenido);
                        });
            } else {
                idRespuesta = existentes.get(0);
                actualizarCon(c, "UPDATE respuesta SET contenido = ? WHERE id_respuesta = ?", ps -> {
                    ps.setString(1, contenido);
                    ps.setInt(2, idRespuesta);
                });
            }
            if (idOpcion > 0) {
                actualizarCon(c, "UPDATE respuesta SET id_version_opcion = ? WHERE id_respuesta = ?", ps -> {
                    ps.setInt(1, idOpcion);
                    ps.setInt(2, idRespuesta);
                });
            }
            return true;
        });
    }

    /** Respuesta de texto (abierta o corta) de una evaluación virtual, para el análisis de similitud (RF-50). */
    public static final class TextoRespuesta {
        public int idIntento;
        public int idPregunta;
        public TipoPregunta tipo;
        public String contenido;
        public String respuestaEsperada;
        public String enunciado;
    }

    /**
     * Respuestas abiertas y cortas con texto de todos los intentos de una evaluación virtual.
     * La clave entre versiones es la pregunta original (id_pregunta), no la copia de la versión.
     */
    public List<TextoRespuesta> listarTextoDeEvaluacionVirtual(int idEvaluacionVirtual) {
        return consultarLista("SELECT r.id_intento, vp.id_pregunta, p.tipo, r.contenido, "
                + "vp.respuesta_esperada, vp.enunciado "
                + "FROM respuesta r JOIN version_pregunta vp ON vp.id_version_pregunta = r.id_version_pregunta "
                + "JOIN pregunta p ON p.id_pregunta = vp.id_pregunta "
                + "JOIN intento i ON i.id_intento = r.id_intento "
                + "WHERE i.id_evaluacion_virtual = ? AND p.tipo IN ('RESPUESTA_ABIERTA','RESPUESTA_CORTA') "
                + "AND r.contenido IS NOT NULL AND TRIM(r.contenido) <> ''",
                ps -> ps.setInt(1, idEvaluacionVirtual), rs -> {
                    TextoRespuesta fila = new TextoRespuesta();
                    fila.idIntento = rs.getInt("id_intento");
                    fila.idPregunta = rs.getInt("id_pregunta");
                    fila.tipo = enumeracion(rs, "tipo", TipoPregunta.class);
                    fila.contenido = rs.getString("contenido");
                    fila.respuestaEsperada = rs.getString("respuesta_esperada");
                    fila.enunciado = rs.getString("enunciado");
                    return fila;
                });
    }

    /**
     * Califica automáticamente las respuestas de opciones y respuesta corta.
     * Las abiertas quedan sin puntaje (pendientes del docente). Devuelve cuántas quedan pendientes.
     */
    public int calificarAutomaticas(int idIntento) {
        List<FilaAuto> filas = consultarLista("SELECT r.id_respuesta, r.contenido, r.id_version_opcion, "
                + "vp.puntaje, vp.respuesta_esperada, p.tipo, "
                + "(SELECT vo.es_correcta FROM version_opcion vo "
                + "WHERE vo.id_version_opcion = r.id_version_opcion) AS opcion_correcta "
                + "FROM respuesta r JOIN version_pregunta vp ON vp.id_version_pregunta = r.id_version_pregunta "
                + "JOIN pregunta p ON p.id_pregunta = vp.id_pregunta WHERE r.id_intento = ?",
                ps -> ps.setInt(1, idIntento), rs -> {
                    FilaAuto fila = new FilaAuto();
                    fila.idRespuesta = rs.getInt("id_respuesta");
                    fila.contenido = rs.getString("contenido");
                    fila.puntajeMaximo = rs.getDouble("puntaje");
                    fila.respuestaEsperada = rs.getString("respuesta_esperada");
                    fila.tipo = TipoPregunta.valueOf(rs.getString("tipo"));
                    Object correcta = rs.getObject("opcion_correcta");
                    fila.opcionCorrecta = correcta == null ? null : rs.getBoolean("opcion_correcta");
                    return fila;
                });
        for (FilaAuto fila : filas) {
            Double puntaje = null;
            if (PreguntaDAO.usaOpciones(fila.tipo)) {
                puntaje = Boolean.TRUE.equals(fila.opcionCorrecta) ? fila.puntajeMaximo : 0.0;
            } else if (fila.tipo == TipoPregunta.RESPUESTA_CORTA) {
                puntaje = coincide(fila.contenido, fila.respuestaEsperada) ? fila.puntajeMaximo : 0.0;
            } else if (fila.tipo == TipoPregunta.RESPUESTA_ABIERTA
                    && (fila.contenido == null || fila.contenido.isBlank())) {
                // Abierta sin responder: 0 puntos y no queda pendiente de calificacion (RF-53).
                puntaje = 0.0;
            }
            if (puntaje != null) {
                final double valor = puntaje;
                actualizar("UPDATE respuesta SET puntaje = ? WHERE id_respuesta = ?", ps -> {
                    ps.setDouble(1, valor);
                    ps.setInt(2, fila.idRespuesta);
                });
            }
        }
        return contarPendientes(idIntento);
    }

    /**
     * Respuestas realmente pendientes de calificacion manual: abiertas CON contenido y sin puntaje.
     * Una abierta en blanco no es una respuesta que el docente deba calificar (RF-53).
     */
    public int contarPendientes(int idIntento) {
        return contar("SELECT COUNT(*) FROM respuesta r "
                + "JOIN version_pregunta vp ON vp.id_version_pregunta = r.id_version_pregunta "
                + "JOIN pregunta p ON p.id_pregunta = vp.id_pregunta "
                + "WHERE r.id_intento = ? AND r.puntaje IS NULL AND p.tipo = 'RESPUESTA_ABIERTA' "
                + "AND r.contenido IS NOT NULL AND TRIM(r.contenido) <> ''",
                ps -> ps.setInt(1, idIntento));
    }

    /** Verifica que la respuesta pertenezca al intento indicado. */
    public boolean perteneceAIntento(int idRespuesta, int idIntento) {
        return existe("SELECT 1 FROM respuesta WHERE id_respuesta = ? AND id_intento = ? LIMIT 1", ps -> {
            ps.setInt(1, idRespuesta);
            ps.setInt(2, idIntento);
        });
    }

    /** Identificadores de las respuestas abiertas con contenido aun sin calificar. */
    public List<Integer> idsPendientes(int idIntento) {
        return consultarLista("SELECT r.id_respuesta FROM respuesta r "
                + "JOIN version_pregunta vp ON vp.id_version_pregunta = r.id_version_pregunta "
                + "JOIN pregunta p ON p.id_pregunta = vp.id_pregunta "
                + "WHERE r.id_intento = ? AND r.puntaje IS NULL AND p.tipo = 'RESPUESTA_ABIERTA' "
                + "AND r.contenido IS NOT NULL AND TRIM(r.contenido) <> '' ORDER BY r.id_respuesta",
                ps -> ps.setInt(1, idIntento), rs -> rs.getInt(1));
    }

    private static boolean coincide(String contenido, String esperada) {
        String dada = normalizar(contenido);
        if (dada.isEmpty() || esperada == null) {
            return false;
        }
        for (String aceptada : esperada.split(";")) {
            if (normalizar(aceptada).equals(dada)) {
                return true;
            }
        }
        return false;
    }

    private static final class FilaAuto {
        int idRespuesta;
        String contenido;
        double puntajeMaximo;
        String respuestaEsperada;
        TipoPregunta tipo;
        Boolean opcionCorrecta;
    }

    private Respuesta mapearConPregunta(ResultSet rs) throws SQLException {
        Respuesta respuesta = new Respuesta();
        respuesta.setIdRespuesta(rs.getInt("id_respuesta"));
        respuesta.contenidoRespuesta = rs.getString("contenido");
        respuesta.fechaRespuesta = Mapeos.fecha(rs, "fecha");
        respuesta.puntajeRespuesta = rs.getDouble("puntaje");
        respuesta.comentarioDocenteRespuesta = rs.getString("comentario_docente");

        VersionPregunta versionPregunta = new VersionPregunta();
        versionPregunta.setIdVersionPregunta(rs.getInt("id_version_pregunta"));
        versionPregunta.enunciadoVersionPregunta = rs.getString("enunciado");
        versionPregunta.respuestaEsperadaVersionPregunta = rs.getString("respuesta_esperada");
        versionPregunta.puntajeVersionPregunta = rs.getDouble("puntaje_version");
        respuesta.versionPreguntaRespuesta = versionPregunta;

        Object idOpcion = rs.getObject("id_version_opcion");
        if (idOpcion != null) {
            VersionOpcion opcion = new VersionOpcion();
            opcion.setIdVersionOpcion(rs.getInt("id_version_opcion"));
            opcion.textoVersionOpcion = rs.getString("texto");
            opcion.esCorrectaVersionOpcion = rs.getBoolean("es_correcta");
            respuesta.versionOpcionRespuesta = opcion;
        }
        return respuesta;
    }

    /** Marca la opción seleccionada (para preguntas de selección). */
    public void seleccionarOpcion(Respuesta respuesta, VersionOpcion opcion) {
        actualizar("UPDATE respuesta SET id_version_opcion = ? WHERE id_respuesta = ?", ps -> {
            if (opcion == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, opcion.getIdVersionOpcion());
            }
            ps.setInt(2, respuesta.getIdRespuesta());
        });
        respuesta.versionOpcionRespuesta = opcion;
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private Respuesta mapear(ResultSet rs) throws SQLException {
        Respuesta respuesta = new Respuesta();
        respuesta.setIdRespuesta(rs.getInt("id_respuesta"));
        respuesta.contenidoRespuesta = rs.getString("contenido");
        respuesta.fechaRespuesta = Mapeos.fecha(rs, "fecha");
        respuesta.puntajeRespuesta = rs.getDouble("puntaje");
        respuesta.comentarioDocenteRespuesta = rs.getString("comentario_docente");
        return respuesta;
    }
}
