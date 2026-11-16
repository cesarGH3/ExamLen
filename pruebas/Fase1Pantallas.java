package servlet;

import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import logica.*;
import modelo.*;

/** Ejecuta los controladores y plantillas reales con peticiones/sesiones simuladas. */
public final class Fase1Pantallas {
    private static MysqlDataSource fuente;
    private static ServletContext contexto;
    private static Docente docente;
    private static final Map<String,Object> atributosSesion = new HashMap<>();
    private static String redireccion;
    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length != 1 || !"INSTANCIA_DESECHABLE".equals(argumentos[0])) throw new IllegalArgumentException();
        fuente = new MysqlDataSource();
        fuente.setURL("jdbc:mysql://127.0.0.1:33681/examlen?allowPublicKeyRetrieval=true&sslMode=DISABLED");
        fuente.setUser("prueba_fase"); fuente.setPassword("SoloPruebasFase1");
        try (Connection conexion = fuente.getConnection(); Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery("SELECT @@datadir")) {
            if (!resultado.next() || !resultado.getString(1).contains("examlen-fase1-mysql-")) throw new IllegalStateException();
        }
        contexto = (ServletContext) Proxy.newProxyInstance(Fase1Pantallas.class.getClassLoader(), new Class<?>[]{ServletContext.class},
                (objeto, metodo, valores) -> metodo.getName().equals("getResourceAsStream") ? Files.newInputStream(Path.of("web" + valores[0])) : null);
        docente = new Docente(); docente.establecerIdUsuario(3); docente.nombreUsuario = "Docente de prueba";
        String listado = ejecutar(new DocenteEvaluacionesServlet(), false, Map.of());
        comprobar(!listado.contains("name=\"idPregunta\"") && listado.contains("name=\"titulo\""), "Crear evaluación no exige pregunta");
        ejecutar(new DocenteEvaluacionesServlet(), true, Map.of("titulo", "Evaluación desde pantalla", "descripcion", ""));
        String identificador = redireccion.substring(redireccion.indexOf("?id=") + 4);
        String evaluacion = ejecutar(new DocenteEvaluacionServlet(), false, Map.of("id", identificador));
        comprobar(evaluacion.contains("Crear pregunta en esta evaluación") && evaluacion.contains("value=\"SELECCION_MULTIPLE\""), "Formulario contextual completo");
        ejecutar(new DocenteEvaluacionServlet(), true, Map.of("id", identificador, "accion", "crearPregunta",
                "enunciado", "Elige la forma correcta", "tipo", "SELECCION_MULTIPLE", "nivel", "B1",
                "dificultad", "BAJA", "competencia", "GRAMMAR", "tema", "Pasado"));
        String pregunta = redireccion.substring(redireccion.indexOf("?id=") + 4);
        String editor = ejecutar(new DocentePreguntaServlet(), false, Map.of("id", pregunta));
        comprobar(editor.contains("Sin banco") && editor.contains("Volver a evaluación " + identificador)
                && !editor.contains("value=\"aprobar\""), "Editor manual sin banco y sin aprobación redundante");
        ejecutar(new DocentePreguntaServlet(), true, Map.of("id", pregunta, "accion", "agregarOpcion", "texto", "went", "esCorrecta", "true"));
        ejecutar(new DocentePreguntaServlet(), true, Map.of("id", pregunta, "accion", "agregarOpcion", "texto", "go"));
        comprobar(new PreguntaDAO(fuente).buscarPorId(Integer.parseInt(pregunta)).orElseThrow().estadoPregunta == EstadoPregunta.APROBADA,
                "POST opciones produce aprobaci�n autom�tica");
        ejecutar(new DocenteEvaluacionServlet(), true, Map.of("id", identificador, "accion", "quitarPregunta",
                "idPregunta", pregunta));
        comprobar(new PreguntaDAO(fuente).listarPorEvaluacion(Integer.parseInt(identificador)).isEmpty(),
                "La evaluación puede quedar vacía otra vez");
        docente.establecerIdUsuario(2);
        try {
            ejecutar(new DocentePreguntaServlet(), false, Map.of("id", pregunta));
            throw new AssertionError("Acceso ajeno permitido");
        } catch (OperacionNoPermitidaException esperada) { comprobar(true, "Controlador rechaza URL ajena"); }
    }
    private static String ejecutar(ServletBase controlador, boolean escritura, Map<String,String> parametros) throws Exception {
        controlador.vistas = new Vistas(contexto);
        controlador.preguntaDAO = new PreguntaDAO(fuente); controlador.opcionDAO = new OpcionRespuestaDAO(fuente);
        controlador.evaluacionDAO = new EvaluacionDAO(fuente); controlador.bancoDAO = new BancoPreguntasDAO(fuente);
        controlador.versionDAO = new VersionEvaluacionDAO(fuente); controlador.versionPreguntaDAO = new VersionPreguntaDAO(fuente);
        controlador.versionOpcionDAO = new VersionOpcionDAO(fuente); controlador.cursoDAO = new CursoDAO(fuente);
        controlador.materialDAO = new MaterialPracticaDAO(fuente); controlador.virtualDAO = new EvaluacionVirtualDAO(fuente);
        controlador.intentoDAO = new IntentoDAO(fuente);
        HttpSession sesion = (HttpSession) Proxy.newProxyInstance(Fase1Pantallas.class.getClassLoader(), new Class<?>[]{HttpSession.class},
                (objeto, metodo, valores) -> {
                    switch (metodo.getName()) {
                        case "getAttribute": return atributosSesion.get(valores[0]);
                        case "setAttribute": atributosSesion.put((String) valores[0], valores[1]); return null;
                        case "removeAttribute": atributosSesion.remove(valores[0]); return null;
                        default: return null;
                    }
                });
        HttpServletRequest peticion = (HttpServletRequest) Proxy.newProxyInstance(Fase1Pantallas.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (objeto, metodo, valores) -> {
                    switch (metodo.getName()) {
                        case "getParameter": return parametros.get(valores[0]);
                        case "getAttribute": return AutenticacionFiltro.ATRIBUTO_USUARIO.equals(valores[0]) ? docente : null;
                        case "getSession": return sesion;
                        case "getContextPath": return "/ExamLen";
                        default: return null;
                    }
                });
        StringWriter salida = new StringWriter();
        HttpServletResponse respuesta = (HttpServletResponse) Proxy.newProxyInstance(Fase1Pantallas.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (objeto, metodo, valores) -> {
                    if (metodo.getName().equals("getWriter")) return new PrintWriter(salida);
                    if (metodo.getName().equals("sendRedirect")) redireccion = (String) valores[0];
                    if (metodo.getName().equals("encodeRedirectURL")) return valores[0];
                    return null;
                });
        Method metodo = controlador.getClass().getDeclaredMethod(escritura ? "doPost" : "doGet", HttpServletRequest.class, HttpServletResponse.class);
        metodo.setAccessible(true);
        try { metodo.invoke(controlador, peticion, respuesta); }
        catch (InvocationTargetException excepcion) { throw (Exception) excepcion.getCause(); }
        return salida.toString();
    }
    private static void comprobar(boolean cumple, String caso) {
        if (!cumple) throw new AssertionError(caso);
        System.out.println("OK pantalla: " + caso);
    }
}
