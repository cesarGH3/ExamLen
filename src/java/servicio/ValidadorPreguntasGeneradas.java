package servicio;

import logica.Competencia;
import logica.Dificultad;
import logica.NivelIngles;
import logica.TipoPregunta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normaliza y valida cada ítem devuelto por la IA según su tipo antes de persistir (RF-26/27,
 * CU-19 5b). Los metadatos que el docente o la pregunta base fijan no se toman del modelo:
 * llegan como valores fijos y solo se completan con lo generado cuando no hay valor fijo.
 */
public final class ValidadorPreguntasGeneradas {

    /** TEXT permite 65535 caracteres: recorte técnico, no un máximo académico (RF-19). */
    private static final int MAX_ENUNCIADO = 65535;
    private static final int MAX_RESPUESTA = 500;
    private static final int MAX_TEMA = 120;
    private static final int MIN_OPCIONES = 2;
    private static final String[] PALABRAS_VERDADERO = {"verdadero", "true", "v", "cierto"};
    private static final String[] PALABRAS_FALSO = {"falso", "false", "f"};

    private ValidadorPreguntasGeneradas() {
    }

    public static final class Opcion {
        public final String texto;
        public final boolean correcta;

        public Opcion(String texto, boolean correcta) {
            this.texto = texto;
            this.correcta = correcta;
        }
    }

    public static final class PreguntaNormalizada {
        public String enunciado;
        public TipoPregunta tipo;
        public Dificultad dificultad;
        public Competencia competencia;
        public String tema;
        public String respuestaEsperada;
        public final List<Opcion> opciones = new ArrayList<>();
    }

    public static final class Resultado {
        public final List<PreguntaNormalizada> validas = new ArrayList<>();
        public int descartadas;
        public final List<String> motivos = new ArrayList<>();
    }

    /**
     * @param tipoForzado        para variantes: tipo de la pregunta base (null = el del modelo)
     * @param dificultadForzada  para variantes: dificultad de la base (null = la del modelo)
     * @param competenciaFija    valor del docente/base (null = el del modelo)
     * @param temaFijo           valor del docente/base (null o vacío = el del modelo o "IA")
     */
    public static Resultado normalizar(List<PreguntaGenerada> items, NivelIngles nivel,
                                       TipoPregunta tipoForzado, Dificultad dificultadForzada,
                                       Competencia competenciaFija, String temaFijo) {
        Resultado resultado = new Resultado();
        for (PreguntaGenerada generada : items) {
            PreguntaNormalizada normalizada = normalizarUna(generada, nivel, tipoForzado, dificultadForzada,
                    competenciaFija, temaFijo);
            if (normalizada == null) {
                resultado.descartadas++;
                resultado.motivos.add("Ítem descartado por estructura inválida según su tipo.");
            } else {
                resultado.validas.add(normalizada);
            }
        }
        return resultado;
    }

    private static PreguntaNormalizada normalizarUna(PreguntaGenerada generada, NivelIngles nivel,
                                                     TipoPregunta tipoForzado, Dificultad dificultadForzada,
                                                     Competencia competenciaFija, String temaFijo) {
        PreguntaNormalizada normalizada = new PreguntaNormalizada();
        normalizada.enunciado = recortar(generada.enunciado, MAX_ENUNCIADO);
        if (normalizada.enunciado == null || normalizada.enunciado.isBlank()) {
            return null;
        }
        normalizada.enunciado = normalizada.enunciado.strip();
        normalizada.tipo = tipoForzado != null ? tipoForzado : enumeracion(TipoPregunta.class, generada.tipo);
        if (normalizada.tipo == null) {
            return null;
        }
        Dificultad dificultad = enumeracion(Dificultad.class, generada.dificultad);
        normalizada.dificultad = dificultadForzada != null ? dificultadForzada
                : (dificultad == null ? Dificultad.MEDIA : dificultad);
        Competencia competencia = enumeracion(Competencia.class, generada.competencia);
        normalizada.competencia = competenciaFija != null ? competenciaFija
                : (competencia == null ? Competencia.GRAMMAR : competencia);
        String tema = temaFijo != null && !temaFijo.isBlank() ? temaFijo : generada.tema;
        String temaLimpio = tema == null || tema.isBlank() ? "IA" : recortar(tema.strip(), MAX_TEMA);
        normalizada.tema = temaLimpio == null || temaLimpio.isBlank() ? "IA" : temaLimpio;

        switch (normalizada.tipo) {
            case SELECCION_MULTIPLE:
                if (!opcionesSeleccionMultiple(generada, normalizada)) {
                    return null;
                }
                return normalizada;
            case VERDADERO_FALSO:
                if (!opcionesVerdaderoFalso(generada, normalizada)) {
                    return null;
                }
                return normalizada;
            case RESPUESTA_CORTA:
                String esperada = recortar(generada.respuestaEsperada, MAX_RESPUESTA);
                if (esperada == null || esperada.isBlank()) {
                    return null;
                }
                normalizada.respuestaEsperada = esperada.strip();
                return normalizada;
            default:
                String abierta = recortar(generada.respuestaEsperada, MAX_RESPUESTA);
                normalizada.respuestaEsperada = abierta == null || abierta.isBlank() ? null : abierta.strip();
                return normalizada;
        }
    }

    private static boolean opcionesSeleccionMultiple(PreguntaGenerada generada, PreguntaNormalizada normalizada) {
        List<Opcion> opciones = new ArrayList<>();
        int correctas = 0;
        for (PreguntaGenerada.OpcionGenerada opcion : generada.opciones) {
            String texto = recortar(opcion.texto, 500);
            if (texto == null || texto.isBlank()) {
                continue;
            }
            texto = texto.strip();
            if (duplicada(opciones, texto)) {
                continue;
            }
            opciones.add(new Opcion(texto, opcion.correcta));
            if (opcion.correcta) {
                correctas++;
            }
        }
        if (opciones.size() < MIN_OPCIONES || correctas != 1) {
            return false;
        }
        normalizada.opciones.addAll(opciones);
        return true;
    }

    /** Produce SIEMPRE las opciones Verdadero/Falso con una sola correcta, sin inferir al azar. */
    private static boolean opcionesVerdaderoFalso(PreguntaGenerada generada, PreguntaNormalizada normalizada) {
        Boolean correctaEsVerdadero = null;
        for (PreguntaGenerada.OpcionGenerada opcion : generada.opciones) {
            String texto = opcion.texto == null ? "" : opcion.texto.strip().toLowerCase(Locale.ROOT);
            boolean esVerdadero = coincide(texto, PALABRAS_VERDADERO);
            boolean esFalso = coincide(texto, PALABRAS_FALSO);
            if (!esVerdadero && !esFalso) {
                continue;
            }
            if (opcion.correcta) {
                if (correctaEsVerdadero != null) {
                    return false;
                }
                correctaEsVerdadero = esVerdadero;
            }
        }
        if (correctaEsVerdadero == null && generada.respuestaEsperada != null) {
            String esperada = generada.respuestaEsperada.strip().toLowerCase(Locale.ROOT);
            if (coincide(esperada, PALABRAS_VERDADERO)) {
                correctaEsVerdadero = Boolean.TRUE;
            } else if (coincide(esperada, PALABRAS_FALSO)) {
                correctaEsVerdadero = Boolean.FALSE;
            }
        }
        if (correctaEsVerdadero == null) {
            return false;
        }
        normalizada.opciones.add(new Opcion("Verdadero", correctaEsVerdadero));
        normalizada.opciones.add(new Opcion("Falso", !correctaEsVerdadero));
        return true;
    }

    private static boolean duplicada(List<Opcion> opciones, String texto) {
        for (Opcion opcion : opciones) {
            if (opcion.texto.equalsIgnoreCase(texto)) {
                return true;
            }
        }
        return false;
    }

    private static boolean coincide(String texto, String[] palabras) {
        for (String palabra : palabras) {
            if (texto.equals(palabra)) {
                return true;
            }
        }
        return false;
    }

    /** Comparación determinista para RF-28: trim, espacios repetidos a uno y sin mayúsculas. */
    public static String normalizarEnunciado(String enunciado) {
        if (enunciado == null) {
            return "";
        }
        return enunciado.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E enumeracion(Class<E> tipo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
