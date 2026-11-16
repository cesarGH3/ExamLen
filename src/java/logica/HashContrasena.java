package logica;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Hash de contrasenas conforme a RNF-01: BCrypt con factor de coste 12 para todo hash nuevo
 * (registro, cambio, altas administrativas, reseteo y recuperacion por correo).
 * Los hashes legacy PBKDF2-HMAC-SHA256 (formato pbkdf2_sha256$iteraciones$sal$hash) se siguen
 * verificando para poder migrar la cuenta a BCrypt en el primer login correcto
 * (ver {@link #necesitaRecalcularHash(String)}). No se implementa criptografia propia: BCrypt de jBCrypt.
 */
public final class HashContrasena {

    private static final String PREFIJO_ANTERIOR = "pbkdf2_sha256";
    private static final int COSTO_BCRYPT = 12;

    private final SecureRandom aleatorio = new SecureRandom();

    /** Genera un hash BCrypt nuevo con coste 12. */
    public String generarHash(String contrasena) {
        return BCrypt.hashpw(contrasena, BCrypt.gensalt(COSTO_BCRYPT, aleatorio));
    }

    /** Verifica una contrasena contra un hash BCrypt o contra un hash legacy PBKDF2. */
    public boolean verificar(String contrasena, String almacenado) {
        if (contrasena == null || almacenado == null) {
            return false;
        }
        if (esBcrypt(almacenado)) {
            try {
                return BCrypt.checkpw(contrasena, almacenado);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return verificarFormatoAnterior(contrasena, almacenado);
    }

    /** true si el hash almacenado no es BCrypt y conviene regenerarlo tras un login correcto. */
    public boolean necesitaRecalcularHash(String almacenado) {
        return almacenado != null && !esBcrypt(almacenado) && esFormatoAnterior(almacenado);
    }

    private static boolean esBcrypt(String almacenado) {
        return almacenado.startsWith("$2a$") || almacenado.startsWith("$2b$") || almacenado.startsWith("$2y$");
    }

    private static boolean esFormatoAnterior(String almacenado) {
        return almacenado.startsWith(PREFIJO_ANTERIOR + "$");
    }

    private boolean verificarFormatoAnterior(String contrasena, String almacenado) {
        String[] partes = almacenado.split("\\$");
        if (partes.length != 4 || !PREFIJO_ANTERIOR.equals(partes[0])) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = derivar(contrasena.toCharArray(), sal, iteraciones);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] derivar(char[] contrasena, byte[] sal, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(contrasena, sal, iteraciones, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("No fue posible calcular el hash de la contraseña", e);
        } finally {
            spec.clearPassword();
        }
    }
}
