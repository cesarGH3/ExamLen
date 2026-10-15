package logica;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Genera contrasenas temporales que cumplen la politica. Excluye caracteres confusos (0/O, 1/l/I). */
public final class GeneradorContrasenaTemporal {

    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String ESPECIALES = "!@#$%*?-_";
    private static final String TODOS = MAYUSCULAS + MINUSCULAS + NUMEROS + ESPECIALES;
    private static final int LONGITUD = 16;

    private final SecureRandom random = new SecureRandom();

    public String generar() {
        List<Character> caracteres = new ArrayList<>(LONGITUD);
        caracteres.add(tomar(MAYUSCULAS));
        caracteres.add(tomar(MINUSCULAS));
        caracteres.add(tomar(NUMEROS));
        caracteres.add(tomar(ESPECIALES));
        while (caracteres.size() < LONGITUD) {
            caracteres.add(tomar(TODOS));
        }
        Collections.shuffle(caracteres, random);
        StringBuilder sb = new StringBuilder(LONGITUD);
        for (char c : caracteres) {
            sb.append(c);
        }
        return sb.toString();
    }

    private char tomar(String conjunto) {
        return conjunto.charAt(random.nextInt(conjunto.length()));
    }
}
