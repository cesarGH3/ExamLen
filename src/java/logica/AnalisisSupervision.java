package logica;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reglas de análisis de supervisión (RF-50): similitud de texto y tiempos atípicos.
 * Los umbrales del ERS (coeficiente de similitud y fracción de la mediana) se configuran en el
 * servidor y llegan como parámetros; aquí solo queda lo que no es configurable.
 */
public final class AnalisisSupervision {

    /**
     * Mínimo de caracteres normalizados para comparar dos respuestas: por debajo, cualquier
     * coincidencia es trivial (p. ej. «the dog is brown», 16 caracteres, no dice nada sobre nadie).
     */
    public static final int MINIMO_CARACTERES = 40;

    /**
     * Longitud de los n-gramas comparados. Tres caracteres toleran cambios menores entre copias
     * y a la vez evitan que dos respuestas cortas coincidan por casualidad.
     */
    public static final int LONGITUD_N_GRAMA = 3;

    private AnalisisSupervision() {
    }

    /** Recorta, colapsa espacios internos y unifica mayúsculas (misma regla que la práctica). */
    public static String normalizarComparacion(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** ¿La respuesta coincide con alguna de las formas aceptadas (separadas por punto y coma)? */
    public static boolean coincideConEsperada(String respuesta, String esperada) {
        String dada = normalizarComparacion(respuesta);
        if (dada.isEmpty() || esperada == null) {
            return false;
        }
        for (String aceptada : esperada.split(";")) {
            if (normalizarComparacion(aceptada).equals(dada)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Coeficiente de Jaccard sobre n-gramas de caracteres: intersección dividida por la unión.
     * Es una medida explicable (0 = nada en común, 1 = el mismo texto). Devuelve 0 cuando alguna
     * de las dos respuestas no llega al mínimo de caracteres.
     */
    public static double similitud(String textoA, String textoB) {
        String a = normalizarParaGramas(textoA);
        String b = normalizarParaGramas(textoB);
        if (a.length() < MINIMO_CARACTERES || b.length() < MINIMO_CARACTERES) {
            return 0;
        }
        Set<String> gramasA = gramas(a);
        Set<String> gramasB = gramas(b);
        if (gramasA.isEmpty() || gramasB.isEmpty()) {
            return 0;
        }
        Set<String> interseccion = new HashSet<>(gramasA);
        interseccion.retainAll(gramasB);
        Set<String> union = new HashSet<>(gramasA);
        union.addAll(gramasB);
        return union.isEmpty() ? 0 : (double) interseccion.size() / union.size();
    }

    public static long mediana(List<Long> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        List<Long> ordenados = new ArrayList<>(valores);
        Collections.sort(ordenados);
        int medio = ordenados.size() / 2;
        if (ordenados.size() % 2 == 1) {
            return ordenados.get(medio);
        }
        return (ordenados.get(medio - 1) + ordenados.get(medio)) / 2;
    }

    /** RF-50: duración menor a la fracción configurada de la mediana del grupo. */
    public static boolean tiempoAtipico(long duracion, long mediana, double fraccion) {
        return mediana > 0 && fraccion > 0 && duracion < mediana * fraccion;
    }

    /** Normalización para n-gramas: minúsculas, sin puntuación y con espacios colapsados. */
    private static String normalizarParaGramas(String valor) {
        return valor == null ? "" : valor.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static Set<String> gramas(String texto) {
        Set<String> gramas = new HashSet<>();
        for (int i = 0; i + LONGITUD_N_GRAMA <= texto.length(); i++) {
            gramas.add(texto.substring(i, i + LONGITUD_N_GRAMA));
        }
        return gramas;
    }
}
