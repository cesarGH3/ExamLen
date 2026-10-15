package servlet;

import java.util.HashMap;
import java.util.Map;

/** Textos de error para el usuario. Nunca incluyen detalles tecnicos. */
public final class PaginasError {

    private PaginasError() {
    }

    public static Map<String, String> datos(String contextPath, int estado) {
        String titulo;
        String mensaje;
        switch (estado) {
            case 400:
                titulo = "Solicitud no válida";
                mensaje = "Los datos enviados no tienen el formato esperado. Revisa el formulario e inténtalo de nuevo.";
                break;
            case 403:
                titulo = "Acción no permitida";
                mensaje = "No tienes permiso para esta acción o tu sesión expiró. Vuelve a cargar la página e inténtalo de nuevo.";
                break;
            case 404:
                titulo = "Página no encontrada";
                mensaje = "La dirección no existe. Revisa el enlace o vuelve al inicio.";
                break;
            case 405:
                titulo = "Método no permitido";
                mensaje = "Esta dirección no admite ese tipo de solicitud.";
                break;
            case 503:
                titulo = "Servicio no disponible";
                mensaje = "No hay conexión con la base de datos. ExamLen no puede funcionar hasta que se restablezca. Inténtalo de nuevo en unos minutos.";
                break;
            default:
                estado = 500;
                titulo = "Error interno";
                mensaje = "Ocurrió un error inesperado. Si se repite, informa al administrador la hora en que ocurrió.";
        }
        Map<String, String> datos = new HashMap<>();
        datos.put("ctx", contextPath);
        datos.put("codigo", String.valueOf(estado));
        datos.put("titulo", titulo);
        datos.put("mensaje", mensaje);
        return datos;
    }
}
