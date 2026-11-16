package servlet;

import logica.BancoPreguntas;
import logica.Competencia;
import logica.Dificultad;
import logica.Docente;
import logica.EstadoPregunta;
import logica.NivelIngles;
import logica.OpcionRespuesta;
import logica.Pregunta;
import logica.RecursoNoEncontradoException;
import logica.TipoPregunta;
import logica.ValidacionException;
import logica.Validador;
import modelo.PreguntaDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet(name = "DocentePreguntaServlet", urlPatterns = "/app/docente/pregunta")
public class DocentePreguntaServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/pregunta";
    private static final String AVISO_BORRADOR = " La pregunta volvió a estado BORRADOR porque su contenido fue "
            + "modificado. Debes revisarla y aprobarla nuevamente antes de que pueda utilizarse.";
    private static final String AVISO_BORRADOR_MATERIAL = " Mientras no vuelva a estar APROBADA, los materiales "
            + "publicados que la contienen no estarán disponibles para los estudiantes.";
    /** TEXT permite 65535 bytes: límite técnico, no académico (RF-19). */
    private static final int MAX_ENUNCIADO_TECNICO = 65535;
    private static final int MAX_TEMA = 120;
    private static final int MAX_RESPUESTA = 500;
    private static final int MAX_TEXTO_OPCION = 500;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = parametroId(req, "id");
        Pregunta pregunta = preguntaDelDocente(docente, id);
        Integer idBanco = pregunta.idBancoPreguntas;
        BancoPreguntas banco = idBanco == null ? null : bancoDAO.buscarPorIdDeDocente(idBanco, docente.consultarIdUsuario()).orElse(null);
        Map<String, String> formulario = recuperarFormulario(req);
        boolean protegida = preguntaDAO.estaProtegida(pregunta);
        boolean editable = pregunta.estadoPregunta != EstadoPregunta.ARCHIVADA && !protegida;
        Map<String, String> datos = new HashMap<>();
        datos.put("bancoNombre", banco == null ? "Mis preguntas" : banco.nombreBancoPreguntas);
        datos.put("bancoUrl", ruta(req, banco == null ? "/app/docente/preguntas" : "/app/docente/banco?id=" + idBanco));
        StringBuilder regreso = new StringBuilder();
        for (Integer identificador : preguntaDAO.consultarEvaluacionesPropias(pregunta)) {
            regreso.append(vistas.componer("_enlace-accion.html", Map.of("clase", "boton boton-contorno",
                    "url", ruta(req, "/app/docente/evaluacion?id=" + identificador), "texto", "Volver a evaluación " + identificador)));
        }
        for (Integer identificador : preguntaDAO.consultarMaterialesPropios(pregunta)) {
            regreso.append(vistas.componer("_enlace-accion.html", Map.of("clase", "boton boton-contorno",
                    "url", ruta(req, "/app/docente/practica?id=" + identificador), "texto", "Volver a material " + identificador)));
        }
        datos.put("volverEvaluaciones", regreso.toString());
        datos.put("proteccion", protegida ? Html.alerta(vistas, "alerta-info",
                "El contenido está protegido porque se comparte o tiene referencias históricas. Desde la evaluación o el material puedes crear una copia para editarla sin alterar el original.") : "");
        StringBuilder bancos = new StringBuilder(Formulario.opcion(vistas, "", "Sin banco", idBanco == null));
        for (BancoPreguntas propio : bancoDAO.listarPorDocente(docente.consultarIdUsuario())) {
            bancos.append(Formulario.opcion(vistas, String.valueOf(propio.consultarIdBancoPreguntas()), propio.nombreBancoPreguntas,
                    java.util.Objects.equals(idBanco, propio.consultarIdBancoPreguntas())));
        }
        datos.put("opcionesBancos", bancos.toString());
        datos.put("idPregunta", String.valueOf(id));
        datos.put("tipo", Etiquetas.de(pregunta.tipoPregunta));
        datos.put("origen", Etiquetas.de(pregunta.origenPregunta));
        datos.put("estado", Etiquetas.de(pregunta.estadoPregunta));
        datos.put("claseEstado", DocenteBancoServlet.claseEstado(pregunta.estadoPregunta));
        datos.put("datosPregunta", seccionDatos(req, pregunta, formulario, editable));
        datos.put("seccionRespuesta", DocenteBancoServlet.usaOpciones(pregunta.tipoPregunta)
                ? seccionOpciones(req, pregunta, formulario, editable)
                : seccionRespuesta(req, pregunta, formulario, editable));
        datos.put("pendientes", pendientes(pregunta));
        datos.put("accionesEstado", accionesEstado(req, pregunta));
        renderizar(req, resp, "docente-pregunta.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int id = parametroId(req, "id");
        String destino = RUTA + "?id=" + id;

        preguntaDelDocente(docente, id);
        switch (parametro(req, "accion")) {
            case "organizarBanco": {
                String valor = parametro(req, "idBanco");
                Integer idBanco = valor.isBlank() ? null : parametroId(req, "idBanco");
                if (idBanco != null) verificarPropiedad(bancoDAO.buscarPorIdDeDocente(idBanco, docente.consultarIdUsuario()).isPresent(), true, "Banco no encontrado");
                preguntaDAO.organizarEnBanco(preguntaDelDocente(docente, id), idBanco);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Organización actualizada.");
                redirigir(req, resp, destino);
                break;
            }
            case "actualizar": {
                String enunciado = Validador.limpiarTexto(req.getParameter("enunciado") == null ? "" : req.getParameter("enunciado"));
                String tema = parametro(req, "tema");
                NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
                Dificultad dificultad = Formulario.enumeracion(Dificultad.class, parametro(req, "dificultad"));
                Competencia competencia = Formulario.enumeracion(Competencia.class, parametro(req, "competencia"));

                List<String> errores = new ArrayList<>();
                Validador.validarLongitud(enunciado, 5, MAX_ENUNCIADO_TECNICO,
                        "El enunciado debe tener al menos 5 caracteres.", errores);
                Validador.validarRequerido(nivel, "Selecciona el nivel.", errores);
                Validador.validarRequerido(dificultad, "Selecciona la dificultad.", errores);
                Validador.validarRequerido(competencia, "Selecciona la competencia.", errores);
                Validador.validarLongitud(tema, 2, MAX_TEMA, "El tema debe tener entre 2 y 120 caracteres.", errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("enunciado", enunciado, "tema", tema,
                            "nivel", nivel == null ? "" : nivel.name(),
                            "dificultad", dificultad == null ? "" : dificultad.name(),
                            "competencia", competencia == null ? "" : competencia.name()));
                    redirigir(req, resp, destino);
                    break;
                }
                ejecutar(req, resp, destino, null, () -> {
                    Pregunta pregunta = preguntaDelDocente(docente, id);
                    EstadoPregunta anterior = pregunta.estadoPregunta;
                    preguntaDAO.actualizarPregunta(pregunta, enunciado, nivel, dificultad, competencia, tema);
                    return mensaje("Cambios guardados.", pregunta, anterior);
                });
                break;
            }
            case "agregarOpcion": {
                String texto = Validador.limpiarTexto(req.getParameter("texto") == null ? "" : req.getParameter("texto"));
                boolean correcta = "true".equals(req.getParameter("esCorrecta"));
                List<String> errores = new ArrayList<>();
                Validador.validarLongitud(texto, 1, MAX_TEXTO_OPCION, "La opción debe tener entre 1 y 500 caracteres.", errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("textoOpcion", texto));
                    redirigir(req, resp, destino);
                    break;
                }
                ejecutar(req, resp, destino, Map.of("textoOpcion", texto), () -> {
                    Pregunta pregunta = preguntaDelDocente(docente, id);
                    EstadoPregunta anterior = pregunta.estadoPregunta;
                    opcionDAO.crearOpcionRespuesta(pregunta, texto, correcta);
                    return mensaje("Opción agregada.", pregunta, anterior);
                });
                break;
            }
            case "eliminarOpcion": {
                int idOpcion = parametroId(req, "idOpcion");
                ejecutar(req, resp, destino, null, () -> {
                    Pregunta pregunta = preguntaDelDocente(docente, id);
                    EstadoPregunta anterior = pregunta.estadoPregunta;
                    opcionDAO.eliminarOpcionRespuesta(pregunta, opcionDe(pregunta, idOpcion));
                    return mensaje("Opción eliminada.", pregunta, anterior);
                });
                break;
            }
            case "marcarCorrecta": {
                int idOpcion = parametroId(req, "idOpcion");
                ejecutar(req, resp, destino, null, () -> {
                    Pregunta pregunta = preguntaDelDocente(docente, id);
                    EstadoPregunta anterior = pregunta.estadoPregunta;
                    opcionDAO.marcarCorrectaOpcionRespuesta(pregunta, opcionDe(pregunta, idOpcion));
                    return mensaje("Respuesta correcta actualizada.", pregunta, anterior);
                });
                break;
            }
            case "actualizarOpcion": {
                int idOpcion = parametroId(req, "idOpcion");
                String texto = Validador.limpiarTexto(req.getParameter("texto") == null ? "" : req.getParameter("texto"));
                boolean correcta = "true".equals(req.getParameter("esCorrecta"));
                List<String> errores = new ArrayList<>();
                Validador.validarLongitud(texto, 1, MAX_TEXTO_OPCION,
                        "La opción debe tener entre 1 y 500 caracteres.", errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("textoOpcion", texto));
                    redirigir(req, resp, destino);
                    break;
                }
                ejecutar(req, resp, destino, null, () -> {
                    Pregunta pregunta = preguntaDelDocente(docente, id);
                    EstadoPregunta anterior = pregunta.estadoPregunta;
                    OpcionRespuesta opcion = opcionDe(pregunta, idOpcion);
                    if (correcta && !opcion.esCorrectaOpcionRespuesta) {
                        opcionDAO.marcarCorrectaOpcionRespuesta(pregunta, opcion);
                    }
                    opcionDAO.actualizarOpcionRespuesta(pregunta, opcion, texto);
                    return mensaje("Opción actualizada.", pregunta, anterior);
                });
                break;
            }
            case "respuestaEsperada": {
                String respuesta = Validador.limpiarTexto(req.getParameter("respuestaEsperada") == null
                        ? "" : req.getParameter("respuestaEsperada"));
                List<String> errores = new ArrayList<>();
                Validador.validarLongitud(respuesta, 0, MAX_RESPUESTA, "La respuesta admite máximo 500 caracteres.", errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("respuestaEsperada", respuesta));
                    redirigir(req, resp, destino);
                    break;
                }
                ejecutar(req, resp, destino, Map.of("respuestaEsperada", respuesta), () -> {
                    Pregunta pregunta = preguntaDelDocente(docente, id);
                    EstadoPregunta anterior = pregunta.estadoPregunta;
                    preguntaDAO.definirRespuestaEsperada(pregunta, respuesta);
                    return mensaje("Respuesta guardada.", pregunta, anterior);
                });
                break;
            }
            case "aprobar":
                ejecutar(req, resp, destino, null, () -> {
                    preguntaDAO.aprobarPregunta(preguntaDelDocente(docente, id));
                    return "Pregunta aprobada. Ya puede usarse en evaluaciones.";
                });
                break;
            case "archivar":
                ejecutar(req, resp, destino, null, () -> {
                    preguntaDAO.archivarPregunta(preguntaDelDocente(docente, id));
                    return "Pregunta archivada. No se usará en evaluaciones nuevas.";
                });
                break;
            case "restaurar":
                ejecutar(req, resp, destino, null, () -> {
                    preguntaDAO.restaurarPregunta(preguntaDelDocente(docente, id));
                    return "Pregunta restaurada. Su disponibilidad depende de la validación y del origen.";
                });
                break;
            case "eliminar": {
                Pregunta pregunta = preguntaDelDocente(docente, id);
                Integer idBanco = preguntaDAO.idBancoDePregunta(id);
                PreguntaDAO.ResultadoEliminacionPregunta resultado = preguntaDAO.eliminarSiNoUsada(pregunta);
                if (resultado == PreguntaDAO.ResultadoEliminacionPregunta.EN_USO) {
                    agregarErrores(req, List.of("La pregunta ya está en uso y no puede eliminarse. "
                            + "Puedes archivarla."));
                    redirigir(req, resp, destino);
                    break;
                }
                if (resultado == PreguntaDAO.ResultadoEliminacionPregunta.NO_ENCONTRADA) {
                    throw new RecursoNoEncontradoException("Pregunta no encontrada");
                }
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Pregunta eliminada.");
                redirigir(req, resp, idBanco == null ? "/app/docente/preguntas" : "/app/docente/banco?id=" + idBanco);
                break;
            }
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /** Mensaje de la acción; si la edición devolvió la pregunta a BORRADOR, lo explica al docente. */
    private String mensaje(String base, Pregunta pregunta, EstadoPregunta anterior) {
        if (anterior != EstadoPregunta.APROBADA || pregunta.estadoPregunta != EstadoPregunta.BORRADOR) {
            return base;
        }
        StringBuilder aviso = new StringBuilder(base).append(pregunta.origenPregunta == logica.OrigenContenido.MANUAL
                ? " Completa los datos pendientes; se validará automáticamente." : AVISO_BORRADOR);
        if (materialDAO.estaEnMaterialPublicado(pregunta.consultarIdPregunta())) {
            aviso.append(AVISO_BORRADOR_MATERIAL);
        }
        return aviso.toString();
    }

    private String seccionDatos(HttpServletRequest req, Pregunta p, Map<String, String> formulario, boolean editable) {
        Map<String, String> d = new HashMap<>();
        d.put("ctx", req.getContextPath());
        d.put("csrf", csrf(req));
        d.put("id", String.valueOf(p.consultarIdPregunta()));
        if (!editable) {
            d.put("enunciado", p.enunciadoPregunta);
            d.put("nivel", Etiquetas.de(p.nivelPregunta));
            d.put("dificultad", Etiquetas.de(p.dificultadPregunta));
            d.put("competencia", Etiquetas.de(p.competenciaPregunta));
            d.put("tema", p.temaPregunta);
            return vistas.componer("_pregunta-datos-lectura.html", d);
        }
        d.put("enunciado", formulario.getOrDefault("enunciado", p.enunciadoPregunta));
        d.put("tema", formulario.getOrDefault("tema", p.temaPregunta));
        d.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(),
                formulario.containsKey("nivel") ? Formulario.enumeracion(NivelIngles.class, formulario.get("nivel")) : p.nivelPregunta, null));
        d.put("opcionesDificultad", Formulario.opciones(vistas, Dificultad.values(),
                formulario.containsKey("dificultad") ? Formulario.enumeracion(Dificultad.class, formulario.get("dificultad")) : p.dificultadPregunta, null));
        d.put("opcionesCompetencia", Formulario.opciones(vistas, Competencia.values(),
                formulario.containsKey("competencia") ? Formulario.enumeracion(Competencia.class, formulario.get("competencia")) : p.competenciaPregunta, null));
        d.put("avisoAprobada", p.estadoPregunta == EstadoPregunta.APROBADA && p.origenPregunta == logica.OrigenContenido.GENERADO_IA
                ? parrafoAyuda("Si guardas cambios, la pregunta vuelve a borrador para que la revises antes de usarla.") : "");
        return vistas.componer("_pregunta-datos.html", d);
    }

    private String seccionOpciones(HttpServletRequest req, Pregunta p, Map<String, String> formulario, boolean editable) {
        int id = p.consultarIdPregunta();
        boolean esSeleccion = p.tipoPregunta == TipoPregunta.SELECCION_MULTIPLE;
        List<Map<String, String>> filas = new ArrayList<>();
        for (OpcionRespuesta o : p.opcionesPregunta) {
            Map<String, String> fila = new HashMap<>();
            fila.put("texto", o.textoOpcionRespuesta);
            fila.put("claseCorrecta", o.esCorrectaOpcionRespuesta ? "opcion-correcta" : "");
            fila.put("insignia", o.esCorrectaOpcionRespuesta
                    ? vistas.componer("_insignia.html", Map.of("clase", "insignia-activo", "texto", "Correcta"))
                    : "");
            StringBuilder acciones = new StringBuilder();
            if (editable) {
                String idOpcion = String.valueOf(o.consultarIdOpcionRespuesta());
                if (!o.esCorrectaOpcionRespuesta) {
                    acciones.append(boton(req, RUTA, id, "marcarCorrecta", "Marcar correcta", "boton-contorno boton-pequeno", "idOpcion", idOpcion));
                }
                Map<String, String> enlace = new HashMap<>();
                enlace.put("clase", "boton boton-contorno boton-pequeno");
                enlace.put("url", ruta(req, RUTA + "?id=" + id + "&opcion=" + idOpcion));
                enlace.put("texto", "Editar");
                acciones.append(vistas.componer("_enlace-accion.html", enlace));
                if (esSeleccion) {
                    acciones.append(boton(req, RUTA, id, "eliminarOpcion", "Eliminar", "boton-peligro boton-pequeno", "idOpcion", idOpcion));
                }
            }
            fila.put("acciones", acciones.toString());
            filas.add(fila);
        }

        Map<String, String> d = new HashMap<>();
        d.put("ayuda", esSeleccion
                ? "Mínimo dos opciones, con exactamente una correcta (sin máximo artificial)."
                : "Marca cuál de las dos es la respuesta correcta.");
        d.put("filas", componerFilas("_fila-opcion.html", filas,
                opcionVacia("Todavía no hay opciones. Agrega al menos dos.")));
        if (editable && esSeleccion) {
            Map<String, String> form = new HashMap<>();
            form.put("destino", ruta(req, RUTA));
            form.put("ctx", req.getContextPath());
            form.put("csrf", csrf(req));
            form.put("id", String.valueOf(id));
            form.put("textoOpcion", formulario.getOrDefault("textoOpcion", ""));
            d.put("formAgregar", vistas.componer("_form-agregar-opcion.html", form));
        } else {
            d.put("formAgregar", "");
        }
        String opcionesHtml = vistas.componer("_pregunta-opciones.html", d);

        // RF-20: edición del texto de una opción (se muestra sobre el listado).
        int idEdicion = Validador.parsearId(parametro(req, "opcion"));
        OpcionRespuesta enEdicion = null;
        for (OpcionRespuesta o : p.opcionesPregunta) {
            if (o.consultarIdOpcionRespuesta() == idEdicion) {
                enEdicion = o;
                break;
            }
        }
        if (!editable || enEdicion == null) {
            return opcionesHtml;
        }
        Map<String, String> edicion = new HashMap<>();
        edicion.put("destino", ruta(req, RUTA));
        edicion.put("csrf", csrf(req));
        edicion.put("id", String.valueOf(id));
        edicion.put("idOpcion", String.valueOf(enEdicion.consultarIdOpcionRespuesta()));
        edicion.put("texto", enEdicion.textoOpcionRespuesta);
        edicion.put("marcada", enEdicion.esCorrectaOpcionRespuesta ? "checked" : "");
        edicion.put("volver", ruta(req, RUTA + "?id=" + id));
        return vistas.componer("_form-editar-opcion.html", edicion) + opcionesHtml;
    }

    private String seccionRespuesta(HttpServletRequest req, Pregunta p, Map<String, String> formulario, boolean editable) {
        boolean corta = p.tipoPregunta == TipoPregunta.RESPUESTA_CORTA;
        Map<String, String> d = new HashMap<>();
        d.put("ctx", req.getContextPath());
        d.put("csrf", csrf(req));
        d.put("id", String.valueOf(p.consultarIdPregunta()));
        d.put("titulo", corta ? "Respuesta esperada" : "Guía de calificación");
        d.put("respuesta", formulario.getOrDefault("respuestaEsperada", p.respuestaEsperadaPregunta == null ? "" : p.respuestaEsperadaPregunta));
        if (!editable) {
            if (p.respuestaEsperadaPregunta == null) {
                d.put("respuesta", "Sin definir.");
            }
            return vistas.componer("_pregunta-respuesta-lectura.html", d);
        }
        d.put("etiqueta", corta ? "Respuestas aceptadas" : "Qué esperas en la respuesta (opcional)");
        d.put("ayuda", corta
                ? "Se comparará sin distinguir mayúsculas ni espacios extra. Si aceptas varias formas, sepáralas con punto y coma, por ejemplo: went; has gone."
                : "Te servirá al calificar manualmente. Los estudiantes no la ven.");
        return vistas.componer("_pregunta-respuesta.html", d);
    }

    private String pendientes(Pregunta p) {
        if (p.estadoPregunta != EstadoPregunta.BORRADOR) {
            return "";
        }
        List<String> faltantes = preguntaDAO.faltantesPregunta(p);
        if (faltantes.isEmpty()) {
            return Html.alerta(vistas, "alerta-info", p.origenPregunta == logica.OrigenContenido.GENERADO_IA
                    ? "Revisa el contenido generado y apruébalo expresamente para utilizarlo."
                    : "La pregunta está completa. Guarda sus datos para validar su disponibilidad.");
        }
        return Html.alertaConLista(vistas, "alerta-info", "Para aprobar esta pregunta falta:", faltantes);
    }

    private String accionesEstado(HttpServletRequest req, Pregunta p) {
        int id = p.consultarIdPregunta();
        StringBuilder sb = new StringBuilder();
        switch (p.estadoPregunta) {
            case BORRADOR:
                if (p.origenPregunta == logica.OrigenContenido.GENERADO_IA) sb.append(boton(req, RUTA, id, "aprobar", "Aprobar", ""));
                sb.append(boton(req, RUTA, id, "archivar", "Archivar", "boton-contorno"));
                break;
            case APROBADA:
                sb.append(boton(req, RUTA, id, "archivar", "Archivar", "boton-contorno"));
                break;
            default:
                sb.append(boton(req, RUTA, id, "restaurar", "Restaurar", "boton-contorno"));
        }
        sb.append(boton(req, RUTA, id, "eliminar", "Eliminar pregunta", "boton-peligro"));
        return sb.toString();
    }

    private Pregunta preguntaDelDocente(Docente docente, int idPregunta) {
        Pregunta pregunta = preguntaDAO.buscarPorId(idPregunta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pregunta no encontrada"));
        verificarPropiedad(preguntaDAO.esDeDocente(idPregunta, docente.consultarIdUsuario()), true,
                "Pregunta no encontrada");
        return pregunta;
    }

    private OpcionRespuesta opcionDe(Pregunta pregunta, int idOpcion) throws ValidacionException {
        for (OpcionRespuesta opcion : pregunta.opcionesPregunta) {
            if (opcion.consultarIdOpcionRespuesta() == idOpcion) {
                return opcion;
            }
        }
        throw new ValidacionException("La opción indicada no pertenece a esta pregunta.");
    }
}
