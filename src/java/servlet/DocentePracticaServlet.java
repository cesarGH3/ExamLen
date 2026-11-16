package servlet;

import logica.Competencia;
import logica.Curso;
import logica.Dificultad;
import logica.Docente;
import logica.EstadoMaterial;
import logica.EstadoPregunta;
import logica.MaterialPractica;
import logica.NivelIngles;
import logica.Pregunta;
import logica.TipoPregunta;
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
        datos.put("nuevaPregunta", formularioNuevaPregunta(req, material));
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
                    redirigir(req, resp, RUTA + "?id=" + material.consultarIdMaterialPractica());
                    return;
                }
                case "crearPregunta": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    if (material.estadoMaterialPractica == EstadoMaterial.ARCHIVADO) {
                        throw new ValidacionException("El material está archivado y no se puede editar.");
                    }
                    String enunciado = Validador.limpiarTexto(req.getParameter("enunciado") == null
                            ? "" : req.getParameter("enunciado"));
                    String tema = parametro(req, "tema");
                    TipoPregunta tipo = Formulario.enumeracion(TipoPregunta.class, parametro(req, "tipo"));
                    NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
                    Dificultad dificultad = Formulario.enumeracion(Dificultad.class, parametro(req, "dificultad"));
                    Competencia competencia = Formulario.enumeracion(Competencia.class, parametro(req, "competencia"));
                    List<String> errores = new ArrayList<>();
                    Validador.validarLongitud(enunciado, 5, 65535,
                            "El enunciado debe tener entre 5 y 65535 caracteres.", errores);
                    Validador.validarLongitud(tema, 2, 120,
                            "El tema debe tener entre 2 y 120 caracteres.", errores);
                    Validador.validarRequerido(tipo, "Selecciona el tipo.", errores);
                    Validador.validarRequerido(nivel, "Selecciona el nivel.", errores);
                    Validador.validarRequerido(dificultad, "Selecciona la dificultad.", errores);
                    Validador.validarRequerido(competencia, "Selecciona la competencia.", errores);
                    if (!errores.isEmpty()) {
                        guardarFormulario(req, valoresPregunta(req));
                        throw new ValidacionException(errores);
                    }
                    Pregunta creada = preguntaDAO.crearEnMaterial(docente.consultarIdUsuario(),
                            material.consultarIdMaterialPractica(), enunciado, tipo, nivel, dificultad, competencia, tema);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                            "Pregunta creada en el material. Completa sus respuestas.");
                    redirigir(req, resp, "/app/docente/pregunta?id=" + creada.consultarIdPregunta());
                    return;
                }
                case "copiarPregunta": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    Pregunta copia = preguntaDAO.copiarParaMaterial(docente.consultarIdUsuario(),
                            material.consultarIdMaterialPractica(), parametroId(req, "idPregunta"));
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO,
                            "Copia creada para este material. El original conserva sus referencias.");
                    redirigir(req, resp, "/app/docente/pregunta?id=" + copia.consultarIdPregunta());
                    return;
                }
                case "agregarPregunta": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    Pregunta pregunta = preguntaDeDocente(docente, parametroId(req, "idPregunta"));
                    if (pregunta.estadoPregunta != EstadoPregunta.APROBADA) {
                        throw new ValidacionException("Solo se pueden incluir preguntas aprobadas.");
                    }
                    materialDAO.agregarPregunta(material, pregunta.consultarIdPregunta());
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta incluida en el material.");
                    redirigir(req, resp, RUTA + "?id=" + material.consultarIdMaterialPractica());
                    return;
                }
                case "quitarPregunta": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    materialDAO.quitarPregunta(material, parametroId(req, "idPregunta"));
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta quitada del material.");
                    redirigir(req, resp, RUTA + "?id=" + material.consultarIdMaterialPractica());
                    return;
                }
                case "agregarCurso": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    int idCurso = parametroId(req, "idCurso");
                    verificarPropiedad(cursoDAO.buscarPorIdDeDocente(idCurso, docente.consultarIdUsuario()).isPresent(),
                            cursoDAO.buscarPorId(idCurso).isPresent(), "Curso no encontrado");
                    materialDAO.agregarCurso(material, idCurso);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Curso agregado al material.");
                    redirigir(req, resp, RUTA + "?id=" + material.consultarIdMaterialPractica());
                    return;
                }
                case "quitarCurso": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    materialDAO.quitarCurso(material, parametroId(req, "idCurso"));
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Curso quitado del material.");
                    redirigir(req, resp, RUTA + "?id=" + material.consultarIdMaterialPractica());
                    return;
                }
                case "publicar": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    materialDAO.publicarMaterialPractica(material);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Material publicado. Los estudiantes de sus cursos ya pueden practicar.");
                    redirigir(req, resp, RUTA + "?id=" + material.consultarIdMaterialPractica());
                    return;
                }
                case "archivar": {
                    MaterialPractica material = materialDelDocente(docente, parametroId(req, "id"));
                    materialDAO.archivarMaterialPractica(material);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Material archivado.");
                    redirigir(req, resp, RUTA + "?id=" + material.consultarIdMaterialPractica());
                    return;
                }
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.consultarErrores());
            int id = Validador.parsearId(req.getParameter("id"));
            redirigir(req, resp, id >= 0 ? RUTA + "?id=" + id : RUTA);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private String formularioNuevaPregunta(HttpServletRequest req, MaterialPractica material) {
        if (material.estadoMaterialPractica == EstadoMaterial.ARCHIVADO) {
            return "";
        }
        Map<String, String> guardado = recuperarFormulario(req);
        Map<String, String> datos = new HashMap<>();
        datos.put("ctx", req.getContextPath());
        datos.put("csrf", csrf(req));
        datos.put("id", String.valueOf(material.consultarIdMaterialPractica()));
        datos.put("destino", ruta(req, RUTA));
        datos.put("tituloNuevaPregunta", "Crear pregunta en este material");
        datos.put("ayudaNuevaPregunta", "Completa la pregunta y sus respuestas. No necesitas un banco.");
        datos.put("enunciado", guardado.getOrDefault("enunciado", ""));
        datos.put("tema", guardado.getOrDefault("tema", ""));
        datos.put("opcionesTipo", Formulario.opciones(vistas, TipoPregunta.values(),
                Formulario.enumeracion(TipoPregunta.class, guardado.get("tipo")), "Selecciona"));
        datos.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(),
                Formulario.enumeracion(NivelIngles.class, guardado.get("nivel")), "Selecciona"));
        datos.put("opcionesDificultad", Formulario.opciones(vistas, Dificultad.values(),
                Formulario.enumeracion(Dificultad.class, guardado.get("dificultad")), "Selecciona"));
        datos.put("opcionesCompetencia", Formulario.opciones(vistas, Competencia.values(),
                Formulario.enumeracion(Competencia.class, guardado.get("competencia")), "Selecciona"));
        return vistas.componer("_evaluacion-nueva-pregunta.html", datos);
    }

    private Map<String, String> valoresPregunta(HttpServletRequest req) {
        Map<String, String> valores = new HashMap<>();
        valores.put("enunciado", Validador.limpiarTexto(req.getParameter("enunciado") == null
                ? "" : req.getParameter("enunciado")));
        valores.put("tema", parametro(req, "tema"));
        valores.put("tipo", parametro(req, "tipo"));
        valores.put("nivel", parametro(req, "nivel"));
        valores.put("dificultad", parametro(req, "dificultad"));
        valores.put("competencia", parametro(req, "competencia"));
        return valores;
    }

    private void mostrarLista(HttpServletRequest req, HttpServletResponse resp, Docente docente) throws IOException {
        List<Map<String, String>> filas = new ArrayList<>();
        for (MaterialPractica material : materialDAO.listarPorDocente(docente.consultarIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("url", ruta(req, RUTA + "?id=" + material.consultarIdMaterialPractica()));
            fila.put("titulo", material.tituloMaterialPractica);
            fila.put("preguntas", String.valueOf(materialDAO.contarPreguntas(material.consultarIdMaterialPractica())));
            fila.put("intentos", String.valueOf(intentoPracticaDAO.contarPorMaterial(material.consultarIdMaterialPractica())));
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
        java.util.Optional<MaterialPractica> propio = materialDAO.buscarPorIdDeDocente(id, docente.consultarIdUsuario());
        verificarPropiedad(propio.isPresent(), materialDAO.buscarPorId(id).isPresent(), "Material no encontrado");
        return propio.get();
    }

    private Pregunta preguntaDeDocente(Docente docente, int idPregunta) {
        Pregunta pregunta = preguntaDAO.buscarPorId(idPregunta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pregunta no encontrada"));
        verificarPropiedad(preguntaDAO.esDeDocente(idPregunta, docente.consultarIdUsuario()), true,
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
            String editar = "<a class=\"boton boton-contorno boton-pequeno\" href=\""
                    + ruta(req, "/app/docente/pregunta?id=" + pregunta.consultarIdPregunta()) + "\">Editar</a>";
            String copiar = material.estadoMaterialPractica != EstadoMaterial.ARCHIVADO
                    && preguntaDAO.estaProtegida(pregunta)
                    ? boton(req, RUTA, material.consultarIdMaterialPractica(), "copiarPregunta",
                            "Crear copia para editar aquí", "boton-contorno boton-pequeno",
                            "idPregunta", String.valueOf(pregunta.consultarIdPregunta()))
                    : "";
            String quitar = boton(req, RUTA, material.consultarIdMaterialPractica(), "quitarPregunta", "Quitar",
                    "boton-peligro boton-pequeno", "idPregunta", String.valueOf(pregunta.consultarIdPregunta()));
            fila.put("accion", editar + " " + copiar + " " + quitar);
            filas.add(fila);
        }
        return componerFilas("_fila-practica-pregunta.html", filas,
                filaVacia("3", "El material no tiene preguntas. Agrega preguntas aprobadas."));
    }

    private String filasDisponibles(HttpServletRequest req, Docente docente, MaterialPractica material,
                                    List<Pregunta> incluidas) {
        List<Integer> ids = new ArrayList<>();
        for (Pregunta pregunta : incluidas) {
            ids.add(pregunta.consultarIdPregunta());
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Pregunta pregunta : preguntaDAO.listarAprobadasDeDocente(docente.consultarIdUsuario())) {
            if (ids.contains(pregunta.consultarIdPregunta())) {
                continue;
            }
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", Html.resumir(pregunta.enunciadoPregunta, 110));
            fila.put("meta", Etiquetas.de(pregunta.tipoPregunta) + " · " + Etiquetas.de(pregunta.nivelPregunta)
                    + " · " + Etiquetas.de(pregunta.dificultadPregunta));
            fila.put("accion", boton(req, RUTA, material.consultarIdMaterialPractica(), "agregarPregunta", "Agregar",
                    "boton-contorno boton-pequeno", "idPregunta", String.valueOf(pregunta.consultarIdPregunta())));
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
            fila.put("accion", boton(req, RUTA, material.consultarIdMaterialPractica(), "quitarCurso", "Quitar",
                    "boton-peligro boton-pequeno", "idCurso", String.valueOf(curso.consultarIdCurso())));
            filas.add(fila);
        }
        return componerFilas("_fila-practica-pregunta.html", filas,
                filaVacia("3", "El material no está ofrecido a ningún curso."));
    }

    private String filasCursosDisponibles(HttpServletRequest req, Docente docente, MaterialPractica material,
                                          List<Curso> incluidos) {
        List<Integer> ids = new ArrayList<>();
        for (Curso curso : incluidos) {
            ids.add(curso.consultarIdCurso());
        }
        List<Map<String, String>> filas = new ArrayList<>();
        for (Curso curso : cursoDAO.listarPorDocente(docente.consultarIdUsuario())) {
            if (ids.contains(curso.consultarIdCurso())) {
                continue;
            }
            Map<String, String> fila = new HashMap<>();
            fila.put("enunciado", curso.nombreCurso);
            fila.put("meta", Etiquetas.de(curso.nivelCurso) + (curso.activoCurso ? "" : " · Inactivo"));
            fila.put("accion", boton(req, RUTA, material.consultarIdMaterialPractica(), "agregarCurso", "Agregar",
                    "boton-contorno boton-pequeno", "idCurso", String.valueOf(curso.consultarIdCurso())));
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
        int id = material.consultarIdMaterialPractica();
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
