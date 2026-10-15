package servlet;

import logica.BancoPreguntas;
import logica.Docente;
import logica.ValidacionException;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DocenteBancosServlet", urlPatterns = "/app/docente/bancos")
public class DocenteBancosServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final int MAX_NOMBRE_BANCO = 100;
    private static final int MAX_DESCRIPCION_BANCO = 500;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        Map<String, String> formulario = recuperarFormulario(req);
        // CU-15: los conteos por estado se obtienen en una sola consulta (sin N+1).
        Map<Integer, Map<logica.EstadoPregunta, Integer>> conteos =
                bancoDAO.contarPorEstadoDeDocente(docente.getIdUsuario());
        List<Map<String, String>> filas = new ArrayList<>();
        for (BancoPreguntas banco : bancoDAO.listarPorDocente(docente.getIdUsuario())) {
            Map<logica.EstadoPregunta, Integer> porEstado = conteos.getOrDefault(banco.getIdBancoPreguntas(),
                    Map.of());
            int borrador = porEstado.getOrDefault(logica.EstadoPregunta.BORRADOR, 0);
            int aprobadas = porEstado.getOrDefault(logica.EstadoPregunta.APROBADA, 0);
            int archivadas = porEstado.getOrDefault(logica.EstadoPregunta.ARCHIVADA, 0);
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, "/app/docente/banco?id=" + banco.getIdBancoPreguntas()));
            fila.put("nombre", banco.nombreBancoPreguntas);
            fila.put("descripcion", Html.resumir(banco.descripcionBancoPreguntas, 120));
            fila.put("borrador", String.valueOf(borrador));
            fila.put("aprobadas", String.valueOf(aprobadas));
            fila.put("archivadas", String.valueOf(archivadas));
            fila.put("cantidad", String.valueOf(borrador + aprobadas + archivadas));
            filas.add(fila);
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-banco.html", filas,
                filaVacia("2", "Todavía no tienes bancos. Crea uno por unidad o por tema.")));
        datos.put("nombre", formulario.getOrDefault("nombre", ""));
        datos.put("descripcion", formulario.getOrDefault("descripcion", ""));
        renderizar(req, resp, "docente-bancos.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        String nombre = parametro(req, "nombre");
        String descripcion = Validador.limpiarTexto(req.getParameter("descripcion") == null ? "" : req.getParameter("descripcion"));

        List<String> errores = new ArrayList<>();
        Validador.validarLongitud(nombre, 3, MAX_NOMBRE_BANCO, "El nombre del banco debe tener entre 3 y 100 caracteres.", errores);
        Validador.validarLongitud(descripcion, 0, MAX_DESCRIPCION_BANCO, "La descripción admite máximo 500 caracteres.", errores);
        if (!errores.isEmpty()) {
            agregarErrores(req, errores);
            guardarFormulario(req, Map.of("nombre", nombre, "descripcion", descripcion));
            redirigir(req, resp, "/app/docente/bancos");
            return;
        }
        try {
            BancoPreguntas banco = bancoDAO.crearBancoPreguntas(docente, nombre, descripcion);
            agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Banco creado. Agrega su primera pregunta.");
            redirigir(req, resp, "/app/docente/banco?id=" + banco.getIdBancoPreguntas());
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
            guardarFormulario(req, Map.of("nombre", nombre, "descripcion", descripcion));
            redirigir(req, resp, "/app/docente/bancos");
        }
    }
}
