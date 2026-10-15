package servlet;

import logica.Administrador;
import logica.Competencia;
import logica.Dificultad;
import logica.Docente;
import logica.EstadoEvaluacion;
import logica.EstadoEvaluacionVirtual;
import logica.EstadoGrupo;
import logica.EstadoInscripcion;
import logica.EstadoIntento;
import logica.EstadoMaterial;
import logica.EstadoPregunta;
import logica.EstadoSolicitud;
import logica.NivelIngles;
import logica.OrigenContenido;
import logica.TipoEventoSupervision;
import logica.TipoGeneracion;
import logica.TipoPregunta;
import logica.Usuario;

/** Textos legibles para la interfaz (las enumeraciones del diagrama ya no tienen métodos). */
final class Etiquetas {

    private Etiquetas() {
    }

    static String rol(Usuario usuario) {
        if (usuario instanceof Administrador) {
            return "Administrador";
        }
        if (usuario instanceof Docente) {
            return "Docente";
        }
        return "Estudiante";
    }

    static String de(Object valor) {
        if (valor instanceof NivelIngles) {
            return nivel((NivelIngles) valor);
        }
        if (valor instanceof TipoPregunta) {
            return tipoPregunta((TipoPregunta) valor);
        }
        if (valor instanceof Dificultad) {
            return dificultad((Dificultad) valor);
        }
        if (valor instanceof Competencia) {
            return competencia((Competencia) valor);
        }
        if (valor instanceof EstadoPregunta) {
            return estadoPregunta((EstadoPregunta) valor);
        }
        if (valor instanceof EstadoGrupo) {
            return valor == EstadoGrupo.ABIERTO ? "Abierto" : "Cerrado";
        }
        if (valor instanceof EstadoInscripcion) {
            return valor == EstadoInscripcion.ACTIVA ? "Activa" : "Retirada";
        }
        if (valor instanceof OrigenContenido) {
            return valor == OrigenContenido.MANUAL ? "Manual" : "Generado por IA";
        }
        if (valor instanceof EstadoEvaluacion) {
            switch ((EstadoEvaluacion) valor) {
                case BORRADOR: return "Borrador";
                case LISTA: return "Lista";
                default: return "Archivada";
            }
        }
        if (valor instanceof EstadoEvaluacionVirtual) {
            switch ((EstadoEvaluacionVirtual) valor) {
                case PROGRAMADA: return "Programada";
                case ABIERTA: return "Abierta";
                case CERRADA: return "Cerrada";
                default: return "Cancelada";
            }
        }
        if (valor instanceof EstadoIntento) {
            switch ((EstadoIntento) valor) {
                case EN_CURSO: return "En curso";
                case FINALIZADO: return "Finalizado";
                default: return "Calificado";
            }
        }
        if (valor instanceof TipoEventoSupervision) {
            switch ((TipoEventoSupervision) valor) {
                case CAMBIO_PESTANA: return "Cambio de pestaña";
                case PERDIDA_FOCO: return "Pérdida de foco";
                case SALIDA_PAGINA: return "Salida de página";
                case DESCONEXION: return "Desconexión";
                case RECONEXION: return "Reconexión";
                case TIEMPO_ATIPICO: return "Tiempo atípico";
                default: return "Similitud de respuestas";
            }
        }
        if (valor instanceof EstadoMaterial) {
            switch ((EstadoMaterial) valor) {
                case BORRADOR: return "Borrador";
                case PUBLICADO: return "Publicado";
                default: return "Archivado";
            }
        }
        if (valor instanceof TipoGeneracion) {
            switch ((TipoGeneracion) valor) {
                case PREGUNTAS: return "Preguntas";
                case VARIANTES: return "Variantes";
                default: return "Material de práctica";
            }
        }
        if (valor instanceof EstadoSolicitud) {
            switch ((EstadoSolicitud) valor) {
                case PENDIENTE: return "Pendiente";
                case EN_PROCESO: return "En proceso";
                case COMPLETADA: return "Completada";
                default: return "Fallida";
            }
        }
        return valor == null ? "" : valor.toString();
    }

    private static String nivel(NivelIngles valor) {
        switch (valor) {
            case A1: return "A1 (principiante)";
            case A2: return "A2 (elemental)";
            case B1: return "B1 (intermedio)";
            case B2: return "B2 (intermedio alto)";
            case C1: return "C1 (avanzado)";
            default: return "C2 (dominio)";
        }
    }

    private static String tipoPregunta(TipoPregunta valor) {
        switch (valor) {
            case SELECCION_MULTIPLE: return "Selección múltiple";
            case VERDADERO_FALSO: return "Verdadero o falso";
            case RESPUESTA_CORTA: return "Respuesta corta";
            default: return "Respuesta abierta";
        }
    }

    private static String dificultad(Dificultad valor) {
        switch (valor) {
            case BAJA: return "Baja";
            case MEDIA: return "Media";
            default: return "Alta";
        }
    }

    private static String competencia(Competencia valor) {
        switch (valor) {
            case GRAMMAR: return "Grammar";
            case VOCABULARY: return "Vocabulary";
            case READING: return "Reading";
            default: return "Writing";
        }
    }

    private static String estadoPregunta(EstadoPregunta valor) {
        switch (valor) {
            case BORRADOR: return "Borrador";
            case APROBADA: return "Aprobada";
            default: return "Archivada";
        }
    }
}
