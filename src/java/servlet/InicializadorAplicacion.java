package servlet;

import conexion.Conexion;
import modelo.AdministradorDAO;
import modelo.BancoPreguntasDAO;
import modelo.ClaveIADocenteDAO;
import modelo.CursoDAO;
import modelo.DocenteDAO;
import modelo.DocumentoFuenteDAO;
import modelo.EstudianteDAO;
import modelo.EvaluacionDAO;
import modelo.EvaluacionVirtualDAO;
import modelo.EventoSupervisionDAO;
import modelo.GrupoDAO;
import modelo.InscripcionDAO;
import modelo.IntentoDAO;
import modelo.IntentoPracticaDAO;
import modelo.MaterialPracticaDAO;
import modelo.OpcionRespuestaDAO;
import modelo.PreguntaDAO;
import modelo.RespuestaDAO;
import modelo.RespuestaPracticaDAO;
import modelo.SolicitudGeneracionDAO;
import modelo.TokenRecuperacionDAO;
import modelo.UsuarioDAO;
import modelo.VerificadorBaseDatos;
import modelo.VersionEvaluacionDAO;
import modelo.VersionOpcionDAO;
import modelo.VersionPreguntaDAO;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Punto único donde se construyen los DAO (inyección manual de dependencias). */
@WebListener
public class InicializadorAplicacion implements ServletContextListener {

    public static final String VISTAS = "examlen.vistas";
    public static final String VERIFICADOR_BD = "examlen.verificadorBD";
    public static final String USUARIO_DAO = "examlen.usuarioDAO";
    public static final String ADMINISTRADOR_DAO = "examlen.administradorDAO";
    public static final String DOCENTE_DAO = "examlen.docenteDAO";
    public static final String ESTUDIANTE_DAO = "examlen.estudianteDAO";
    public static final String CURSO_DAO = "examlen.cursoDAO";
    public static final String GRUPO_DAO = "examlen.grupoDAO";
    public static final String INSCRIPCION_DAO = "examlen.inscripcionDAO";
    public static final String BANCO_DAO = "examlen.bancoPreguntasDAO";
    public static final String PREGUNTA_DAO = "examlen.preguntaDAO";
    public static final String OPCION_DAO = "examlen.opcionRespuestaDAO";
    public static final String EVALUACION_DAO = "examlen.evaluacionDAO";
    public static final String VERSION_DAO = "examlen.versionEvaluacionDAO";
    public static final String VERSION_PREGUNTA_DAO = "examlen.versionPreguntaDAO";
    public static final String VERSION_OPCION_DAO = "examlen.versionOpcionDAO";
    public static final String VIRTUAL_DAO = "examlen.evaluacionVirtualDAO";
    public static final String INTENTO_DAO = "examlen.intentoDAO";
    public static final String RESPUESTA_DAO = "examlen.respuestaDAO";
    public static final String EVENTO_DAO = "examlen.eventoSupervisionDAO";
    public static final String MATERIAL_DAO = "examlen.materialPracticaDAO";
    public static final String INTENTO_PRACTICA_DAO = "examlen.intentoPracticaDAO";
    public static final String RESPUESTA_PRACTICA_DAO = "examlen.respuestaPracticaDAO";
    public static final String DOCUMENTO_DAO = "examlen.documentoFuenteDAO";
    public static final String SOLICITUD_DAO = "examlen.solicitudGeneracionDAO";
    public static final String TOKEN_API_DAO = "examlen.tokenApiDAO";
    public static final String TOKEN_DAO = "examlen.tokenRecuperacionDAO";
    public static final String CLAVE_IA_DAO = "examlen.claveIADocenteDAO";

    private static final Logger LOG = Logger.getLogger(InicializadorAplicacion.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent evento) {
        ServletContext contexto = evento.getServletContext();
        servicio.Secretos.validarConfiguracion();
        servicio.CifradoClaves.validarConfiguracion();
        modelo.InicializadorEsquema.inicializar(contexto);
        DataSource dataSource = Conexion.obtenerFuente();

        VerificadorBaseDatos verificador = new VerificadorBaseDatos(dataSource);
        if (!verificador.estaDisponible()) {
            LOG.severe("MySQL no está disponible al iniciar. La aplicación responderá 503 hasta que se restablezca.");
        }

        contexto.setAttribute(VERIFICADOR_BD, verificador);
        contexto.setAttribute(VISTAS, new Vistas(contexto));
        contexto.setAttribute(TOKEN_API_DAO, new modelo.TokenApiDAO(dataSource));
        contexto.setAttribute(USUARIO_DAO, new UsuarioDAO(dataSource));
        contexto.setAttribute(ADMINISTRADOR_DAO, new AdministradorDAO(dataSource));
        contexto.setAttribute(DOCENTE_DAO, new DocenteDAO(dataSource));
        contexto.setAttribute(ESTUDIANTE_DAO, new EstudianteDAO(dataSource));
        contexto.setAttribute(CURSO_DAO, new CursoDAO(dataSource));
        contexto.setAttribute(GRUPO_DAO, new GrupoDAO(dataSource));
        contexto.setAttribute(INSCRIPCION_DAO, new InscripcionDAO(dataSource));
        contexto.setAttribute(BANCO_DAO, new BancoPreguntasDAO(dataSource));
        contexto.setAttribute(PREGUNTA_DAO, new PreguntaDAO(dataSource));
        contexto.setAttribute(OPCION_DAO, new OpcionRespuestaDAO(dataSource));
        contexto.setAttribute(EVALUACION_DAO, new EvaluacionDAO(dataSource));
        contexto.setAttribute(VERSION_DAO, new VersionEvaluacionDAO(dataSource));
        contexto.setAttribute(VERSION_PREGUNTA_DAO, new VersionPreguntaDAO(dataSource));
        contexto.setAttribute(VERSION_OPCION_DAO, new VersionOpcionDAO(dataSource));
        contexto.setAttribute(VIRTUAL_DAO, new EvaluacionVirtualDAO(dataSource));
        contexto.setAttribute(INTENTO_DAO, new IntentoDAO(dataSource));
        contexto.setAttribute(RESPUESTA_DAO, new RespuestaDAO(dataSource));
        contexto.setAttribute(EVENTO_DAO, new EventoSupervisionDAO(dataSource));
        contexto.setAttribute(MATERIAL_DAO, new MaterialPracticaDAO(dataSource));
        contexto.setAttribute(INTENTO_PRACTICA_DAO, new IntentoPracticaDAO(dataSource));
        contexto.setAttribute(RESPUESTA_PRACTICA_DAO, new RespuestaPracticaDAO(dataSource));
        contexto.setAttribute(DOCUMENTO_DAO, new DocumentoFuenteDAO(dataSource));
        contexto.setAttribute(SOLICITUD_DAO, new SolicitudGeneracionDAO(dataSource));
        contexto.setAttribute(TOKEN_DAO, new TokenRecuperacionDAO(dataSource));
        contexto.setAttribute(CLAVE_IA_DAO, new ClaveIADocenteDAO(dataSource));
        LOG.info("ExamLen inicializado.");
    }
}
