package servlet;

import logica.BancoPreguntas;
import logica.Competencia;
import logica.Docente;
import logica.DocumentoFuente;
import logica.Evaluacion;
import logica.NivelIngles;
import logica.Pregunta;
import logica.SolicitudGeneracion;
import logica.TipoGeneracion;
import logica.ValidacionException;
import logica.Validador;
import modelo.DocumentoFuenteDAO;
import servicio.ConfiguracionIA;
import servicio.ProveedorIAFabrica;
import servicio.RegistroProveedoresIA;
import servicio.ServicioGeneracionIA;
import servicio.TrabajoGeneracionIA;
import servicio.ValidadorDocumento;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Documentos fuente y registro de generaciones con IA (RF-25 a RF-30, CU-18/CU-19): el POST
 * valida, crea la solicitud PENDIENTE, entrega un comando inmutable al servicio y responde de
 * inmediato; la generación continúa en segundo plano. Los proveedores se descubren por el
 * registro (RNF-12) y la pantalla no tiene opciones cableadas.
 */
// El contenedor acepta hasta 12/14 MB para poder responder con el mensaje propio al superar los
// 10 MB reales (RF-25); por encima de eso el filtro CSRF responde 413 controlado.
@WebServlet(name = "DocenteIAServlet", urlPatterns = "/app/docente/ia")
@MultipartConfig(maxFileSize = 12 * 1024 * 1024, maxRequestSize = 14 * 1024 * 1024)
public class DocenteIAServlet extends ServletBase {

    private static final long serialVersionUID = 1L;
    private static final String RUTA = "/app/docente/ia";
    private static final long MAX_ARCHIVO = 10L * 1024 * 1024;
    private static final String MENSAJE_TAMANO = "El archivo supera el tamaño máximo permitido de 10 MB.";
    private static final int MAX_INSTRUCCIONES = 1000;
    private static final int MAX_TEMA = 120;
    private static final int MIN_CANTIDAD = 1;
    private static final int MAX_CANTIDAD = 30;
    private static final String MENSAJE_OCUPADO =
            "El servicio de generación está temporalmente ocupado. Puedes reintentar.";
    private static final Logger LOG = Logger.getLogger(DocenteIAServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        Map<String, String> formulario = recuperarFormulario(req);

        List<Map<String, String>> filasDocumentos = new ArrayList<>();
        for (DocumentoFuente documento : documentoDAO.listarPorDocente(docente.getIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            fila.put("nombre", documento.nombreArchivoDocumentoFuente);
            fila.put("tipo", documento.tipoArchivoDocumentoFuente);
            fila.put("fecha", fechaHora(documento.fechaCargaDocumentoFuente));
            fila.put("accion", boton(req, RUTA, documento.getIdDocumentoFuente(), "eliminarDocumento", "Eliminar",
                    "boton-peligro boton-pequeno"));
            filasDocumentos.add(fila);
        }

        List<Map<String, String>> filasSolicitudes = new ArrayList<>();
        for (SolicitudGeneracion solicitud : solicitudDAO.listarPorDocente(docente.getIdUsuario())) {
            Map<String, String> fila = new HashMap<>();
            int generadas = preguntaDAO.contarPreguntasDeSolicitud(solicitud.getIdSolicitudGeneracion());
            fila.put("tipo", Etiquetas.de(solicitud.tipoSolicitudGeneracion));
            fila.put("estado", Etiquetas.de(solicitud.estadoSolicitudGeneracion));
            fila.put("claseEstado", claseEstadoSolicitud(solicitud));
            fila.put("cantidad", String.valueOf(solicitud.cantidadSolicitudGeneracion));
            fila.put("nivel", Etiquetas.de(solicitud.nivelSolicitudGeneracion));
            fila.put("modelo", solicitud.modeloSolicitudGeneracion == null ? "—" : solicitud.modeloSolicitudGeneracion);
            fila.put("generadas", String.valueOf(generadas));
            fila.put("mensaje", mensajeDeSolicitud(solicitud, generadas));
            fila.put("fecha", fechaHora(solicitud.fechaSolicitudGeneracion));
            if (solicitud.estadoSolicitudGeneracion == logica.EstadoSolicitud.FALLIDA) {
                fila.put("accionReintentar", boton(req, RUTA, solicitud.getIdSolicitudGeneracion(),
                        "prepararReintento", "Reintentar", "boton-contorno boton-pequeno"));
            } else {
                fila.put("accionReintentar", "");
            }
            filasSolicitudes.add(fila);
        }

        Map<String, String> datos = new HashMap<>();
        datos.put("filasDocumentos", componerFilas("_fila-documento.html", filasDocumentos,
                filaVacia("4", "Todavía no has cargado documentos.")));
        datos.put("filasSolicitudes", componerFilas("_fila-solicitud.html", filasSolicitudes,
                filaVacia("9", "Todavía no has hecho solicitudes de generación.")));
        datos.put("opcionesTipo", Formulario.opciones(vistas, TipoGeneracion.values(),
                Formulario.enumeracion(TipoGeneracion.class, formulario.get("tipo")), "Selecciona el tipo"));
        datos.put("opcionesNivel", Formulario.opciones(vistas, NivelIngles.values(),
                Formulario.enumeracion(NivelIngles.class, formulario.get("nivel")), "Selecciona el nivel"));
        datos.put("opcionesCompetencia", Formulario.opciones(vistas, Competencia.values(),
                Formulario.enumeracion(Competencia.class, formulario.get("competencia")), "Selecciona la competencia"));
        datos.put("opcionesProveedores", opcionesProveedores(formulario.get("proveedor")));
        datos.put("opcionesModelos", opcionesModelos(formulario.get("modelo")));
        datos.put("opcionesBancos", opcionesBancos(docente, formulario.get("idBanco")));
        datos.put("opcionesDocumentos", opcionesDocumentos(docente, formulario.get("idDocumento")));
        datos.put("opcionesPreguntasBase", opcionesPreguntasBase(docente, formulario.get("idPreguntasBase")));
        datos.put("opcionesEvaluaciones", opcionesEvaluaciones(docente, formulario.get("idEvaluacionReferencia")));
        datos.put("formCantidad", valor(formulario, "cantidad", "5"));
        datos.put("formTema", valor(formulario, "tema", ""));
        datos.put("formInstrucciones", valor(formulario, "instrucciones", ""));
        boolean sinClaves = RegistroProveedoresIA.todas().stream().noneMatch(ProveedorIAFabrica::disponible);
        datos.put("avisoClaves", sinClaves
                ? Html.alerta(vistas, "alerta-info", ConfiguracionIA.mensajeFaltaClave())
                : "");
        datos.put("rutaConfiguracion", ConfiguracionIA.ubicacionArchivo());
        renderizar(req, resp, "docente-ia.html", datos, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Docente docente = docenteActual(req);
        try {
            String accion;
            try {
                accion = parametro(req, "accion");
            } catch (IllegalStateException e) {
                throw new ValidacionException(MENSAJE_TAMANO);
            }
            switch (accion) {
                case "subirDocumento":
                    subirDocumento(req, docente);
                    agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Documento cargado. Ya puedes usarlo como contexto.");
                    break;
                case "eliminarDocumento":
                    eliminarDocumento(req, docente);
                    break;
                case "prepararReintento":
                    prepararReintento(req, docente);
                    break;
                case "generar":
                    generar(req, docente);
                    break;
                default:
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
            }
        } catch (ValidacionException e) {
            agregarErrores(req, e.getErrores());
        } catch (IOException | ServletException e) {
            agregarErrores(req, List.of("No se pudo procesar el archivo."));
        }
        redirigir(req, resp, RUTA);
    }

    // ------------------------------------------------------------------ documentos

    private void subirDocumento(HttpServletRequest req, Docente docente)
            throws IOException, ServletException, ValidacionException {
        Part parte;
        try {
            parte = req.getPart("archivo");
        } catch (IllegalStateException e) {
            throw new ValidacionException(MENSAJE_TAMANO);
        }
        if (parte == null || parte.getSize() == 0) {
            throw new ValidacionException("Selecciona un archivo.");
        }
        if (parte.getSize() > MAX_ARCHIVO) {
            throw new ValidacionException(MENSAJE_TAMANO);
        }
        String nombreOriginal = Paths.get(parte.getSubmittedFileName() == null ? "documento" : parte.getSubmittedFileName())
                .getFileName().toString();
        String minuscula = nombreOriginal.toLowerCase(Locale.ROOT);
        String extension;
        if (minuscula.endsWith(".pdf")) {
            extension = ".pdf";
        } else if (minuscula.endsWith(".docx")) {
            extension = ".docx";
        } else if (minuscula.endsWith(".txt")) {
            extension = ".txt";
        } else {
            throw new ValidacionException("Formato no soportado. Usa PDF, DOCX o TXT.");
        }
        byte[] contenido;
        try (InputStream entrada = parte.getInputStream()) {
            contenido = entrada.readAllBytes();
        }
        try {
            ValidadorDocumento.validar(contenido, extension.substring(1));
        } catch (servicio.GeneracionFallidaException e) {
            throw new ValidacionException(e.getMessage());
        }
        Path directorio = ConfiguracionIA.directorioDocumentos();
        Path destino = directorio.resolve(UUID.randomUUID().toString() + extension);
        Files.write(destino, contenido);
        documentoDAO.cargarDocumentoFuente(docente, nombreOriginal,
                extension.substring(1).toUpperCase(Locale.ROOT), destino.toString());
    }

    /**
     * RF-25: si el documento está referenciado por una solicitud no se elimina nada (ni fila ni
     * archivo); si no lo está, se elimina la fila en transacción y después el archivo, dejando
     * solo un huérfano de archivo (registrado) si el borrado físico falla.
     */
    private void eliminarDocumento(HttpServletRequest req, Docente docente) throws ValidacionException {
        int id = parametroId(req, "id");
        DocumentoFuente documento = documentoDAO.buscarPorId(id).orElse(null);
        verificarPropiedad(documento != null && documentoDAO.perteneceA(id, docente.getIdUsuario()),
                documento != null, "Documento no encontrado");
        DocumentoFuenteDAO.ResultadoEliminacion resultado = documentoDAO.eliminarSiNoReferenciado(id,
                docente.getIdUsuario());
        if (resultado == DocumentoFuenteDAO.ResultadoEliminacion.NO_ENCONTRADO) {
            throw new logica.RecursoNoEncontradoException("Documento no encontrado");
        }
        if (resultado == DocumentoFuenteDAO.ResultadoEliminacion.REFERENCIADO) {
            throw new ValidacionException("El documento está asociado a solicitudes de generación y debe conservarse.");
        }
        try {
            Files.deleteIfExists(Paths.get(documento.rutaAlmacenamientoDocumentoFuente));
        } catch (IOException e) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Archivo huerfano: la fila del documento " + id + " se eliminó pero el archivo no: "
                            + documento.rutaAlmacenamientoDocumentoFuente, e);
        }
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Documento eliminado.");
    }

    // ------------------------------------------------------------------ reintento (RF-29)

    private void prepararReintento(HttpServletRequest req, Docente docente) throws ValidacionException {
        int id = parametroId(req, "id");
        SolicitudGeneracion solicitud = solicitudDAO.buscarPorIdDeDocente(id, docente.getIdUsuario())
                .orElse(null);
        verificarPropiedad(solicitud != null, solicitudDAO.buscarPorId(id).isPresent(),
                "Solicitud no encontrada");
        if (solicitud.estadoSolicitudGeneracion != logica.EstadoSolicitud.FALLIDA) {
            throw new ValidacionException("Solo se pueden reintentar solicitudes fallidas.");
        }
        Map<String, String> valores = new HashMap<>();
        valores.put("tipo", solicitud.tipoSolicitudGeneracion.name());
        valores.put("cantidad", String.valueOf(solicitud.cantidadSolicitudGeneracion));
        if (solicitud.nivelSolicitudGeneracion != null) {
            valores.put("nivel", solicitud.nivelSolicitudGeneracion.name());
        }
        if (solicitud.instruccionesSolicitudGeneracion != null) {
            valores.put("instrucciones", solicitud.instruccionesSolicitudGeneracion);
        }
        if (solicitud.modeloSolicitudGeneracion != null) {
            valores.put("modelo", solicitud.modeloSolicitudGeneracion);
        }
        if (solicitud.documentoFuenteSolicitudGeneracion != null) {
            valores.put("idDocumento", String.valueOf(
                    solicitud.documentoFuenteSolicitudGeneracion.getIdDocumentoFuente()));
        }
        if (solicitud.bancoPreguntaSolicitudGeneracion != null) {
            valores.put("idBanco", String.valueOf(
                    solicitud.bancoPreguntaSolicitudGeneracion.getIdBancoPreguntas()));
        }
        if (solicitud.proveedorSolicitudGeneracion != null) {
            valores.put("proveedor", solicitud.proveedorSolicitudGeneracion);
        }
        if (solicitud.temaSolicitudGeneracion != null) {
            valores.put("tema", solicitud.temaSolicitudGeneracion);
        }
        if (solicitud.competenciaSolicitudGeneracion != null) {
            valores.put("competencia", solicitud.competenciaSolicitudGeneracion);
        }
        if (solicitud.idEvaluacionReferenciaSolicitudGeneracion != null) {
            valores.put("idEvaluacionReferencia",
                    String.valueOf(solicitud.idEvaluacionReferenciaSolicitudGeneracion));
        }
        if (solicitud.idPreguntasBaseSolicitudGeneracion != null) {
            valores.put("idPreguntasBase", solicitud.idPreguntasBaseSolicitudGeneracion);
        }
        guardarFormulario(req, valores);
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Datos precargados desde la solicitud fallida. Revisa y "
                + "envía: se creará una solicitud nueva con los mismos datos.");
    }

    // ------------------------------------------------------------------ registro de generación

    private void generar(HttpServletRequest req, Docente docente) throws ValidacionException {
        TipoGeneracion tipo = Formulario.enumeracion(TipoGeneracion.class, parametro(req, "tipo"));
        String instrucciones = Validador.limpiarTexto(req.getParameter("instrucciones") == null
                ? "" : req.getParameter("instrucciones"));
        int cantidad = entero(req, "cantidad");
        NivelIngles nivel = Formulario.enumeracion(NivelIngles.class, parametro(req, "nivel"));
        String tema = Validador.limpiarTexto(req.getParameter("tema") == null ? "" : req.getParameter("tema"));
        Competencia competencia = Formulario.enumeracion(Competencia.class, parametro(req, "competencia"));
        String proveedorNombre = parametro(req, "proveedor");
        String modelo = parametro(req, "modelo");
        int idDocumento = Validador.parsearId(req.getParameter("idDocumento"));
        int idBanco = Validador.parsearId(req.getParameter("idBanco"));
        int idEvaluacionReferencia = Validador.parsearId(req.getParameter("idEvaluacionReferencia"));
        List<Integer> idsBases = idsPreguntasBase(req);

        List<String> errores = new ArrayList<>();
        Validador.validarRequerido(tipo, "Selecciona el tipo de generación.", errores);
        Validador.validarLongitud(instrucciones, 0, MAX_INSTRUCCIONES,
                "Las instrucciones admiten máximo 1000 caracteres.", errores);
        if (cantidad < MIN_CANTIDAD || cantidad > MAX_CANTIDAD) {
            errores.add("La cantidad debe estar entre 1 y 30.");
        }
        Validador.validarRequerido(nivel, "Selecciona el nivel.", errores);
        boolean modoReferencia = tipo == TipoGeneracion.MATERIAL_PRACTICA && idEvaluacionReferencia > 0;
        boolean exigeTemaYCompetencia = tipo == TipoGeneracion.PREGUNTAS
                || (tipo == TipoGeneracion.MATERIAL_PRACTICA && !modoReferencia);
        if (exigeTemaYCompetencia) {
            Validador.validarLongitud(tema, 2, MAX_TEMA, "El tema debe tener entre 2 y 120 caracteres.", errores);
            Validador.validarRequerido(competencia, "Selecciona la competencia.", errores);
        } else {
            Validador.validarLongitud(tema, 0, MAX_TEMA, "El tema admite máximo 120 caracteres.", errores);
        }
        if (RegistroProveedoresIA.porCodigo(proveedorNombre).isEmpty()) {
            errores.add("Selecciona el proveedor de IA.");
        } else if (!RegistroProveedoresIA.esModeloPermitido(proveedorNombre, modelo)) {
            errores.add("El modelo indicado no está permitido para el proveedor seleccionado.");
        }
        if ((tipo == TipoGeneracion.PREGUNTAS || tipo == TipoGeneracion.VARIANTES) && idBanco < 0) {
            errores.add("Selecciona el banco destino.");
        }
        if (tipo == TipoGeneracion.VARIANTES) {
            if (idsBases.isEmpty()) {
                errores.add("Selecciona al menos una pregunta base para las variantes.");
            } else if (idsBases.size() > cantidad) {
                errores.add("La cantidad total de variantes debe ser al menos el número de preguntas base.");
            }
        }
        if (tipo == TipoGeneracion.MATERIAL_PRACTICA && !modoReferencia
                && (tema.isBlank() || competencia == null)) {
            errores.add("Indica una evaluación de referencia o el tema y la competencia del material.");
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }

        BancoPreguntas banco = null;
        if (idBanco > 0) {
            banco = bancoDAO.buscarPorIdDeDocente(idBanco, docente.getIdUsuario())
                    .orElseThrow(() -> new ValidacionException("El banco indicado no existe."));
        }
        DocumentoFuente documento = null;
        if (idDocumento > 0) {
            if (!documentoDAO.perteneceA(idDocumento, docente.getIdUsuario())) {
                throw new ValidacionException("El documento indicado no existe.");
            }
            documento = documentoDAO.buscarPorId(idDocumento)
                    .orElseThrow(() -> new ValidacionException("El documento indicado no existe."));
        }
        if (modoReferencia) {
            Evaluacion evaluacion = evaluacionDAO.buscarPorIdDeDocente(idEvaluacionReferencia,
                            docente.getIdUsuario())
                    .orElseThrow(() -> new ValidacionException("La evaluación de referencia indicada no existe."));
            if (preguntaDAO.listarPorEvaluacion(evaluacion.getIdEvaluacion()).isEmpty()) {
                throw new ValidacionException("La evaluación de referencia no tiene preguntas.");
            }
        }
        if (tipo == TipoGeneracion.VARIANTES) {
            for (Integer idBase : idsBases) {
                Pregunta base = preguntaDAO.buscarPorId(idBase).orElse(null);
                if (base == null || base.estadoPregunta == logica.EstadoPregunta.ARCHIVADA
                        || !preguntaDAO.esDeDocente(idBase, docente.getIdUsuario())) {
                    throw new ValidacionException("Una de las preguntas base no está disponible.");
                }
            }
        }
        ServicioGeneracionIA servicio = servicioIA();
        String instruccionesCompletas = instruccionesConTemaYCompetencia(instrucciones, tema, competencia);
        String modeloEfectivo = RegistroProveedoresIA.modeloEfectivo(proveedorNombre, modelo);
        SolicitudGeneracion solicitud = solicitudDAO.crearSolicitudGeneracion(docente, tipo, instruccionesCompletas,
                cantidad, nivel, modeloEfectivo, documento == null ? null : documento.getIdDocumentoFuente(),
                banco == null ? null : banco.getIdBancoPreguntas(), proveedorNombre,
                exigeTemaYCompetencia && !tema.isEmpty() ? tema : null,
                exigeTemaYCompetencia ? competencia : null,
                modoReferencia ? idEvaluacionReferencia : null, idsBases);
        TrabajoGeneracionIA trabajo = new TrabajoGeneracionIA(solicitud.getIdSolicitudGeneracion(),
                docente.getIdUsuario(), tipo, instruccionesCompletas, cantidad, nivel, proveedorNombre,
                modeloEfectivo, documento == null ? null : documento.getIdDocumentoFuente(),
                banco == null ? null : banco.getIdBancoPreguntas(),
                exigeTemaYCompetencia && !tema.isEmpty() ? tema : null,
                exigeTemaYCompetencia ? competencia : null,
                idsBases, modoReferencia ? idEvaluacionReferencia : null);
        if (!servicio.encolar(trabajo)) {
            solicitudDAO.marcarFallidaSolicitudGeneracion(solicitud.getIdSolicitudGeneracion(), MENSAJE_OCUPADO);
            throw new ValidacionException(MENSAJE_OCUPADO);
        }
        agregarMensaje(req, MensajeFlash.Tipo.EXITO, "Solicitud registrada. La generación continúa en segundo "
                + "plano. Puedes seguir usando ExamLen y consultar su estado aquí.");
    }

    private ServicioGeneracionIA servicioIA() throws ValidacionException {
        Object servicio = getServletContext().getAttribute(ProcesadorGeneracionIA.SERVICIO_IA);
        if (!(servicio instanceof ServicioGeneracionIA)) {
            throw new ValidacionException("El servicio de generación no está disponible en este momento. "
                    + "Inténtalo de nuevo en unos segundos.");
        }
        return (ServicioGeneracionIA) servicio;
    }

    /** Añade el tema y la competencia elegidos a las instrucciones guardadas y usadas como prompt (RF-26). */
    private static String instruccionesConTemaYCompetencia(String instrucciones, String tema, Competencia competencia) {
        if (tema.isEmpty() && competencia == null) {
            return instrucciones;
        }
        StringBuilder sb = new StringBuilder();
        if (!tema.isEmpty()) {
            sb.append("Tema: ").append(tema).append(". ");
        }
        if (competencia != null) {
            sb.append("Competencia: ").append(competencia.name()).append(". ");
        }
        sb.append(instrucciones);
        return sb.toString().strip();
    }

    // ------------------------------------------------------------------ opciones de la pantalla

    private String opcionesProveedores(String seleccionado) {
        StringBuilder sb = new StringBuilder(Formulario.opcion(vistas, "", "Selecciona el proveedor",
                seleccionado == null || seleccionado.isBlank()));
        for (ProveedorIAFabrica fabrica : RegistroProveedoresIA.todas()) {
            String texto = fabrica.nombreVisible() + (fabrica.disponible() ? "" : " (sin configurar)");
            sb.append(Formulario.opcion(vistas, fabrica.codigo(), texto,
                    fabrica.codigo().equals(seleccionado)));
        }
        return sb.toString();
    }

    private String opcionesModelos(String seleccionado) {
        StringBuilder sb = new StringBuilder(Formulario.opcion(vistas, "", "Predeterminado del proveedor",
                seleccionado == null || seleccionado.isBlank()));
        for (ProveedorIAFabrica fabrica : RegistroProveedoresIA.todas()) {
            sb.append(vistas.componer("_optgroup-abrir.html", Map.of("etiqueta", fabrica.nombreVisible())));
            for (String modelo : fabrica.modelosPermitidos()) {
                sb.append(Formulario.opcion(vistas, modelo, modelo, modelo.equals(seleccionado)));
            }
            sb.append(vistas.componer("_optgroup-cerrar.html", Map.of()));
        }
        return sb.toString();
    }

    private String opcionesBancos(Docente docente, String seleccionado) {
        StringBuilder sb = new StringBuilder(Formulario.opcion(vistas, "", "Sin banco", seleccionado == null
                || seleccionado.isBlank()));
        for (BancoPreguntas banco : bancoDAO.listarPorDocente(docente.getIdUsuario())) {
            sb.append(Formulario.opcion(vistas, String.valueOf(banco.getIdBancoPreguntas()),
                    banco.nombreBancoPreguntas, String.valueOf(banco.getIdBancoPreguntas()).equals(seleccionado)));
        }
        return sb.toString();
    }

    private String opcionesDocumentos(Docente docente, String seleccionado) {
        StringBuilder sb = new StringBuilder(Formulario.opcion(vistas, "", "Sin documento", seleccionado == null
                || seleccionado.isBlank()));
        for (DocumentoFuente documento : documentoDAO.listarPorDocente(docente.getIdUsuario())) {
            sb.append(Formulario.opcion(vistas, String.valueOf(documento.getIdDocumentoFuente()),
                    documento.nombreArchivoDocumentoFuente,
                    String.valueOf(documento.getIdDocumentoFuente()).equals(seleccionado)));
        }
        return sb.toString();
    }

    private String opcionesPreguntasBase(Docente docente, String seleccionadosCsv) {
        java.util.Set<String> seleccionados = new java.util.HashSet<>();
        if (seleccionadosCsv != null && !seleccionadosCsv.isBlank()) {
            for (String parte : seleccionadosCsv.split(",")) {
                seleccionados.add(parte.strip());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Pregunta pregunta : preguntaDAO.listarDeDocente(docente.getIdUsuario())) {
            String texto = Etiquetas.de(pregunta.estadoPregunta) + " · "
                    + Html.resumir(pregunta.enunciadoPregunta, 90);
            String valor = String.valueOf(pregunta.getIdPregunta());
            sb.append(Formulario.opcion(vistas, valor, texto, seleccionados.contains(valor)));
        }
        return sb.toString();
    }

    private String opcionesEvaluaciones(Docente docente, String seleccionado) {
        StringBuilder sb = new StringBuilder(Formulario.opcion(vistas, "", "Sin evaluación de referencia",
                seleccionado == null || seleccionado.isBlank()));
        for (Evaluacion evaluacion : evaluacionDAO.listarPorDocente(docente.getIdUsuario())) {
            int preguntas = preguntaDAO.listarPorEvaluacion(evaluacion.getIdEvaluacion()).size();
            if (preguntas == 0) {
                continue;
            }
            String texto = evaluacion.tituloEvaluacion + " (" + preguntas + " preguntas)";
            sb.append(Formulario.opcion(vistas, String.valueOf(evaluacion.getIdEvaluacion()), texto,
                    String.valueOf(evaluacion.getIdEvaluacion()).equals(seleccionado)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ auxiliares

    /**
     * CU-19 5b visible para el docente: desglose EXACTO cuando esta persistido (validas, invalidas y
     * no devueltas por separado, sin mezclarlas). Si la solicitud es historica y no tiene conteos,
     * se mantiene el mensaje anterior derivado de cantidad - generadas.
     */
    private static String mensajeDeSolicitud(SolicitudGeneracion solicitud, int generadas) {
        if (solicitud.estadoSolicitudGeneracion == logica.EstadoSolicitud.FALLIDA) {
            return solicitud.mensajeErrorSolicitudGeneracion == null ? "" : solicitud.mensajeErrorSolicitudGeneracion;
        }
        if (solicitud.estadoSolicitudGeneracion == logica.EstadoSolicitud.COMPLETADA) {
            Integer invalidas = solicitud.cantidadInvalidaSolicitudGeneracion;
            Integer noDevueltas = solicitud.cantidadNoDevueltaSolicitudGeneracion;
            if (invalidas == null && noDevueltas == null) {
                int descartadas = solicitud.cantidadSolicitudGeneracion - generadas;
                if (descartadas > 0) {
                    return "Se descartaron " + descartadas + " de las " + solicitud.cantidadSolicitudGeneracion
                            + " solicitadas (inválidas o no devueltas).";
                }
                return "";
            }
            int in = invalidas == null ? 0 : invalidas;
            int nd = noDevueltas == null ? 0 : noDevueltas;
            if (in == 0 && nd == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (in > 0) {
                sb.append("Se descartaron ").append(in).append(" ítem(s) inválido(s)");
            }
            if (nd > 0) {
                if (sb.length() > 0) {
                    sb.append("; además, ");
                } else {
                    sb.append("El proveedor no devolvió ");
                }
                sb.append(nd).append(" ítem(s) no devuelto(s) por el proveedor");
            }
            sb.append(" de ").append(solicitud.cantidadSolicitudGeneracion).append(" solicitados.");
            return sb.toString();
        }
        return "";
    }

    private static List<Integer> idsPreguntasBase(HttpServletRequest req) {
        List<Integer> ids = new ArrayList<>();
        String[] valores = req.getParameterValues("idPreguntasBase");
        if (valores == null) {
            return ids;
        }
        for (String valor : valores) {
            int id = Validador.parsearId(valor);
            if (id > 0 && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String valor(Map<String, String> formulario, String clave, String porDefecto) {
        String valor = formulario.get(clave);
        return valor == null ? porDefecto : valor;
    }

    private static String claseEstadoSolicitud(SolicitudGeneracion solicitud) {
        switch (solicitud.estadoSolicitudGeneracion) {
            case COMPLETADA:
                return "insignia-activo";
            case FALLIDA:
                return "insignia-inactivo";
            case EN_PROCESO:
                return "insignia-borrador";
            default:
                return "insignia-neutra";
        }
    }

    private static int entero(HttpServletRequest req, String nombre) {
        try {
            return Integer.parseInt(Validador.limpiar(req.getParameter(nombre)));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }
}
