package servicio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import logica.CatalogoCasosUso;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera el XLSX real del benchmark: hoja principal con los 34 CU (tiempos LOCAL vs NGROK),
 * hoja Resumen con estadisticas y hoja Coherencia_CU. Solamente datos medidos; lo ausente
 * queda vacio o N.D. (nunca 0).
 */
public final class GeneradorExcelRendimiento {

    private static final String[] CABECERAS = {
        "CU", "Nombre caso de uso", "Rol",
        "Interfaz inicio", "Accion inicio", "Endpoint inicio", "Metodo inicio",
        "Metodo fin", "Tabla / resultado final", "Tablas secundarias",
        "Retorna?", "Tipo retorno",
        "Estado funcional",
        "Estado medicion LOCAL", "Tiempo servidor LOCAL (ms)", "Tiempo total LOCAL (ms)", "Fecha/hora LOCAL",
        "Estado medicion NGROK", "Tiempo servidor NGROK (ms)", "Tiempo total NGROK (ms)", "Fecha/hora NGROK",
        "Diferencia total (ms)", "Sobrecosto NGROK (%)",
        "% respecto al promedio", "Clasificacion",
        "Observacion LOCAL", "Observacion NGROK",
        "Tiempo aceptacion LOCAL (ms)", "Tiempo total IA LOCAL (ms)",
        "Tiempo aceptacion NGROK (ms)", "Tiempo total IA NGROK (ms)"
    };

    private GeneradorExcelRendimiento() {
    }

    public static byte[] generar(JsonArray local, JsonArray ngrok, int umbralPct, List<String[]> filasCoherencia)
            throws IOException {
        Map<String, JsonObject> mapaL = indexar(local);
        Map<String, JsonObject> mapaN = indexar(ngrok);
        List<CatalogoCasosUso.Entrada> catalogo = CatalogoCasosUso.todas();
        double promedio = promedioReferencia(catalogo, mapaL, mapaN);
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            hojaPrincipal(libro, estilos, catalogo, mapaL, mapaN, promedio, umbralPct);
            hojaResumen(libro, estilos, catalogo, mapaL, mapaN, promedio, umbralPct);
            hojaCoherencia(libro, estilos, catalogo, filasCoherencia);
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    // ------------------------------------------------------------------ hojas

    private static void hojaPrincipal(Workbook libro, Estilos estilos, List<CatalogoCasosUso.Entrada> catalogo,
                                      Map<String, JsonObject> mapaL, Map<String, JsonObject> mapaN,
                                      double promedio, int umbralPct) {
        Sheet hoja = libro.createSheet("Rendimiento_Casos_Uso");
        Row cabecera = hoja.createRow(0);
        for (int c = 0; c < CABECERAS.length; c++) {
            Cell celda = cabecera.createCell(c);
            celda.setCellValue(CABECERAS[c]);
            celda.setCellStyle(estilos.cabecera);
        }
        int fila = 1;
        for (CatalogoCasosUso.Entrada entrada : catalogo) {
            Row r = hoja.createRow(fila++);
            JsonObject dl = mapaL.get(entrada.cuId);
            JsonObject dn = mapaN.get(entrada.cuId);
            texto(r, 0, entrada.cuId, estilos.normal);
            texto(r, 1, entrada.nombre, estilos.textoLargo);
            texto(r, 2, entrada.rol, estilos.normal);
            texto(r, 3, entrada.interfazInicio, estilos.normal);
            texto(r, 4, entrada.accionInicio, estilos.textoLargo);
            texto(r, 5, entrada.endpointInicio, estilos.normal);
            texto(r, 6, entrada.metodoInicio, estilos.normal);
            texto(r, 7, entrada.metodoFin, estilos.textoLargo);
            texto(r, 8, entrada.tablaResultadoFinal, estilos.textoLargo);
            texto(r, 9, entrada.tablasSecundarias, estilos.normal);
            texto(r, 10, entrada.retorna, estilos.normal);
            texto(r, 11, entrada.tipoRetorno, estilos.normal);
            texto(r, 12, entrada.estadoFuncional, estilos.normal);
            texto(r, 13, estadoDe(dl), estilos.estadoDe(dl));
            numero(r, 14, numeroDe(dl, "tiempo_servidor_ms"), estilos.numero);
            numero(r, 15, numeroDe(dl, "tiempo_total_ms"), estilos.numero);
            texto(r, 16, textoDe(dl, "fecha_hora"), estilos.normal);
            texto(r, 17, estadoDe(dn), estilos.estadoDe(dn));
            numero(r, 18, numeroDe(dn, "tiempo_servidor_ms"), estilos.numero);
            numero(r, 19, numeroDe(dn, "tiempo_total_ms"), estilos.numero);
            texto(r, 20, textoDe(dn, "fecha_hora"), estilos.normal);
            Double totalLocal = numeroDe(dl, "tiempo_total_ms");
            Double totalNgrok = numeroDe(dn, "tiempo_total_ms");
            if (totalLocal != null && totalNgrok != null) {
                numero(r, 21, redondear(totalNgrok - totalLocal), estilos.numero);
            } else {
                texto(r, 21, "N.D.", estilos.normal);
            }
            if (totalLocal != null && totalLocal > 0 && totalNgrok != null) {
                numero(r, 22, redondear((totalNgrok - totalLocal) / totalLocal * 100.0), estilos.porcentaje);
            } else {
                texto(r, 22, "N.D.", estilos.normal);
            }
            Double referencia = totalLocal != null ? totalLocal : totalNgrok;
            boolean exito = "OK".equals(estadoDe(dl)) || "OK".equals(estadoDe(dn));
            if (exito && referencia != null && promedio > 0) {
                numero(r, 23, redondear(referencia / promedio * 100.0), estilos.porcentaje);
                boolean critico = referencia > promedio * (1 + umbralPct / 100.0);
                texto(r, 24, critico ? "CRITICO" : "NORMAL", critico ? estilos.critico : estilos.normal);
            } else {
                texto(r, 23, "N.D.", estilos.normal);
                texto(r, 24, "N.D.", estilos.normal);
            }
            texto(r, 25, textoDe(dl, "observacion"), estilos.textoLargo);
            texto(r, 26, textoDe(dn, "observacion"), estilos.textoLargo);
            numero(r, 27, numeroDe(dl, "tiempo_aceptacion_ms"), estilos.numero);
            numero(r, 28, numeroDe(dl, "tiempo_total_ia_ms"), estilos.numero);
            numero(r, 29, numeroDe(dn, "tiempo_aceptacion_ms"), estilos.numero);
            numero(r, 30, numeroDe(dn, "tiempo_total_ia_ms"), estilos.numero);
        }
        hoja.createFreezePane(0, 1);
        hoja.setAutoFilter(new CellRangeAddress(0, 34, 0, CABECERAS.length - 1));
        int[] anchos = {9, 30, 15, 24, 26, 24, 40, 60, 34, 28, 9, 15, 18, 18, 20, 20, 20, 18, 20, 20, 20,
            20, 20, 20, 13, 42, 42, 22, 22, 22, 22};
        for (int c = 0; c < anchos.length; c++) {
            hoja.setColumnWidth(c, anchos[c] * 256);
        }
    }

    private static void hojaResumen(Workbook libro, Estilos estilos, List<CatalogoCasosUso.Entrada> catalogo,
                                    Map<String, JsonObject> mapaL, Map<String, JsonObject> mapaN,
                                    double promedio, int umbralPct) {
        Sheet hoja = libro.createSheet("Resumen");
        int fila = 0;
        fila = pareja(hoja, fila, "Casos definidos", 34, estilos);
        fila = pareja(hoja, fila, "Casos ejecutados LOCAL", contarEjecutados(mapaL), estilos);
        fila = pareja(hoja, fila, "Casos ejecutados NGROK", contarEjecutados(mapaN), estilos);
        fila++;
        fila = estadisticas(hoja, fila, "LOCAL", mapaL, catalogo, estilos);
        fila++;
        fila = estadisticas(hoja, fila, "NGROK", mapaN, catalogo, estilos);
        fila++;
        fila = parejaTexto(hoja, fila, "Umbral utilizado (%)", String.valueOf(umbralPct), estilos);
        fila = parejaTexto(hoja, fila, "Promedio de referencia (ms)",
                promedio > 0 ? formato(promedio) : "N.D.", estilos);
        fila = parejaTexto(hoja, fila, "Fecha ultima prueba LOCAL", ultimaFecha(mapaL), estilos);
        fila = parejaTexto(hoja, fila, "Fecha ultima prueba NGROK", ultimaFecha(mapaN), estilos);
        fila++;
        fila = nota(hoja, fila, "La medicion se ejecuta desde el navegador con performance.now() "
                + "(tiempo total, incluidos red y tunel) y con System.nanoTime() en el servidor "
                + "(procesamiento Java/MySQL).", estilos);
        fila = nota(hoja, fila, "Es normal que el tiempo de servidor LOCAL y NGROK sean parecidos: "
                + "Tomcat, Java y MySQL siguen en la misma maquina. La diferencia del tunel se refleja "
                + "principalmente en el tiempo total NGROK.", estilos);
        fila = nota(hoja, fila, "CRITICO significa comparativamente lento (tiempo > promedio x "
                + "(1 + umbral/100)); NO implica incumplimiento de la ERS.", estilos);
        fila = nota(hoja, fila, "Los CU con estado funcional PARCIAL se miden igual: el estado funcional "
                + "es independiente del estado de medicion.", estilos);
        hoja.setColumnWidth(0, 40 * 256);
        hoja.setColumnWidth(1, 60 * 256);
    }

    private static int estadisticas(Sheet hoja, int fila, String entorno, Map<String, JsonObject> mapa,
                                    List<CatalogoCasosUso.Entrada> catalogo, Estilos estilos) {
        List<Double> tiempos = new ArrayList<>();
        String lentoCu = "N.D.";
        double lentoMs = -1;
        for (CatalogoCasosUso.Entrada entrada : catalogo) {
            JsonObject filaDatos = mapa.get(entrada.cuId);
            if (filaDatos == null || !"OK".equals(estadoDe(filaDatos))) {
                continue;
            }
            Double total = numeroDe(filaDatos, "tiempo_total_ms");
            if (total != null) {
                tiempos.add(total);
                if (total > lentoMs) {
                    lentoMs = total;
                    lentoCu = entrada.cuId + " " + entrada.nombre;
                }
            }
        }
        tiempos.sort(Comparator.naturalOrder());
        fila = parejaTexto(hoja, fila, "Promedio " + entorno + " (ms)", tiempos.isEmpty() ? "N.D."
                : formato(promedioDe(tiempos)), estilos);
        fila = parejaTexto(hoja, fila, "Mediana " + entorno + " (ms)", tiempos.isEmpty() ? "N.D."
                : formato(medianaDe(tiempos)), estilos);
        fila = parejaTexto(hoja, fila, "Minimo " + entorno + " (ms)", tiempos.isEmpty() ? "N.D."
                : formato(tiempos.get(0)), estilos);
        fila = parejaTexto(hoja, fila, "Maximo " + entorno + " (ms)", tiempos.isEmpty() ? "N.D."
                : formato(tiempos.get(tiempos.size() - 1)), estilos);
        return parejaTexto(hoja, fila, "CU mas lento " + entorno,
                lentoMs < 0 ? "N.D." : lentoCu + " (" + formato(lentoMs) + " ms)", estilos);
    }

    private static void hojaCoherencia(Workbook libro, Estilos estilos, List<CatalogoCasosUso.Entrada> catalogo,
                                       List<String[]> filasCoherencia) {
        Sheet hoja = libro.createSheet("Coherencia_CU");
        Row cabecera = hoja.createRow(0);
        String[] titulos = {"Tipo", "Elemento", "CU asociado", "Estado", "Observacion"};
        for (int c = 0; c < titulos.length; c++) {
            Cell celda = cabecera.createCell(c);
            celda.setCellValue(titulos[c]);
            celda.setCellStyle(estilos.cabecera);
        }
        int fila = 1;
        for (CatalogoCasosUso.Entrada entrada : catalogo) {
            Row r = hoja.createRow(fila++);
            texto(r, 0, "OK", estilos.normal);
            texto(r, 1, entrada.cuId + " " + entrada.nombre, estilos.normal);
            texto(r, 2, entrada.cuId, estilos.normal);
            texto(r, 3, "OK", estilos.normal);
            texto(r, 4, "Estado funcional: " + entrada.estadoFuncional
                    + ("PARCIAL".equals(entrada.estadoFuncional)
                            ? " (medicion permitida; desviacion funcional pendiente)" : ""), estilos.textoLargo);
        }
        if (filasCoherencia != null) {
            for (String[] filaCoherencia : filasCoherencia) {
                Row r = hoja.createRow(fila++);
                for (int c = 0; c < 5; c++) {
                    texto(r, c, c < filaCoherencia.length ? filaCoherencia[c] : "", estilos.textoLargo);
                }
            }
        }
        int[] anchos = {24, 46, 14, 28, 70};
        for (int c = 0; c < anchos.length; c++) {
            hoja.setColumnWidth(c, anchos[c] * 256);
        }
        hoja.createFreezePane(0, 1);
        hoja.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, 4));
    }

    // ------------------------------------------------------------------ apoyo

    private static Map<String, JsonObject> indexar(JsonArray filas) {
        Map<String, JsonObject> mapa = new LinkedHashMap<>();
        if (filas != null) {
            for (JsonElement elemento : filas) {
                if (elemento.isJsonObject() && elemento.getAsJsonObject().has("cu_id")) {
                    mapa.put(elemento.getAsJsonObject().get("cu_id").getAsString(), elemento.getAsJsonObject());
                }
            }
        }
        return mapa;
    }

    private static double promedioReferencia(List<CatalogoCasosUso.Entrada> catalogo,
                                             Map<String, JsonObject> mapaL, Map<String, JsonObject> mapaN) {
        List<Double> tiempos = new ArrayList<>();
        for (CatalogoCasosUso.Entrada entrada : catalogo) {
            JsonObject dl = mapaL.get(entrada.cuId);
            JsonObject dn = mapaN.get(entrada.cuId);
            Double total = dl != null && "OK".equals(estadoDe(dl)) ? numeroDe(dl, "tiempo_total_ms")
                    : dn != null && "OK".equals(estadoDe(dn)) ? numeroDe(dn, "tiempo_total_ms") : null;
            if (total != null) {
                tiempos.add(total);
            }
        }
        return promedioDe(tiempos);
    }

    private static int contarEjecutados(Map<String, JsonObject> mapa) {
        int total = 0;
        for (JsonObject fila : mapa.values()) {
            String estado = estadoDe(fila);
            if ("OK".equals(estado) || "ERROR".equals(estado)) {
                total++;
            }
        }
        return total;
    }

    private static String ultimaFecha(Map<String, JsonObject> mapa) {
        String ultima = "";
        for (JsonObject fila : mapa.values()) {
            String fecha = textoDe(fila, "fecha_hora");
            if (fecha != null && fecha.compareTo(ultima) > 0) {
                ultima = fecha;
            }
        }
        return ultima.isEmpty() ? "N.D." : ultima;
    }

    private static String estadoDe(JsonObject fila) {
        return fila == null ? "N.D." : textoDe(fila, "estado_medicion");
    }

    private static Double numeroDe(JsonObject fila, String clave) {
        if (fila == null || !fila.has(clave) || fila.get(clave).isJsonNull()) {
            return null;
        }
        try {
            double valor = fila.get(clave).getAsDouble();
            return valor < 0 ? null : valor;
        } catch (Exception e) {
            return null;
        }
    }

    private static String textoDe(JsonObject fila, String clave) {
        if (fila == null || !fila.has(clave) || fila.get(clave).isJsonNull()) {
            return "";
        }
        return fila.get(clave).getAsString();
    }

    private static double promedioDe(List<Double> valores) {
        if (valores.isEmpty()) {
            return -1;
        }
        double suma = 0;
        for (double valor : valores) {
            suma += valor;
        }
        return suma / valores.size();
    }

    private static double medianaDe(List<Double> ordenados) {
        int n = ordenados.size();
        if (n == 0) {
            return -1;
        }
        if (n % 2 == 1) {
            return ordenados.get(n / 2);
        }
        return (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2.0;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private static String formato(double valor) {
        return String.format(java.util.Locale.ROOT, "%.2f", valor);
    }

    private static void texto(Row fila, int columna, String valor, CellStyle estilo) {
        if (valor == null) {
            valor = "";
        }
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(estilo);
    }

    private static void numero(Row fila, int columna, Double valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        if (valor == null) {
            celda.setBlank();
        } else {
            celda.setCellValue(valor);
        }
        celda.setCellStyle(estilo);
    }

    private static int pareja(Sheet hoja, int fila, String etiqueta, int valor, Estilos estilos) {
        Row r = hoja.createRow(fila);
        texto(r, 0, etiqueta, estilos.negrita);
        Cell celda = r.createCell(1);
        celda.setCellValue(valor);
        celda.setCellStyle(estilos.entero);
        return fila + 1;
    }

    private static int parejaTexto(Sheet hoja, int fila, String etiqueta, String valor, Estilos estilos) {
        Row r = hoja.createRow(fila);
        texto(r, 0, etiqueta, estilos.negrita);
        texto(r, 1, valor, estilos.normal);
        return fila + 1;
    }

    private static int nota(Sheet hoja, int fila, String texto, Estilos estilos) {
        Row r = hoja.createRow(fila);
        texto(r, 0, texto, estilos.textoLargo);
        return fila + 1;
    }

    private static final class Estilos {

        final CellStyle cabecera;
        final CellStyle normal;
        final CellStyle textoLargo;
        final CellStyle numero;
        final CellStyle porcentaje;
        final CellStyle entero;
        final CellStyle negrita;
        final CellStyle critico;

        Estilos(Workbook libro) {
            Font fuenteCabecera = libro.createFont();
            fuenteCabecera.setBold(true);
            fuenteCabecera.setColor(IndexedColors.WHITE.getIndex());
            cabecera = libro.createCellStyle();
            cabecera.setFont(fuenteCabecera);
            cabecera.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setWrapText(true);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);

            normal = libro.createCellStyle();
            normal.setVerticalAlignment(VerticalAlignment.TOP);
            normal.setBorderBottom(BorderStyle.THIN);
            normal.setBorderRight(BorderStyle.THIN);

            textoLargo = libro.createCellStyle();
            textoLargo.cloneStyleFrom(normal);
            textoLargo.setWrapText(true);

            numero = libro.createCellStyle();
            numero.cloneStyleFrom(normal);
            numero.setDataFormat(libro.createDataFormat().getFormat("0.00"));
            numero.setAlignment(HorizontalAlignment.RIGHT);

            porcentaje = libro.createCellStyle();
            porcentaje.cloneStyleFrom(numero);
            porcentaje.setDataFormat(libro.createDataFormat().getFormat("0.00\"%\""));

            entero = libro.createCellStyle();
            entero.cloneStyleFrom(normal);
            entero.setDataFormat(libro.createDataFormat().getFormat("0"));

            Font fuenteNegrita = libro.createFont();
            fuenteNegrita.setBold(true);
            negrita = libro.createCellStyle();
            negrita.cloneStyleFrom(normal);
            negrita.setFont(fuenteNegrita);

            critico = libro.createCellStyle();
            critico.cloneStyleFrom(normal);
            critico.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            critico.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            critico.setFont(fuenteNegrita);
        }

        CellStyle estadoDe(JsonObject fila) {
            if (fila == null) {
                return normal;
            }
            String estado = GeneradorExcelRendimiento.estadoDe(fila);
            return "ERROR".equals(estado) ? critico : normal;
        }
    }
}
