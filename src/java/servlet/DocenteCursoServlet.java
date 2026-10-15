package servlet;

import logica.Curso;
import logica.Docente;
import logica.Grupo;
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

@WebServlet(name = "DocenteCursoServlet", urlPatterns = "/app/docente/curso")
public class DocenteCursoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/curso";
    private static final int MAX_NOMBRE_CURSO = 100;
    private static final int MAX_DESCRIPCION_CURSO = 500;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idCurso = parametroId(req, "id");
        Curso curso = cursoDelDocente(docente, idCurso);
        if ("confirmarDesactivar".equals(parametro(req, "accion"))) {
            int abiertos = 0;
            for (Grupo grupo : grupoDAO.listarPorCurso(idCurso)) {
                if (grupo.estadoGrupo == logica.EstadoGrupo.ABIERTO) {
                    abiertos++;
                }
            }
            String aviso = abiertos > 0
                    ? Html.alerta(vistas, "alerta-aviso", "Este curso tiene " + abiertos
                            + " grupo(s) abierto(s): seguirán funcionando con normalidad, pero no podrás abrir "
                            + "grupos nuevos hasta que reactives el curso.")
                    : "";
            confirmar(req, resp, "Desactivar curso",
                    "¿Confirma que desea desactivar el curso " + curso.nombreCurso + "?",
                    aviso, RUTA, "desactivar", idCurso, "Desactivar", "boton-peligro",
                    req.getContextPath() + RUTA + "?id=" + idCurso);
            return;
        }
        Map<String, String> formulario = recuperarFormulario(req);

        List<Map<String, String>> filas = new ArrayList<>();
        for (Grupo grupo : grupoDAO.listarPorCurso(idCurso)) {
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, "/app/docente/grupo?id=" + grupo.getIdGrupo()));
            fila.put("nombre", grupo.nombreGrupo);
            fila.put("periodo", grupo.periodoGrupo);
            fila.put("inscritos", String.valueOf(inscripcionDAO.contarActivosPorGrupo(grupo.getIdGrupo())));
            fila.put("estado", Etiquetas.de(grupo.estadoGrupo));
            fila.put("claseEstado", grupo.estadoGrupo == logica.EstadoGrupo.ABIERTO ? "insignia-activo" : "insignia-neutra");
            filas.add(fila);
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(idCurso));
        datos.put("tituloCurso", curso.nombreCurso);
        datos.put("nivel", Etiquetas.de(curso.nivelCurso));
        datos.put("estado", curso.activoCurso ? "Activo" : "Inactivo");
        datos.put("claseEstado", curso.activoCurso ? "insignia-activo" : "insignia-neutra");
        datos.put("nombre", formulario.getOrDefault("nombre", curso.nombreCurso));
        datos.put("descripcion", formulario.getOrDefault("descripcion", curso.descripcionCurso));
        datos.put("accionEstado", curso.activoCurso
                ? boton(req, RUTA, idCurso, "desactivar", "Desactivar curso", "boton-peligro")
                : boton(req, RUTA, idCurso, "activar", "Activar curso", "boton-contorno"));
        datos.put("filasGrupos", componerFilas("_fila-grupo.html", filas,
                filaVacia("4", "Este curso no tiene grupos. Abre uno para el periodo actual.")));

        if (curso.activoCurso) {
            Map<String, String> form = new HashMap<>();
            form.put("ctx", req.getContextPath());
            form.put("csrf", csrf(req));
            form.put("id", String.valueOf(idCurso));
            form.put("grupoNombre", formulario.getOrDefault("grupoNombre", ""));
            form.put("grupoPeriodo", formulario.getOrDefault("grupoPeriodo", ""));
            datos.put("formGrupo", vistas.componer("_form-abrir-grupo.html", form));
        } else {
            datos.put("formGrupo", parrafoAyuda("Activa el curso para abrir grupos nuevos."));
        }
        renderizar(req, resp, "docente-curso.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idCurso = parametroId(req, "id");
        String destino = RUTA + "?id=" + idCurso;

        switch (parametro(req, "accion")) {
            case "actualizar": {
                String nombre = parametro(req, "nombre");
                String descripcion = Validador.limpiarTexto(req.getParameter("descripcion") == null ? "" : req.getParameter("descripcion"));
                List<String> errores = new ArrayList<>();
                Validador.validarLongitud(nombre, 3, MAX_NOMBRE_CURSO, "El nombre del curso debe tener entre 3 y 100 caracteres.", errores);
                Validador.validarLongitud(descripcion, 0, MAX_DESCRIPCION_CURSO, "La descripción admite máximo 500 caracteres.", errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("nombre", nombre, "descripcion", descripcion));
                    redirigir(req, resp, destino);
                    break;
                }
                ejecutar(req, resp, destino, Map.of("nombre", nombre, "descripcion", descripcion), () -> {
                    Curso curso = cursoDelDocente(docente, idCurso);
                    cursoDAO.actualizarCurso(curso, nombre, descripcion);
                    return "Datos del curso actualizados.";
                });
                break;
            }
            case "activar":
                ejecutar(req, resp, destino, null, () -> {
                    cursoDAO.activarCurso(cursoDelDocente(docente, idCurso));
                    return "Curso activado.";
                });
                break;
            case "desactivar":
                if (!"1".equals(req.getParameter("confirmado"))) {
                    redirigir(req, resp, RUTA + "?id=" + idCurso + "&accion=confirmarDesactivar");
                    break;
                }
                ejecutar(req, resp, destino, null, () -> {
                    cursoDAO.desactivarCurso(cursoDelDocente(docente, idCurso));
                    return "Curso desactivado. Los grupos abiertos siguen funcionando; no se pueden abrir "
                            + "grupos nuevos hasta que lo reactives.";
                });
                break;
            case "abrirGrupo": {
                String nombre = parametro(req, "grupoNombre");
                String periodo = parametro(req, "grupoPeriodo");
                List<String> errores = new ArrayList<>();
                Validador.validarNombreGrupo(nombre, errores);
                Validador.validarPeriodo(periodo, errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("grupoNombre", nombre, "grupoPeriodo", periodo));
                    redirigir(req, resp, destino);
                    break;
                }
                try {
                    Curso curso = cursoDelDocente(docente, idCurso);
                    Grupo grupo = grupoDAO.abrirGrupo(curso, nombre, periodo);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Grupo abierto. Comparte el código de acceso con tus estudiantes.");
                    redirigir(req, resp, "/app/docente/grupo?id=" + grupo.getIdGrupo());
                } catch (ValidacionException e) {
                    agregarErrores(req, e.getErrores());
                    guardarFormulario(req, Map.of("grupoNombre", nombre, "grupoPeriodo", periodo));
                    redirigir(req, resp, destino);
                }
                break;
            }
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private Curso cursoDelDocente(Docente docente, int idCurso) {
        java.util.Optional<Curso> propio = cursoDAO.buscarPorIdDeDocente(idCurso, docente.getIdUsuario());
        verificarPropiedad(propio.isPresent(), cursoDAO.buscarPorId(idCurso).isPresent(), "Curso no encontrado");
        return propio.get();
    }
}
