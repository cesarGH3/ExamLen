package servlet;

import logica.Curso;
import logica.Docente;
import logica.NivelIngles;
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

@WebServlet(name = "DocenteCursosServlet", urlPatterns = "/app/docente/cursos")
public class DocenteCursosServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final int MAX_NOMBRE_CURSO = 100;
    private static final int MAX_DESCRIPCION_CURSO = 500;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        Map<String, String> formulario = recuperarFormulario(req);

        List<Map<String, String>> filas = new ArrayList<>();
        for (Curso curso : cursoDAO.listarPorDocente(docente.getIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, "/app/docente/curso?id=" + curso.getIdCurso()));
            fila.put("nombre", curso.nombreCurso);
            fila.put("nivel", Etiquetas.de(curso.nivelCurso));
            fila.put("estado", curso.activoCurso ? "Activo" : "Inactivo");
            fila.put("claseEstado", curso.activoCurso ? "insignia-activo" : "insignia-neutra");
            filas.add(fila);
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-curso.html", filas,
                filaVacia("3", "Todavía no tienes cursos. Crea el primero con el formulario.")));
        datos.put("nombre", formulario.getOrDefault("nombre", ""));
        datos.put("descripcion", formulario.getOrDefault("descripcion", ""));
        datos.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(),
                Formulario.enumeracion(NivelIngles.class, formulario.get("nivel")), "Selecciona el nivel"));
        renderizar(req, resp, "docente-cursos.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        String nombre = parametro(req, "nombre");
        String descripcion = Validador.limpiarTexto(req.getParameter("descripcion"));
        String nivel = parametro(req, "nivel");

        List<String> errores = new ArrayList<>();
        Validador.validarLongitud(nombre, 3, MAX_NOMBRE_CURSO, "El nombre del curso debe tener entre 3 y 100 caracteres.", errores);
        Validador.validarLongitud(descripcion, 0, MAX_DESCRIPCION_CURSO, "La descripción admite máximo 500 caracteres.", errores);
        NivelIngles nivelEnum = Formulario.enumeracion(NivelIngles.class, nivel);
        Validador.validarRequerido(nivelEnum, "Selecciona el nivel del curso.", errores);
        if (!errores.isEmpty()) {
            agregarErrores(req, errores);
            guardarFormulario(req, Map.of("nombre", nombre, "descripcion", descripcion, "nivel", nivel));
            redirigir(req, resp, "/app/docente/cursos");
            return;
        }
        try {
            Curso curso = cursoDAO.crearCurso(docente, nombre, descripcion, nivelEnum);
            agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Curso creado. Ahora abre su primer grupo.");
            redirigir(req, resp, "/app/docente/curso?id=" + curso.getIdCurso());
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
            guardarFormulario(req, Map.of("nombre", nombre, "descripcion", descripcion, "nivel", nivel));
            redirigir(req, resp, "/app/docente/cursos");
        }
    }
}
