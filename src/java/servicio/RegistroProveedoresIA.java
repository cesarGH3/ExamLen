package servicio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registro de proveedores de IA descubiertos con {@link ServiceLoader} y ordenados por código.
 * La interfaz, la validación y el servicio consultan SOLO este registro (RNF-12).
 */
public final class RegistroProveedoresIA {

    private static final List<ProveedorIAFabrica> FABRICAS = cargar();

    private RegistroProveedoresIA() {
    }

    private static List<ProveedorIAFabrica> cargar() {
        List<ProveedorIAFabrica> lista = new ArrayList<>();
        for (ProveedorIAFabrica fabrica : ServiceLoader.load(ProveedorIAFabrica.class)) {
            lista.add(fabrica);
        }
        lista.sort(Comparator.comparing(ProveedorIAFabrica::codigo));
        return List.copyOf(lista);
    }

    public static List<ProveedorIAFabrica> todas() {
        return FABRICAS;
    }

    public static Optional<ProveedorIAFabrica> porCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        for (ProveedorIAFabrica fabrica : FABRICAS) {
            if (fabrica.codigo().equalsIgnoreCase(codigo.strip())) {
                return Optional.of(fabrica);
            }
        }
        return Optional.empty();
    }

    /** true si el modelo está en la lista permitida del proveedor; vacío significa el predeterminado. */
    public static boolean esModeloPermitido(String codigo, String modelo) {
        Optional<ProveedorIAFabrica> fabrica = porCodigo(codigo);
        if (fabrica.isEmpty()) {
            return false;
        }
        if (modelo == null || modelo.isBlank()) {
            return true;
        }
        String limpio = modelo.strip();
        for (String permitido : fabrica.get().modelosPermitidos()) {
            if (permitido.equals(limpio)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Modelo que usará el trabajo: el solicitado (ya validado) o el predeterminado del proveedor.
     * No exige clave ni llamada de red.
     */
    public static String modeloEfectivo(String codigo, String modelo) {
        ProveedorIAFabrica fabrica = porCodigo(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor de IA desconocido: " + codigo));
        if (modelo != null && !modelo.isBlank()) {
            return modelo.strip();
        }
        return fabrica.modeloPredeterminado();
    }
}
