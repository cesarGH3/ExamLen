package servicio;

import java.util.List;

/**
 * SPI de un proveedor de IA (RNF-12). Añadir un proveedor nuevo requiere únicamente una
 * implementación de esta interfaz y su registro en
 * {@code META-INF/services/servicio.ProveedorIAFabrica}; no se toca el servlet, el servicio
 * de generación ni la pantalla.
 */
public interface ProveedorIAFabrica {

    /** Código estable que viaja en las solicitudes (por ejemplo GEMINI). */
    String codigo();

    /** Nombre visible para la interfaz. */
    String nombreVisible();

    String modeloPredeterminado();

    /** Lista permitida de modelos; cualquier otro valor se rechaza antes de llamar al proveedor. */
    List<String> modelosPermitidos();

    /** true si la configuración/clave del proveedor está presente. */
    boolean disponible();

    /** Crea el cliente con la clave personal del docente. */
    ProveedorIA crear(String modelo, String clave) throws GeneracionFallidaException;
}
