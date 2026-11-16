package servicio;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Cifra las claves de IA de los docentes con AES-GCM y una clave externa al WAR/BD. */
public final class CifradoClaves {

    public static final String VARIABLE_CLAVE = "EXAMENL_IA_CRYPTO_KEY";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CifradoClaves() {
    }

    public static boolean configurado() {
        String valor = System.getenv(VARIABLE_CLAVE);
        return valor != null && !valor.isBlank();
    }

    public static String cifrar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("La clave de IA no puede estar vacia.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] salida = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, salida, 0, iv.length);
            System.arraycopy(cifrado, 0, salida, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(salida);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la clave de IA.", e);
        }
    }

    public static String descifrar(String textoCifrado) {
        if (textoCifrado == null || textoCifrado.isBlank()) {
            throw new IllegalArgumentException("La clave cifrada esta vacia.");
        }
        try {
            byte[] datos = Base64.getDecoder().decode(textoCifrado);
            if (datos.length <= IV_BYTES) {
                throw new IllegalArgumentException("La clave cifrada no tiene formato valido.");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(datos, 0, iv, 0, IV_BYTES);
            byte[] cifrado = new byte[datos.length - IV_BYTES];
            System.arraycopy(datos, IV_BYTES, cifrado, 0, cifrado.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar la clave de IA.", e);
        }
    }

    private static SecretKeySpec clave() {
        String valor = System.getenv(VARIABLE_CLAVE);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + VARIABLE_CLAVE
                    + " para cifrar las claves de IA.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(VARIABLE_CLAVE + " no es Base64 valido.", e);
        }
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            throw new IllegalStateException(VARIABLE_CLAVE + " debe decodificar a 16, 24 o 32 bytes.");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
