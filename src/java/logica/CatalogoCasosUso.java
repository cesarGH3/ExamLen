package logica;

import java.util.List;

/**
 * Catalogo unico de los 34 casos de uso del ERS (CU-01..CU-34). La informacion de interfaz,
 * accion, endpoint y metodos de inicio/fin sale del codigo real del proyecto; es la unica
 * fuente para la herramienta temporal de rendimiento (no duplicar en otros archivos).
 */
public final class CatalogoCasosUso {

    public static final class Entrada {

        public final String cuId;
        public final String nombre;
        public final String rol;
        public final String interfazInicio;
        public final String accionInicio;
        public final String endpointInicio;
        public final String metodoInicio;
        public final String metodoFin;
        public final String tablaResultadoFinal;
        public final String tablasSecundarias;
        public final String retorna;
        public final String tipoRetorno;
        public final String estadoFuncional;

        Entrada(String cuId, String nombre, String rol, String interfazInicio, String accionInicio,
                String endpointInicio, String metodoInicio, String metodoFin, String tablaResultadoFinal,
                String tablasSecundarias, String retorna, String tipoRetorno, String estadoFuncional) {
            this.cuId = cuId;
            this.nombre = nombre;
            this.rol = rol;
            this.interfazInicio = interfazInicio;
            this.accionInicio = accionInicio;
            this.endpointInicio = endpointInicio;
            this.metodoInicio = metodoInicio;
            this.metodoFin = metodoFin;
            this.tablaResultadoFinal = tablaResultadoFinal;
            this.tablasSecundarias = tablasSecundarias;
            this.retorna = retorna;
            this.tipoRetorno = tipoRetorno;
            this.estadoFuncional = estadoFuncional;
        }
    }

    private static final List<Entrada> ENTRADAS = List.of(
        e("CU-01", "Iniciar sesion", "Publico", "login.html", "Ingresar", "/login",
          "LoginServlet.doPost()", "AutenticacionFiltro + UsuarioDAO (sesion y bloqueo) + COMMIT",
          "usuario", "usuario (intentos_fallidos/bloqueado_hasta)", "SI", "SI_REDIRECT", "PARCIAL"),
        e("CU-02", "Cambiar contrasena", "Todos", "cambiar-contrasena.html", "Cambiar contrasena",
          "/app/perfil/contrasena", "CambiarContrasenaServlet.doPost()",
          "UsuarioDAO.cambiarContrasenaUsuario() + COMMIT", "usuario", "", "SI", "SI_REDIRECT", "PARCIAL"),
        e("CU-03", "Actualizar datos personales", "Todos", "perfil.html", "Guardar cambios", "/app/perfil",
          "PerfilServlet.doPost()", "UsuarioDAO.actualizarUsuario() + COMMIT", "usuario", "",
          "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-04", "Registrarse como estudiante", "Publico", "registrarse.html", "Crear cuenta", "/registro",
          "RegistroServlet.doPost()", "EstudianteDAO.crearEstudiante() + COMMIT", "usuario", "estudiante",
          "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-05", "Gestionar administradores", "Administrador", "admin-administradores.html",
          "Crear / Actualizar / Eliminar", "/app/admin/administradores", "AdminAdministradoresServlet.doPost()",
          "AdministradorDAO.crearAdministrador()/actualizarAdministrador()/eliminarAdministrador() + COMMIT",
          "administrador", "usuario", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-06", "Registrar docente", "Administrador", "admin-docentes.html", "Registrar docente",
          "/app/admin/docentes", "AdminDocentesServlet.doPost()", "DocenteDAO.registrarDocente() + COMMIT",
          "usuario", "docente", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-07", "Gestionar estudiantes", "Administrador", "admin-estudiantes.html",
          "Buscar / Actualizar / Eliminar", "/app/admin/estudiantes", "AdminEstudiantesServlet.doPost()",
          "EstudianteDAO.actualizarEstudiante()/eliminarEstudiante() + COMMIT",
          "usuario", "estudiante", "SI", "SI_HTML", "COMPLETO"),
        e("CU-08", "Activar o desactivar cuenta", "Administrador", "admin-docentes.html",
          "Activar / Desactivar", "/app/admin/docentes/estado", "AdminEstadoDocenteServlet.doPost()",
          "UsuarioDAO.activarUsuario()/desactivarUsuario() + COMMIT", "usuario", "docente/estudiante",
          "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-09", "Restablecer contrasena de usuario", "Administrador", "admin-docentes.html", "Restablecer",
          "/app/admin/docentes", "AdminDocentesServlet.doPost() (accion=restablecer)",
          "UsuarioDAO.restablecerContrasenaUsuario() + COMMIT", "usuario",
          "docente/estudiante/administrador", "SI", "SI_REDIRECT", "PARCIAL"),
        e("CU-10", "Recuperar contrasena olvidada", "Publico", "recuperar.html", "Solicitar enlace / Restablecer",
          "/recuperar", "RecuperarContrasenaServlet.doPost() + RestablecerContrasenaServlet.doPost()",
          "TokenRecuperacionDAO.consumirTokenYRestablecer() + UsuarioDAO + COMMIT",
          "token_recuperacion", "usuario", "SI", "SI_REDIRECT", "PARCIAL"),
        e("CU-11", "Gestionar cursos", "Docente", "docente-cursos.html", "Crear curso", "/app/docente/cursos",
          "DocenteCursosServlet.doPost()", "CursoDAO.crearCurso()/actualizarCurso() + COMMIT",
          "curso", "", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-12", "Gestionar grupos", "Docente", "docente-curso.html", "Abrir / Cerrar / Reabrir",
          "/app/docente/curso", "DocenteCursoServlet.doPost()",
          "GrupoDAO.abrirGrupo()/cerrarGrupo()/reabrirGrupo() + COMMIT", "grupo", "curso",
          "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-13", "Gestionar inscripciones", "Docente", "docente-grupo.html", "Retirar / Reincorporar",
          "/app/docente/grupo", "DocenteGrupoServlet.doPost()",
          "InscripcionDAO.retirarInscripcion()/reincorporarInscripcion()/eliminarInscripcion() + COMMIT",
          "inscripcion", "grupo/estudiante", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-14", "Inscribirse en un grupo", "Estudiante", "estudiante-grupos.html", "Inscribirse",
          "/app/estudiante/grupos", "EstudianteGruposServlet.doPost()",
          "InscripcionDAO.crearInscripcion() + COMMIT", "inscripcion", "grupo", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-15", "Gestionar bancos de preguntas", "Docente", "docente-bancos.html", "Crear banco",
          "/app/docente/bancos", "DocenteBancosServlet.doPost()",
          "BancoPreguntasDAO.crearBancoPreguntas()/actualizarBancoPreguntas() + COMMIT",
          "banco_preguntas", "", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-16", "Gestionar preguntas", "Docente", "docente-pregunta.html", "Guardar",
          "/app/docente/pregunta", "DocentePreguntaServlet.doPost()",
          "PreguntaDAO + OpcionRespuestaDAO + COMMIT", "pregunta", "opcion_respuesta", "SI", "SI_REDIRECT",
          "COMPLETO"),
        e("CU-17", "Revisar y aprobar preguntas", "Docente", "docente-pregunta.html",
          "Aprobar / Archivar / Restaurar", "/app/docente/pregunta", "DocentePreguntaServlet.doPost()",
          "PreguntaDAO.aprobarPregunta()/archivarPregunta()/restaurarPregunta() + COMMIT", "pregunta", "",
          "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-18", "Cargar documento fuente", "Docente", "docente-ia.html", "Subir documento",
          "/app/docente/ia", "DocenteIAServlet.doPost() (accion=subirDocumento)",
          "DocumentoFuenteDAO.cargarDocumentoFuente() + COMMIT", "documento_fuente", "", "SI", "SI_REDIRECT",
          "COMPLETO"),
        e("CU-19", "Generar contenido con IA", "Docente", "docente-ia.html", "Generar", "/app/docente/ia",
          "DocenteIAServlet.doPost() (accion=generar)",
          "SolicitudGeneracionDAO.crearSolicitudGeneracion() + ServicioGeneracionIA.encolar() (ACK); fin asincrono: estado COMPLETADA/FALLIDA (ProcesadorGeneracionIA)",
          "solicitud_generacion", "pregunta/material_practica/banco_preguntas", "SI", "ACK_ASINCRONO", "PARCIAL"),
        e("CU-20", "Gestionar evaluaciones", "Docente", "docente-evaluaciones.html", "Crear evaluacion",
          "/app/docente/evaluaciones", "DocenteEvaluacionesServlet.doPost()",
          "EvaluacionDAO.crearEvaluacionConPregunta()/marcarListaEvaluacion()/archivarEvaluacion() + COMMIT",
          "evaluacion", "evaluacion_pregunta", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-21", "Generar versiones de evaluacion", "Docente", "docente-evaluacion.html",
          "Generar versiones", "/app/docente/evaluacion", "DocenteEvaluacionServlet.doPost()",
          "VersionEvaluacionDAO.generarVersionEvaluacion() + COMMIT", "version_evaluacion",
          "version_pregunta/version_opcion", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-22", "Editar version de evaluacion", "Docente", "docente-version-pregunta.html",
          "Guardar cambios", "/app/docente/version-pregunta", "DocenteVersionPreguntaServlet.doPost()",
          "VersionPreguntaDAO.actualizar() + VersionOpcionDAO + COMMIT", "version_pregunta", "version_opcion",
          "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-23", "Descargar evaluacion en PDF", "Docente", "docente-evaluacion.html", "Descargar PDF",
          "/app/docente/version/pdf", "DocenteVersionPdfServlet.doGet()",
          "VersionEvaluacionDAO.generarPDFVersionEvaluacion() (hasta el ultimo byte)",
          "Sin escritura (consulta: version_evaluacion)", "version_pregunta", "SI", "SI_ARCHIVO", "COMPLETO"),
        e("CU-24", "Descargar clave de respuestas", "Docente", "docente-evaluacion.html", "Descargar clave",
          "/app/docente/version/pdf", "DocenteVersionPdfServlet.doGet() (clave)",
          "VersionEvaluacionDAO.generarClaveRespuestasPDFVersionEvaluacion() (hasta el ultimo byte)",
          "Sin escritura (consulta: version_opcion)", "version_pregunta", "SI", "SI_ARCHIVO", "COMPLETO"),
        e("CU-25", "Programar evaluacion virtual", "Docente", "docente-virtual.html", "Programar",
          "/app/docente/virtual", "DocenteVirtualServlet.doPost()",
          "EvaluacionVirtualDAO.programarEvaluacionVirtual()/agregarVersion() + COMMIT", "evaluacion_virtual",
          "evaluacion_virtual_version", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-26", "Controlar evaluacion virtual", "Docente", "docente-virtual.html",
          "Abrir / Cerrar / Cancelar", "/app/docente/virtual", "DocenteVirtualServlet.doPost()",
          "ServicioEvaluacionVirtual.abrir()/cerrar() + EvaluacionVirtualDAO + COMMIT", "evaluacion_virtual",
          "intento", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-27", "Presentar evaluacion virtual", "Estudiante", "estudiante-evaluaciones.html",
          "Iniciar / Responder / Entregar", "/app/estudiante/intento",
          "EstudianteInicioIntentoServlet.doPost() + EstudianteIntentoServlet.doPost()",
          "IntentoDAO.crearIntento()/finalizarIntento() + RespuestaDAO.guardarRespuestaVigente() + COMMIT",
          "intento", "respuesta", "SI", "SI_REDIRECT", "PARCIAL"),
        e("CU-28", "Registrar eventos de supervision", "Estudiante", "estudiante-intento.html",
          "Registrar evento", "/app/estudiante/intento/evento", "EstudianteEventoServlet.doPost()",
          "EventoSupervisionDAO.registrarEventoSupervision() + COMMIT", "evento_supervision", "intento",
          "NO", "NO_CONTENIDO_204", "COMPLETO"),
        e("CU-29", "Calificar intentos", "Docente", "docente-intento.html", "Calificar",
          "/app/docente/intento", "DocenteIntentoServlet.doPost()",
          "RespuestaDAO.calificarManualmenteRespuesta() + IntentoDAO.calificarIntento()/actualizarCalificacion() + COMMIT",
          "respuesta", "intento", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-30", "Revisar indicadores de supervision", "Docente", "docente-intento.html",
          "Ver indicadores", "/app/docente/intento", "DocenteIntentoServlet.doGet()",
          "EventoSupervisionDAO.listarPorIntento()/contarPorTipo() (solo lectura)",
          "Sin escritura (consulta: evento_supervision)", "intento", "SI", "SI_HTML", "COMPLETO"),
        e("CU-31", "Consultar resultados de evaluacion", "Estudiante", "estudiante-resultado.html",
          "Ver resultado", "/app/estudiante/intento", "EstudianteIntentoServlet.doGet()",
          "IntentoDAO.puntosDeIntento() + RespuestaDAO.listarConPregunta() (solo lectura)",
          "Sin escritura (consulta: intento)", "respuesta", "SI", "SI_HTML", "COMPLETO"),
        e("CU-32", "Gestionar material de practica", "Docente", "docente-practica.html",
          "Publicar / Archivar", "/app/docente/practica", "DocentePracticaServlet.doPost()",
          "MaterialPracticaDAO.crearMaterialPractica()/publicarMaterialPractica()/archivarMaterialPractica() + COMMIT",
          "material_practica", "material_pregunta/curso_material", "SI", "SI_REDIRECT", "COMPLETO"),
        e("CU-33", "Descargar material de practica en PDF", "Docente/Estudiante", "estudiante-practica.html",
          "Descargar PDF", "/app/estudiante/practica/pdf",
          "EstudiantePracticaPdfServlet.doGet() (y DocentePracticaPdfServlet para el docente)",
          "MaterialPracticaDAO.generarPDFMaterialPractica()/generarPDFMaterialPracticaConRespuestas() (hasta el ultimo byte)",
          "Sin escritura (consulta: material_pregunta)", "material_practica", "SI", "SI_ARCHIVO", "COMPLETO"),
        e("CU-34", "Resolver material de practica", "Estudiante", "estudiante-practica.html",
          "Responder / Terminar", "/app/estudiante/practica", "EstudiantePracticaServlet.doPost()",
          "IntentoPracticaDAO.guardarRespuesta()/finalizarIntento() + COMMIT", "intento_practica",
          "respuesta_practica", "SI", "SI_REDIRECT", "COMPLETO")
    );

    private CatalogoCasosUso() {
    }

    private static Entrada e(String cuId, String nombre, String rol, String interfazInicio, String accionInicio,
                             String endpointInicio, String metodoInicio, String metodoFin,
                             String tablaResultadoFinal, String tablasSecundarias, String retorna,
                             String tipoRetorno, String estadoFuncional) {
        return new Entrada(cuId, nombre, rol, interfazInicio, accionInicio, endpointInicio, metodoInicio, metodoFin,
                tablaResultadoFinal, tablasSecundarias, retorna, tipoRetorno, estadoFuncional);
    }

    public static List<Entrada> todas() {
        return ENTRADAS;
    }

    public static Entrada porId(String cuId) {
        for (Entrada entrada : ENTRADAS) {
            if (entrada.cuId.equals(cuId)) {
                return entrada;
            }
        }
        return null;
    }
}
