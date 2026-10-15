package servicio;

/** Proveedor Groq registrado por ServiceLoader (RNF-12). */
public final class FabricaGroq implements ProveedorIAFabrica {

    @Override
    public String codigo() {
        return "GROQ";
    }

    @Override
    public String nombreVisible() {
        return "Groq";
    }

    @Override
    public String modeloPredeterminado() {
        return ClienteGroq.MODELO_PREDETERMINADO;
    }

    @Override
    public java.util.List<String> modelosPermitidos() {
        return ClienteGroq.modelosPermitidos();
    }

    @Override
    public boolean disponible() {
        return ConfiguracionIA.claveGroq() != null;
    }

    @Override
    public ProveedorIA crear(String modelo) {
        return new ClienteGroq(modelo);
    }
}
