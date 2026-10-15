package servlet;

import logica.Curso;
import logica.Docente;
import logica.EstadoMaterial;
import logica.EstadoPregunta;
import logica.MaterialPractica;
import logica.NivelIngles;
import logica.Pregunta;
import logica.RecursoNoEncontradoException;
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

@WebServlet(name = "DocentePracticaServlet", urlPatterns = "/app/docente/practica")
public class DocentePracticaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/practica";
    private static final int MAX_TITULO = 150;
    private static final int MAX_DESCRIPCION = 500;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = Validador.parsearId(req.getParameter("id"));
        if (id < 0) {
            mostrarLista(req, resp, docente);
            return;
        }
        MaterialPractica material = materialDelDocente(docente, id);
        List<Pregunta> incluidas = materialDAO.listarPreguntas(id);
        List<Curso> cursosIncluidos = materialDAO.listarCursos(id);
        int noAprobadas = materialDAO.contarPreguntasNoAprobadas(id);

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(id));
        datos.put("titulo", material.tituloMaterialPractica);
        datos.put("descripcion", material.descripcionMaterialPractica);
        datos.put("estado", Etiquetas.de(material.estadoMaterialPractica));
        datos.put("claseEstado", claseEstado(material.estadoMaterialPractica));
        datos.put("origen", Etiquetas.de(material.origenMaterialPractica));
        datos.put("totalPreguntas", String.valueOf(incluidas.size()));
        datos.put("avisoPublicacion", avisoPublicacion(material, noAprobadas, incluidas.size()));
        datos.put("urlPdf", ruta(req, RUTA + "/pdf?id=" + id + "&tipo=examen"));
        datos.put("urlClave", ruta(req, RUTA + "/pdf?id=" + id + "&tipo=clave"));
        datos.put("urlWord", ruta(req, RUTA + "/pdf?id=" + id + "&tipo=word"));
        datos.put("accionesEstado", accionesEstado(req, material));
        datos.put("filasIncluidas", filasIncluidas(req, material, incluidas));
        datos.put("filasDisponibles", filasDisponibles(req, docente, material, incluidas));
        datos.put("filasCursos", filasCursos(req, material, cursosIncluidos));
        datos.put("filasCursosDisponibles", filasCursosDisponibles(req, docente, material, cursosIncluidos));
        renderizar(req, resp, "docente-practica.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        try {
            switch (parametro(req, "accion")) {
                case "crear": {
                    String titulo = parametro(req, "titulo");
                    String descripcion = Validador.limpiarTexto(req.getParameter("descripcion") == null
                            ? "" : req.getParameter("descripcion"));
                    NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
                    List<String> errores = new ArrayList<>();
                    Validador.validarLongitud(titulo, 3, MAX_TITULO, "El título debe tener entre 3 y 150 caracteres.", errores);
                    Validador.validarLongitud(descripcion, 0, MAX_DESCRIPCION, "La descripción admite máximo 500 caracteres.", errores);
                    Validador.validarRequerido(nivel, "Selecciona el nivel.", errores);
                    if (!errores.isEmpty()) {
                        throw new ValidacionException(errores);
                    }
                    MaterialPractica material = materialDAO.crearMaterialPractica(docente, titulo, descripcion, nivel);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Material creado. Agrega preguntas y cursos.");
                    redirigir(req, resp, RUTA + "?id=" + material.getIdMaterialPractica());
                    return;
                }
                case "agregarPregunta": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    Pregunta pregunta = preguntaDeDocente(docente, parametroId(req, "idPregunta"));
                    if (pregunta.estadoPregunta != EstadoPregunta.APROBADA) {
                        throw new ValidacionException("Solo se pueden incluir preguntas aprobadas.");
                    }
                    materialDAO.agregarPregunta(material, pregunta.getIdPregunta());
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta incluida en el material.");
                    redirigir(req, resp, RUTA + "?id=" + material.getIdMaterialPractica());
                    return;
                }
                case "quitarPregunta": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    if (materialDAO.contarPreguntas(material.getIdMaterialPractica()) <= 1) {
                        throw new ValidacionException("El material debe conservar al menos una pregunta. "
                                + "Puedes archivarlo si ya no lo usas.");
                    }
                    materialDAO.quitarPregunta(material, parametroId(req, "idPregunta"));
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta quitada del material.");
                    redirigir(req, resp, RUTA + "?id=" + material.getIdMaterialPractica());
                    return;
                }
                case "agregarCurso": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    int idCurso = parametroId(req, "idCurso");
                    verificarPropiedad(cursoDAO.buscarPorIdDeDocente(idCurso, docente.getIdUsuario()).isPresent(),
                            cursoDAO.buscarPorId(idCurso).isPresent(), "Curso no encontrado");
                    materialDAO.agregarCurso(material, idCurso);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Curso agregado al material.");
                    redirigir(req, resp, RUTA + "?id=" + material.getIdMaterialPractica());
                    return;
                }
                case "quitarCurso": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    materialDAO.quitarCurso(material, parametroId(req, "idCurso"));
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Curso quitado del material.");
                    redirigir(req, resp, RUTA + "?id=" + material.getIdMaterialPractica());
                    return;
                }
                case "publicar": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    materialDAO.publicarMaterialPractica(material);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Material publicado. Los estudiantes de sus cursos ya pueden practicar.");
                    redirigir(req, resp, RUTA + "?id=" + material.getIdMaterialPractica());
                    return;
                }
                case "archivar": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    materialDAO.archivarMaterialPractica(material);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Material archivado.");
                    redirigir(req, resp, RUTA + "?id=" + material.getIdMaterialPractica());
                    return;
                }
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
            int id = Validador.parsearId(req.getParameter("id"));
            redirigir(req, resp, id >= 0 ? RUTA + "?id=" + id : RUTA);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private void mostrarLista(HttpServletRequest req, HttpServletResponse resp, Docente docente) throws IOException {
        List<Map<String, String>> filas = new ArrayList<>();
        for (MaterialPractica material : materialDAO.listarPorDocente(docente.getIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, RUTA + "?id=" + material.getIdMaterialPractica()));
            fila.put("titulo", material.tituloMaterialPractica);
            fila.put("preguntas", String.valueOf(materialDAO.contarPreguntas(material.getIdMaterialPractica())));
            fila.put("intentos", String.valueOf(intentoPracticaDAO.contarPorMaterial(material.getIdMaterialPractica())));
            fila.put("estado", Etiquetas.de(material.estadoMaterialPractica));
            fila.put("claseEstado", claseEstado(material.estadoMaterialPractica));
            filas.add(fila);
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("filas", componerFilas("_fila-practica.html", filas,
                filaVacia("5", "Todavía no tienes material de práctica. Crea el primero con el formulario.")));
        datos.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(), null, "Selecciona el nivel"));
        renderizar(req, resp, "docente-practicas.html", datos, null);
    }

    private MaterialPractica materialDelDocente(Docente docente, int id) {
        java.util.Optional<MaterialPractica> propio = materialDAO.buscarPorIdDeDocente(id, docente.getIdUsuario());
        verificarPropiedad(propio.isPresent(), materialDAO.buscarPorId(id).isPresent(), "Material no encontrado");
        return propio.get();
    }

    private Pregunta preguntaDeDocente(Docente docente, int idPregunta) {
        Pregunta pregunta = preguntaDAO.buscarPorId(idPregunta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pregunta no encontrada"));
        verificarPropiedad(preguntaDAO.esDeDocente(idPregunta, docente.getIdUsuario()), true,
                "Pregunta no encontrada");
        return pregunta;
    }

    private String filasIncluidas(HttpServletRequest req, MaterialPractica material, List<Pregunta> incluidas) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (Pregunta pregunta : incluidas) {
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", Html.resumir(pregunta.enunciadoPregunta, 110));
            fila.put("meta", Etiquetas.de(pregunta.tipoPregunta) + " · " + Etiquetas.de(pregunta.nivelPregunta)
                    + " · " + Etiquetas.de(pregunta.dificultadPregunta) + " · "
                    + Etiquetas.de(pregunta.estadoPregunta));
            fila.put("accion", boton(req, RUTA, material.getIdMaterialPractica(), "quitarPregunta", "Quitar",
                    "boton-peligro boton-pequeno", "idPregunta", String.valueOf(pregunta.getIdPregunta())));
            filas.add(fila);
        }
        return componerFilas("_fila-practica-pregunta.html", filas,
                filaVacia("3", "El material no tiene preguntas. Agrega preguntas aprobadas."));
    }

    private String filasDisponibles(HttpServletRequest req, Docente docente, MaterialPractica material,
                                    List<Pregunta> incluidas) {
        List<Integer> ids = new ArrayList<>();
        for (Pregunta pregunta : incluidas) {
            ids.add(pregunta.getIdPregunta());
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Pregunta pregunta : preguntaDAO.listarAprobadasDeDocente(docente.getIdUsuario())) {
            if (ids.contains(pregunta.getIdPregunta())) {
                continue;
            }
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", Html.resumir(pregunta.enunciadoPregunta, 110));
            fila.put("meta", Etiquetas.de(pregunta.tipoPregunta) + " · " + Etiquetas.de(pregunta.nivelPregunta)
                    + " · " + Etiquetas.de(pregunta.dificultadPregunta));
            fila.put("accion", boton(req, RUTA, material.getIdMaterialPractica(), "agregarPregunta", "Agregar",
                    "boton-contorno boton-pequeno", "idPregunta", String.valueOf(pregunta.getIdPregunta())));
            filas.add(fila);
        }
        return componerFilas("_fila-practica-pregunta.html", filas,
                filaVacia("3", "No hay preguntas aprobadas disponibles."));
    }

    private String filasCursos(HttpServletRequest req, MaterialPractica material, List<Curso> cursos) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (Curso curso : cursos) {
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", curso.nombreCurso);
            fila.put("meta", "Curso");
            fila.put("accion", boton(req, RUTA, material.getIdMaterialPractica(), "quitarCurso", "Quitar",
                    "boton-peligro boton-pequeno", "idCurso", String.valueOf(curso.getIdCurso())));
            filas.add(fila);
        }
        return componerFilas("_fila-practica-pregunta.html", filas,
                filaVacia("3", "El material no está ofrecido a ningún curso."));
    }

    private String filasCursosDisponibles(HttpServletRequest req, Docente docente, MaterialPractica material,
                                          List<Curso> incluidos) {
        List<Integer> ids = new ArrayList<>();
        for (Curso curso : incluidos) {
            ids.add(curso.getIdCurso());
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Curso curso : cursoDAO.listarPorDocente(docente.getIdUsuario())) {
            if (ids.contains(curso.getIdCurso())) {
                continue;
            }
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", curso.nombreCurso);
            fila.put("meta", Etiquetas.de(curso.nivelCurso) + (curso.activoCurso ? "" : " · Inactivo"));
            fila.put("accion", boton(req, RUTA, material.getIdMaterialPractica(), "agregarCurso", "Agregar",
                    "boton-contorno boton-pequeno", "idCurso", String.valueOf(curso.getIdCurso())));
            filas.add(fila);
        }
        return componerFilas("_fila-practica-pregunta.html", filas,
                filaVacia("3", "No hay cursos disponibles."));
    }

    /**
     * RF-30/RF-57: aviso de estado de publicación. Un material publicado con preguntas no
     * aprobadas no puede usarse por estudiantes; uno en borrador con preguntas no aprobadas
     * no puede publicarse.
     */
    private String avisoPublicacion(MaterialPractica material, int noAprobadas, int total) {
        if (noAprobadas == 0) {
            return "";
        }
        String detalle = noAprobadas + " de " + total + " preguntas no están aprobadas.";
        if (material.estadoMaterialPractica == EstadoMaterial.PUBLICADO) {
            return Html.alerta(vistas, "alerta-error", "Este material está publicado, pero " + detalle
                    + " Los estudiantes no pueden practicarlo hasta que todas vuelvan a estar aprobadas.");
        }
        if (material.estadoMaterialPractica == EstadoMaterial.BORRADOR) {
            return Html.alerta(vistas, "alerta-info", "Para publicar este material, todas las preguntas deben "
                    + "estar aprobadas. " + detalle + " Apruébalas o quítalas para publicar.");
        }
        return "";
    }

    private String accionesEstado(HttpServletRequest req, MaterialPractica material) {
        int id = material.getIdMaterialPractica();
        StringBuilder sb = new StringBuilder();
        if (material.estadoMaterialPractica == EstadoMaterial.BORRADOR) {
            sb.append(boton(req, RUTA, id, "publicar", "Publicar", ""));
        }
        if (material.estadoMaterialPractica != EstadoMaterial.ARCHIVADO) {
            sb.append(boton(req, RUTA, id, "archivar", "Archivar", "boton-contorno"));
        }
        return sb.toString();
    }

    static String claseEstado(EstadoMaterial estado) {
        switch (estado) {
            case PUBLICADO:
                return "insignia-activo";
            case BORRADOR:
                return "insignia-borrador";
            default:
                return "insignia-neutra";
        }
    }
}
