package servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import logica.CatalogoCasosUso;
import servicio.ArchivoRendimiento;
import servicio.GeneradorExcelRendimiento;
import servicio.RendimientoEscenarios;
import servicio.ServicioGeneracionIA;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TEMPORAL - HERRAMIENTA ACADEMICA DE RENDIMIENTO
 * RETIRAR DESPUES DE LA PRUEBA
 *
 * Backend del benchmark de los 34 CU (solo ADMIN por el filtro /app/admin/). No renderiza
 * ninguna pagina: expone acciones JSON para el runner del navegador (preparar, estadoIA,
 * guardar, excel, limpiar, catalogo, coherencia). Mide con System.nanoTime() y lo informa en
 * la cabecera Server-Timing. No crea tablas ni migraciones.
 */
@WebServlet(name = "AdminRendimientoServlet", urlPatterns = "/app/admin/rendimiento")
public class AdminRendimientoServlet extends ServletBase {

    private static final long serialVersionUID = 1L;

    private transient RendimientoEscenarios escenarios;

    private synchronized RendimientoEscenarios escenarios() {
        if (escenarios == null) {
            Object servicioIA = getServletContext().getAttribute(ProcesadorGeneracionIA.SERVICIO_IA);
            escenarios = new RendimientoEscenarios(administradorDAO, usuarioDAO, docenteDAO, estudianteDAO,
                    cursoDAO, grupoDAO, inscripcionDAO, bancoDAO, preguntaDAO, opcionDAO, documentoDAO, tokenDAO,
                    evaluacionDAO, versionDAO, versionPreguntaDAO, virtualDAO, intentoDAO, respuestaDAO, eventoDAO,
                    materialDAO, intentoPracticaDAO, respuestaPracticaDAO, solicitudDAO, servicioVirtual,
                    servicioIA instanceof ServicioGeneracionIA ? (ServicioGeneracionIA) servicioIA : null);
        }
        return escenarios;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String accion = parametro(req, "accion");
        if (accion == null || accion.isBlank()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        switch (accion) {
            case "catalogo":
                responderCatalogo(resp);
                return;
            case "coherencia":
                responderCoherencia(resp);
                return;
            case "preparar":
                prepararCu(req, resp);
                return;
            case "estadoIA":
                responderEstadoIA(req, resp);
                return;
            case "excel":
                responderExcel(resp);
                return;
            case "limpiar":
                limpiar(resp);
                return;
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("guardar".equals(parametro(req, "accion"))) {
            guardar(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    // ------------------------------------------------------------------ acciones del runner

    private void responderCatalogo(HttpServletResponse resp) throws IOException {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("total", CatalogoCasosUso.todas().size());
        JsonArray arreglo = new JsonArray();
        for (CatalogoCasosUso.Entrada entrada : CatalogoCasosUso.todas()) {
            arreglo.add(aJson(entrada));
        }
        respuesta.add("casos", arreglo);
        escribirJson(resp, respuesta, 0);
    }

    private static JsonObject aJson(CatalogoCasosUso.Entrada entrada) {
        JsonObject objeto = new JsonObject();
        objeto.addProperty("cu_id", entrada.cuId);
        objeto.addProperty("caso_de_uso", entrada.nombre);
        objeto.addProperty("rol", entrada.rol);
        objeto.addProperty("interfaz_inicio", entrada.interfazInicio);
        objeto.addProperty("accion_inicio", entrada.accionInicio);
        objeto.addProperty("endpoint_inicio", entrada.endpointInicio);
        objeto.addProperty("metodo_inicio", entrada.metodoInicio);
        objeto.addProperty("metodo_fin", entrada.metodoFin);
        objeto.addProperty("tabla_resultado_final", entrada.tablaResultadoFinal);
        objeto.addProperty("tablas_secundarias", entrada.tablasSecundarias);
        objeto.addProperty("retorna", entrada.retorna);
        objeto.addProperty("tipo_retorno", entrada.tipoRetorno);
        objeto.addProperty("estado_funcional", entrada.estadoFuncional);
        return objeto;
    }

    private void prepararCu(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String cu = parametro(req, "cu");
        CatalogoCasosUso.Entrada entrada = CatalogoCasosUso.porId(cu);
        if (entrada == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("cu", cu);
            respuesta.addProperty("estado_medicion", "ERROR");
            respuesta.addProperty("error", "Caso de uso no encontrado en el catalogo");
            escribirJson(resp, respuesta, 0);
            return;
        }
        if ("1".equals(parametro(req, "falla"))) {
            escribirJson(resp, resultadoError(cu, "Fallo inducido de benchmark"), 0);
            return;
        }
        long inicio = System.nanoTime();
        try {
            if ("CU-23".equals(cu) || "CU-24".equals(cu) || "CU-33".equals(cu)) {
                byte[] pdf = "CU-24".equals(cu) ? escenarios().pdfClaveVersion()
                        : "CU-23".equals(cu) ? escenarios().pdfVersion()
                        : escenarios().pdfPractica("1".equals(parametro(req, "respuestas")));
                double servidorMs = (System.nanoTime() - inicio) / 1_000_000.0;
                resp.setContentType("application/pdf");
                resp.setHeader("Content-Disposition", "inline; filename=\"perf-cu.pdf\"");
                resp.setHeader("Server-Timing", "total;dur=" + String.format(java.util.Locale.ROOT, "%.2f", servidorMs));
                resp.setHeader("X-Server-Ms", String.format(java.util.Locale.ROOT, "%.2f", servidorMs));
                resp.setContentLength(pdf.length);
                resp.getOutputStream().write(pdf);
                return;
            }
            RendimientoEscenarios.Resultado resultado = escenarios().ejecutar(cu);
            double servidorMs = (System.nanoTime() - inicio) / 1_000_000.0;
            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("cu", cu);
            respuesta.addProperty("estado_medicion", resultado.verificado ? "OK" : "ERROR");
            respuesta.addProperty("verificado", resultado.verificado);
            respuesta.addProperty("observacion", resultado.observacion);
            respuesta.addProperty("server_ms", redondear(servidorMs));
            if (resultado.solicitudId != null) {
                respuesta.addProperty("solicitud_id", resultado.solicitudId);
                respuesta.addProperty("aceptacion_ms", redondear(servidorMs));
            }
            escribirJson(resp, respuesta, servidorMs);
        } catch (Exception e) {
            double servidorMs = (System.nanoTime() - inicio) / 1_000_000.0;
            escribirJson(resp, resultadoError(cu, e.getClass().getSimpleName() + ": " + e.getMessage()), servidorMs);
        }
    }

    private void responderEstadoIA(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int solicitud = logica.Validador.parsearId(req.getParameter("solicitud"));
        String estado = escenarios().estadoIA(solicitud);
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("solicitud_id", solicitud);
        respuesta.addProperty("estado", estado);
        escribirJson(resp, respuesta, 0);
    }

    private void guardar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String entorno = parametro(req, "entorno");
        String hostname = req.getServerName() == null ? "" : req.getServerName().toLowerCase();
        String clase = clasificarHost(hostname);
        if (!ArchivoRendimiento.LOCAL.equals(entorno) && !ArchivoRendimiento.NGROK.equals(entorno)) {
            responderError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Entorno invalido: se espera local o ngrok");
            return;
        }
        if (clase == null || !clase.equals(entorno)) {
            responderError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Entorno desconocido: el host " + hostname + " no corresponde a " + entorno
                    + "; no se sobrescriben mediciones validas");
            return;
        }
        JsonArray recibidas;
        try {
            String cuerpo = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            recibidas = JsonParser.parseString(cuerpo).getAsJsonArray();
        } catch (Exception e) {
            responderError(resp, HttpServletResponse.SC_BAD_REQUEST, "Cuerpo JSON invalido");
            return;
        }
        Map<String, JsonObject> porCu = new LinkedHashMap<>();
        for (JsonElement elemento : recibidas) {
            if (!elemento.isJsonObject()) {
                continue;
            }
            JsonObject fila = elemento.getAsJsonObject();
            if (fila.has("cu_id")) {
                porCu.put(fila.get("cu_id").getAsString(), fila);
            }
        }
        List<String> faltantes = new ArrayList<>();
        for (CatalogoCasosUso.Entrada entrada : CatalogoCasosUso.todas()) {
            if (!porCu.containsKey(entrada.cuId)) {
                faltantes.add(entrada.cuId);
            }
        }
        if (!faltantes.isEmpty()) {
            responderError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Faltan resultados de " + faltantes.size() + " CU: " + String.join(", ", faltantes));
            return;
        }
        String urlBase = req.getScheme() + "://" + hostname + req.getContextPath();
        String fechaServidor = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        JsonArray filas = new JsonArray();
        for (CatalogoCasosUso.Entrada entrada : CatalogoCasosUso.todas()) {
            JsonObject origen = porCu.get(entrada.cuId);
            JsonObject fila = aJson(entrada);
            copiarTexto(origen, fila, "estado_medicion", "ERROR");
            copiarNumero(origen, fila, "tiempo_servidor_ms");
            copiarNumero(origen, fila, "tiempo_total_ms");
            copiarNumero(origen, fila, "tiempo_aceptacion_ms");
            copiarNumero(origen, fila, "tiempo_total_ia_ms");
            String fecha = textoDe(origen, "fecha_hora");
            fila.addProperty("fecha_hora", fecha == null || fecha.isBlank() ? fechaServidor : fecha);
            fila.addProperty("entorno", entorno);
            fila.addProperty("hostname", hostname);
            fila.addProperty("url_base", urlBase);
            fila.addProperty("observacion", textoDe(origen, "observacion") == null
                    ? "" : textoDe(origen, "observacion"));
            filas.add(fila);
        }
        try {
            String archivo = ArchivoRendimiento.guardar(entorno, filas);
            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("entorno", entorno);
            respuesta.addProperty("hostname", hostname);
            respuesta.addProperty("guardados", filas.size());
            respuesta.addProperty("archivo", archivo);
            escribirJson(resp, respuesta, 0);
        } catch (IOException e) {
            responderError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "No se pudo guardar la medicion: " + e.getMessage());
        }
    }

    private void responderExcel(HttpServletResponse resp) throws IOException {
        JsonArray local = ArchivoRendimiento.leer(ArchivoRendimiento.LOCAL);
        JsonArray ngrok = ArchivoRendimiento.leer(ArchivoRendimiento.NGROK);
        if (local == null && ngrok == null) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("error", "Primero debe ejecutarse una prueba de rendimiento "
                    + "(no hay mediciones LOCAL ni NGROK).");
            escribirJson(resp, respuesta, 0);
            return;
        }
        try {
            byte[] xlsx = GeneradorExcelRendimiento.generar(local, ngrok, ArchivoRendimiento.umbralPct(),
                    filasCoherencia());
            String nombre = "ExamLen_Rendimiento_Casos_Uso_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx";
            resp.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + nombre + "\"");
            resp.setContentLength(xlsx.length);
            resp.getOutputStream().write(xlsx);
        } catch (Exception e) {
            responderError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void limpiar(HttpServletResponse resp) throws IOException {
        long inicio = System.nanoTime();
        try {
            int restantes = new modelo.RendimientoDAO(conexion.Conexion.obtenerFuente()).limpiar();
            escenarios().reiniciar();
            double servidorMs = (System.nanoTime() - inicio) / 1_000_000.0;
            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("restantes_perf", restantes);
            respuesta.addProperty("server_ms", redondear(servidorMs));
            escribirJson(resp, respuesta, servidorMs);
        } catch (Exception e) {
            double servidorMs = (System.nanoTime() - inicio) / 1_000_000.0;
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject respuesta = new JsonObject();
            respuesta.addProperty("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            escribirJson(resp, respuesta, servidorMs);
        }
    }

    // ------------------------------------------------------------------ coherencia CU <-> software

    private void responderCoherencia(HttpServletResponse resp) throws IOException {
        Set<String> esperados = new LinkedHashSet<>();
        Set<String> repetidos = new LinkedHashSet<>();
        Set<String> mapeados = new LinkedHashSet<>();
        for (int i = 1; i <= 34; i++) {
            esperados.add(String.format("CU-%02d", i));
        }
        for (CatalogoCasosUso.Entrada entrada : CatalogoCasosUso.todas()) {
            if (!mapeados.add(entrada.cuId)) {
                repetidos.add(entrada.cuId);
            }
        }
        Set<String> conEscenario = new LinkedHashSet<>(RendimientoEscenarios.escenarios());
        List<String> faltantes = new ArrayList<>(esperados);
        faltantes.removeAll(mapeados);
        List<String> sinEscenario = new ArrayList<>(mapeados);
        sinEscenario.removeAll(conEscenario);
        List<String> escenariosSinCu = new ArrayList<>(conEscenario);
        escenariosSinCu.removeAll(mapeados);

        Set<String> endpointsRegistrados = endpointsRegistrados();
        List<String> sinImplementacion = new ArrayList<>();
        for (CatalogoCasosUso.Entrada entrada : CatalogoCasosUso.todas()) {
            if (!endpointsRegistrados.contains(entrada.endpointInicio)) {
                sinImplementacion.add(entrada.cuId + " " + entrada.endpointInicio);
            }
        }
        List<String> endpointsSinCu = new ArrayList<>();
        Set<String> endpointsCatalogo = new LinkedHashSet<>();
        for (CatalogoCasosUso.Entrada entrada : CatalogoCasosUso.todas()) {
            endpointsCatalogo.add(entrada.endpointInicio);
        }
        for (String patron : endpointsRegistrados) {
            if (!endpointsCatalogo.contains(patron) && !esAuxiliarConocido(patron)) {
                endpointsSinCu.add(patron);
            }
        }

        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("CU_ESPERADOS", 34);
        respuesta.addProperty("CU_REGISTRADOS", mapeados.size());
        respuesta.addProperty("ESCENARIOS", conEscenario.size());
        respuesta.add("CU_FALTANTES", aArreglo(faltantes));
        respuesta.add("CU_DUPLICADOS", aArreglo(new ArrayList<>(repetidos)));
        respuesta.add("CU_SIN_ESCENARIO", aArreglo(sinEscenario));
        respuesta.add("CU_SIN_IMPLEMENTACION", aArreglo(sinImplementacion));
        respuesta.add("ESCENARIOS_SIN_CU", aArreglo(escenariosSinCu));
        respuesta.add("ENDPOINT_SIN_CU", aArreglo(endpointsSinCu));
        respuesta.addProperty("coherente", faltantes.isEmpty() && repetidos.isEmpty()
                && sinEscenario.isEmpty() && sinImplementacion.isEmpty());
        escribirJson(resp, respuesta, 0);
    }

    private List<String[]> filasCoherencia() {
        List<String[]> filas = new ArrayList<>();
        Set<String> registrados = endpointsRegistrados();
        Set<String> catalogo = new LinkedHashSet<>();
        for (CatalogoCasosUso.Entrada entrada : CatalogoCasosUso.todas()) {
            catalogo.add(entrada.endpointInicio);
            if (!registrados.contains(entrada.endpointInicio)) {
                filas.add(new String[]{"CU_SIN_IMPLEMENTACION", entrada.endpointInicio, entrada.cuId, "REVISAR",
                        "El endpoint del catalogo no aparece entre los mapeos de servlets"});
            }
            if (!RendimientoEscenarios.escenarios().contains(entrada.cuId)) {
                filas.add(new String[]{"CU_SIN_ESCENARIO", entrada.nombre, entrada.cuId, "REVISAR",
                        "No existe escenario de benchmark para este CU"});
            }
        }
        for (String patron : registrados) {
            if (!catalogo.contains(patron) && !esAuxiliarConocido(patron)) {
                filas.add(new String[]{"ENDPOINT_SIN_CU", patron, "", "INFORMATIVO",
                        "Endpoint funcional expuesto al usuario que no se asocia directamente a CU-01..CU-34"});
            }
        }
        return filas;
    }

    /**
     * Endpoints auxiliares vinculados a un CU existente (listados, pasos intermedios o pantallas
     * de navegacion). No son funcionalidad sobrante: pertenecen al flujo de un CU del catalogo.
     */
    private static boolean esAuxiliarConocido(String patron) {
        switch (patron) {
            case "/app/admin/rendimiento":
            case "/error":
            case "*.jspx":
            case "*.jsp":
            case "/":
            case "/app/inicio":
            case "/app/salir":
            case "/politica-datos":
            case "/restablecer":
            case "/app/estudiante/intento/inicio":
            case "/app/estudiante/evaluaciones":
            case "/app/docente/preguntas":
            case "/app/docente/banco":
            case "/app/docente/practica/pdf":
            case "/app/estudiante/practica/historial":
                return true;
            default:
                return false;
        }
    }

    private Set<String> endpointsRegistrados() {
        Set<String> patrones = new LinkedHashSet<>();
        for (jakarta.servlet.ServletRegistration registro : getServletContext().getServletRegistrations().values()) {
            patrones.addAll(registro.getMappings());
        }
        return patrones;
    }

    // ------------------------------------------------------------------ apoyo

    private static String clasificarHost(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return null;
        }
        String host = hostname.toLowerCase();
        if ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host)) {
            return ArchivoRendimiento.LOCAL;
        }
        if (host.endsWith(".ngrok-free.dev") || host.endsWith(".ngrok-free.app") || host.endsWith(".ngrok.app")
                || host.endsWith(".ngrok.io")) {
            return ArchivoRendimiento.NGROK;
        }
        return null;
    }

    private static void copiarTexto(JsonObject origen, JsonObject destino, String clave, String porDefecto) {
        String valor = textoDe(origen, clave);
        destino.addProperty(clave, valor == null || valor.isBlank() ? porDefecto : valor);
    }

    private static void copiarNumero(JsonObject origen, JsonObject destino, String clave) {
        if (origen == null || !origen.has(clave) || origen.get(clave).isJsonNull()) {
            return;
        }
        try {
            double valor = origen.get(clave).getAsDouble();
            if (valor >= 0) {
                destino.addProperty(clave, redondear(valor));
            }
        } catch (Exception e) {
            // valor no numerico: se omite (sin inventar 0)
        }
    }

    private static String textoDe(JsonObject objeto, String clave) {
        if (objeto == null || !objeto.has(clave) || objeto.get(clave).isJsonNull()) {
            return null;
        }
        try {
            return objeto.get(clave).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject resultadoError(String cu, String mensaje) {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("cu", cu);
        respuesta.addProperty("estado_medicion", "ERROR");
        respuesta.addProperty("verificado", false);
        respuesta.addProperty("error", mensaje == null ? "Error" : mensaje);
        return respuesta;
    }

    private void responderError(HttpServletResponse resp, int codigo, String mensaje) throws IOException {
        resp.setStatus(codigo);
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("error", mensaje);
        escribirJson(resp, respuesta, 0);
    }

    private static JsonArray aArreglo(List<String> valores) {
        JsonArray arreglo = new JsonArray();
        for (String valor : valores) {
            arreglo.add(valor);
        }
        return arreglo;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private static void escribirJson(HttpServletResponse resp, JsonObject objeto, double servidorMs) throws IOException {
        if (servidorMs > 0) {
            resp.setHeader("Server-Timing", "total;dur=" + String.format(java.util.Locale.ROOT, "%.2f", servidorMs));
            resp.setHeader("X-Server-Ms", String.format(java.util.Locale.ROOT, "%.2f", servidorMs));
        }
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(objeto.toString());
    }
}
