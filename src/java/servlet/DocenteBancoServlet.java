package servlet;

import logica.BancoPreguntas;
import logica.Competencia;
import logica.CriteriosBusquedaPregunta;
import logica.Dificultad;
import logica.Docente;
import logica.EstadoPregunta;
import logica.NivelIngles;
import logica.OrigenContenido;
import logica.Pregunta;
import logica.TipoPregunta;
import logica.Validador;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CU-15/CU-16: gestiona un banco concreto y sus preguntas, con la búsqueda común de RF-24. */
@WebServlet(name = "DocenteBancoServlet", urlPatterns = "/app/docente/banco")
public class DocenteBancoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/banco";
    private static final int MAX_NOMBRE_BANCO = 100;
    private static final int MAX_DESCRIPCION_BANCO = 500;
    /** TEXT permite 65535 bytes; es un límite técnico, no académico (RF-19). */
    private static final int MAX_ENUNCIADO_TECNICO = 65535;
    private static final int MAX_TEMA = 120;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idBanco = parametroId(req, "id");
        BancoPreguntas banco = bancoDelDocente(docente, idBanco);
        Map<String, String> formulario = recuperarFormulario(req);

        CriteriosBusquedaPregunta criterios = criteriosDe(req, idBanco);
        List<Map<String, String>> filas = new ArrayList<>();
        for (Pregunta p : preguntaDAO.buscarDeDocente(docente.consultarIdUsuario(), criterios)) {
            Map<String, String> fila = filaPregunta(p);
            fila.put("url", ruta(req, fila.get("url")));
            filas.add(fila);
        }
        Map<EstadoPregunta, Integer> porEstado = bancoDAO.contarPorEstado(idBanco);
        int total = porEstado.values().stream().mapToInt(Integer::intValue).sum();

        Map<String, String> datos = new HashMap<>();
        datos.put("id", String.valueOf(idBanco));
        datos.put("bancoNombre", banco.nombreBancoPreguntas);
        datos.put("cantidad", String.valueOf(total));
        datos.put("conteoBorrador", String.valueOf(porEstado.getOrDefault(EstadoPregunta.BORRADOR, 0)));
        datos.put("conteoAprobadas", String.valueOf(porEstado.getOrDefault(EstadoPregunta.APROBADA, 0)));
        datos.put("conteoArchivadas", String.valueOf(porEstado.getOrDefault(EstadoPregunta.ARCHIVADA, 0)));
        datos.put("filasPreguntas", componerFilas("_fila-pregunta.html", filas,
                filaVacia("7", criterios.hayFiltros()
                        ? "Ninguna pregunta de este banco coincide con la búsqueda."
                        : "No hay preguntas en este banco. Crea una con el formulario.")));
        Map<String, String> busqueda = new HashMap<>();
        busqueda.put("destino", ruta(req, RUTA));
        busqueda.put("bancoFijo", vistas.componer("_input-oculto.html", Map.of("nombre", "id",
                "valor", String.valueOf(idBanco))));
        busqueda.put("valorTexto", valor(formulario, req, "texto"));
        busqueda.put("valorTema", valor(formulario, req, "tema"));
        busqueda.put("opcionesFiltroBanco", "");
        busqueda.put("opcionesFiltroTipo", Formulario.opciones(vistas, TipoPregunta.values(),
                Formulario.enumeracion(TipoPregunta.class, req.getParameter("tipo")), "Todos"));
        busqueda.put("opcionesFiltroNivel", Formulario.opciones(vistas, NivelIngles.values(),
                Formulario.enumeracion(NivelIngles.class, req.getParameter("nivel")), "Todos"));
        busqueda.put("opcionesFiltroDificultad", Formulario.opciones(vistas, Dificultad.values(),
                Formulario.enumeracion(Dificultad.class, req.getParameter("dificultad")), "Todas"));
        busqueda.put("opcionesFiltroCompetencia", Formulario.opciones(vistas, Competencia.values(),
                Formulario.enumeracion(Competencia.class, req.getParameter("competencia")), "Todas"));
        busqueda.put("opcionesFiltroEstado", Formulario.opciones(vistas, EstadoPregunta.values(),
                Formulario.enumeracion(EstadoPregunta.class, req.getParameter("estado")), "Todos"));
        busqueda.put("opcionesFiltroOrigen", Formulario.opciones(vistas, OrigenContenido.values(),
                Formulario.enumeracion(OrigenContenido.class, req.getParameter("origen")), "Todos"));
        busqueda.put("limpiarFiltros", criterios.hayFiltros()
                ? vistas.componer("_enlace-accion.html", Map.of("clase", "boton boton-contorno boton-pequeno",
                        "url", ruta(req, RUTA + "?id=" + idBanco), "texto", "Quitar filtros"))
                : "");
        datos.put("formularioBusqueda", vistas.componer("_form-busqueda-preguntas.html", busqueda));
        datos.put("nombre", formulario.getOrDefault("nombre", banco.nombreBancoPreguntas));
        datos.put("descripcion", formulario.getOrDefault("descripcion", banco.descripcionBancoPreguntas));
        datos.put("enunciado", formulario.getOrDefault("enunciado", ""));
        datos.put("tema", formulario.getOrDefault("tema", ""));
        datos.put("opcionesTipo", Formulario.opciones(vistas, TipoPregunta.values(),
                Formulario.enumeracion(TipoPregunta.class, formulario.get("tipo")), "Selecciona el tipo"));
        datos.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(),
                Formulario.enumeracion(NivelIngles.class, formulario.get("nivel")), "Selecciona"));
        datos.put("opcionesDificultad", Formulario.opciones(vistas, Dificultad.values(),
                Formulario.enumeracion(Dificultad.class, formulario.get("dificultad")), "Selecciona"));
        datos.put("opcionesCompetencia", Formulario.opciones(vistas, Competencia.values(),
                Formulario.enumeracion(Competencia.class, formulario.get("competencia")), "Selecciona"));
        renderizar(req, resp, "docente-banco.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        int idBanco = parametroId(req, "id");
        String destino = RUTA + "?id=" + idBanco;
        BancoPreguntas banco = bancoDelDocente(docente, idBanco);

        switch (parametro(req, "accion")) {
            case "actualizar": {
                String nombre = parametro(req, "nombre");
                String descripcion = Validador.limpiarTexto(req.getParameter("descripcion") == null ? "" : req.getParameter("descripcion"));
                List<String> errores = new ArrayList<>();
                Validador.validarLongitud(nombre, 3, MAX_NOMBRE_BANCO, "El nombre del banco debe tener entre 3 y 100 caracteres.", errores);
                Validador.validarLongitud(descripcion, 0, MAX_DESCRIPCION_BANCO, "La descripción admite máximo 500 caracteres.", errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("nombre", nombre, "descripcion", descripcion));
                    redirigir(req, resp, destino);
                    break;
                }
                ejecutar(req, resp, destino, Map.of("nombre", nombre, "descripcion", descripcion), () -> {
                    bancoDAO.actualizarBancoPreguntas(banco, nombre, descripcion);
                    return "Datos del banco actualizados.";
                });
                break;
            }
            case "crearPregunta": {
                String enunciado = Validador.limpiarTexto(req.getParameter("enunciado") == null ? "" : req.getParameter("enunciado"));
                String tema = parametro(req, "tema");
                TipoPregunta tipo = Formulario.enumeracion(TipoPregunta.class, parametro(req, "tipo"));
                NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
                Dificultad dificultad = Formulario.enumeracion(Dificultad.class, parametro(req, "dificultad"));
                Competencia competencia = Formulario.enumeracion(Competencia.class, parametro(req, "competencia"));

                List<String> errores = new ArrayList<>();
                Validador.validarRequerido(tipo, "Selecciona el tipo de pregunta.", errores);
                Validador.validarLongitud(enunciado, 5, MAX_ENUNCIADO_TECNICO,
                        "El enunciado debe tener al menos 5 caracteres.", errores);
                Validador.validarRequerido(nivel, "Selecciona el nivel.", errores);
                Validador.validarRequerido(dificultad, "Selecciona la dificultad.", errores);
                Validador.validarRequerido(competencia, "Selecciona la competencia.", errores);
                Validador.validarLongitud(tema, 2, MAX_TEMA, "El tema debe tener entre 2 y 120 caracteres.", errores);
                if (!errores.isEmpty()) {
                    agregarErrores(req, errores);
                    guardarFormulario(req, Map.of("tipo", tipo == null ? "" : tipo.name(), "nivel", nivel == null ? "" : nivel.name(),
                            "dificultad", dificultad == null ? "" : dificultad.name(),
                            "competencia", competencia == null ? "" : competencia.name(),
                            "enunciado", enunciado, "tema", tema));
                    redirigir(req, resp, destino);
                    break;
                }
                Pregunta pregunta = preguntaDAO.crearPregunta(docente.consultarIdUsuario(), banco.consultarIdBancoPreguntas(), enunciado, tipo, nivel, dificultad, competencia, tema);
                agregarMensaje(req, MensajeFlash.Tipo.EXITO, usaOpciones(tipo)
                        ? "Pregunta creada. Completa sus opciones; se validará automáticamente."
                        : "Pregunta creada. Completa la respuesta cuando corresponda; se validará automáticamente.");
                redirigir(req, resp, "/app/docente/pregunta?id=" + pregunta.consultarIdPregunta());
                break;
            }
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    /** Compone la fila del listado común (banco y búsqueda global); el URL lo pone cada página. */
    static Map<String, String> filaPregunta(Pregunta p) {
        Map<String, String> fila = new HashMap<>();
        fila.put("url", "/app/docente/pregunta?id=" + p.consultarIdPregunta());
        fila.put("enunciado", Html.resumir(p.enunciadoPregunta, 110));
        fila.put("tipo", Etiquetas.de(p.tipoPregunta));
        fila.put("nivel", p.nivelPregunta == null ? "" : p.nivelPregunta.name());
        fila.put("competencia", Etiquetas.de(p.competenciaPregunta));
        fila.put("tema", p.temaPregunta == null ? "" : p.temaPregunta);
        fila.put("origen", Etiquetas.de(p.origenPregunta));
        fila.put("estado", Etiquetas.de(p.estadoPregunta));
        fila.put("claseEstado", claseEstado(p.estadoPregunta));
        return fila;
    }

    static CriteriosBusquedaPregunta criteriosDe(HttpServletRequest req, Integer idBanco) {
        CriteriosBusquedaPregunta criterios = new CriteriosBusquedaPregunta();
        criterios.idBanco = idBanco;
        criterios.texto = textoONulo(req.getParameter("texto"));
        criterios.tema = textoONulo(req.getParameter("tema"));
        criterios.tipo = Formulario.enumeracion(TipoPregunta.class, req.getParameter("tipo"));
        criterios.nivel = Formulario.enumeracion(NivelIngles.class, req.getParameter("nivel"));
        criterios.dificultad = Formulario.enumeracion(Dificultad.class, req.getParameter("dificultad"));
        criterios.competencia = Formulario.enumeracion(Competencia.class, req.getParameter("competencia"));
        criterios.estado = Formulario.enumeracion(EstadoPregunta.class, req.getParameter("estado"));
        criterios.origen = Formulario.enumeracion(OrigenContenido.class, req.getParameter("origen"));
        return criterios;
    }

    static boolean usaOpciones(TipoPregunta tipo) {
        return tipo == TipoPregunta.SELECCION_MULTIPLE || tipo == TipoPregunta.VERDADERO_FALSO;
    }

    static String claseEstado(EstadoPregunta estado) {
        switch (estado) {
            case APROBADA:
                return "insignia-activo";
            case BORRADOR:
                return "insignia-borrador";
            default:
                return "insignia-neutra";
        }
    }

    private BancoPreguntas bancoDelDocente(Docente docente, int idBanco) {
        java.util.Optional<BancoPreguntas> propio = bancoDAO.buscarPorIdDeDocente(idBanco, docente.consultarIdUsuario());
        verificarPropiedad(propio.isPresent(), bancoDAO.buscarPorId(idBanco).isPresent(), "Banco no encontrado");
        return propio.get();
    }

    /** Valor del formulario: lo enviado tiene prioridad sobre el filtro de la URL. */
    private static String valor(Map<String, String> formulario, HttpServletRequest req, String nombre) {
        if (formulario.containsKey(nombre)) {
            return formulario.get(nombre);
        }
        String parametro = Validador.limpiar(req.getParameter(nombre));
        return parametro == null ? "" : parametro;
    }

    static String textoONulo(String valor) {
        String limpio = Validador.limpiar(valor);
        return limpio == null || limpio.isBlank() ? null : limpio;
    }
}
