package servlet;

import logica.Curso;
import logica.Docente;
import logica.Evaluacion;
import logica.RecursoNoEncontradoException;
import logica.ValidacionException;
import logica.Validador;
import logica.VersionEvaluacion;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * RF-39/RF-40 / CU-23: descarga el PDF de una versión (examen o clave) o el ZIP con todas las
 * versiones de una evaluación. El curso es solo contexto de impresión (no se guarda). Ante
 * cualquier fallo devuelve un error controlado al docente, sin archivos parciales.
 */
@WebServlet(name = "DocenteVersionPdfServlet", urlPatterns = "/app/docente/version/pdf")
public class DocenteVersionPdfServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(DocenteVersionPdfServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        String destino = parametro(req, "destino");
        if (destino == null || destino.isBlank()) {
            destino = "version:" + parametroId(req, "id") + ("1".equals(req.getParameter("clave")) ? ":clave" : "");
        }
        String curso = nombreCurso(req, docente);
        String[] partes = destino.split(":");
        try {
            if ("version".equals(partes[0]) && partes.length >= 2) {
                generarVersion(resp, docente, Validador.parsearId(partes[1]),
                        partes.length >= 3 && "clave".equals(partes[2]), curso);
            } else if ("evaluacion".equals(partes[0]) && partes.length >= 2) {
                generarTodas(resp, docente, Validador.parsearId(partes[1]),
                        partes.length >= 3 && "clave".equals(partes[2]), curso);
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (RecursoNoEncontradoException e) {
            throw e;
        } catch (ValidacionException | RuntimeException e) {
            LOG.log(Level.SEVERE, "No se pudo generar el PDF de la evaluación", e);
            agregarMensaje(req, MensajeFlash.Tipo.ERROR,
                    "No se pudo generar el PDF. Vuelve a intentarlo.");
            redirigir(req, resp, destinoDeVuelta(destino));
        }
    }

    private void generarVersion(HttpServletResponse resp, Docente docente,
                                int idVersion, boolean clave, String curso) throws IOException, ValidacionException {
        VersionEvaluacion version = versionDAO.buscarPorIdDeDocente(idVersion, docente.getIdUsuario()).orElse(null);
        verificarPropiedad(version != null, versionDAO.buscarPorId(idVersion).isPresent(), "Versión no encontrada");
        byte[] pdf = clave
                ? versionDAO.generarClaveRespuestasPDFVersionEvaluacion(version, curso)
                : versionDAO.generarPDFVersionEvaluacion(version, curso);
        String titulo = tituloDeEvaluacion(versionDAO.idEvaluacionDeVersion(idVersion));
        escribir(resp, pdf, "application/pdf",
                "Evaluacion_" + sanitizar(titulo) + "_" + version.codigoVersionEvaluacion
                        + (clave ? "_clave" : "") + ".pdf");
    }

    private void generarTodas(HttpServletResponse resp, Docente docente,
                              int idEvaluacion, boolean clave, String curso) throws IOException, ValidacionException {
        Evaluacion evaluacion = evaluacionDAO.buscarPorIdDeDocente(idEvaluacion, docente.getIdUsuario())
                .orElse(null);
        verificarPropiedad(evaluacion != null, evaluacionDAO.buscarPorId(idEvaluacion).isPresent(),
                "Evaluación no encontrada");
        List<VersionEvaluacion> versiones = versionDAO.listarPorEvaluacion(idEvaluacion);
        if (versiones.isEmpty()) {
            throw new ValidacionException("La evaluación todavía no tiene versiones generadas.");
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (VersionEvaluacion version : versiones) {
                byte[] pdf = clave
                        ? versionDAO.generarClaveRespuestasPDFVersionEvaluacion(version, curso)
                        : versionDAO.generarPDFVersionEvaluacion(version, curso);
                zip.putNextEntry(new ZipEntry("Evaluacion_" + sanitizar(evaluacion.tituloEvaluacion) + "_"
                        + version.codigoVersionEvaluacion + (clave ? "_clave" : "") + ".pdf"));
                zip.write(pdf);
                zip.closeEntry();
            }
        }
        escribir(resp, salida.toByteArray(), "application/zip", "Evaluacion_"
                + sanitizar(evaluacion.tituloEvaluacion) + (clave ? "_claves" : "_versiones") + ".zip");
    }

    /** Curso elegido por el docente para el encabezado, solo si le pertenece; si no, sin curso. */
    private String nombreCurso(HttpServletRequest req, Docente docente) {
        int idCurso = Validador.parsearId(req.getParameter("curso"));
        if (idCurso < 0) {
            return null;
        }
        return cursoDAO.buscarPorIdDeDocente(idCurso, docente.getIdUsuario())
                .map(curso -> curso.nombreCurso)
                .orElse(null);
    }

    private String tituloDeEvaluacion(int idEvaluacion) {
        return evaluacionDAO.buscarPorId(idEvaluacion)
                .map(evaluacion -> evaluacion.tituloEvaluacion)
                .orElse("Evaluacion");
    }

    /** A dónde volver con el error: a la evaluación si se puede deducir, o al listado. */
    private String destinoDeVuelta(String destino) {
        String[] partes = destino.split(":");
        if (partes.length >= 2) {
            int id = Validador.parsearId(partes[1]);
            if (id >= 0) {
                if ("version".equals(partes[0])) {
                    id = versionDAO.idEvaluacionDeVersion(id);
                }
                if (id >= 0) {
                    return "/app/docente/evaluacion?id=" + id;
                }
            }
        }
        return "/app/docente/evaluaciones";
    }

    private static void escribir(HttpServletResponse resp, byte[] contenido, String tipo, String nombre)
            throws IOException {
        resp.setContentType(tipo);
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
        resp.setContentLength(contenido.length);
        resp.getOutputStream().write(contenido);
    }

    /** Nombre de archivo seguro: sin acentos ni signos raros (los espacios quedan como "_"). */
    static String sanitizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "Evaluacion";
        }
        String plano = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String limpio = plano.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return limpio.isBlank() ? "Evaluacion" : limpio;
    }
}
