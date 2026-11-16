package servlet;

import logica.EstadoIntento;
import logica.Estudiante;
import logica.Intento;
import logica.TipoEventoSupervision;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Registra eventos de supervisión del intento (los envía el navegador). */
@WebServlet(name = "EstudianteEventoServlet", urlPatterns = "/app/estudiante/intento/evento")
public class EstudianteEventoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final int MAX_DESCRIPCION = 500;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Estudiante estudiante = (Estudiante) usuarioActual(req);
        int idIntento = Validador.parsearId(req.getParameter("idIntento"));
        if (idIntento > 0) {
            Intento intento = intentoDAO.buscarPorIdDeEstudiante(idIntento, estudiante.consultarIdUsuario()).orElse(null);
            if (intento != null && intento.estadoIntento == EstadoIntento.EN_CURSO) {
                int idVirtual = intentoDAO.idVirtualDeIntento(idIntento);
                boolean supervision = virtualDAO.buscarPorId(idVirtual)
                        .map(v -> v.supervisionActivaEvaluacionVirtual).orElse(false);
                TipoEventoSupervision tipo = Formulario.enumeracion(TipoEventoSupervision.class,
                        parametro(req, "tipo"));
                if (supervision && tipo != null) {
                    String descripcion = Validador.limpiarTexto(req.getParameter("descripcion") == null
                            ? "" : req.getParameter("descripcion"));
                    if (descripcion.length() > MAX_DESCRIPCION) {
                        descripcion = descripcion.substring(0, MAX_DESCRIPCION);
                    }
                    // Reintentos offline pueden repetir un evento: se suprime el duplicado cercano.
                    if (!eventoDAO.existeSimilarReciente(idIntento, tipo, descripcion)) {
                        eventoDAO.registrarEventoSupervision(intento, tipo, descripcion);
                    }
                }
            }
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
