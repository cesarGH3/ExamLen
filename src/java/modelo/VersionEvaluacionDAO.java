package modelo;

import logica.Fechas;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import logica.Evaluacion;
import logica.RecursoNoEncontradoException;
import logica.ValidacionException;
import logica.VersionEvaluacion;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

public class VersionEvaluacionDAO extends DaoBase {

    private static final String COLUMNAS = "id_version_evaluacion, id_evaluacion, codigo, "
            + "fecha_generacion";

    /** Aleatoriedad de las versiones (RF-35): se usa SecureRandom, no el generador por defecto. */
    private static final SecureRandom AZAR = new SecureRandom();

    public VersionEvaluacionDAO(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * RF-35/RF-36: genera exactamente numero_versiones versiones NUEVAS, cada una con exactamente
     * numero_preguntas preguntas, conservando las anteriores. Solo con la evaluación LISTA (RF-34)
     * y todas las preguntas seleccionadas APROBADAS; la validación, la lectura de origen y la copia
     * ocurren bloqueadas dentro de la misma transacción (todo o nada).
     */
    public List<VersionEvaluacion> generarVersionEvaluacion(Evaluacion evaluacion) throws ValidacionException {
        try {
            return enTransaccion(c -> {
                exigirLista(c, evaluacion.getIdEvaluacion());
                List<Fuente> fuentes = cargarFuentes(c, evaluacion.getIdEvaluacion());
                Perfil perfil = construirPerfil(fuentes, evaluacion.numeroPreguntasEvaluacion);
                Map<String, Integer> desplazamientos = desplazamientosAleatorios(perfil.candidatos);
                String ultimo = ultimoCodigo(c, evaluacion.getIdEvaluacion());
                int totalVersiones = Math.max(1, evaluacion.numeroVersionesEvaluacion);
                List<VersionEvaluacion> generadas = new ArrayList<>();
                for (int v = 0; v < totalVersiones; v++) {
                    generadas.add(crearVersion(c, evaluacion, siguienteCodigo(ultimo, v + 1), v, perfil,
                            desplazamientos));
                }
                return generadas;
            });
        } catch (GeneracionInvalidaException e) {
            throw new ValidacionException(e.getErrores());
        }
    }

    /** Crea una única versión "A" con las preguntas actuales (cuando la evaluación aún no tiene versiones). */
    public VersionEvaluacion generarVersionOriginal(Evaluacion evaluacion) throws ValidacionException {
        try {
            return enTransaccion(c -> {
                exigirLista(c, evaluacion.getIdEvaluacion());
                List<Fuente> fuentes = cargarFuentes(c, evaluacion.getIdEvaluacion());
                Perfil perfil = construirPerfil(fuentes, evaluacion.numeroPreguntasEvaluacion);
                return crearVersion(c, evaluacion, "A", 0, perfil, desplazamientosAleatorios(perfil.candidatos));
            });
        } catch (GeneracionInvalidaException e) {
            throw new ValidacionException(e.getErrores());
        }
    }

    /** RF-34: solo una evaluación LISTA genera versiones; se comprueba bloqueando su fila. */
    private static void exigirLista(Connection c, int idEvaluacion) throws SQLException {
        List<String> estados = consultarListaCon(c, "SELECT estado FROM evaluacion WHERE id_evaluacion = ? FOR UPDATE",
                ps -> ps.setInt(1, idEvaluacion), rs -> rs.getString(1));
        if (estados.isEmpty()) {
            throw new GeneracionInvalidaException(List.of("La evaluación no existe."));
        }
        if (!"LISTA".equals(estados.get(0))) {
            throw new GeneracionInvalidaException(List.of(
                    "Solo se pueden generar versiones de una evaluación en estado LISTA."));
        }
    }

    /** Un punto de partida aleatorio por tipo de slot; las versiones se reparten desde ahí. */
    private static Map<String, Integer> desplazamientosAleatorios(Map<String, List<Fuente>> candidatos) {
        Map<String, Integer> desplazamientos = new HashMap<>();
        for (Map.Entry<String, List<Fuente>> entrada : candidatos.entrySet()) {
            desplazamientos.put(entrada.getKey(), AZAR.nextInt(entrada.getValue().size()));
        }
        return desplazamientos;
    }

    /** Código de la última versión existente (solo códigos de letras A, B… Z, AA…). */
    private String ultimoCodigo(Connection c, int idEvaluacion) throws SQLException {
        List<String> codigos = consultarListaCon(c,
                "SELECT codigo FROM version_evaluacion WHERE id_evaluacion = ?",
                ps -> ps.setInt(1, idEvaluacion), rs -> rs.getString("codigo"));
        String mayor = null;
        for (String codigo : codigos) {
            if (codigo != null && codigo.matches("[A-Z]+")
                    && (mayor == null || compararCodigos(codigo, mayor) > 0)) {
                mayor = codigo;
            }
        }
        return mayor;
    }

    /** A, B… Z, AA, AB…: primero por longitud y luego alfabético. */
    private static int compararCodigos(String a, String b) {
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return a.compareTo(b);
    }

    private static String siguienteCodigo(String ultimo, int paso) {
        String codigo = ultimo;
        for (int i = 0; i < paso; i++) {
            codigo = incrementarCodigo(codigo);
        }
        return codigo;
    }

    private static String incrementarCodigo(String codigo) {
        if (codigo == null || codigo.isEmpty()) {
            return "A";
        }
        char[] letras = codigo.toCharArray();
        int i = letras.length - 1;
        while (i >= 0) {
            if (letras[i] < 'Z') {
                letras[i]++;
                return new String(letras);
            }
            letras[i] = 'A';
            i--;
        }
        return "A" + new String(letras);
    }

    /** Error de negocio dentro de la transacción de generación; se traduce a ValidacionException. */
    private static final class GeneracionInvalidaException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final List<String> errores;

        GeneracionInvalidaException(List<String> errores) {
            super(String.join(" ", errores));
            this.errores = errores;
        }

        List<String> getErrores() {
            return errores;
        }
    }

    /**
     * Regla de integridad: una generación nueva solo puede usar preguntas APROBADAS en este momento.
     * Si alguna pregunta seleccionada no lo está, se rechaza la generación completa (sin filtrar en
     * silencio) y se informa al docente cuáles son.
     */
    private static void rechazarPreguntasNoAprobadas(List<Fuente> fuentes) {
        List<Fuente> noAprobadas = new ArrayList<>();
        for (Fuente fuente : fuentes) {
            if (!"APROBADA".equals(fuente.estado)) {
                noAprobadas.add(fuente);
            }
        }
        if (noAprobadas.isEmpty()) {
            return;
        }
        List<String> errores = new ArrayList<>();
        errores.add(PreguntaDAO.resumenPreguntasNoAprobadas("No se pueden generar versiones", noAprobadas.size()));
        for (Fuente fuente : noAprobadas) {
            errores.add(PreguntaDAO.detallePreguntaNoAprobada(fuente.idPregunta, fuente.estado, fuente.enunciado));
        }
        throw new GeneracionInvalidaException(errores);
    }

    private List<Fuente> cargarFuentes(Connection c, int idEvaluacion) throws SQLException {
        // FOR UPDATE: bloquea las preguntas fuente hasta el commit, de modo que no puedan pasar a
        // BORRADOR/ARCHIVADA entre la validación y la copia (o se genera todo, o no se genera nada).
        List<Fuente> fuentes = consultarListaCon(c,
                "SELECT p.id_pregunta, p.enunciado, p.estado, p.respuesta_esperada, "
                + "p.competencia, p.dificultad, p.tema, ep.puntaje_evaluacion_pregunta "
                + "FROM evaluacion_pregunta ep JOIN pregunta p ON p.id_pregunta = ep.id_pregunta "
                + "WHERE ep.id_evaluacion = ? ORDER BY ep.orden_evaluacion_pregunta, p.id_pregunta "
                + "FOR UPDATE",
                ps -> ps.setInt(1, idEvaluacion), rs -> {
                    Fuente fuente = new Fuente();
                    fuente.idPregunta = rs.getInt("id_pregunta");
                    fuente.enunciado = rs.getString("enunciado");
                    fuente.estado = rs.getString("estado");
                    fuente.respuestaEsperada = rs.getString("respuesta_esperada");
                    fuente.competencia = rs.getString("competencia");
                    fuente.dificultad = rs.getString("dificultad");
                    fuente.tema = rs.getString("tema");
                    fuente.puntaje = rs.getDouble("puntaje_evaluacion_pregunta");
                    return fuente;
                });
        rechazarPreguntasNoAprobadas(fuentes);
        if (fuentes.isEmpty()) {
            throw new GeneracionInvalidaException(List.of(
                    "Selecciona preguntas aprobadas antes de generar versiones."));
        }
        return fuentes;
    }

    /**
     * Perfil canónico de la versión (RF-35): el conjunto de "slots" que TODAS las versiones de la
     * operación comparten. Un slot es un tipo (tema, competencia, dificultad, puntaje); como todas
     * las versiones toman un representante de cada slot, quedan garantizadas la misma cantidad, las
     * mismas distribuciones de tema/competencia/dificultad y el mismo puntaje total.
     */
    private static final class Perfil {
        final List<Fuente> slots = new ArrayList<>();
        final Map<String, List<Fuente>> candidatos = new LinkedHashMap<>();
    }

    /**
     * Construye el perfil con exactamente numeroPreguntas slots. Se ordena el conjunto seleccionado
     * por (tema, competencia, dificultad, puntaje, id) y se muestrea sistemáticamente el índice
     * i*N/P, de modo que el perfil conserva la proporción del conjunto y ningún tipo aparece más
     * veces de las preguntas que existen de ese tipo (los índices muestreados son distintos).
     */
    private static Perfil construirPerfil(List<Fuente> fuentes, int numeroPreguntas) {
        if (numeroPreguntas < 1) {
            throw new GeneracionInvalidaException(List.of(
                    "Configura el número de preguntas por versión (mínimo 1)."));
        }
        if (fuentes.size() < numeroPreguntas) {
            throw new GeneracionInvalidaException(List.of("Faltan preguntas para completar la evaluación: hay "
                    + fuentes.size() + " seleccionadas y se piden " + numeroPreguntas + " por versión."));
        }
        List<Fuente> ordenadas = new ArrayList<>(fuentes);
        ordenadas.sort(Comparator.comparing((Fuente f) -> f.tema == null ? "" : f.tema)
                .thenComparing(f -> f.competencia == null ? "" : f.competencia)
                .thenComparing(f -> f.dificultad == null ? "" : f.dificultad)
                .thenComparingDouble(f -> f.puntaje)
                .thenComparingInt(f -> f.idPregunta));
        Perfil perfil = new Perfil();
        for (Fuente fuente : fuentes) {
            perfil.candidatos.computeIfAbsent(clave(fuente), k -> new ArrayList<>()).add(fuente);
        }
        Map<String, Integer> ocurrencias = new HashMap<>();
        for (int i = 0; i < numeroPreguntas; i++) {
            Fuente slot = ordenadas.get((int) ((long) i * ordenadas.size() / numeroPreguntas));
            perfil.slots.add(slot);
            ocurrencias.merge(clave(slot), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entrada : ocurrencias.entrySet()) {
            List<Fuente> pool = perfil.candidatos.get(entrada.getKey());
            if (pool == null || pool.size() < entrada.getValue()) {
                throw new GeneracionInvalidaException(List.of("No hay suficientes preguntas equivalentes para "
                        + "generar " + numeroPreguntas + " preguntas por versión manteniendo la distribución "
                        + "configurada (tipo " + entrada.getKey() + ")."));
            }
        }
        return perfil;
    }

    /** Tipo de slot de una pregunta: tema, competencia, dificultad y puntaje en la evaluación. */
    private static String clave(Fuente fuente) {
        return (fuente.tema == null ? "" : fuente.tema) + "|" + fuente.competencia + "|"
                + fuente.dificultad + "|" + fuente.puntaje;
    }

    private VersionEvaluacion crearVersion(Connection c, Evaluacion evaluacion, String codigo, int indiceVersion,
                                           Perfil perfil, Map<String, Integer> desplazamientos) throws SQLException {
        int idVersion = insertarCon(c, "INSERT INTO version_evaluacion (id_evaluacion, codigo) "
                + "VALUES (?, ?)", ps -> {
            ps.setInt(1, evaluacion.getIdEvaluacion());
            ps.setString(2, codigo);
        });

        // Un representante distinto por slot: para cada tipo se rota desde el desplazamiento
        // aleatorio y, si el perfil repite tipo, se avanza (nunca se repite la misma pregunta
        // dentro de la versión porque el perfil no pide más slots de los que el pool tiene).
        List<Fuente> elegidas = new ArrayList<>();
        Map<String, Integer> ocurrencia = new HashMap<>();
        for (Fuente slot : perfil.slots) {
            String clave = clave(slot);
            List<Fuente> pool = perfil.candidatos.get(clave);
            int k = ocurrencia.merge(clave, 1, Integer::sum) - 1;
            int indice = Math.floorMod(desplazamientos.get(clave) + indiceVersion + k, pool.size());
            elegidas.add(pool.get(indice));
        }
        // Orden aleatorio de las preguntas dentro de la versión (RF-35).
        Collections.shuffle(elegidas, AZAR);

        int slot = 1;
        for (Fuente elegida : elegidas) {
            final int ordenPregunta = slot;
            int idVersionPregunta = insertarCon(c, "INSERT INTO version_pregunta (id_version_evaluacion, "
                    + "id_pregunta, enunciado, respuesta_esperada, "
                    + "orden, puntaje) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
                ps.setInt(1, idVersion);
                ps.setInt(2, elegida.idPregunta);
                ps.setString(3, elegida.enunciado);
                ps.setString(4, elegida.respuestaEsperada);
                ps.setInt(5, ordenPregunta);
                ps.setDouble(6, elegida.puntaje);
            });

            List<String[]> opciones = consultarListaCon(c,
                    "SELECT texto, es_correcta FROM opcion_respuesta "
                    + "WHERE id_pregunta = ? ORDER BY id_opcion_respuesta",
                    ps -> ps.setInt(1, elegida.idPregunta), rs -> new String[]{
                        rs.getString("texto"),
                        String.valueOf(rs.getBoolean("es_correcta"))});
            // Orden aleatorio de las opciones dentro de la pregunta (RF-35).
            Collections.shuffle(opciones, AZAR);
            int ordenOpcion = 1;
            for (String[] opcion : opciones) {
                final int orden = ordenOpcion;
                insertarCon(c, "INSERT INTO version_opcion (id_version_pregunta, texto, "
                        + "es_correcta, orden) VALUES (?, ?, ?, ?)", ps -> {
                    ps.setInt(1, idVersionPregunta);
                    ps.setString(2, opcion[0]);
                    ps.setBoolean(3, Boolean.parseBoolean(opcion[1]));
                    ps.setInt(4, orden);
                });
                ordenOpcion++;
            }
            slot++;
        }

        VersionEvaluacion version = new VersionEvaluacion();
        version.setIdVersionEvaluacion(idVersion);
        version.codigoVersionEvaluacion = codigo;
        version.fechaGeneracionVersionEvaluacion = Fechas.ahora();
        return version;
    }

    /** PDF del examen (con espacio para responder); el curso es solo contexto de impresión (RF-39). */
    public byte[] generarPDFVersionEvaluacion(VersionEvaluacion version, String curso) {
        return construirPDF(version, false, curso);
    }

    /** PDF de la clave de respuestas (solo para el docente); mismo curso de contexto (RF-40). */
    public byte[] generarClaveRespuestasPDFVersionEvaluacion(VersionEvaluacion version, String curso) {
        return construirPDF(version, true, curso);
    }

    public Optional<VersionEvaluacion> buscarPorId(int idVersionEvaluacion) {
        return consultarUno("SELECT " + COLUMNAS + " FROM version_evaluacion WHERE id_version_evaluacion = ?",
                ps -> ps.setInt(1, idVersionEvaluacion), this::mapear);
    }

    /** Solo devuelve la versión si su evaluación pertenece al docente indicado. */
    public Optional<VersionEvaluacion> buscarPorIdDeDocente(int idVersionEvaluacion, int idDocente) {
        return consultarUno("SELECT v.id_version_evaluacion, v.id_evaluacion, v.codigo, "
                + "v.fecha_generacion FROM version_evaluacion v "
                + "JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion "
                + "WHERE v.id_version_evaluacion = ? AND e.id_docente = ?", ps -> {
            ps.setInt(1, idVersionEvaluacion);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    public List<VersionEvaluacion> listarPorEvaluacion(int idEvaluacion) {
        return consultarLista("SELECT " + COLUMNAS + " FROM version_evaluacion WHERE id_evaluacion = ? "
                + "ORDER BY id_version_evaluacion", ps -> ps.setInt(1, idEvaluacion), this::mapear);
    }

    public int contarVersionesDeEvaluacion(int idEvaluacion) {
        return contar("SELECT COUNT(*) FROM version_evaluacion WHERE id_evaluacion = ?",
                ps -> ps.setInt(1, idEvaluacion));
    }

    public boolean perteneceAEvaluacion(int idVersionEvaluacion, int idEvaluacion) {
        return existe("SELECT 1 FROM version_evaluacion WHERE id_version_evaluacion = ? AND id_evaluacion = ? "
                + "LIMIT 1", ps -> {
            ps.setInt(1, idVersionEvaluacion);
            ps.setInt(2, idEvaluacion);
        });
    }

    public int idEvaluacionDeVersion(int idVersionEvaluacion) {
        return consultarUno("SELECT id_evaluacion FROM version_evaluacion WHERE id_version_evaluacion = ?",
                ps -> ps.setInt(1, idVersionEvaluacion), rs -> rs.getInt("id_evaluacion")).orElse(0);
    }

    // ------------------------------------------------------------------ comparativa (RF-37)

    /** Distribución de una versión: cantidad, puntaje total y mapas por tema, competencia y dificultad. */
    public static final class Distribucion {
        public String codigo;
        public int cantidad;
        public double puntajeTotal;
        public final Map<String, Integer> porTema = new LinkedHashMap<>();
        public final Map<String, Integer> porCompetencia = new LinkedHashMap<>();
        public final Map<String, Integer> porDificultad = new LinkedHashMap<>();
    }

    /** Distribución por versión para el cuadro comparativo (tema/competencia/dificultad se leen del banco). */
    public List<Distribucion> listarDistribucionPorVersion(int idEvaluacion) {
        List<Distribucion> filas = consultarLista(
                "SELECT v.id_version_evaluacion, v.codigo, p.tema, p.competencia, "
                + "p.dificultad, COUNT(*) AS cantidad, SUM(vp.puntaje) AS puntaje "
                + "FROM version_evaluacion v "
                + "JOIN version_pregunta vp ON vp.id_version_evaluacion = v.id_version_evaluacion "
                + "JOIN pregunta p ON p.id_pregunta = vp.id_pregunta "
                + "WHERE v.id_evaluacion = ? "
                + "GROUP BY v.id_version_evaluacion, v.codigo, p.tema, p.competencia, p.dificultad "
                + "ORDER BY v.id_version_evaluacion, p.competencia, p.dificultad, p.tema",
                ps -> ps.setInt(1, idEvaluacion), rs -> {
                    Distribucion distribucion = new Distribucion();
                    distribucion.codigo = rs.getString("codigo");
                    distribucion.cantidad = rs.getInt("cantidad");
                    distribucion.puntajeTotal = rs.getDouble("puntaje");
                    distribucion.porTema.put(rs.getString("tema"), rs.getInt("cantidad"));
                    distribucion.porCompetencia.put(rs.getString("competencia"), rs.getInt("cantidad"));
                    distribucion.porDificultad.put(rs.getString("dificultad"), rs.getInt("cantidad"));
                    return distribucion;
                });
        Map<String, Distribucion> porCodigo = new LinkedHashMap<>();
        for (Distribucion fila : filas) {
            Distribucion acumulada = porCodigo.get(fila.codigo);
            if (acumulada == null) {
                porCodigo.put(fila.codigo, fila);
            } else {
                acumulada.cantidad += fila.cantidad;
                acumulada.puntajeTotal += fila.puntajeTotal;
                fila.porTema.forEach((clave, valor) -> acumulada.porTema.merge(clave, valor, Integer::sum));
                fila.porCompetencia.forEach((clave, valor) -> acumulada.porCompetencia.merge(clave, valor, Integer::sum));
                fila.porDificultad.forEach((clave, valor) -> acumulada.porDificultad.merge(clave, valor, Integer::sum));
            }
        }
        return new ArrayList<>(porCodigo.values());
    }

    // ------------------------------------------------------------------ PDF

    private static final class DatosVersion {
        String codigo;
        String titulo;
        String docente;
    }

    private static final class PreguntaPDF {
        String enunciado;
        String respuestaEsperada;
        double puntaje;
        List<String[]> opciones = new ArrayList<>();
    }

    private byte[] construirPDF(VersionEvaluacion version, boolean clave, String curso) {
        DatosVersion datos = consultarUno("SELECT v.codigo, e.titulo, "
                + "u.nombre FROM version_evaluacion v JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion "
                + "JOIN usuario u ON u.id_usuario = e.id_docente WHERE v.id_version_evaluacion = ?",
                ps -> ps.setInt(1, version.getIdVersionEvaluacion()), rs -> {
                    DatosVersion d = new DatosVersion();
                    d.codigo = rs.getString("codigo");
                    d.titulo = rs.getString("titulo");
                    d.docente = rs.getString("nombre");
                    return d;
                }).orElseThrow(() -> new RecursoNoEncontradoException("Versión no encontrada"));

        List<PreguntaPDF> preguntas = consultarLista("SELECT id_version_pregunta, enunciado, "
                + "respuesta_esperada, puntaje FROM version_pregunta "
                + "WHERE id_version_evaluacion = ? ORDER BY orden",
                ps -> ps.setInt(1, version.getIdVersionEvaluacion()), rs -> {
                    PreguntaPDF p = new PreguntaPDF();
                    p.enunciado = rs.getString("enunciado");
                    p.respuestaEsperada = rs.getString("respuesta_esperada");
                    p.puntaje = rs.getDouble("puntaje");
                    int idVersionPregunta = rs.getInt("id_version_pregunta");
                    p.opciones.addAll(consultarLista("SELECT texto, es_correcta "
                            + "FROM version_opcion WHERE id_version_pregunta = ? ORDER BY orden",
                            ps -> ps.setInt(1, idVersionPregunta), rso -> new String[]{
                                rso.getString("texto"),
                                String.valueOf(rso.getBoolean("es_correcta"))}));
                    return p;
                });

        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document documento = new Document(PageSize.LETTER, 56, 56, 80, 56);
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            String contextoCurso = curso == null || curso.isBlank() ? "" : "  ·  Curso: " + curso;
            escritor.setPageEvent(new PdfFormato(datos.titulo + contextoCurso + "  ·  Versión " + datos.codigo
                    + "  ·  Docente: " + datos.docente));
            documento.open();

            Font fuenteDatos = new Font(Font.HELVETICA, 11, Font.NORMAL);
            Font fuentePregunta = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font fuenteOpcion = new Font(Font.HELVETICA, 11, Font.NORMAL);

            if (!clave) {
                documento.add(new Paragraph("Nombre: ______________________________________   "
                        + "Identificación: ______________________   Fecha: ____________", fuenteDatos));
                documento.add(new Paragraph(" "));
            }

            int numero = 1;
            for (PreguntaPDF pregunta : preguntas) {
                PdfPTable bloque = PdfFormato.bloquePregunta();
                bloque.addCell(PdfFormato.celda(new Paragraph(numero + ". (" + puntajeTexto(pregunta.puntaje)
                        + " pts) " + pregunta.enunciado, fuentePregunta)));
                char letra = 'a';
                for (String[] opcion : pregunta.opciones) {
                    boolean correcta = Boolean.parseBoolean(opcion[1]);
                    bloque.addCell(PdfFormato.celda(new Paragraph("     " + letra + ") " + opcion[0]
                            + (clave && correcta ? "   (correcta)" : ""), fuenteOpcion)));
                    letra++;
                }
                if (pregunta.opciones.isEmpty()) {
                    if (clave && pregunta.respuestaEsperada != null) {
                        bloque.addCell(PdfFormato.celda(new Paragraph("     Respuesta esperada: "
                                + pregunta.respuestaEsperada, fuenteOpcion)));
                    } else if (!clave) {
                        bloque.addCell(PdfFormato.celda(new Paragraph("     Respuesta: "
                                + "____________________________________________________", fuenteOpcion)));
                    }
                }
                documento.add(bloque);
                numero++;
            }

            documento.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la versión.", e);
        }
    }

    private static String puntajeTexto(double puntaje) {
        if (puntaje == Math.floor(puntaje)) {
            return String.valueOf((int) puntaje);
        }
        return String.valueOf(puntaje);
    }

    // ------------------------------------------------------------------ generación

    private static final class Fuente {
        int idPregunta;
        String enunciado;
        String estado;
        String respuestaEsperada;
        String competencia;
        String dificultad;
        String tema;
        double puntaje;
    }

    private VersionEvaluacion mapear(ResultSet rs) throws SQLException {
        VersionEvaluacion version = new VersionEvaluacion();
        version.setIdVersionEvaluacion(rs.getInt("id_version_evaluacion"));
        version.codigoVersionEvaluacion = rs.getString("codigo");
        version.fechaGeneracionVersionEvaluacion = Mapeos.fecha(rs, "fecha_generacion");
        return version;
    }
}
