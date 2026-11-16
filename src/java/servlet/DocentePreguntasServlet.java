package servlet;

import logica.BancoPreguntas;
import logica.CriteriosBusquedaPregunta;
import logica.Docente;
import logica.Pregunta;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** RF-24: búsqueda transversal por texto y todos los filtros en los bancos del docente. */
@WebServlet(name = "DocentePreguntasServlet", urlPatterns = "/app/docente/preguntas")
public class DocentePreguntasServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/preguntas";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idBanco = Validador.parsearId(req.getParameter("idBanco"));
        CriteriosBusquedaPregunta criterios = DocenteBancoServlet.criteriosDe(req, idBanco > 0 ? idBanco : null);

        List<Map<String, String>> filas = new ArrayList<>();
        for (Pregunta p : preguntaDAO.buscarDeDocente(docente.consultarIdUsuario(), criterios)) {
            Map<String, String> fila = DocenteBancoServlet.filaPregunta(p);
            fila.put("url", ruta(req, fila.get("url")));
            filas.add(fila);
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("cantidad", String.valueOf(filas.size()));
        datos.put("filasPreguntas", componerFilas("_fila-pregunta.html", filas,
                filaVacia("7", criterios.hayFiltros()
                        ? "Ninguna pregunta coincide con la búsqueda."
                        : "Todavía no has creado preguntas. Crea una pregunta dentro de una evaluación.")));
        Map<String, String> busqueda = new HashMap<>();
        busqueda.put("destino", ruta(req, RUTA));
        busqueda.put("bancoFijo", "");
        busqueda.put("valorTexto", DocenteBancoServlet.textoONulo(req.getParameter("texto")) == null
                ? "" : Validador.limpiar(req.getParameter("texto")));
        busqueda.put("valorTema", DocenteBancoServlet.textoONulo(req.getParameter("tema")) == null
                ? "" : Validador.limpiar(req.getParameter("tema")));
        busqueda.put("opcionesFiltroBanco", campoBanco(docente, idBanco));
        busqueda.putAll(filtros(req));
        busqueda.put("limpiarFiltros", criterios.hayFiltros()
                ? vistas.componer("_enlace-accion.html", Map.of("clase", "boton boton-contorno boton-pequeno",
                        "url", ruta(req, RUTA), "texto", "Quitar filtros"))
                : "");
        datos.put("formularioBusqueda", vistas.componer("_form-busqueda-preguntas.html", busqueda));
        renderizar(req, resp, "docente-preguntas.html", datos, null);
    }

    private String campoBanco(Docente docente, int seleccionado) {
        StringBuilder opciones = new StringBuilder(Formulario.opcion(vistas, "", "Todas mis preguntas",
                seleccionado <= 0));
        for (BancoPreguntas banco : bancoDAO.listarPorDocente(docente.consultarIdUsuario())) {
            opciones.append(Formulario.opcion(vistas, String.valueOf(banco.consultarIdBancoPreguntas()),
                    banco.nombreBancoPreguntas,
                    seleccionado > 0 && banco.consultarIdBancoPreguntas() == seleccionado));
        }
        return vistas.componer("_campo-banco-filtro.html", Map.of("opcionesBancos", opciones.toString()));
    }

    private Map<String, String> filtros(HttpServletRequest req) {
        Map<String, String> datos = new HashMap<>();
        datos.put("opcionesFiltroTipo", Formulario.opciones(vistas, logica.TipoPregunta.values(),
                Formulario.enumeracion(logica.TipoPregunta.class, req.getParameter("tipo")), "Todos"));
        datos.put("opcionesFiltroNivel", Formulario.opciones(vistas, logica.NivelIngles.values(),
                Formulario.enumeracion(logica.NivelIngles.class, req.getParameter("nivel")), "Todos"));
        datos.put("opcionesFiltroDificultad", Formulario.opciones(vistas, logica.Dificultad.values(),
                Formulario.enumeracion(logica.Dificultad.class, req.getParameter("dificultad")), "Todas"));
        datos.put("opcionesFiltroCompetencia", Formulario.opciones(vistas, logica.Competencia.values(),
                Formulario.enumeracion(logica.Competencia.class, req.getParameter("competencia")), "Todas"));
        datos.put("opcionesFiltroEstado", Formulario.opciones(vistas, logica.EstadoPregunta.values(),
                Formulario.enumeracion(logica.EstadoPregunta.class, req.getParameter("estado")), "Todos"));
        datos.put("opcionesFiltroOrigen", Formulario.opciones(vistas, logica.OrigenContenido.values(),
                Formulario.enumeracion(logica.OrigenContenido.class, req.getParameter("origen")), "Todos"));
        return datos;
    }
}
