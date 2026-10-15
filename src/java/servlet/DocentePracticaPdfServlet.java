package servlet;

import logica.Docente;
import logica.MaterialPractica;
import logica.OpcionRespuesta;
import logica.Pregunta;
import logica.RecursoNoEncontradoException;
import servicio.GeneradorWordPractica;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Descarga el material de práctica en PDF, la clave o la versión Word. Solo el docente dueño. */
@WebServlet(name = "DocentePracticaPdfServlet", urlPatterns = "/app/docente/practica/pdf")
public class DocentePracticaPdfServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    private final GeneradorWordPractica generadorWord = new GeneradorWordPractica();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = parametroId(req, "id");
        MaterialPractica material = materialDAO.buscarPorIdDeDocente(id, docente.getIdUsuario()).orElse(null);
        verificarPropiedad(material != null, materialDAO.buscarPorId(id).isPresent(), "Material no encontrado");

        String tipo = parametro(req, "tipo");
        byte[] contenido;
        String nombre;
        if ("clave".equals(tipo)) {
            contenido = materialDAO.generarClavePDFMaterialPractica(material);
            nombre = "clave-material-" + id + ".pdf";
            resp.setContentType("application/pdf");
        } else if ("word".equals(tipo)) {
            contenido = generadorWord.generar(componerWord(material));
            nombre = "practica-" + id + ".doc";
            resp.setContentType("application/msword;charset=UTF-8");
        } else {
            contenido = materialDAO.generarPDFMaterialPractica(material);
            nombre = "practica-" + id + ".pdf";
            resp.setContentType("application/pdf");
        }
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
        resp.setContentLength(contenido.length);
        resp.getOutputStream().write(contenido);
    }

    /** Compone la hoja Word con los fragmentos _material-practica-word*.html. */
    private String componerWord(MaterialPractica material) {
        StringBuilder preguntas = new StringBuilder();
        int numero = 1;
        for (Pregunta pregunta : materialDAO.listarPreguntas(material.getIdMaterialPractica())) {
            Map<String, String> datos = new HashMap<>();
            datos.put("numero", String.valueOf(numero));
            datos.put("enunciado", pregunta.enunciadoPregunta);
            datos.put("bloques", bloquesDePregunta(pregunta));
            preguntas.append(vistas.componer("_material-practica-word-pregunta.html", datos));
            numero++;
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("titulo", material.tituloMaterialPractica);
        datos.put("descripcion", descripcionWord(material));
        datos.put("preguntas", preguntas.toString());
        return vistas.componer("_material-practica-word.html", datos);
    }

    private String descripcionWord(MaterialPractica material) {
        String descripcion = material.descripcionMaterialPractica;
        if (descripcion == null || descripcion.isBlank()) {
            return "";
        }
        return vistas.componer("_material-practica-word-descripcion.html", Map.of("texto", descripcion));
    }

    private String bloquesDePregunta(Pregunta pregunta) {
        if (pregunta.opcionesPregunta.isEmpty()) {
            return vistas.componer("_material-practica-word-respuesta.html", Map.of());
        }
        StringBuilder bloques = new StringBuilder();
        char letra = 'a';
        for (OpcionRespuesta opcion : pregunta.opcionesPregunta) {
            Map<String, String> datos = new HashMap<>();
            datos.put("letra", String.valueOf(letra));
            datos.put("texto", opcion.textoOpcionRespuesta);
            bloques.append(vistas.componer("_material-practica-word-opcion.html", datos));
            letra++;
        }
        return bloques.toString();
    }
}
