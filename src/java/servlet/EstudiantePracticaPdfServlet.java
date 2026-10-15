package servlet;

import logica.Estudiante;
import logica.MaterialPractica;
import logica.RecursoNoEncontradoException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * RF-63: el estudiante descarga el PDF de un material PUBLICADO de sus cursos con inscripción
 * activa, sin respuestas o con la sección final de respuestas correctas/esperadas.
 */
@WebServlet(name = "EstudiantePracticaPdfServlet", urlPatterns = "/app/estudiante/practica/pdf")
public class EstudiantePracticaPdfServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        int id = parametroId(req, "id");
        MaterialPractica material = materialDAO.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Material no encontrado"));
        if (!materialDAO.esDeEstudiante(id, estudiante.getIdUsuario())) {
            // BORRADOR, ARCHIVADO o material de cursos sin inscripción activa: si existe, es ajeno (403).
            verificarPropiedad(false, materialDAO.buscarPorId(id).isPresent(), "Material no encontrado");
        }
        boolean conRespuestas = "1".equals(parametro(req, "respuestas"));
        byte[] contenido = conRespuestas
                ? materialDAO.generarPDFMaterialPracticaConRespuestas(material)
                : materialDAO.generarPDFMaterialPractica(material);
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition", "attachment; filename=\"practica-" + id
                + (conRespuestas ? "-respuestas" : "") + ".pdf\"");
        resp.setContentLength(contenido.length);
        resp.getOutputStream().write(contenido);
    }
}
