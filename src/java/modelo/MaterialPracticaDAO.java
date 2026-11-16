package modelo;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import logica.Curso;
import logica.Docente;
import logica.EstadoMaterial;
import logica.MaterialPractica;
import logica.NivelIngles;
import logica.OrigenContenido;
import logica.Pregunta;
import logica.ValidacionException;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public class MaterialPracticaDAO extends DaoBase {

    private static final String COLUMNAS = "id_material_practica, id_docente, titulo, "
            + "descripcion, nivel, estado, "
            + "origen";

    public MaterialPracticaDAO(DataSource dataSource) {
        super(dataSource);
    }

    public MaterialPractica crearMaterialPractica(Docente docente, String titulo, String descripcion,
            NivelIngles nivel) {
        MaterialPractica material = new MaterialPractica();
        material.tituloMaterialPractica = titulo;
        material.descripcionMaterialPractica = descripcion;
        material.nivelMaterialPractica = nivel;
        material.estadoMaterialPractica = EstadoMaterial.BORRADOR;
        material.origenMaterialPractica = OrigenContenido.MANUAL;
        int id = insertar("INSERT INTO material_practica (id_docente, titulo, "
                + "descripcion, nivel, estado, origen) "
                + "VALUES (?, ?, ?, ?, 'BORRADOR', 'MANUAL')", ps -> {
            ps.setInt(1, docente.consultarIdUsuario());
            ps.setString(2, titulo);
            ps.setString(3, descripcion);
            ps.setString(4, nivel.name());
        });
        material.establecerIdMaterialPractica(id);
        return material;
    }

    /**
     * Publicar exige al menos una pregunta y que todas estén aprobadas (RF-30, RF-57).
     * La validación y el cambio de estado ocurren en una transacción que bloquea las
     * preguntas para que no cambien de estado entre la comprobación y la publicación.
     */
    public void publicarMaterialPractica(MaterialPractica material) throws ValidacionException {
        int[] resultado = enTransaccion(c -> {
            List<String> estados = consultarListaCon(c, "SELECT p.estado FROM material_pregunta mp "
                    + "JOIN pregunta p ON p.id_pregunta = mp.id_pregunta "
                    + "WHERE mp.id_material_practica = ? FOR UPDATE",
                    ps -> ps.setInt(1, material.consultarIdMaterialPractica()), rs -> rs.getString("estado"));
            if (estados.isEmpty()) {
                return new int[]{-1, 0};
            }
            int cursos = consultarUnoCon(c, "SELECT COUNT(*) FROM curso_material "
                    + "WHERE id_material_practica = ?", sentencia -> sentencia.setInt(1, material.consultarIdMaterialPractica()),
                    fila -> fila.getInt(1)).orElse(0);
            if (cursos == 0) {
                return new int[]{-2, estados.size()};
            }
            int noAprobadas = 0;
            for (String estado : estados) {
                if (!"APROBADA".equals(estado)) {
                    noAprobadas++;
                }
            }
            if (noAprobadas > 0) {
                return new int[]{noAprobadas, estados.size()};
            }
            actualizarCon(c, "UPDATE material_practica SET estado = 'PUBLICADO' "
                    + "WHERE id_material_practica = ?", ps -> ps.setInt(1, material.consultarIdMaterialPractica()));
            return new int[]{0, estados.size()};
        });
        if (resultado[0] == -1) {
            throw new ValidacionException("Agrega al menos una pregunta antes de publicar el material.");
        }
        if (resultado[0] == -2) {
            throw new ValidacionException("Asigna al menos un curso antes de publicar el material.");
        }
        if (resultado[0] > 0) {
            throw new ValidacionException("No se puede publicar el material: " + resultado[0] + " de "
                    + resultado[1] + " preguntas no están aprobadas. Apruébalas o quítalas para publicar.");
        }
        material.estadoMaterialPractica = EstadoMaterial.PUBLICADO;
    }

    public void archivarMaterialPractica(MaterialPractica material) {
        cambiarEstado(material, "ARCHIVADO", EstadoMaterial.ARCHIVADO);
    }

    /** Editar un material publicado lo devuelve a borrador (misma regla que preguntas y evaluaciones). */
    public void volverBorrador(MaterialPractica material) {
        if (material.estadoMaterialPractica == EstadoMaterial.PUBLICADO) {
            cambiarEstado(material, "BORRADOR", EstadoMaterial.BORRADOR);
        }
    }

    public void marcarOrigenIa(MaterialPractica material) {
        actualizar("UPDATE material_practica SET origen = 'GENERADO_IA' "
                + "WHERE id_material_practica = ?", ps -> ps.setInt(1, material.consultarIdMaterialPractica()));
        material.origenMaterialPractica = OrigenContenido.GENERADO_IA;
    }

    // ------------------------------------------------------------------ preguntas y cursos

    public void agregarPregunta(MaterialPractica material, int idPregunta) {
        enTransaccion(conexion -> {
            Integer propietario = consultarUnoCon(conexion,
                    "SELECT id_docente FROM material_practica WHERE id_material_practica = ? AND estado <> 'ARCHIVADO' FOR UPDATE",
                    sentencia -> sentencia.setInt(1, material.consultarIdMaterialPractica()), resultado -> resultado.getInt(1))
                    .orElseThrow(() -> new logica.OperacionNoPermitidaException("Material no editable."));
            Pregunta pregunta = PreguntaDAO.cargarPregunta(conexion, idPregunta, true);
            if (pregunta.idDocente != propietario || pregunta.estadoPregunta != logica.EstadoPregunta.APROBADA) {
                throw new logica.OperacionNoPermitidaException("Selecciona una pregunta propia aprobada.");
            }
            actualizarCon(conexion, "UPDATE material_practica SET estado = 'BORRADOR' WHERE id_material_practica = ?",
                    sentencia -> sentencia.setInt(1, material.consultarIdMaterialPractica()));
            actualizarCon(conexion, "INSERT IGNORE INTO material_pregunta (id_material_practica, id_pregunta) VALUES (?, ?)",
                    sentencia -> { sentencia.setInt(1, material.consultarIdMaterialPractica()); sentencia.setInt(2, idPregunta); });
            return null;
        });
        material.estadoMaterialPractica = logica.EstadoMaterial.BORRADOR;
    }

    public void quitarPregunta(MaterialPractica material, int idPregunta) {
        volverBorrador(material);
        actualizar("DELETE FROM material_pregunta WHERE id_material_practica = ? AND id_pregunta = ?", ps -> {
            ps.setInt(1, material.consultarIdMaterialPractica());
            ps.setInt(2, idPregunta);
        });
    }

    public List<Pregunta> listarPreguntas(int idMaterialPractica) {
        List<Pregunta> preguntas = consultarLista("SELECT p.id_pregunta, p.id_docente, p.id_banco_preguntas, p.enunciado, "
                + "p.tipo, p.nivel, p.dificultad, p.competencia, p.tema, "
                + "p.respuesta_esperada, p.estado, p.origen FROM material_pregunta mpp "
                + "JOIN pregunta p ON p.id_pregunta = mpp.id_pregunta WHERE mpp.id_material_practica = ? "
                + "ORDER BY p.id_pregunta", ps -> ps.setInt(1, idMaterialPractica), rs -> {
                    Pregunta pregunta = new Pregunta();
                    pregunta.establecerIdPregunta(rs.getInt("id_pregunta"));
                    pregunta.idDocente = rs.getInt("id_docente");
                    pregunta.idBancoPreguntas = rs.getObject("id_banco_preguntas") == null ? null : rs.getInt("id_banco_preguntas");
                    pregunta.enunciadoPregunta = rs.getString("enunciado");
                    pregunta.tipoPregunta = enumeracion(rs, "tipo", logica.TipoPregunta.class);
                    pregunta.nivelPregunta = enumeracion(rs, "nivel", NivelIngles.class);
                    pregunta.dificultadPregunta = enumeracion(rs, "dificultad", logica.Dificultad.class);
                    pregunta.competenciaPregunta = enumeracion(rs, "competencia", logica.Competencia.class);
                    pregunta.temaPregunta = rs.getString("tema");
                    pregunta.respuestaEsperadaPregunta = rs.getString("respuesta_esperada");
                    pregunta.estadoPregunta = enumeracion(rs, "estado", logica.EstadoPregunta.class);
                    pregunta.origenPregunta = enumeracion(rs, "origen", OrigenContenido.class);
                    return pregunta;
                });
        for (Pregunta pregunta : preguntas) {
            pregunta.opcionesPregunta.addAll(consultarLista("SELECT id_opcion_respuesta, texto, "
                    + "es_correcta FROM opcion_respuesta WHERE id_pregunta = ? "
                    + "ORDER BY id_opcion_respuesta", ps -> ps.setInt(1, pregunta.consultarIdPregunta()),
                    PreguntaDAO::mapearOpcion));
        }
        return preguntas;
    }

    public int contarPreguntas(int idMaterialPractica) {
        return contar("SELECT COUNT(*) FROM material_pregunta WHERE id_material_practica = ?",
                ps -> ps.setInt(1, idMaterialPractica));
    }

    /** Preguntas del material que no están aprobadas (avisos del docente y defensa del estudiante). */
    public int contarPreguntasNoAprobadas(int idMaterialPractica) {
        return contar("SELECT COUNT(*) FROM material_pregunta mp "
                + "JOIN pregunta p ON p.id_pregunta = mp.id_pregunta "
                + "WHERE mp.id_material_practica = ? AND p.estado <> 'APROBADA'",
                ps -> ps.setInt(1, idMaterialPractica));
    }

    /** Solo para avisos al docente: indica si la pregunta pertenece a algún material PUBLICADO. */
    public boolean estaEnMaterialPublicado(int idPregunta) {
        return existe("SELECT 1 FROM material_pregunta mp "
                + "JOIN material_practica m ON m.id_material_practica = mp.id_material_practica "
                + "WHERE mp.id_pregunta = ? AND m.estado = 'PUBLICADO' LIMIT 1",
                ps -> ps.setInt(1, idPregunta));
    }

    /**
     * Un material es utilizable por estudiantes solo si tiene al menos una pregunta aprobada
     * y ninguna en otro estado (RF-30, RF-57). Cubre datos inconsistentes por cualquier vía.
     */
    public boolean esIntegro(int idMaterialPractica) {
        return contar("SELECT (EXISTS (SELECT 1 FROM material_pregunta mp "
                + "JOIN pregunta p ON p.id_pregunta = mp.id_pregunta "
                + "WHERE mp.id_material_practica = ? AND p.estado = 'APROBADA') "
                + "AND NOT EXISTS (SELECT 1 FROM material_pregunta mp "
                + "JOIN pregunta p ON p.id_pregunta = mp.id_pregunta "
                + "WHERE mp.id_material_practica = ? AND p.estado <> 'APROBADA'))", ps -> {
                    ps.setInt(1, idMaterialPractica);
                    ps.setInt(2, idMaterialPractica);
                }) > 0;
    }

    public void agregarCurso(MaterialPractica material, int idCurso) {
        volverBorrador(material);
        actualizar("INSERT IGNORE INTO curso_material (id_curso, id_material_practica) VALUES (?, ?)", ps -> {
            ps.setInt(1, idCurso);
            ps.setInt(2, material.consultarIdMaterialPractica());
        });
    }

    public void quitarCurso(MaterialPractica material, int idCurso) {
        volverBorrador(material);
        actualizar("DELETE FROM curso_material WHERE id_curso = ? AND id_material_practica = ?", ps -> {
            ps.setInt(1, idCurso);
            ps.setInt(2, material.consultarIdMaterialPractica());
        });
    }

    public List<Curso> listarCursos(int idMaterialPractica) {
        return consultarLista("SELECT c.id_curso, c.nombre FROM curso_material cmp "
                + "JOIN curso c ON c.id_curso = cmp.id_curso WHERE cmp.id_material_practica = ? "
                + "ORDER BY c.nombre", ps -> ps.setInt(1, idMaterialPractica), rs -> {
                    Curso curso = new Curso();
                    curso.establecerIdCurso(rs.getInt("id_curso"));
                    curso.nombreCurso = rs.getString("nombre");
                    return curso;
                });
    }

    /**
     * Materiales publicados e íntegros ofrecidos a los cursos donde el estudiante tiene inscripción
     * activa, opcionalmente filtrados por nivel (RF-59). Un material publicado con preguntas no
     * aprobadas no se considera disponible (RF-30, RF-57).
     */
    public List<MaterialPractica> listarPublicadosDeEstudiante(int idEstudiante, NivelIngles nivel) {
        String filtro = nivel == null ? "" : "AND mp.nivel = ? ";
        return consultarLista("SELECT DISTINCT mp.id_material_practica, mp.id_docente, mp.titulo, "
                + "mp.descripcion, mp.nivel, mp.estado, "
                + "mp.origen FROM material_practica mp "
                + "JOIN curso_material cmp ON cmp.id_material_practica = mp.id_material_practica "
                + "JOIN grupo g ON g.id_curso = cmp.id_curso "
                + "JOIN inscripcion i ON i.id_grupo = g.id_grupo "
                + "WHERE i.id_estudiante = ? AND i.estado = 'ACTIVA' "
                + "AND mp.estado = 'PUBLICADO' "
                + "AND EXISTS (SELECT 1 FROM material_pregunta mpx "
                + "JOIN pregunta px ON px.id_pregunta = mpx.id_pregunta "
                + "WHERE mpx.id_material_practica = mp.id_material_practica AND px.estado = 'APROBADA') "
                + "AND NOT EXISTS (SELECT 1 FROM material_pregunta mpx "
                + "JOIN pregunta px ON px.id_pregunta = mpx.id_pregunta "
                + "WHERE mpx.id_material_practica = mp.id_material_practica AND px.estado <> 'APROBADA') "
                + filtro + "ORDER BY mp.titulo",
                ps -> {
                    ps.setInt(1, idEstudiante);
                    if (nivel != null) {
                        ps.setString(2, nivel.name());
                    }
                }, this::mapear);
    }

    public boolean esDeEstudiante(int idMaterialPractica, int idEstudiante) {
        return existe("SELECT 1 FROM material_practica mp "
                + "JOIN curso_material cmp ON cmp.id_material_practica = mp.id_material_practica "
                + "JOIN grupo g ON g.id_curso = cmp.id_curso "
                + "JOIN inscripcion i ON i.id_grupo = g.id_grupo "
                + "WHERE mp.id_material_practica = ? AND i.id_estudiante = ? AND i.estado = 'ACTIVA' "
                + "AND mp.estado = 'PUBLICADO' LIMIT 1", ps -> {
            ps.setInt(1, idMaterialPractica);
            ps.setInt(2, idEstudiante);
        });
    }

    // ------------------------------------------------------------------ consultas

    public Optional<MaterialPractica> buscarPorId(int idMaterialPractica) {
        return consultarUno("SELECT " + COLUMNAS + " FROM material_practica WHERE id_material_practica = ?",
                ps -> ps.setInt(1, idMaterialPractica), this::mapear);
    }

    public Optional<MaterialPractica> buscarPorIdDeDocente(int idMaterialPractica, int idDocente) {
        return consultarUno("SELECT " + COLUMNAS + " FROM material_practica WHERE id_material_practica = ? "
                + "AND id_docente = ?", ps -> {
            ps.setInt(1, idMaterialPractica);
            ps.setInt(2, idDocente);
        }, this::mapear);
    }

    public List<MaterialPractica> listarPorDocente(int idDocente) {
        return consultarLista("SELECT " + COLUMNAS + " FROM material_practica WHERE id_docente = ? "
                + "ORDER BY estado, titulo", ps -> ps.setInt(1, idDocente),
                this::mapear);
    }

    // ------------------------------------------------------------------ PDF y Word

    /** Hoja de práctica imprimible (con espacio para responder). */
    public byte[] generarPDFMaterialPractica(MaterialPractica material) {
        return construirPDF(material, false, false);
    }

    /** Clave de respuestas (solo para el docente). */
    public byte[] generarClavePDFMaterialPractica(MaterialPractica material) {
        return construirPDF(material, true, false);
    }

    /** RF-63: material con seccion final de respuestas correctas/esperadas. */
    public byte[] generarPDFMaterialPracticaConRespuestas(MaterialPractica material) {
        return construirPDF(material, false, true);
    }

    private static final class PreguntaPDF {
        String enunciado;
        String respuestaEsperada;
        final List<String[]> opciones = new ArrayList<>();
    }

    private List<PreguntaPDF> cargarPreguntasPDF(int idMaterialPractica) {
        List<PreguntaPDF> preguntas = consultarLista("SELECT p.id_pregunta, p.enunciado, "
                + "p.respuesta_esperada FROM material_pregunta mpp "
                + "JOIN pregunta p ON p.id_pregunta = mpp.id_pregunta WHERE mpp.id_material_practica = ? "
                + "ORDER BY p.id_pregunta", ps -> ps.setInt(1, idMaterialPractica), rs -> {
                    PreguntaPDF pregunta = new PreguntaPDF();
                    pregunta.enunciado = rs.getString("enunciado");
                    pregunta.respuestaEsperada = rs.getString("respuesta_esperada");
                    int idPregunta = rs.getInt("id_pregunta");
                    pregunta.opciones.addAll(consultarLista("SELECT texto, "
                            + "es_correcta FROM opcion_respuesta WHERE id_pregunta = ? "
                            + "ORDER BY id_opcion_respuesta", ps -> ps.setInt(1, idPregunta), rso -> new String[]{
                                rso.getString("texto"),
                                String.valueOf(rso.getBoolean("es_correcta"))}));
                    return pregunta;
                });
        return preguntas;
    }

    private byte[] construirPDF(MaterialPractica material, boolean clave, boolean respuestasFinales) {
        StringBuilder nombresCursos = new StringBuilder();
        for (Curso curso : listarCursos(material.consultarIdMaterialPractica())) {
            if (nombresCursos.length() > 0) {
                nombresCursos.append(", ");
            }
            nombresCursos.append(curso.nombreCurso);
        }
        String encabezado = material.tituloMaterialPractica + "  ·  Curso: "
                + (nombresCursos.length() == 0 ? "sin curso asignado" : nombresCursos.toString());
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document documento = new Document(PageSize.LETTER, 56, 56, 80, 56);
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            escritor.setPageEvent(new PdfFormato(encabezado));
            documento.open();

            Font fuenteDatos = new Font(Font.HELVETICA, 11, Font.NORMAL);
            Font fuentePregunta = new Font(Font.HELVETICA, 11, Font.BOLD);

            if (material.descripcionMaterialPractica != null && !material.descripcionMaterialPractica.isBlank()) {
                documento.add(new Paragraph(material.descripcionMaterialPractica, fuenteDatos));
            }
            documento.add(new Paragraph(clave ? "CLAVE DE RESPUESTAS" : "Material de práctica", fuenteDatos));
            documento.add(new Paragraph(" "));
            if (!clave) {
                documento.add(new Paragraph("Nombre: ______________________________________   "
                        + "Fecha: ____________", fuenteDatos));
                documento.add(new Paragraph(" "));
            }

            int numero = 1;
            for (PreguntaPDF pregunta : cargarPreguntasPDF(material.consultarIdMaterialPractica())) {
                PdfPTable bloque = PdfFormato.bloquePregunta();
                bloque.addCell(PdfFormato.celda(new Paragraph(numero + ". " + pregunta.enunciado, fuentePregunta)));
                char letra = 'a';
                for (String[] opcion : pregunta.opciones) {
                    boolean correcta = Boolean.parseBoolean(opcion[1]);
                    bloque.addCell(PdfFormato.celda(new Paragraph("     " + letra + ") " + opcion[0]
                            + (clave && correcta ? "   (correcta)" : ""), fuenteDatos)));
                    letra++;
                }
                if (pregunta.opciones.isEmpty()) {
                    if (clave && pregunta.respuestaEsperada != null) {
                        bloque.addCell(PdfFormato.celda(new Paragraph("     Respuesta esperada: "
                                + pregunta.respuestaEsperada, fuenteDatos)));
                    } else if (!clave) {
                        bloque.addCell(PdfFormato.celda(new Paragraph("     Respuesta: "
                                + "____________________________________________________", fuenteDatos)));
                    }
                }
                documento.add(bloque);
                numero++;
            }
            if (respuestasFinales && !clave) {
                documento.newPage();
                documento.add(new Paragraph("Respuestas correctas / esperadas", fuentePregunta));
                documento.add(new Paragraph(" "));
                int indice = 1;
                for (PreguntaPDF pregunta : cargarPreguntasPDF(material.consultarIdMaterialPractica())) {
                    String respuesta = "";
                    for (String[] opcion : pregunta.opciones) {
                        if (Boolean.parseBoolean(opcion[1])) {
                            respuesta = opcion[0];
                            break;
                        }
                    }
                    if (respuesta.isEmpty()) {
                        respuesta = pregunta.respuestaEsperada == null ? "(sin definir)" : pregunta.respuestaEsperada;
                    }
                    documento.add(new Paragraph(indice + ". " + respuesta, fuenteDatos));
                    indice++;
                }
            }
            documento.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del material.", e);
        }
    }

    private static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------ internos

    private void cambiarEstado(MaterialPractica material, String estadoBd, EstadoMaterial estado) {
        actualizar("UPDATE material_practica SET estado = ? WHERE id_material_practica = ?", ps -> {
            ps.setString(1, estadoBd);
            ps.setInt(2, material.consultarIdMaterialPractica());
        });
        material.estadoMaterialPractica = estado;
    }

    private MaterialPractica mapear(ResultSet rs) throws SQLException {
        MaterialPractica material = new MaterialPractica();
        material.establecerIdMaterialPractica(rs.getInt("id_material_practica"));
        material.tituloMaterialPractica = rs.getString("titulo");
        material.descripcionMaterialPractica = rs.getString("descripcion");
        material.nivelMaterialPractica = enumeracion(rs, "nivel", NivelIngles.class);
        material.estadoMaterialPractica = enumeracion(rs, "estado", EstadoMaterial.class);
        material.origenMaterialPractica = enumeracion(rs, "origen", OrigenContenido.class);
        return material;
    }
}
