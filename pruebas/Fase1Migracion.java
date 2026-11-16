import jakarta.servlet.ServletContext;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import modelo.InicializadorEsquema;

/** Actualización 017->020, baseline y fallos; exclusivamente servidor temporal validado. */
public final class Fase1Migracion {
    private static final String SERVIDOR = "jdbc:mysql://127.0.0.1:33681/";
    private static final String OPCIONES = "?allowMultiQueries=true&allowPublicKeyRetrieval=true&sslMode=DISABLED";
    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length != 1 || !"INSTANCIA_DESECHABLE".equals(argumentos[0])) throw new IllegalArgumentException();
        for (String variable : List.of("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD")) {
            if (System.getenv(variable) != null) throw new IllegalStateException("Entorno de BD no permitido");
        }
        ServletContext contexto = (ServletContext) Proxy.newProxyInstance(Fase1Migracion.class.getClassLoader(),
                new Class<?>[]{ServletContext.class}, (objeto, metodo, valores) ->
                    metodo.getName().equals("getResourceAsStream") ? Files.newInputStream(Path.of("web" + valores[0])) : null);
        List<String> indice = Files.readAllLines(Path.of("web/WEB-INF/sql/migraciones/indice.txt"));
        for (String escenario : List.of("registrada", "sin_registro", "propietario_invalido", "parcial")) {
            String base = "fase1_" + escenario;
            try (Connection servidor = DriverManager.getConnection(SERVIDOR + OPCIONES, "root", "");
                 Statement sentencia = servidor.createStatement()) {
                try (ResultSet resultado = sentencia.executeQuery("SELECT @@datadir")) {
                    if (!resultado.next() || !resultado.getString(1).contains("examlen-fase1-mysql-")) throw new IllegalStateException("Servidor no desechable");
                }
                sentencia.execute("DROP DATABASE IF EXISTS " + base);
                sentencia.execute("CREATE DATABASE " + base);
            }
            try (Connection conexion = DriverManager.getConnection(SERVIDOR + base + OPCIONES, "root", "")) {
                for (String archivo : indice) {
                    if (archivo.isBlank() || archivo.startsWith("#") || archivo.startsWith("018") || archivo.startsWith("019") || archivo.startsWith("020")) continue;
                    String consulta = Files.readString(Path.of("web/WEB-INF/sql/migraciones", archivo))
                            .replaceAll("\\bexamlen\\b", base);
                    ejecutar(conexion, consulta);
                }
                ejecutar(conexion, "INSERT INTO usuario (tipo,identificacion,nombre,correo,contrasena_hash) VALUES "
                        + "('ADMINISTRADOR','M1','Admin','admin@prueba.local','prueba'),('DOCENTE','M2','Docente','doc@prueba.local','prueba')");
                ejecutar(conexion, "INSERT INTO administrador (id_usuario) VALUES (1)");
                ejecutar(conexion, "INSERT INTO docente (id_usuario,id_administrador) VALUES (2,1)");
                ejecutar(conexion, "INSERT INTO banco_preguntas (id_docente,nombre) VALUES (2,'Histórico')");
                ejecutar(conexion, "INSERT INTO pregunta (id_banco_preguntas,enunciado,tipo,nivel,dificultad,competencia,tema,estado,origen,fecha_actualizacion) "
                        + "VALUES (1,'Pregunta conservada','RESPUESTA_ABIERTA','B1','BAJA','WRITING','Pasado','APROBADA','MANUAL','2025-01-01 12:00:00')");
                if (escenario.equals("registrada")) {
                    ejecutar(conexion, "CREATE TABLE esquema_migracion (nombre VARCHAR(100) PRIMARY KEY, fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    try (PreparedStatement sentencia = conexion.prepareStatement("INSERT INTO esquema_migracion(nombre) VALUES (?)")) {
                        for (String archivo : indice) if (!archivo.isBlank() && !archivo.startsWith("#") && !archivo.startsWith("018") && !archivo.startsWith("019") && !archivo.startsWith("020")) {
                            sentencia.setString(1, archivo); sentencia.executeUpdate();
                        }
                    }
                }
                if (escenario.equals("propietario_invalido")) ejecutar(conexion, "UPDATE banco_preguntas SET id_docente=1");
                if (escenario.equals("parcial")) ejecutar(conexion, "ALTER TABLE pregunta ADD COLUMN id_docente INT UNSIGNED NULL");
                System.setProperty("examlen.db.url", SERVIDOR + base);
                System.setProperty("examlen.db.user", "prueba_fase");
                System.setProperty("examlen.db.password", "SoloPruebasFase1");
                InicializadorEsquema.inicializar(contexto);
                boolean valida = escenario.equals("registrada") || escenario.equals("sin_registro");
                if (InicializadorEsquema.consultarDisponibilidadEsquema() != valida) throw new AssertionError(escenario);
                if (valida) {
                    try (Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(
                            "SELECT id_docente,enunciado,estado,fecha_actualizacion,(SELECT COUNT(*) FROM esquema_migracion) FROM pregunta")) {
                        if (!resultado.next() || resultado.getInt(1) != 2 || !"Pregunta conservada".equals(resultado.getString(2))
                                || !"APROBADA".equals(resultado.getString(3)) || !resultado.getString(4).startsWith("2025-01-01 12:00:00")
                                || resultado.getInt(5) != 20) throw new AssertionError("Datos alterados: " + escenario);
                    }
                    InicializadorEsquema.inicializar(contexto);
                    if (!InicializadorEsquema.consultarDisponibilidadEsquema()) throw new AssertionError("Reinicio " + escenario);
                } else {
                    try (Statement sentencia = conexion.createStatement(); ResultSet resultado = sentencia.executeQuery(
                            "SELECT COUNT(*) FROM esquema_migracion WHERE nombre LIKE '018%' OR nombre LIKE '019%' OR nombre LIKE '020%'")) {
                        resultado.next(); if (resultado.getInt(1) != 0) throw new AssertionError("Migración fallida marcada aplicada");
                    }
                }
                System.out.println("OK migración: " + escenario);
            }
        }
    }
    private static void ejecutar(Connection conexion, String consulta) throws Exception {
        try (Statement sentencia = conexion.createStatement()) {
            boolean resultado = sentencia.execute(consulta);
            while (true) {
                if (resultado) { try (ResultSet filas = sentencia.getResultSet()) { while (filas.next()) { } } }
                else if (sentencia.getUpdateCount() == -1) break;
                resultado = sentencia.getMoreResults();
            }
        }
    }
}
