package api;

/**
 * Proceso auxiliar de pruebas: valida un token con el secreto recibido por entorno.
 * Uso: api.ProcesoSecreto <token>. Imprime OK o NO. No imprime secretos.
 */
public final class ProcesoSecreto {

    private ProcesoSecreto() {
    }

    public static void main(String[] args) {
        boolean valido = args.length == 1 && ApiTokens.validar(args[0]).isPresent();
        System.out.println(valido ? "OK" : "NO");
        System.exit(valido ? 0 : 2);
    }
}
