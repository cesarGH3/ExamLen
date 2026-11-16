package modelo;

import logica.OperacionNoPermitidaException;
import logica.Competencia;
import logica.CriteriosBusquedaPregunta;
import logica.Dificultad;
import logica.EstadoPregunta;
import logica.Evaluacion;
import logica.MaterialPractica;
import logica.NivelIngles;
import logica.OpcionRespuesta;
import logica.OrigenContenido;
import logica.Pregunta;
import logica.TipoPregunta;
import logica.ValidacionException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

public class PreguntaDAO extends DaoBase {

    public static final int MIN_OPCIONES = 2;

    private static final String CONSULTA_PREGUNTA = "SELECT p.id_pregunta, p.id_docente, p.id_banco_preguntas, p.enunciado, "
            + "p.tipo, p.nivel, p.dificultad, p.competencia, p.tema, "
            + "p.respuesta_esperada, p.estado, p.origen FROM pregunta p";
    private static final String CONSULTA_OPCIONES = "SELECT id_opcion_respuesta, texto, "
            + "es_correcta FROM opcion_respuesta WHERE id_pregunta = ? ORDER BY id_opcion_respuesta";

    public PreguntaDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Tipos que se responden eligiendo opciones (las enumeraciones del diagrama no tienen métodos). */
    public static boolean usaOpciones(TipoPregunta tipo) {
        return tipo == TipoPregunta.SELECCION_MULTIPLE || tipo == TipoPregunta.VERDADERO_FALSO;
    }

    /** RF-19: propietario obligatorio; banco opcional. */
    public Pregunta crearPregunta(int idDocente, Integer idBanco, String enunciado, TipoPregunta tipo,
            NivelIngles nivel, Dificultad dificultad, Competencia competencia, String tema) {
        return enTransaccion(conexion -> crearPregunta(conexion, idDocente, idBanco, enunciado,
                tipo, nivel, dificultad, competencia, tema));
    }

    private static Pregunta crearPregunta(Connection conexion, int idDocente, Integer idBanco,
            String enunciado, TipoPregunta tipo, NivelIngles nivel, Dificultad dificultad,
            Competencia competencia, String tema) throws SQLException {
        comprobarBanco(conexion, idDocente, idBanco);
        if (tipo == null || nivel == null || dificultad == null || competencia == null
                || enunciado == null || enunciado.strip().length() < 5 || enunciado.length() > 65535
                || tema == null || tema.strip().length() < 2 || tema.length() > 120) {
            throw new OperacionNoPermitidaException("Completa el enunciado y la clasificación de la pregunta.");
        }
        int identificador = insertarCon(conexion, "INSERT INTO pregunta (id_docente, id_banco_preguntas, "
                + "enunciado, tipo, nivel, dificultad, competencia, tema, estado, origen) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'BORRADOR', 'MANUAL')", sentencia -> {
            sentencia.setInt(1, idDocente);
            sentencia.setObject(2, idBanco, java.sql.Types.INTEGER);
            sentencia.setString(3, enunciado);
            sentencia.setString(4, tipo.name());
            sentencia.setString(5, nivel.name());
            sentencia.setString(6, dificultad.name());
            sentencia.setString(7, competencia.name());
            sentencia.setString(8, tema);
        });
        Pregunta pregunta = cargarPregunta(conexion, identificador, false);
        if (tipo == TipoPregunta.VERDADERO_FALSO) {
            insertarOpcion(conexion, pregunta, "Verdadero", false);
            insertarOpcion(conexion, pregunta, "Falso", false);
        }
        actualizarDisponibilidad(conexion, pregunta, false);
        return pregunta;
    }

    public Pregunta crearEnEvaluacion(int idDocente, int idEvaluacion, String enunciado, TipoPregunta tipo,
            NivelIngles nivel, Dificultad dificultad, Competencia competencia, String tema) {
        return enTransaccion(conexion -> {
            comprobarEvaluacion(conexion, idDocente, idEvaluacion);
            Pregunta pregunta = crearPregunta(conexion, idDocente, null, enunciado, tipo, nivel,
                    dificultad, competencia, tema);
            asociarEnEvaluacion(conexion, idEvaluacion, pregunta.consultarIdPregunta());
            return pregunta;
        });
    }

    public Pregunta crearEnMaterial(int idDocente, int idMaterial, String enunciado, TipoPregunta tipo,
            NivelIngles nivel, Dificultad dificultad, Competencia competencia, String tema) {
        return enTransaccion(conexion -> {
            comprobarMaterial(conexion, idDocente, idMaterial);
            Pregunta pregunta = crearPregunta(conexion, idDocente, null, enunciado, tipo, nivel,
                    dificultad, competencia, tema);
            asociarEnMaterial(conexion, idMaterial, pregunta.consultarIdPregunta());
            return pregunta;
        });
    }

    private static void comprobarBanco(Connection conexion, int idDocente, Integer idBanco) throws SQLException {
        if (idBanco != null && consultarUnoCon(conexion,
                "SELECT id_banco_preguntas FROM banco_preguntas WHERE id_banco_preguntas = ? AND id_docente = ? FOR SHARE",
                sentencia -> { sentencia.setInt(1, idBanco); sentencia.setInt(2, idDocente); },
                resultado -> resultado.getInt(1)).isEmpty()) {
            throw new OperacionNoPermitidaException("El banco no pertenece al docente.");
        }
    }

    private static void comprobarEvaluacion(Connection conexion, int idDocente, int idEvaluacion) throws SQLException {
        if (consultarUnoCon(conexion, "SELECT id_evaluacion FROM evaluacion WHERE id_evaluacion = ? "
                + "AND id_docente = ? AND estado <> 'ARCHIVADA' FOR UPDATE", sentencia -> {
            sentencia.setInt(1, idEvaluacion); sentencia.setInt(2, idDocente);
        }, resultado -> resultado.getInt(1)).isEmpty()) {
            throw new OperacionNoPermitidaException("La evaluación no está disponible para edición.");
        }
    }

    private static void asociarEnEvaluacion(Connection conexion, int idEvaluacion, int idPregunta) throws SQLException {
        int orden = consultarUnoCon(conexion, "SELECT COALESCE(MAX(orden_evaluacion_pregunta), 0) + 1 "
                + "FROM evaluacion_pregunta WHERE id_evaluacion = ?", sentencia -> sentencia.setInt(1, idEvaluacion),
                resultado -> resultado.getInt(1)).orElse(1);
        actualizarCon(conexion, "INSERT IGNORE INTO evaluacion_pregunta "
                + "(id_evaluacion, id_pregunta, orden_evaluacion_pregunta) VALUES (?, ?, ?)", sentencia -> {
            sentencia.setInt(1, idEvaluacion); sentencia.setInt(2, idPregunta); sentencia.setInt(3, orden);
        });
        actualizarCon(conexion, "UPDATE evaluacion SET estado = 'BORRADOR' WHERE id_evaluacion = ?",
                sentencia -> sentencia.setInt(1, idEvaluacion));
    }

    private static void comprobarMaterial(Connection conexion, int idDocente, int idMaterial) throws SQLException {
        if (consultarUnoCon(conexion, "SELECT id_material_practica FROM material_practica "
                + "WHERE id_material_practica = ? AND id_docente = ? AND estado <> 'ARCHIVADO' FOR UPDATE",
                sentencia -> { sentencia.setInt(1, idMaterial); sentencia.setInt(2, idDocente); },
                resultado -> resultado.getInt(1)).isEmpty()) {
            throw new OperacionNoPermitidaException("El material no está disponible para edición.");
        }
    }

    private static void asociarEnMaterial(Connection conexion, int idMaterial, int idPregunta) throws SQLException {
        actualizarCon(conexion, "INSERT IGNORE INTO material_pregunta (id_material_practica, id_pregunta) "
                + "VALUES (?, ?)", sentencia -> { sentencia.setInt(1, idMaterial); sentencia.setInt(2, idPregunta); });
        actualizarCon(conexion, "UPDATE material_practica SET estado = 'BORRADOR' WHERE id_material_practica = ?",
                sentencia -> sentencia.setInt(1, idMaterial));
    }

    public void organizarEnBanco(Pregunta pregunta, Integer idBanco) {
        enTransaccion(conexion -> {
            Pregunta actual = cargarPregunta(conexion, pregunta.consultarIdPregunta(), true);
            comprobarPropietario(actual, pregunta.idDocente);
            comprobarBanco(conexion, actual.idDocente, idBanco);
            actualizarCon(conexion, "UPDATE pregunta SET id_banco_preguntas = ? WHERE id_pregunta = ?",
                    sentencia -> { sentencia.setObject(1, idBanco, java.sql.Types.INTEGER);
                        sentencia.setInt(2, actual.consultarIdPregunta()); });
            return null;
        });
        pregunta.idBancoPreguntas = idBanco;
    }

    /** Copia explícita: reemplaza solo la asociación actual, nunca el original ni sus históricos. */
    public Pregunta copiarParaEvaluacion(int idDocente, int idEvaluacion, int idPregunta) {
        return enTransaccion(conexion -> {
            comprobarEvaluacion(conexion, idDocente, idEvaluacion);
            Pregunta original = cargarPregunta(conexion, idPregunta, true);
            comprobarPropietario(original, idDocente);
            if (original.estadoPregunta == EstadoPregunta.ARCHIVADA || consultarUnoCon(conexion,
                    "SELECT id_pregunta FROM evaluacion_pregunta WHERE id_evaluacion = ? AND id_pregunta = ? FOR UPDATE",
                    sentencia -> { sentencia.setInt(1, idEvaluacion); sentencia.setInt(2, idPregunta); },
                    resultado -> resultado.getInt(1)).isEmpty()) {
                throw new OperacionNoPermitidaException("La pregunta no está disponible para copiar en esta evaluación.");
            }
            int identificador = copiarContenidoPregunta(conexion, idPregunta);
            actualizarCon(conexion, "UPDATE evaluacion_pregunta SET id_pregunta = ? WHERE id_evaluacion = ? AND id_pregunta = ?",
                    sentencia -> { sentencia.setInt(1, identificador); sentencia.setInt(2, idEvaluacion); sentencia.setInt(3, idPregunta); });
            actualizarCon(conexion, "UPDATE evaluacion SET estado = 'BORRADOR' WHERE id_evaluacion = ?",
                    sentencia -> sentencia.setInt(1, idEvaluacion));
            Pregunta copia = cargarPregunta(conexion, identificador, false);
            actualizarDisponibilidad(conexion, copia, false);
            return copia;
        });
    }

    /** Copia contextual para material: reemplaza solo la asociación de ESTE material. */
    public Pregunta copiarParaMaterial(int idDocente, int idMaterial, int idPregunta) {
        return enTransaccion(conexion -> {
            comprobarMaterial(conexion, idDocente, idMaterial);
            Pregunta original = cargarPregunta(conexion, idPregunta, true);
            comprobarPropietario(original, idDocente);
            if (original.estadoPregunta == EstadoPregunta.ARCHIVADA || consultarUnoCon(conexion,
                    "SELECT id_pregunta FROM material_pregunta WHERE id_material_practica = ? AND id_pregunta = ? FOR UPDATE",
                    sentencia -> { sentencia.setInt(1, idMaterial); sentencia.setInt(2, idPregunta); },
                    resultado -> resultado.getInt(1)).isEmpty()) {
                throw new OperacionNoPermitidaException("La pregunta no está disponible para copiar en este material.");
            }
            int identificador = copiarContenidoPregunta(conexion, idPregunta);
            actualizarCon(conexion, "UPDATE material_pregunta SET id_pregunta = ? "
                    + "WHERE id_material_practica = ? AND id_pregunta = ?",
                    sentencia -> { sentencia.setInt(1, identificador); sentencia.setInt(2, idMaterial); sentencia.setInt(3, idPregunta); });
            actualizarCon(conexion, "UPDATE material_practica SET estado = 'BORRADOR' WHERE id_material_practica = ?",
                    sentencia -> sentencia.setInt(1, idMaterial));
            Pregunta copia = cargarPregunta(conexion, identificador, false);
            actualizarDisponibilidad(conexion, copia, false);
            return copia;
        });
    }

    private static int copiarContenidoPregunta(Connection conexion, int idPregunta) throws SQLException {
        int identificador = insertarCon(conexion, "INSERT INTO pregunta (id_docente, id_banco_preguntas, "
                + "id_solicitud_generacion, enunciado, tipo, nivel, dificultad, competencia, tema, respuesta_esperada, estado, origen) "
                + "SELECT id_docente, NULL, id_solicitud_generacion, enunciado, tipo, nivel, dificultad, competencia, "
                + "tema, respuesta_esperada, 'BORRADOR', origen FROM pregunta WHERE id_pregunta = ?",
                sentencia -> sentencia.setInt(1, idPregunta));
        actualizarCon(conexion, "INSERT INTO opcion_respuesta (id_pregunta, texto, es_correcta) "
                + "SELECT ?, texto, es_correcta FROM opcion_respuesta WHERE id_pregunta = ? ORDER BY id_opcion_respuesta",
                sentencia -> { sentencia.setInt(1, identificador); sentencia.setInt(2, idPregunta); });
        return identificador;
    }

    public List<Integer> consultarMaterialesPropios(Pregunta pregunta) {
        return consultarLista("SELECT m.id_material_practica FROM material_practica m "
                + "JOIN material_pregunta mp ON mp.id_material_practica = m.id_material_practica "
                + "WHERE mp.id_pregunta = ? AND m.id_docente = ? ORDER BY m.id_material_practica",
                sentencia -> { sentencia.setInt(1, pregunta.consultarIdPregunta()); sentencia.setInt(2, pregunta.idDocente); },
                resultado -> resultado.getInt(1));
    }

    public List<Integer> consultarEvaluacionesPropias(Pregunta pregunta) {
        return consultarLista("SELECT e.id_evaluacion FROM evaluacion e JOIN evaluacion_pregunta ep "
                + "ON ep.id_evaluacion = e.id_evaluacion WHERE ep.id_pregunta = ? AND e.id_docente = ?",
                sentencia -> { sentencia.setInt(1, pregunta.consultarIdPregunta()); sentencia.setInt(2, pregunta.idDocente); },
                resultado -> resultado.getInt(1));
    }

    /** Resultado de eliminar una pregunta del banco. */
    public enum ResultadoEliminacionPregunta {
        ELIMINADO, EN_USO, NO_ENCONTRADA
    }

    /**
     * RF-21/RNF-14: elimina físicamente SOLO si no hay ninguna referencia histórica
     * (versiones, evaluaciones, materiales o respuestas de práctica). La comprobación y el DELETE
     * ocurren en una transacción con la fila bloqueada; una referencia concurrente hace fallar la
     * FK y se traduce a EN_USO (nunca HTTP 500).
     */
    public ResultadoEliminacionPregunta eliminarSiNoUsada(Pregunta pregunta) {
        int idPregunta = pregunta.consultarIdPregunta();
        try {
            return enTransaccion(c -> {
                List<Integer> bloqueada = consultarListaCon(c,
                        "SELECT id_pregunta FROM pregunta WHERE id_pregunta = ? FOR UPDATE",
                        ps -> ps.setInt(1, idPregunta), rs -> rs.getInt(1));
                if (bloqueada.isEmpty()) {
                    return ResultadoEliminacionPregunta.NO_ENCONTRADA;
                }
                if (tieneReferencias(c, idPregunta)) {
                    return ResultadoEliminacionPregunta.EN_USO;
                }
                actualizarCon(c, "DELETE FROM pregunta WHERE id_pregunta = ?",
                        ps -> ps.setInt(1, idPregunta));
                return ResultadoEliminacionPregunta.ELIMINADO;
            });
        } catch (logica.RegistroEnUsoException e) {
            return ResultadoEliminacionPregunta.EN_USO;
        }
    }

    private static boolean tieneReferencias(Connection c, int idPregunta) throws SQLException {
        List<Integer> referencias = consultarListaCon(c, "SELECT 1 FROM version_pregunta WHERE id_pregunta = ? "
                + "UNION ALL SELECT 1 FROM evaluacion_pregunta WHERE id_pregunta = ? "
                + "UNION ALL SELECT 1 FROM material_pregunta WHERE id_pregunta = ? "
                + "UNION ALL SELECT 1 FROM respuesta_practica WHERE id_pregunta = ? LIMIT 1", ps -> {
            ps.setInt(1, idPregunta);
            ps.setInt(2, idPregunta);
            ps.setInt(3, idPregunta);
            ps.setInt(4, idPregunta);
        }, rs -> rs.getInt(1));
        return !referencias.isEmpty();
    }

    /** Bloquea la edición de una pregunta archivada; solo el flujo de restauración la devuelve al borrador. */
    static void exigirEditable(Pregunta pregunta) throws ValidacionException {
        if (pregunta.estadoPregunta == EstadoPregunta.ARCHIVADA) {
            throw new ValidacionException("La pregunta está archivada. Restáurala para editarla.");
        }
    }

    /** Recarga y bloquea el original; protege también las opciones y la clasificación histórica. */
    static void prepararEdicion(Connection conexion, Pregunta pregunta) throws SQLException {
        Pregunta actual = cargarPregunta(conexion, pregunta.consultarIdPregunta(), true);
        comprobarPropietario(actual, pregunta.idDocente);
        if (actual.estadoPregunta == EstadoPregunta.ARCHIVADA || estaProtegida(conexion, actual.consultarIdPregunta())) {
            throw new OperacionNoPermitidaException("La pregunta está protegida. Crea una copia desde la evaluación o el material para editarla.");
        }
        pregunta.estadoPregunta = actual.estadoPregunta;
        pregunta.origenPregunta = actual.origenPregunta;
    }

    private static void comprobarPropietario(Pregunta pregunta, int idDocente) {
        if (pregunta.idDocente != idDocente) {
            throw new OperacionNoPermitidaException("La pregunta no pertenece al docente.");
        }
    }

    private static boolean estaProtegida(Connection conexion, int identificador) throws SQLException {
        String referencias = "SELECT (EXISTS(SELECT 1 FROM version_pregunta WHERE id_pregunta = ?) "
                + "OR EXISTS(SELECT 1 FROM respuesta_practica WHERE id_pregunta = ?) "
                + "OR (SELECT COUNT(*) FROM evaluacion_pregunta WHERE id_pregunta = ?) "
                + "+ (SELECT COUNT(*) FROM material_pregunta WHERE id_pregunta = ?) > 1 "
                + "OR EXISTS(SELECT 1 FROM evaluacion_pregunta ep JOIN evaluacion e ON e.id_evaluacion = ep.id_evaluacion "
                + "WHERE ep.id_pregunta = ? AND e.estado = 'ARCHIVADA') "
                + "OR EXISTS(SELECT 1 FROM material_pregunta mp JOIN material_practica m ON m.id_material_practica = mp.id_material_practica "
                + "WHERE mp.id_pregunta = ? AND (m.estado <> 'BORRADOR' OR EXISTS(SELECT 1 FROM intento_practica ip "
                + "WHERE ip.id_material_practica = m.id_material_practica))))";
        return consultarUnoCon(conexion, referencias, sentencia -> {
            for (int indice = 1; indice <= 6; indice++) sentencia.setInt(indice, identificador);
        }, resultado -> resultado.getBoolean(1)).orElse(true);
    }

    public boolean estaProtegida(Pregunta pregunta) {
        return enTransaccion(conexion -> estaProtegida(conexion, pregunta.consultarIdPregunta()));
    }

    static Pregunta cargarPregunta(Connection conexion, int identificador, boolean bloquear) throws SQLException {
        Pregunta pregunta = consultarUnoCon(conexion, CONSULTA_PREGUNTA + " WHERE p.id_pregunta = ?"
                + (bloquear ? " FOR UPDATE" : ""), sentencia -> sentencia.setInt(1, identificador),
                PreguntaDAO::mapear).orElseThrow(() -> new logica.RecursoNoEncontradoException("Pregunta no encontrada"));
        pregunta.opcionesPregunta.addAll(consultarListaCon(conexion, CONSULTA_OPCIONES,
                sentencia -> sentencia.setInt(1, identificador), PreguntaDAO::mapearOpcion));
        return pregunta;
    }

    /** RF-22: solo MANUAL se aprueba automáticamente. Se calcula sobre datos persistidos. */
    static void actualizarDisponibilidad(Connection conexion, Pregunta pregunta, boolean contenidoModificado) throws SQLException {
        Pregunta actual = cargarPregunta(conexion, pregunta.consultarIdPregunta(), false);
        EstadoPregunta estado = actual.estadoPregunta;
        if (estado != EstadoPregunta.ARCHIVADA) {
            if (actual.origenPregunta == OrigenContenido.MANUAL) {
                estado = faltantesPregunta(actual).isEmpty() ? EstadoPregunta.APROBADA : EstadoPregunta.BORRADOR;
            } else if (contenidoModificado) {
                estado = EstadoPregunta.BORRADOR;
            }
        }
        final EstadoPregunta nuevoEstado = estado;
        actualizarCon(conexion, "UPDATE pregunta SET estado = ? WHERE id_pregunta = ?", sentencia -> {
            sentencia.setString(1, nuevoEstado.name()); sentencia.setInt(2, actual.consultarIdPregunta());
        });
        pregunta.estadoPregunta = estado;
        if (contenidoModificado || estado != EstadoPregunta.APROBADA) {
            actualizarCon(conexion, "UPDATE evaluacion e JOIN evaluacion_pregunta ep ON ep.id_evaluacion = e.id_evaluacion "
                    + "SET e.estado = 'BORRADOR' WHERE ep.id_pregunta = ? AND e.estado = 'LISTA'",
                    sentencia -> sentencia.setInt(1, actual.consultarIdPregunta()));
        }
    }

    public void actualizarPregunta(Pregunta pregunta, String enunciado, NivelIngles nivel, Dificultad dificultad,
            Competencia competencia, String tema) throws ValidacionException {
        exigirEditable(pregunta);
        enTransaccion(conexion -> {
            prepararEdicion(conexion, pregunta);
            actualizarCon(conexion, "UPDATE pregunta SET enunciado = ?, nivel = ?, dificultad = ?, "
                    + "competencia = ?, tema = ? WHERE id_pregunta = ?", sentencia -> {
                sentencia.setString(1, enunciado); sentencia.setString(2, nivel == null ? null : nivel.name());
                sentencia.setString(3, dificultad == null ? null : dificultad.name());
                sentencia.setString(4, competencia == null ? null : competencia.name());
                sentencia.setString(5, tema); sentencia.setInt(6, pregunta.consultarIdPregunta());
            });
            actualizarDisponibilidad(conexion, pregunta, true);
            return null;
        });
        pregunta.enunciadoPregunta = enunciado;
        pregunta.nivelPregunta = nivel;
        pregunta.dificultadPregunta = dificultad;
        pregunta.competenciaPregunta = competencia;
        pregunta.temaPregunta = tema;
    }

    public boolean validarPregunta(Pregunta pregunta) {
        return faltantesPregunta(pregunta).isEmpty();
    }

    /** Detalle de lo que falta para aprobar (lo muestra la pantalla del docente). */
    public static List<String> faltantesPregunta(Pregunta pregunta) {
        List<String> errores = new ArrayList<>();
        if (pregunta.enunciadoPregunta == null || pregunta.enunciadoPregunta.isBlank()) {
            errores.add("Escribe el enunciado.");
        }
        if (pregunta.tipoPregunta == null || pregunta.nivelPregunta == null || pregunta.dificultadPregunta == null
                || pregunta.competenciaPregunta == null || pregunta.temaPregunta == null || pregunta.temaPregunta.isBlank()) {
            errores.add("Completa el tipo y la clasificación.");
        }
        if (pregunta.tipoPregunta == null) return errores;
        for (OpcionRespuesta opcion : pregunta.opcionesPregunta) {
            if (opcion.textoOpcionRespuesta == null || opcion.textoOpcionRespuesta.isBlank()) errores.add("Completa el texto de las opciones.");
        }
        long correctas = pregunta.opcionesPregunta.stream().filter(o -> o.esCorrectaOpcionRespuesta).count();
        switch (pregunta.tipoPregunta) {
            case SELECCION_MULTIPLE:
                if (pregunta.opcionesPregunta.size() < MIN_OPCIONES) {
                    errores.add("Agrega al menos " + MIN_OPCIONES + " opciones.");
                }
                if (correctas != 1) {
                    errores.add("Marca exactamente una opción como correcta.");
                }
                break;
            case VERDADERO_FALSO:
                if (pregunta.opcionesPregunta.size() != 2
                        || pregunta.opcionesPregunta.stream().noneMatch(opcion -> "Verdadero".equalsIgnoreCase(opcion.textoOpcionRespuesta))
                        || pregunta.opcionesPregunta.stream().noneMatch(opcion -> "Falso".equalsIgnoreCase(opcion.textoOpcionRespuesta))) {
                    errores.add("Verdadero/falso debe tener exactamente dos opciones (Verdadero y Falso).");
                }
                if (correctas != 1) {
                    errores.add("Indica si la respuesta correcta es Verdadero o Falso.");
                }
                break;
            case RESPUESTA_CORTA:
                if (respuestasAceptadas(pregunta).isEmpty()) {
                    errores.add("Escribe la respuesta esperada.");
                }
                break;
            default:
                break;
        }
        return errores;
    }

    public void aprobarPregunta(Pregunta pregunta) throws ValidacionException {
        if (pregunta.estadoPregunta != EstadoPregunta.BORRADOR) {
            throw new ValidacionException("Solo se pueden aprobar preguntas en borrador.");
        }
        List<String> errores = faltantesPregunta(pregunta);
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
        enTransaccion(conexion -> {
            Pregunta actual = cargarPregunta(conexion, pregunta.consultarIdPregunta(), true);
            comprobarPropietario(actual, pregunta.idDocente);
            if (actual.estadoPregunta != EstadoPregunta.BORRADOR || !faltantesPregunta(actual).isEmpty()) {
                throw new OperacionNoPermitidaException("La pregunta cambió o está incompleta. Recarga y revísala.");
            }
            actualizarCon(conexion, "UPDATE pregunta SET estado = 'APROBADA' WHERE id_pregunta = ?",
                    sentencia -> sentencia.setInt(1, actual.consultarIdPregunta()));
            return null;
        });
        pregunta.estadoPregunta = EstadoPregunta.APROBADA;
    }

    public void archivarPregunta(Pregunta pregunta) throws ValidacionException {
        if (pregunta.estadoPregunta == EstadoPregunta.ARCHIVADA) {
            throw new ValidacionException("La pregunta ya está archivada.");
        }
        enTransaccion(conexion -> {
            actualizarCon(conexion, "UPDATE pregunta SET estado = 'ARCHIVADA' WHERE id_pregunta = ?",
                    sentencia -> sentencia.setInt(1, pregunta.consultarIdPregunta()));
            actualizarCon(conexion, "UPDATE evaluacion e JOIN evaluacion_pregunta ep "
                    + "ON ep.id_evaluacion = e.id_evaluacion SET e.estado = 'BORRADOR' "
                    + "WHERE ep.id_pregunta = ? AND e.estado = 'LISTA'",
                    sentencia -> sentencia.setInt(1, pregunta.consultarIdPregunta()));
            return null;
        });
        pregunta.estadoPregunta = EstadoPregunta.ARCHIVADA;
    }

    /** Devuelve la pregunta archivada al borrador (lo usa la pantalla del docente). */
    public void restaurarPregunta(Pregunta pregunta) throws ValidacionException {
        if (pregunta.estadoPregunta != EstadoPregunta.ARCHIVADA) {
            throw new ValidacionException("Solo se pueden restaurar preguntas archivadas.");
        }
        enTransaccion(conexion -> {
            Pregunta actual = cargarPregunta(conexion, pregunta.consultarIdPregunta(), true);
            comprobarPropietario(actual, pregunta.idDocente);
            if (actual.estadoPregunta != EstadoPregunta.ARCHIVADA) throw new OperacionNoPermitidaException("La pregunta ya fue restaurada.");
            actualizarCon(conexion, "UPDATE pregunta SET estado = 'BORRADOR' WHERE id_pregunta = ?",
                    sentencia -> sentencia.setInt(1, pregunta.consultarIdPregunta()));
            actualizarDisponibilidad(conexion, pregunta, false);
            return null;
        });
    }

    /**
     * Respuesta corta (respuestas aceptadas) o guía de calificación para la abierta.
     * Si la pregunta está aprobada y el valor cambia realmente, vuelve a borrador en la misma
     * sentencia (APROBADA representa el contenido que fue validado). Un valor idéntico no la invalida.
     */
    public void definirRespuestaEsperada(Pregunta pregunta, String respuesta) throws ValidacionException {
        if (usaOpciones(pregunta.tipoPregunta)) {
            throw new ValidacionException("Este tipo de pregunta se responde eligiendo una opción.");
        }
        exigirEditable(pregunta);
        String valor = (respuesta == null || respuesta.isBlank()) ? null : respuesta;
        enTransaccion(conexion -> {
            prepararEdicion(conexion, pregunta);
            actualizarCon(conexion, "UPDATE pregunta SET respuesta_esperada = ? WHERE id_pregunta = ?", sentencia -> {
                sentencia.setString(1, valor); sentencia.setInt(2, pregunta.consultarIdPregunta());
            });
            actualizarDisponibilidad(conexion, pregunta, true);
            return null;
        });
        pregunta.respuestaEsperadaPregunta = valor;
    }

    public void seleccionarPregunta(Pregunta pregunta, Evaluacion evaluacion) {
        enTransaccion(conexion -> {
            comprobarEvaluacion(conexion, pregunta.idDocente, evaluacion.consultarIdEvaluacion());
            Pregunta actual = cargarPregunta(conexion, pregunta.consultarIdPregunta(), true);
            comprobarPropietario(actual, pregunta.idDocente);
            if (actual.estadoPregunta != EstadoPregunta.APROBADA) {
                throw new OperacionNoPermitidaException("Solo se reutilizan preguntas aprobadas.");
            }
            asociarEnEvaluacion(conexion, evaluacion.consultarIdEvaluacion(), actual.consultarIdPregunta());
            return null;
        });
        if (!evaluacion.preguntasEvaluacion.contains(pregunta)) evaluacion.preguntasEvaluacion.add(pregunta);
    }

    public void quitarPregunta(Pregunta pregunta, Evaluacion evaluacion) {
        actualizar("DELETE FROM evaluacion_pregunta WHERE id_evaluacion = ? AND id_pregunta = ?", ps -> {
            ps.setInt(1, evaluacion.consultarIdEvaluacion());
            ps.setInt(2, pregunta.consultarIdPregunta());
        });
        evaluacion.preguntasEvaluacion.remove(pregunta);
    }

    public void asociarPregunta(Pregunta pregunta, MaterialPractica materialPractica) {
        enTransaccion(conexion -> {
            int propietario = consultarUnoCon(conexion,
                    "SELECT id_docente FROM material_practica WHERE id_material_practica = ? AND estado <> 'ARCHIVADO' FOR UPDATE",
                    sentencia -> sentencia.setInt(1, materialPractica.consultarIdMaterialPractica()), resultado -> resultado.getInt(1))
                    .orElseThrow(() -> new OperacionNoPermitidaException("Material no editable."));
            Pregunta actual = cargarPregunta(conexion, pregunta.consultarIdPregunta(), true);
            comprobarPropietario(actual, propietario);
            if (actual.estadoPregunta != EstadoPregunta.APROBADA) throw new OperacionNoPermitidaException("Pregunta no aprobada.");
            actualizarCon(conexion, "INSERT IGNORE INTO material_pregunta (id_material_practica, id_pregunta) VALUES (?, ?)",
                    sentencia -> { sentencia.setInt(1, materialPractica.consultarIdMaterialPractica()); sentencia.setInt(2, actual.consultarIdPregunta()); });
            actualizarCon(conexion, "UPDATE material_practica SET estado = 'BORRADOR' WHERE id_material_practica = ?",
                    sentencia -> sentencia.setInt(1, materialPractica.consultarIdMaterialPractica()));
            return null;
        });
        materialPractica.estadoMaterialPractica = logica.EstadoMaterial.BORRADOR;
        if (!materialPractica.preguntasMaterialPractica.contains(pregunta)) materialPractica.preguntasMaterialPractica.add(pregunta);
    }

    public void desasociarPregunta(Pregunta pregunta, MaterialPractica materialPractica) {
        actualizar("DELETE FROM material_pregunta WHERE id_material_practica = ? AND id_pregunta = ?", ps -> {
            ps.setInt(1, materialPractica.consultarIdMaterialPractica());
            ps.setInt(2, pregunta.consultarIdPregunta());
        });
        materialPractica.preguntasMaterialPractica.remove(pregunta);
    }

    /** Preguntas seleccionadas de una evaluación, con sus opciones cargadas. */
    public List<Pregunta> listarPorEvaluacion(int idEvaluacion) {
        List<Pregunta> preguntas = consultarLista(CONSULTA_PREGUNTA + " JOIN evaluacion_pregunta ep "
                + "ON ep.id_pregunta = p.id_pregunta WHERE ep.id_evaluacion = ? "
                + "ORDER BY ep.orden_evaluacion_pregunta, p.id_pregunta",
                ps -> ps.setInt(1, idEvaluacion), PreguntaDAO::mapear);
        for (Pregunta pregunta : preguntas) {
            pregunta.opcionesPregunta.addAll(consultarLista(CONSULTA_OPCIONES,
                    ps -> ps.setInt(1, pregunta.consultarIdPregunta()), PreguntaDAO::mapearOpcion));
        }
        return preguntas;
    }

    /** Preguntas de una evaluación que no están aprobadas (integridad antes de LISTA o de generar). */
    public List<Pregunta> listarNoAprobadasDeEvaluacion(int idEvaluacion) {
        return consultarLista(CONSULTA_PREGUNTA + " JOIN evaluacion_pregunta ep ON ep.id_pregunta = p.id_pregunta "
                + "WHERE ep.id_evaluacion = ? AND p.estado <> 'APROBADA' "
                + "ORDER BY ep.orden_evaluacion_pregunta, p.id_pregunta",
                ps -> ps.setInt(1, idEvaluacion), PreguntaDAO::mapear);
    }

    /** Resumen legible para el docente cuando hay preguntas seleccionadas que no están aprobadas. */
    public static String resumenPreguntasNoAprobadas(String accion, int cantidad) {
        return accion + ": " + cantidad + " pregunta(s) seleccionada(s) ya no están aprobadas. "
                + "Revísalas, apruébalas nuevamente o quítalas de la evaluación.";
    }

    /** Detalle legible (id, estado y enunciado corto) de una pregunta no aprobada. */
    public static String detallePreguntaNoAprobada(int idPregunta, String estado, String enunciado) {
        String texto = enunciado == null ? "" : enunciado.replace('\n', ' ').strip();
        if (texto.length() > 80) {
            texto = texto.substring(0, 80).strip() + "…";
        }
        return "Pregunta " + idPregunta + " (" + estado + "): " + texto;
    }

    /** Errores completos (resumen + detalle) a partir de las preguntas no aprobadas ya cargadas. */
    public static List<String> erroresPreguntasNoAprobadas(String accion, List<Pregunta> noAprobadas) {
        List<String> errores = new ArrayList<>();
        errores.add(resumenPreguntasNoAprobadas(accion, noAprobadas.size()));
        for (Pregunta pregunta : noAprobadas) {
            errores.add(detallePreguntaNoAprobada(pregunta.consultarIdPregunta(),
                    pregunta.estadoPregunta == null ? "" : pregunta.estadoPregunta.name(),
                    pregunta.enunciadoPregunta));
        }
        return errores;
    }

    /** Preguntas aprobadas propias del docente (para seleccionarlas en una evaluación). */
    public List<Pregunta> listarAprobadasDeDocente(int idDocente) {
        return consultarLista(CONSULTA_PREGUNTA + " "
                + "WHERE p.id_docente = ? AND p.estado = 'APROBADA' "
                + "ORDER BY p.id_pregunta DESC",
                ps -> ps.setInt(1, idDocente), PreguntaDAO::mapear);
    }

    /**
     * RF-24: búsqueda combinable (texto + banco + tipo + nivel + dificultad + competencia + tema +
     * estado + origen) en una única consulta parametrizada sobre las preguntas del docente. El texto
     * busca en enunciado y tema, es parcial, insensible a mayúsculas por la collation y los
     * comodines del usuario se escapan de forma literal (ESCAPE '!').
     */
    public List<Pregunta> buscarDeDocente(int idDocente, CriteriosBusquedaPregunta criterios) {
        StringBuilder sql = new StringBuilder(CONSULTA_PREGUNTA)
                .append(" ")
                .append("WHERE p.id_docente = ?");
        List<String> parametros = new ArrayList<>();
        parametros.add(String.valueOf(idDocente));
        if (criterios.idBanco != null) {
            sql.append(" AND p.id_banco_preguntas = ?");
            parametros.add(String.valueOf(criterios.idBanco));
        }
        if (criterios.texto != null && !criterios.texto.isBlank()) {
            sql.append(" AND (p.enunciado LIKE ? ESCAPE '!' OR p.tema LIKE ? ESCAPE '!')");
            String patron = "%" + escaparLike(criterios.texto.strip()) + "%";
            parametros.add(patron);
            parametros.add(patron);
        }
        if (criterios.tipo != null) {
            sql.append(" AND p.tipo = ?");
            parametros.add(criterios.tipo.name());
        }
        if (criterios.nivel != null) {
            sql.append(" AND p.nivel = ?");
            parametros.add(criterios.nivel.name());
        }
        if (criterios.dificultad != null) {
            sql.append(" AND p.dificultad = ?");
            parametros.add(criterios.dificultad.name());
        }
        if (criterios.competencia != null) {
            sql.append(" AND p.competencia = ?");
            parametros.add(criterios.competencia.name());
        }
        if (criterios.tema != null && !criterios.tema.isBlank()) {
            sql.append(" AND p.tema = ?");
            parametros.add(criterios.tema.strip());
        }
        if (criterios.estado != null) {
            sql.append(" AND p.estado = ?");
            parametros.add(criterios.estado.name());
        }
        if (criterios.origen != null) {
            sql.append(" AND p.origen = ?");
            parametros.add(criterios.origen.name());
        }
        if (!criterios.excluirIds.isEmpty()) {
            sql.append(" AND p.id_pregunta NOT IN (");
            for (int i = 0; i < criterios.excluirIds.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
                parametros.add(String.valueOf(criterios.excluirIds.get(i)));
            }
            sql.append(")");
        }
        sql.append(" ORDER BY p.id_pregunta DESC");
        return consultarLista(sql.toString(), ps -> {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setString(i + 1, parametros.get(i));
            }
        }, PreguntaDAO::mapear);
    }

    /** Comodines de LIKE escapados con '!' para que el texto se busque de forma literal. */
    static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Preguntas propias del docente que pueden usarse como base (no archivadas). */
    public List<Pregunta> listarDeDocente(int idDocente) {
        return consultarLista(CONSULTA_PREGUNTA + " "
                + "WHERE p.id_docente = ? AND p.estado <> 'ARCHIVADA' "
                + "ORDER BY p.id_pregunta DESC",
                ps -> ps.setInt(1, idDocente), PreguntaDAO::mapear);
    }

    /** Preguntas creadas por una solicitud de generación (trazabilidad RF-26/27/28). */
    public int contarPreguntasDeSolicitud(int idSolicitudGeneracion) {
        return contar("SELECT COUNT(*) FROM pregunta WHERE id_solicitud_generacion = ?",
                ps -> ps.setInt(1, idSolicitudGeneracion));
    }

    /** Orden (por grupo de equivalencia) de cada pregunta seleccionada. */
    public Map<Integer, Integer> ordenDeEvaluacion(int idEvaluacion) {
        Map<Integer, Integer> orden = new HashMap<>();
        consultarLista("SELECT id_pregunta, orden_evaluacion_pregunta FROM evaluacion_pregunta "
                + "WHERE id_evaluacion = ?", ps -> ps.setInt(1, idEvaluacion), rs -> {
            orden.put(rs.getInt(1), rs.getInt(2));
            return null;
        });
        return orden;
    }

    /** Puntaje (por grupo de equivalencia) de cada pregunta seleccionada. */
    public Map<Integer, Double> puntajeDeEvaluacion(int idEvaluacion) {
        Map<Integer, Double> puntaje = new HashMap<>();
        consultarLista("SELECT id_pregunta, puntaje_evaluacion_pregunta FROM evaluacion_pregunta "
                + "WHERE id_evaluacion = ?", ps -> ps.setInt(1, idEvaluacion), rs -> {
            puntaje.put(rs.getInt(1), rs.getDouble(2));
            return null;
        });
        return puntaje;
    }

    /** Asigna el puntaje a todo un grupo de equivalencia. */
    public void asignarPuntajeGrupoEnEvaluacion(int idEvaluacion, NivelIngles nivel, Competencia competencia,
                                                Dificultad dificultad, double puntaje) {
        actualizar("UPDATE evaluacion_pregunta ep JOIN pregunta p ON p.id_pregunta = ep.id_pregunta "
                + "SET ep.puntaje_evaluacion_pregunta = ? WHERE ep.id_evaluacion = ? "
                + "AND p.nivel = ? AND p.competencia = ? AND p.dificultad = ?", ps -> {
            ps.setDouble(1, puntaje);
            ps.setInt(2, idEvaluacion);
            ps.setString(3, nivel.name());
            ps.setString(4, competencia.name());
            ps.setString(5, dificultad.name());
        });
    }

    /** Asigna el orden a todo un grupo de equivalencia. */
    public void asignarOrdenGrupoEnEvaluacion(int idEvaluacion, NivelIngles nivel, Competencia competencia,
                                              Dificultad dificultad, int orden) {
        actualizar("UPDATE evaluacion_pregunta ep JOIN pregunta p ON p.id_pregunta = ep.id_pregunta "
                + "SET ep.orden_evaluacion_pregunta = ? WHERE ep.id_evaluacion = ? "
                + "AND p.nivel = ? AND p.competencia = ? AND p.dificultad = ?", ps -> {
            ps.setInt(1, orden);
            ps.setInt(2, idEvaluacion);
            ps.setString(3, nivel.name());
            ps.setString(4, competencia.name());
            ps.setString(5, dificultad.name());
        });
    }

    public Optional<Pregunta> buscarPorId(int idPregunta) {
        Optional<Pregunta> pregunta = consultarUno(CONSULTA_PREGUNTA + " WHERE id_pregunta = ?",
                ps -> ps.setInt(1, idPregunta), PreguntaDAO::mapear);
        pregunta.ifPresent(p -> p.opcionesPregunta.addAll(
                consultarLista(CONSULTA_OPCIONES, ps -> ps.setInt(1, idPregunta), PreguntaDAO::mapearOpcion)));
        return pregunta;
    }

    public List<Pregunta> listarPorBanco(int idBanco, EstadoPregunta filtro) {
        String sql = CONSULTA_PREGUNTA + " WHERE id_banco_preguntas = ?" + (filtro == null ? "" : " AND estado = ?")
                + " ORDER BY id_pregunta DESC";
        return consultarLista(sql, ps -> {
            ps.setInt(1, idBanco);
            if (filtro != null) {
                ps.setString(2, filtro.name());
            }
        }, PreguntaDAO::mapear);
    }

    public int contarPreguntasDeBanco(int idBanco) {
        return contar("SELECT COUNT(*) FROM pregunta WHERE id_banco_preguntas = ?", ps -> ps.setInt(1, idBanco));
    }

    /** Marca la pregunta como generada por IA (el docente la revisa y aprueba). */
    public void marcarOrigenIa(int idPregunta) {
        actualizar("UPDATE pregunta SET origen = 'GENERADO_IA', estado = 'BORRADOR' WHERE id_pregunta = ?",
                ps -> ps.setInt(1, idPregunta));
    }

    public Integer idBancoDePregunta(int idPregunta) {
        return consultarUno("SELECT id_banco_preguntas FROM pregunta WHERE id_pregunta = ?",
                ps -> ps.setInt(1, idPregunta), rs -> rs.getObject(1) == null ? null : rs.getInt(1)).orElse(null);
    }

    /** Verifica directamente el propietario de la pregunta. */
    public boolean esDeDocente(int idPregunta, int idDocente) {
        return existe("SELECT 1 FROM pregunta p "
                + "WHERE p.id_pregunta = ? AND p.id_docente = ? LIMIT 1", ps -> {
            ps.setInt(1, idPregunta);
            ps.setInt(2, idDocente);
        });
    }

    /** Respuestas aceptadas de una respuesta corta (separadas por punto y coma). */
    public static List<String> respuestasAceptadas(Pregunta pregunta) {
        List<String> aceptadas = new ArrayList<>();
        if (pregunta.respuestaEsperadaPregunta != null) {
            for (String parte : pregunta.respuestaEsperadaPregunta.split(";")) {
                if (!parte.isBlank()) {
                    aceptadas.add(parte.strip());
                }
            }
        }
        return aceptadas;
    }

    static void insertarOpcion(Connection c, Pregunta pregunta, String texto, boolean esCorrecta) throws SQLException {
        int id = insertarCon(c, "INSERT INTO opcion_respuesta (id_pregunta, texto, "
                + "es_correcta) VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, pregunta.consultarIdPregunta());
            ps.setString(2, texto);
            ps.setBoolean(3, esCorrecta);
        });
        OpcionRespuesta opcion = new OpcionRespuesta();
        opcion.establecerIdOpcionRespuesta(id);
        opcion.textoOpcionRespuesta = texto;
        opcion.esCorrectaOpcionRespuesta = esCorrecta;
        pregunta.opcionesPregunta.add(opcion);
    }

    static OpcionRespuesta mapearOpcion(ResultSet rs) throws SQLException {
        OpcionRespuesta opcion = new OpcionRespuesta();
        opcion.establecerIdOpcionRespuesta(rs.getInt("id_opcion_respuesta"));
        opcion.textoOpcionRespuesta = rs.getString("texto");
        opcion.esCorrectaOpcionRespuesta = rs.getBoolean("es_correcta");
        return opcion;
    }

    private static Pregunta mapear(ResultSet rs) throws SQLException {
        Pregunta pregunta = new Pregunta();
        pregunta.establecerIdPregunta(rs.getInt("id_pregunta"));
        pregunta.idDocente = rs.getInt("id_docente");
        pregunta.idBancoPreguntas = rs.getObject("id_banco_preguntas") == null ? null : rs.getInt("id_banco_preguntas");
        pregunta.enunciadoPregunta = rs.getString("enunciado");
        pregunta.tipoPregunta = enumeracion(rs, "tipo", TipoPregunta.class);
        pregunta.nivelPregunta = enumeracion(rs, "nivel", NivelIngles.class);
        pregunta.dificultadPregunta = enumeracion(rs, "dificultad", Dificultad.class);
        pregunta.competenciaPregunta = enumeracion(rs, "competencia", Competencia.class);
        pregunta.temaPregunta = rs.getString("tema");
        pregunta.respuestaEsperadaPregunta = rs.getString("respuesta_esperada");
        pregunta.estadoPregunta = enumeracion(rs, "estado", EstadoPregunta.class);
        pregunta.origenPregunta = enumeracion(rs, "origen", OrigenContenido.class);
        return pregunta;
    }
}
