package modelo;

import logica.BancoPreguntas;
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

    private static final String SELECT = "SELECT p.id_pregunta, p.id_banco_preguntas, p.enunciado, "
            + "p.tipo, p.nivel, p.dificultad, p.competencia, p.tema, "
            + "p.respuesta_esperada, p.estado, p.origen FROM pregunta p";
    private static final String SELECT_OPCIONES = "SELECT id_opcion_respuesta, texto, "
            + "es_correcta FROM opcion_respuesta WHERE id_pregunta = ? ORDER BY id_opcion_respuesta";

    public PreguntaDAO(DataSource dataSource) {
        super(dataSource);
    }

    /** Tipos que se responden eligiendo opciones (las enumeraciones del diagrama no tienen métodos). */
    public static boolean usaOpciones(TipoPregunta tipo) {
        return tipo == TipoPregunta.SELECCION_MULTIPLE || tipo == TipoPregunta.VERDADERO_FALSO;
    }

    public Pregunta crearPregunta(BancoPreguntas banco, String enunciado, TipoPregunta tipo, NivelIngles nivel,
                                  Dificultad dificultad, Competencia competencia, String tema) {
        Pregunta pregunta = new Pregunta();
        pregunta.enunciadoPregunta = enunciado;
        pregunta.tipoPregunta = tipo;
        pregunta.nivelPregunta = nivel;
        pregunta.dificultadPregunta = dificultad;
        pregunta.competenciaPregunta = competencia;
        pregunta.temaPregunta = tema;
        pregunta.estadoPregunta = EstadoPregunta.BORRADOR;
        pregunta.origenPregunta = OrigenContenido.MANUAL;

        enTransaccion(c -> {
            int id = insertarCon(c, "INSERT INTO pregunta (id_banco_preguntas, enunciado, tipo, "
                    + "nivel, dificultad, competencia, tema, "
                    + "respuesta_esperada, estado, origen) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'BORRADOR', 'MANUAL')", ps -> {
                ps.setInt(1, banco.getIdBancoPreguntas());
                ps.setString(2, enunciado);
                ps.setString(3, tipo.name());
                ps.setString(4, nivel == null ? null : nivel.name());
                ps.setString(5, dificultad == null ? null : dificultad.name());
                ps.setString(6, competencia == null ? null : competencia.name());
                ps.setString(7, tema);
            });
            pregunta.setIdPregunta(id);
            if (tipo == TipoPregunta.VERDADERO_FALSO) {
                insertarOpcion(c, pregunta, "Verdadero", false);
                insertarOpcion(c, pregunta, "Falso", false);
            }
            return null;
        });
        return pregunta;
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
        int idPregunta = pregunta.getIdPregunta();
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

    /**
     * Regla de dominio (RF-22): modificar el contenido de una pregunta APROBADA la devuelve a BORRADOR.
     * Se ejecuta dentro de la transacción de la modificación para que estado y contenido cambien juntos:
     * si la modificación falla, la pregunta conserva su estado anterior.
     */
    static void desaprobarSiAprobada(Connection c, Pregunta pregunta) throws SQLException {
        if (pregunta.estadoPregunta == EstadoPregunta.APROBADA) {
            actualizarCon(c, "UPDATE pregunta SET estado = 'BORRADOR' "
                    + "WHERE id_pregunta = ? AND estado = 'APROBADA'",
                    ps -> ps.setInt(1, pregunta.getIdPregunta()));
            pregunta.estadoPregunta = EstadoPregunta.BORRADOR;
        }
    }

    /** Guarda los datos de la pregunta. Editar una aprobada la devuelve a borrador. */
    public void actualizarPregunta(Pregunta pregunta, String enunciado, NivelIngles nivel, Dificultad dificultad,
                                   Competencia competencia, String tema) throws ValidacionException {
        exigirEditable(pregunta);
        if (pregunta.estadoPregunta == EstadoPregunta.APROBADA) {
            pregunta.estadoPregunta = EstadoPregunta.BORRADOR;
        }
        pregunta.enunciadoPregunta = enunciado;
        pregunta.nivelPregunta = nivel;
        pregunta.dificultadPregunta = dificultad;
        pregunta.competenciaPregunta = competencia;
        pregunta.temaPregunta = tema;
        actualizar("UPDATE pregunta SET enunciado = ?, nivel = ?, dificultad = ?, "
                + "competencia = ?, tema = ?, estado = ? WHERE id_pregunta = ?", ps -> {
            ps.setString(1, enunciado);
            ps.setString(2, nivel == null ? null : nivel.name());
            ps.setString(3, dificultad == null ? null : dificultad.name());
            ps.setString(4, competencia == null ? null : competencia.name());
            ps.setString(5, tema);
            ps.setString(6, pregunta.estadoPregunta.name());
            ps.setInt(7, pregunta.getIdPregunta());
        });
    }

    public boolean validarPregunta(Pregunta pregunta) {
        return faltantesPregunta(pregunta).isEmpty();
    }

    /** Detalle de lo que falta para aprobar (lo muestra la pantalla del docente). */
    public List<String> faltantesPregunta(Pregunta pregunta) {
        List<String> errores = new ArrayList<>();
        if (pregunta.enunciadoPregunta == null || pregunta.enunciadoPregunta.isBlank()) {
            errores.add("Escribe el enunciado.");
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
                if (pregunta.opcionesPregunta.size() != 2) {
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
        actualizar("UPDATE pregunta SET estado = 'APROBADA' WHERE id_pregunta = ?",
                ps -> ps.setInt(1, pregunta.getIdPregunta()));
        pregunta.estadoPregunta = EstadoPregunta.APROBADA;
    }

    public void archivarPregunta(Pregunta pregunta) throws ValidacionException {
        if (pregunta.estadoPregunta == EstadoPregunta.ARCHIVADA) {
            throw new ValidacionException("La pregunta ya está archivada.");
        }
        actualizar("UPDATE pregunta SET estado = 'ARCHIVADA' WHERE id_pregunta = ?",
                ps -> ps.setInt(1, pregunta.getIdPregunta()));
        pregunta.estadoPregunta = EstadoPregunta.ARCHIVADA;
    }

    /** Devuelve la pregunta archivada al borrador (lo usa la pantalla del docente). */
    public void restaurarPregunta(Pregunta pregunta) throws ValidacionException {
        if (pregunta.estadoPregunta != EstadoPregunta.ARCHIVADA) {
            throw new ValidacionException("Solo se pueden restaurar preguntas archivadas.");
        }
        actualizar("UPDATE pregunta SET estado = 'BORRADOR' WHERE id_pregunta = ?",
                ps -> ps.setInt(1, pregunta.getIdPregunta()));
        pregunta.estadoPregunta = EstadoPregunta.BORRADOR;
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
        if (java.util.Objects.equals(valor, pregunta.respuestaEsperadaPregunta)) {
            return;
        }
        if (pregunta.estadoPregunta == EstadoPregunta.APROBADA) {
            pregunta.estadoPregunta = EstadoPregunta.BORRADOR;
        }
        actualizar("UPDATE pregunta SET respuesta_esperada = ?, estado = ? WHERE id_pregunta = ?", ps -> {
            if (valor == null) {
                ps.setNull(1, java.sql.Types.VARCHAR);
            } else {
                ps.setString(1, valor);
            }
            ps.setString(2, pregunta.estadoPregunta.name());
            ps.setInt(3, pregunta.getIdPregunta());
        });
        pregunta.respuestaEsperadaPregunta = valor;
    }

    public void seleccionarPregunta(Pregunta pregunta, Evaluacion evaluacion) {
        int orden = consultarUno("SELECT COALESCE(MAX(orden_evaluacion_pregunta), 0) + 1 FROM evaluacion_pregunta "
                + "WHERE id_evaluacion = ?", ps -> ps.setInt(1, evaluacion.getIdEvaluacion()), rs -> rs.getInt(1))
                .orElse(1);
        actualizar("INSERT IGNORE INTO evaluacion_pregunta (id_evaluacion, id_pregunta, orden_evaluacion_pregunta) "
                + "VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, evaluacion.getIdEvaluacion());
            ps.setInt(2, pregunta.getIdPregunta());
            ps.setInt(3, orden);
        });
        if (!evaluacion.preguntasEvaluacion.contains(pregunta)) {
            evaluacion.preguntasEvaluacion.add(pregunta);
        }
    }

    public void quitarPregunta(Pregunta pregunta, Evaluacion evaluacion) {
        actualizar("DELETE FROM evaluacion_pregunta WHERE id_evaluacion = ? AND id_pregunta = ?", ps -> {
            ps.setInt(1, evaluacion.getIdEvaluacion());
            ps.setInt(2, pregunta.getIdPregunta());
        });
        evaluacion.preguntasEvaluacion.remove(pregunta);
    }

    public void asociarPregunta(Pregunta pregunta, MaterialPractica materialPractica) {
        actualizar("INSERT IGNORE INTO material_pregunta (id_material_practica, id_pregunta) "
                + "VALUES (?, ?)", ps -> {
            ps.setInt(1, materialPractica.getIdMaterialPractica());
            ps.setInt(2, pregunta.getIdPregunta());
        });
        if (!materialPractica.preguntasMaterialPractica.contains(pregunta)) {
            materialPractica.preguntasMaterialPractica.add(pregunta);
        }
    }

    public void desasociarPregunta(Pregunta pregunta, MaterialPractica materialPractica) {
        actualizar("DELETE FROM material_pregunta WHERE id_material_practica = ? AND id_pregunta = ?", ps -> {
            ps.setInt(1, materialPractica.getIdMaterialPractica());
            ps.setInt(2, pregunta.getIdPregunta());
        });
        materialPractica.preguntasMaterialPractica.remove(pregunta);
    }

    /** Preguntas seleccionadas de una evaluación, con sus opciones cargadas. */
    public List<Pregunta> listarPorEvaluacion(int idEvaluacion) {
        List<Pregunta> preguntas = consultarLista(SELECT + " JOIN evaluacion_pregunta ep "
                + "ON ep.id_pregunta = p.id_pregunta WHERE ep.id_evaluacion = ? "
                + "ORDER BY ep.orden_evaluacion_pregunta, p.id_pregunta",
                ps -> ps.setInt(1, idEvaluacion), this::mapear);
        for (Pregunta pregunta : preguntas) {
            pregunta.opcionesPregunta.addAll(consultarLista(SELECT_OPCIONES,
                    ps -> ps.setInt(1, pregunta.getIdPregunta()), PreguntaDAO::mapearOpcion));
        }
        return preguntas;
    }

    /** Preguntas de una evaluación que no están aprobadas (integridad antes de LISTA o de generar). */
    public List<Pregunta> listarNoAprobadasDeEvaluacion(int idEvaluacion) {
        return consultarLista(SELECT + " JOIN evaluacion_pregunta ep ON ep.id_pregunta = p.id_pregunta "
                + "WHERE ep.id_evaluacion = ? AND p.estado <> 'APROBADA' "
                + "ORDER BY ep.orden_evaluacion_pregunta, p.id_pregunta",
                ps -> ps.setInt(1, idEvaluacion), this::mapear);
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
            errores.add(detallePreguntaNoAprobada(pregunta.getIdPregunta(),
                    pregunta.estadoPregunta == null ? "" : pregunta.estadoPregunta.name(),
                    pregunta.enunciadoPregunta));
        }
        return errores;
    }

    /** Preguntas aprobadas de todos los bancos del docente (para seleccionarlas en una evaluación). */
    public List<Pregunta> listarAprobadasDeDocente(int idDocente) {
        return consultarLista(SELECT + " JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "WHERE b.id_docente = ? AND p.estado = 'APROBADA' "
                + "ORDER BY b.nombre, p.id_pregunta DESC",
                ps -> ps.setInt(1, idDocente), this::mapear);
    }

    /**
     * RF-24: búsqueda combinable (texto + banco + tipo + nivel + dificultad + competencia + tema +
     * estado + origen) en una única consulta parametrizada sobre los bancos del docente. El texto
     * busca en enunciado y tema, es parcial, insensible a mayúsculas por la collation y los
     * comodines del usuario se escapan de forma literal (ESCAPE '!').
     */
    public List<Pregunta> buscarDeDocente(int idDocente, CriteriosBusquedaPregunta criterios) {
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas ")
                .append("WHERE b.id_docente = ?");
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
        sql.append(" ORDER BY b.nombre, p.id_pregunta DESC");
        return consultarLista(sql.toString(), ps -> {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setString(i + 1, parametros.get(i));
            }
        }, this::mapear);
    }

    /** Comodines de LIKE escapados con '!' para que el texto se busque de forma literal. */
    static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Preguntas de los bancos del docente que pueden usarse como base (no archivadas). */
    public List<Pregunta> listarDeDocente(int idDocente) {
        return consultarLista(SELECT + " JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "WHERE b.id_docente = ? AND p.estado <> 'ARCHIVADA' "
                + "ORDER BY b.nombre, p.id_pregunta DESC",
                ps -> ps.setInt(1, idDocente), this::mapear);
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
        Optional<Pregunta> pregunta = consultarUno(SELECT + " WHERE id_pregunta = ?",
                ps -> ps.setInt(1, idPregunta), this::mapear);
        pregunta.ifPresent(p -> p.opcionesPregunta.addAll(
                consultarLista(SELECT_OPCIONES, ps -> ps.setInt(1, idPregunta), PreguntaDAO::mapearOpcion)));
        return pregunta;
    }

    public List<Pregunta> listarPorBanco(int idBanco, EstadoPregunta filtro) {
        String sql = SELECT + " WHERE id_banco_preguntas = ?" + (filtro == null ? "" : " AND estado = ?")
                + " ORDER BY id_pregunta DESC";
        return consultarLista(sql, ps -> {
            ps.setInt(1, idBanco);
            if (filtro != null) {
                ps.setString(2, filtro.name());
            }
        }, this::mapear);
    }

    public int contarPreguntasDeBanco(int idBanco) {
        return contar("SELECT COUNT(*) FROM pregunta WHERE id_banco_preguntas = ?", ps -> ps.setInt(1, idBanco));
    }

    /** Marca la pregunta como generada por IA (el docente la revisa y aprueba). */
    public void marcarOrigenIa(int idPregunta) {
        actualizar("UPDATE pregunta SET origen = 'GENERADO_IA' WHERE id_pregunta = ?",
                ps -> ps.setInt(1, idPregunta));
    }

    public int idBancoDePregunta(int idPregunta) {
        return consultarUno("SELECT id_banco_preguntas FROM pregunta WHERE id_pregunta = ?",
                ps -> ps.setInt(1, idPregunta), rs -> rs.getInt(1)).orElse(-1);
    }

    /** Verifica que la pregunta pertenezca a un banco del docente indicado. */
    public boolean esDeDocente(int idPregunta, int idDocente) {
        return existe("SELECT 1 FROM pregunta p JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas "
                + "WHERE p.id_pregunta = ? AND b.id_docente = ? LIMIT 1", ps -> {
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
            ps.setInt(1, pregunta.getIdPregunta());
            ps.setString(2, texto);
            ps.setBoolean(3, esCorrecta);
        });
        OpcionRespuesta opcion = new OpcionRespuesta();
        opcion.setIdOpcionRespuesta(id);
        opcion.textoOpcionRespuesta = texto;
        opcion.esCorrectaOpcionRespuesta = esCorrecta;
        pregunta.opcionesPregunta.add(opcion);
    }

    static OpcionRespuesta mapearOpcion(ResultSet rs) throws SQLException {
        OpcionRespuesta opcion = new OpcionRespuesta();
        opcion.setIdOpcionRespuesta(rs.getInt("id_opcion_respuesta"));
        opcion.textoOpcionRespuesta = rs.getString("texto");
        opcion.esCorrectaOpcionRespuesta = rs.getBoolean("es_correcta");
        return opcion;
    }

    private Pregunta mapear(ResultSet rs) throws SQLException {
        Pregunta pregunta = new Pregunta();
        pregunta.setIdPregunta(rs.getInt("id_pregunta"));
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
