package api;

import servicio.Secretos;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tokens Bearer opacos de vida corta para /api/v1.
 *
 * Formato: v1.<payload_base64url>.<firma_base64url>, donde el payload contiene el id de usuario
 * y la expiracion en segundos. La firma es HMAC-SHA256 con un secreto externo al WAR. No se
 * guardan credenciales dentro del token ni en la sesion del servidor.
 */
public final class ApiTokens {

    private static final String VERSION = "v1";
    private static final String ALGORITMO = "HmacSHA256";
    private static final int MINUTOS_POR_DEFECTO = 15;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private ApiTokens() {
    }

    public static final class Token {
        public final String valor;
        public final long expiraEnSegundos;

        private Token(String valor, long expiraEnSegundos) {
            this.valor = valor;
            this.expiraEnSegundos = expiraEnSegundos;
        }
    }

    public static final class TokenValido {
        public final int idUsuario;
        public final long expiraEnSegundos;

        private TokenValido(int idUsuario, long expiraEnSegundos) {
            this.idUsuario = idUsuario;
            this.expiraEnSegundos = expiraEnSegundos;
        }
    }

    public static Token emitir(int idUsuario) {
        long expiracion = ahoraEnSegundos() + segundosDeVida();
        return emitir(idUsuario, expiracion);
    }

    static Token emitir(int idUsuario, long expiracionSegundos) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("Usuario invalido para emitir token.");
        }
        String payload = idUsuario + ":" + expiracionSegundos;
        String payloadCodificado = BASE64_URL.encodeToString(payload.getBytes(StandardCharsets.US_ASCII));
        String firmado = VERSION + "." + payloadCodificado;
        String firma = BASE64_URL.encodeToString(firmar(firmado));
        return new Token(firmado + "." + firma, expiracionSegundos);
    }

    public static Optional<TokenValido> validar(String token) {
        return validar(token, ahoraEnSegundos());
    }

    static Optional<TokenValido> validar(String token, long ahoraSegundos) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] partes = token.trim().split("\\.", -1);
        if (partes.length != 3 || !VERSION.equals(partes[0])) {
            return Optional.empty();
        }
        try {
            byte[] firmaRecibida = BASE64_URL_DECODER.decode(partes[2]);
            byte[] firmaCalculada = firmar(partes[0] + "." + partes[1]);
            if (!MessageDigest.isEqual(firmaRecibida, firmaCalculada)) {
                return Optional.empty();
            }
            String payload = new String(BASE64_URL_DECODER.decode(partes[1]), StandardCharsets.US_ASCII);
            int separador = payload.indexOf(':');
            if (separador <= 0 || separador == payload.length() - 1) {
                return Optional.empty();
            }
            int idUsuario = Integer.parseInt(payload.substring(0, separador));
            long expiracion = Long.parseLong(payload.substring(separador + 1));
            if (idUsuario <= 0 || expiracion <= ahoraSegundos) {
                return Optional.empty();
            }
            return Optional.of(new TokenValido(idUsuario, expiracion));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] firmar(String contenido) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(Secretos.claveApi(), ALGORITMO));
            return mac.doFinal(contenido.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible firmar el token de API.", e);
        }
    }

    private static long segundosDeVida() {
        String valor = System.getenv("EXAMLEN_API_TOKEN_MINUTES");
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty("examlen.api.token.minutes");
        }
        long minutos = MINUTOS_POR_DEFECTO;
        if (valor != null && !valor.isBlank()) {
            try {
                minutos = Long.parseLong(valor.trim());
            } catch (NumberFormatException e) {
                minutos = MINUTOS_POR_DEFECTO;
            }
        }
        minutos = Math.max(1, Math.min(minutos, 60));
        return minutos * 60;
    }

    private static long ahoraEnSegundos() {
        return System.currentTimeMillis() / 1000L;
    }

    /** Mensaje de cabecera WWW-Authenticate (sin datos sensibles). */
    public static String desafio(String codigo) {
        String detalle = codigo == null || codigo.isBlank() ? "invalid_token"
                : codigo.toLowerCase(Locale.ROOT);
        return "Bearer realm=\"ExamLen\", error=\"" + detalle + "\"";
    }
}
