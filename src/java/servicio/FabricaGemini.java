package servicio;

/** Proveedor Google Gemini registrado por ServiceLoader (RNF-12). */
public final class FabricaGemini implements ProveedorIAFabrica {

    @Override
    public String codigo() {
        return "GEMINI";
    }

    @Override
    public String nombreVisible() {
        return "Google Gemini";
    }

    @Override
    public String modeloPredeterminado() {
        return ClienteGemini.MODELO_PREDETERMINADO;
    }

    @Override
    public java.util.List<String> modelosPermitidos() {
        return ClienteGemini.modelosPermitidos();
    }

    @Override
    public boolean disponible() {
        return ConfiguracionIA.claveGemini() != null;
    }

    @Override
    public ProveedorIA crear(String modelo, String clave) {
        return new ClienteGemini(modelo, clave);
    }
}
