package logica;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

/**
 * Token de recuperacion de contrasena (RF-11). El token en claro nunca se persiste:
 * en token_recuperacion solo se guarda su hash (SHA-256).
 */
public class TokenRecuperacion {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES_TOKEN = 32;
    private static final long MILISEGUNDOS_VIGENCIA = 30 * 60_000L;

    private int idTokenRecuperacion;
    public String tokenHashTokenRecuperacion;
    public Date fechaCreacionTokenRecuperacion;
    public Date fechaExpiracionTokenRecuperacion;
    public boolean usadoTokenRecuperacion;
    public Usuario usuarioTokenRecuperacion;

    /** Token en claro: existe solo para armar el enlace del correo; jamas se guarda en la base de datos. */
    private String tokenPlanoTokenRecuperacion;

    public static TokenRecuperacion generarTokenRecuperacion(Usuario usuario) {
        byte[] bytes = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(bytes);
        TokenRecuperacion token = new TokenRecuperacion();
        token.usuarioTokenRecuperacion = usuario;
        token.tokenPlanoTokenRecuperacion = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        token.tokenHashTokenRecuperacion = hash(token.tokenPlanoTokenRecuperacion);
        token.fechaCreacionTokenRecuperacion = Fechas.ahora();
        token.fechaExpiracionTokenRecuperacion =
                new Date(token.fechaCreacionTokenRecuperacion.getTime() + MILISEGUNDOS_VIGENCIA);
        token.usadoTokenRecuperacion = false;
        return token;
    }

    public boolean esValidoTokenRecuperacion() {
        return !usadoTokenRecuperacion
                && fechaExpiracionTokenRecuperacion != null
                && fechaExpiracionTokenRecuperacion.after(Fechas.ahora());
    }

    public void marcarUsadoTokenRecuperacion() {
        usadoTokenRecuperacion = true;
    }

    public void invalidarTokenRecuperacion() {
        marcarUsadoTokenRecuperacion();
    }

    public String consultarTokenPlanoTokenRecuperacion() {
        return tokenPlanoTokenRecuperacion;
    }

    /** SHA-256 en Base64 del token: es lo unico que se persiste y con lo que se busca. */
    public static String hash(String tokenPlano) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tokenPlano.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No fue posible calcular el hash del token", e);
        }
    }

    public int consultarIdTokenRecuperacion() {
        return idTokenRecuperacion;
    }

    public void establecerIdTokenRecuperacion(int idTokenRecuperacion) {
        this.idTokenRecuperacion = idTokenRecuperacion;
    }
}
