package api;

import com.mysql.cj.jdbc.MysqlDataSource;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import logica.Administrador;
import logica.Docente;
import logica.Estudiante;
import logica.Intento;
import logica.VersionPregunta;
import modelo.*;
import servlet.CabecerasSeguridadFiltro;

/** Pruebas con proxies Servlet y una BD nueva y desechable, nunca sobre datos del usuario. */
public final class SeguridadRegression {
    private static int verificaciones;
    private static Connection c;
    private static final Path MIGRACIONES = Path.of("web/WEB-INF/sql/migraciones");

    public static void main(String[] args) throws Exception {
        System.setProperty("examlen.env", "desarrollo");
        System.setProperty("examlen.api.token.secret", "s".repeat(64));
        pruebasHttp();
        pruebasFirma();
        String servidor = System.getenv("EXAMLEN_TEST_SERVER");
        if (servidor == null) {
            System.out.println("OK: " + verificaciones + " verificaciones sin BD. BD omitida: definir EXAMLEN_TEST_SERVER.");
            return;
        }
        if (!servidor.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/")) {
            throw new IllegalArgumentException("Usa exclusivamente un servidor MySQL de pruebas en localhost.");
        }
        String base = "examlen_test_" + UUID.randomUUID().toString().replace("-", "");
        String usuario = System.getenv().getOrDefault("EXAMLEN_TEST_USER", "root");
        String clave = System.getenv().getOrDefault("EXAMLEN_TEST_PASSWORD", "");
        String usuarioMigracion = base.substring(0, 30);
        String claveMigracion = UUID.randomUUID().toString();
        String url = servidor + base + "?allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false";
        try (Connection servidorC = DriverManager.getConnection(servidor, usuario, clave)) {
            try {
                servidorC.createStatement().execute("CREATE DATABASE " + base + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                servidorC.createStatement().execute("CREATE USER '" + usuarioMigracion + "'@'localhost' IDENTIFIED BY '" + claveMigracion + "'");
                servidorC.createStatement().execute("GRANT SELECT, INSERT, UPDATE, DELETE, TRIGGER ON " + base + ".* TO '" + usuarioMigracion + "'@'localhost'");
                try (Connection conexion = DriverManager.getConnection(url, usuario, clave)) {
                    c = conexion;
                    System.out.println("Motor de prueba: " + c.getMetaData().getDatabaseProductVersion());
                    crearBaseline();
                    fixtures();
                    preflight();
                    sql(Files.readString(MIGRACIONES.resolve("022_integridad_respuestas_tokens.sql")));
                    sql("INSERT INTO esquema_migracion (nombre) VALUES ('022_integridad_respuestas_tokens.sql')");
                    preflightRoles();
                    sql(Files.readString(MIGRACIONES.resolve("023_roles_sin_columna_tipo.sql")));
                    sql("INSERT INTO esquema_migracion (nombre) VALUES ('023_roles_sin_columna_tipo.sql')");
                    preflightIntegridad();
                    sql(Files.readString(MIGRACIONES.resolve("024_restricciones_integridad.sql")));
                    sql("INSERT INTO esquema_migracion (nombre) VALUES ('024_restricciones_integridad.sql')");
                    pruebasIntegridad024();
                    System.setProperty("examlen.db.url", servidor + base);
                    System.setProperty("examlen.db.user", usuarioMigracion);
                    System.setProperty("examlen.db.password", claveMigracion);
                    System.setProperty("examlen.db.sslMode", "REQUIRED");
                    System.setProperty("examlen.db.devMode", "true");
                    ServletContext contexto = proxy(ServletContext.class, (m, a) -> {
                        if (m.equals("getResourceAsStream")) return Files.newInputStream(Path.of("web" + a[0]));
                        return null;
                    });
                    InicializadorEsquema.inicializar(contexto);
                    comprobar(InicializadorEsquema.consultarDisponibilidadEsquema(), "Arranque verifica 022, 023 y 024 aplicadas por DBA");
                    InicializadorEsquema.inicializar(contexto);
                    comprobar(InicializadorEsquema.consultarDisponibilidadEsquema(), "Reinicio sin reejecutar 022, 023 ni 024");
                    sql("ALTER TABLE evaluacion_virtual DROP CHECK ck_evaluacion_virtual_fechas");
                    InicializadorEsquema.inicializar(contexto);
                    comprobar(!InicializadorEsquema.consultarDisponibilidadEsquema(), "Falta un CHECK de 024: esquema no disponible");
                    sql("ALTER TABLE evaluacion_virtual ADD CONSTRAINT ck_evaluacion_virtual_fechas CHECK (fecha_fin > fecha_inicio)");
                    sql("DROP TRIGGER trg_intento_bi");
                    InicializadorEsquema.inicializar(contexto);
                    comprobar(!InicializadorEsquema.consultarDisponibilidadEsquema(), "Falta un trigger de 024: esquema no disponible");
                    String script024 = Files.readString(MIGRACIONES.resolve("024_restricciones_integridad.sql"));
                    int inicio = script024.indexOf("CREATE TRIGGER trg_intento_bi");
                    sql(script024.substring(inicio, script024.indexOf(";", inicio) + 1));
                    InicializadorEsquema.inicializar(contexto);
                    comprobar(InicializadorEsquema.consultarDisponibilidadEsquema(), "Esquema reparado vuelve a estar disponible");
                    MysqlDataSource fuente = new MysqlDataSource();
                    fuente.setURL(url); fuente.setUser(usuarioMigracion); fuente.setPassword(claveMigracion);
                    pruebasRoles(fuente);
                    pruebasDatos(fuente);
                    pruebasTokens(fuente);
                    pruebasErrores(fuente);
                }
            } finally {
                servidorC.createStatement().execute("DROP DATABASE IF EXISTS " + base);
                servidorC.createStatement().execute("DROP USER IF EXISTS '" + usuarioMigracion + "'@'localhost'");
            }
        }
        System.out.println("OK: " + verificaciones + " verificaciones; BD de pruebas eliminada.");
    }

    private static void crearBaseline() throws Exception {
        sql("CREATE TABLE esquema_migracion (nombre VARCHAR(150) PRIMARY KEY, aplicada_en DATETIME DEFAULT CURRENT_TIMESTAMP)");
        for (String nombre : Files.readAllLines(MIGRACIONES.resolve("indice.txt"))) {
            if (nombre.startsWith("022")) break;
            if (nombre.isBlank() || nombre.startsWith("#")) continue;
            String script = Files.readString(MIGRACIONES.resolve(nombre)).replaceAll("(?m)--.*$", "")
                    .replaceAll("(?is)CREATE DATABASE.*?;", "")
                    .replaceAll("(?is)CREATE USER.*?;", "")
                    .replaceAll("(?is)GRANT .*?;", "")
                    .replaceAll("(?is)FLUSH PRIVILEGES;", "")
                    .replaceAll("(?i)USE examlen;", "");
            sql(script);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO esquema_migracion (nombre) VALUES (?)")) {
                ps.setString(1, nombre); ps.executeUpdate();
            }
        }
    }

    private static void fixtures() throws Exception {
        sql("INSERT INTO usuario(tipo,identificacion,nombre,correo,contrasena_hash) VALUES "
                + "('ADMINISTRADOR','1','Admin','a@test.invalid','test'),"
                + "('DOCENTE','2','Docente','d@test.invalid','test'),"
                + "('ESTUDIANTE','3','Estudiante','e@test.invalid','test')");
        sql("INSERT INTO administrador(id_usuario) VALUES (1)");
        sql("INSERT INTO docente(id_usuario,id_administrador) VALUES (2,1)");
        sql("INSERT INTO estudiante(id_usuario,nivel_ingles) VALUES (3,'A1')");
        sql("INSERT INTO curso(id_docente,nombre,nivel) VALUES (2,'Curso','A1')");
        sql("INSERT INTO grupo(id_curso,nombre,periodo,codigo_acceso) VALUES (1,'Grupo','2026','TEST0001')");
        sql("INSERT INTO inscripcion(id_grupo,id_estudiante) VALUES (1,3)");
        sql("INSERT INTO pregunta(id_docente,enunciado,tipo,nivel,dificultad,competencia,tema) VALUES "
                + "(2,'Uno','SELECCION_MULTIPLE','A1','BAJA','GRAMMAR','Tema'),"
                + "(2,'Dos','SELECCION_MULTIPLE','A1','BAJA','GRAMMAR','Tema')");
        sql("INSERT INTO opcion_respuesta(id_pregunta,texto) VALUES (1,'A'),(2,'B')");
        sql("INSERT INTO evaluacion(id_docente,titulo,descripcion,duracion_minutos) VALUES (2,'Evaluacion','Prueba',60)");
        sql("INSERT INTO version_evaluacion(id_evaluacion,codigo) VALUES (1,'A'),(1,'B')");
        sql("INSERT INTO version_pregunta(id_version_evaluacion,id_pregunta,enunciado,puntaje) VALUES (1,1,'Uno',2),(2,2,'Dos',3)");
        sql("INSERT INTO version_opcion(id_version_pregunta,texto) VALUES (1,'A'),(2,'B')");
        sql("INSERT INTO evaluacion_virtual(id_grupo,id_evaluacion,fecha_inicio,fecha_fin) VALUES (1,1,NOW(),DATE_ADD(NOW(), INTERVAL 2 HOUR))");
        sql("INSERT INTO evaluacion_virtual_version VALUES (1,1),(1,2)");
        sql("INSERT INTO intento(id_evaluacion_virtual,id_inscripcion,id_version_evaluacion) VALUES (1,1,1)");
        sql("INSERT INTO material_practica(id_docente,titulo) VALUES (2,'Practica'),(2,'Otra')");
        sql("INSERT INTO material_pregunta(id_material_practica,id_pregunta) VALUES (1,1),(2,2)");
        sql("INSERT INTO intento_practica(id_material_practica,id_estudiante) VALUES (1,3)");
    }

    private static void preflight() throws Exception {
        sql("INSERT INTO respuesta_practica(id_intento_practica,id_pregunta) VALUES (1,1),(1,1)");
        rechazar(Files.readString(MIGRACIONES.resolve("022_integridad_respuestas_tokens.sql")), "Preflight rechaza duplicados");
        comprobar(numero("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='respuesta' AND COLUMN_NAME='id_version_evaluacion'") == 0, "Preflight sin DDL persistente");
        sql("DROP TEMPORARY TABLE IF EXISTS auditoria_022_preflight");
        sql("DELETE FROM respuesta_practica");
    }

    private static void preflightRoles() throws Exception {
        sql("INSERT INTO usuario (tipo, identificacion, nombre, correo, contrasena_hash) "
                + "VALUES ('ADMINISTRADOR','999999','Sin rol','sinrol@test.invalid','x')");
        rechazar(Files.readString(MIGRACIONES.resolve("023_roles_sin_columna_tipo.sql")),
                "Preflight 023 rechaza usuario sin rol");
        comprobar(numero("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                + "AND TABLE_NAME='usuario' AND COLUMN_NAME='tipo'") == 1,
                "023 no aplico DDL persistente con datos invalidos");
        sql("DROP TEMPORARY TABLE IF EXISTS auditoria_023_preflight");
        sql("DELETE FROM usuario WHERE identificacion='999999'");
    }

    /** 024: el preflight aborta sin DDL persistente si hay respuestas duplicadas. */
    private static void preflightIntegridad() throws Exception {
        sql("INSERT INTO respuesta(id_intento,id_version_pregunta) VALUES (1,1),(1,1)");
        rechazar(Files.readString(MIGRACIONES.resolve("024_restricciones_integridad.sql")),
                "Preflight 024 rechaza respuestas duplicadas");
        comprobar(numero("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() "
                + "AND CONSTRAINT_NAME='fk_intento_virtual_version'") == 0, "024 no aplico DDL persistente con datos invalidos");
        sql("DROP TEMPORARY TABLE IF EXISTS auditoria_024_preflight");
        sql("DELETE FROM respuesta");
    }

    /** 024: restricciones y disparadores restaurados (SQL-001..SQL-007). */
    private static void pruebasIntegridad024() throws Exception {
        sql("INSERT INTO evaluacion(id_docente,titulo,descripcion,duracion_minutos) VALUES (2,'Otra','Prueba',60)");
        sql("INSERT INTO version_evaluacion(id_evaluacion,codigo) VALUES (2,'X')");
        sql("INSERT INTO grupo(id_curso,nombre,periodo,codigo_acceso) VALUES (1,'Grupo 2','2026','TEST0002')");
        sql("INSERT INTO usuario(identificacion,nombre,correo,contrasena_hash) VALUES ('4','Est2','e2@test.invalid','test')");
        sql("INSERT INTO estudiante(id_usuario,nivel_ingles) SELECT id_usuario,'A1' FROM usuario WHERE identificacion='4'");
        sql("INSERT INTO inscripcion(id_grupo,id_estudiante) SELECT 2,id_usuario FROM usuario WHERE identificacion='4'");
        int otraInscripcion = numero("SELECT id_inscripcion FROM inscripcion WHERE id_grupo=2");
        rechazar("INSERT INTO evaluacion_virtual_version VALUES (1,3)", "024 rechaza version de otra evaluacion en la virtual");
        rechazar("INSERT INTO intento(id_evaluacion_virtual,id_inscripcion,id_version_evaluacion,numero_intento) VALUES (1," + otraInscripcion + ",1,1)",
                "024 rechaza intento de una inscripcion de otro grupo");
        rechazar("INSERT INTO intento(id_evaluacion_virtual,id_inscripcion,id_version_evaluacion,numero_intento) VALUES (1,1,3,2)",
                "024 rechaza intento con version de otra evaluacion");
        rechazar("UPDATE inscripcion SET id_grupo=2 WHERE id_inscripcion=1", "024 impide mover una inscripcion con intentos");
        rechazar("UPDATE evaluacion_virtual SET id_grupo=2 WHERE id_evaluacion_virtual=1", "024 impide mover una virtual con intentos");
        rechazar("UPDATE evaluacion_virtual SET id_evaluacion=2 WHERE id_evaluacion_virtual=1", "024 impide cambiar la evaluacion de la virtual");
        rechazar("UPDATE version_evaluacion SET id_evaluacion=2 WHERE id_version_evaluacion=1", "024 impide cambiar la evaluacion de una version aplicada");
        rechazar("DELETE FROM evaluacion_virtual_version WHERE id_evaluacion_virtual=1 AND id_version_evaluacion=1",
                "fk_intento_virtual_version protege la asociacion usada");
        sql("INSERT INTO respuesta(id_intento,id_version_pregunta) VALUES (1,1)");
        rechazar("INSERT INTO respuesta(id_intento,id_version_pregunta) VALUES (1,1)", "uk_respuesta_intento_pregunta");
        sql("DELETE FROM respuesta");
        rechazar("UPDATE evaluacion_virtual SET fecha_fin=fecha_inicio WHERE id_evaluacion_virtual=1", "ck_evaluacion_virtual_fechas");
        rechazar("INSERT INTO evaluacion_pregunta(id_evaluacion,id_pregunta,puntaje_evaluacion_pregunta) VALUES (1,1,-1)",
                "ck_evaluacion_pregunta_puntaje");
        rechazar("INSERT INTO clave_ia_docente(id_docente,proveedor,clave_cifrada) VALUES (3,'groq','x')", "fk_clave_ia_docente");
        rechazar("INSERT INTO estudiante(id_usuario,nivel_ingles) VALUES (2,'A1')", "024 impide docente que tambien sea estudiante");
        rechazar("INSERT INTO docente(id_usuario,id_administrador) VALUES (3,1)", "024 impide estudiante que tambien sea docente");
        rechazar("INSERT INTO administrador(id_usuario) VALUES (2)", "024 impide docente que tambien sea administrador");
        sql("UPDATE intento SET calificacion=4.5 WHERE id_intento=1");
        sql("INSERT INTO evaluacion_virtual_version VALUES (1,1) ON DUPLICATE KEY UPDATE id_version_evaluacion=id_version_evaluacion");
        comprobar(numero("SELECT calificacion FROM intento WHERE id_intento=1") == 4, "024 conserva operaciones validas");
        sql("UPDATE intento SET calificacion=NULL WHERE id_intento=1");
        sql("DELETE FROM inscripcion WHERE id_inscripcion=" + otraInscripcion);
        sql("DELETE FROM usuario WHERE identificacion='4'");
        sql("DELETE FROM grupo WHERE id_grupo=2");
        sql("DELETE FROM version_evaluacion WHERE id_version_evaluacion=3");
        sql("DELETE FROM evaluacion WHERE id_evaluacion=2");
    }

    private static void pruebasRoles(MysqlDataSource fuente) throws Exception {
        AdministradorDAO administradorDAO = new AdministradorDAO(fuente);
        DocenteDAO docenteDAO = new DocenteDAO(fuente);
        EstudianteDAO estudianteDAO = new EstudianteDAO(fuente);
        UsuarioDAO usuarioDAO = new UsuarioDAO(fuente);

        Administrador administrador = administradorDAO.crearAdministrador(
                "900001", "Admin Nuevo", "admin.nuevo@test.invalid", "Clave123", "", null);
        comprobar("ADMINISTRADOR".equals(usuarioDAO.rolDeUsuario(administrador.consultarIdUsuario()).orElse("")),
                "AdministradorDAO crea el rol efectivo");

        Docente docente = docenteDAO.registrarDocente(administrador, "900002", "Docente Nuevo",
                "docente.nuevo@test.invalid", "", "Clave123");
        comprobar("DOCENTE".equals(usuarioDAO.rolDeUsuario(docente.consultarIdUsuario()).orElse("")),
                "DocenteDAO crea el rol efectivo");
        comprobar(usuarioDAO.buscarPorId(docente.consultarIdUsuario()).orElseThrow() instanceof Docente,
                "Docente mapeado como Docente");

        Estudiante estudiante = estudianteDAO.crearEstudiante("900003", "Estudiante Nuevo",
                "estudiante.nuevo@test.invalid", "B1", "", "Clave123");
        comprobar("ESTUDIANTE".equals(usuarioDAO.rolDeUsuario(estudiante.consultarIdUsuario()).orElse("")),
                "EstudianteDAO crea el rol efectivo");
        comprobar(usuarioDAO.buscarPorId(estudiante.consultarIdUsuario()).orElseThrow() instanceof Estudiante,
                "Estudiante mapeado como Estudiante");

        boolean falloEstudiante = false;
        try {
            estudianteDAO.crearEstudiante("900004", "Nivel Invalido", "nivel.invalido@test.invalid",
                    "ZZ", "", "Clave123");
        } catch (RuntimeException e) {
            falloEstudiante = true;
        }
        comprobar(falloEstudiante, "EstudianteDAO propaga el fallo de nivel");
        comprobar(numero("SELECT COUNT(*) FROM usuario WHERE identificacion='900004'") == 0,
                "Rollback de estudiante sin fila usuario");

        Administrador fantasma = new Administrador();
        fantasma.establecerIdUsuario(999999);
        boolean falloDocente = false;
        try {
            docenteDAO.registrarDocente(fantasma, "900005", "Admin Inexistente",
                    "admin.inexistente@test.invalid", "", "Clave123");
        } catch (RuntimeException e) {
            falloDocente = true;
        }
        comprobar(falloDocente, "DocenteDAO propaga el fallo de administrador");
        comprobar(numero("SELECT COUNT(*) FROM usuario WHERE identificacion='900005'") == 0,
                "Rollback de docente sin fila usuario");
    }

    private static void pruebasDatos(MysqlDataSource fuente) throws Exception {
        RespuestaDAO dao = new RespuestaDAO(fuente);
        Intento intento = new Intento(); intento.establecerIdIntento(1);
        VersionPregunta pregunta = new VersionPregunta(); pregunta.establecerIdVersionPregunta(1);
        comprobar(dao.guardarRespuestaVigente(intento, pregunta, "A", 1), "Guardar seleccion");
        comprobar(numero("SELECT id_version_opcion FROM respuesta WHERE id_intento=1") == 1, "Seleccion persistida");
        comprobar(dao.guardarRespuestaVigente(intento, pregunta, "", 0), "Quitar seleccion");
        comprobar(numero("SELECT COUNT(*) FROM respuesta WHERE id_intento=1 AND id_version_opcion IS NULL") == 1, "JAVA-001: seleccion borrada");
        sql("UPDATE respuesta SET puntaje=2 WHERE id_intento=1");
        rechazar("UPDATE respuesta SET id_version_opcion=2 WHERE id_intento=1", "Opcion de otra pregunta");
        rechazar("UPDATE respuesta SET id_version_pregunta=2 WHERE id_intento=1", "Pregunta de otra version");
        rechazar("INSERT INTO respuesta(id_intento,id_version_pregunta) VALUES (1,2)", "Insertar pregunta de otra version");
        rechazar("UPDATE respuesta SET puntaje=2.1 WHERE id_intento=1", "Puntaje supera maximo");
        rechazar("UPDATE respuesta SET puntaje=-0.1 WHERE id_intento=1", "Puntaje negativo");
        rechazar("UPDATE version_pregunta SET puntaje=1 WHERE id_version_pregunta=1", "Maximo historico protegido");
        rechazar("UPDATE version_pregunta SET puntaje=-1 WHERE id_version_pregunta=2", "Maximo negativo");
        rechazar("UPDATE intento SET id_version_evaluacion=2 WHERE id_intento=1", "Cambiar version del padre");
        sql("UPDATE respuesta SET id_version_opcion=1 WHERE id_intento=1");
        rechazar("UPDATE version_opcion SET id_version_pregunta=2 WHERE id_version_opcion=1", "Mover opcion referenciada");
        sql("UPDATE intento SET calificacion=0 WHERE id_intento=1");
        sql("UPDATE intento SET calificacion=5 WHERE id_intento=1");
        rechazar("UPDATE intento SET calificacion=5.1 WHERE id_intento=1", "Nota evaluacion mayor a 5");
        rechazar("UPDATE intento SET calificacion=-0.1 WHERE id_intento=1", "Nota evaluacion negativa");
        sql("INSERT INTO respuesta_practica(id_intento_practica,id_pregunta,id_opcion_respuesta) VALUES (1,1,1)");
        rechazar("INSERT INTO respuesta_practica(id_intento_practica,id_pregunta) VALUES (1,1)", "Respuesta practica duplicada");
        rechazar("INSERT INTO respuesta_practica(id_intento_practica,id_pregunta) VALUES (1,2)", "Pregunta fuera del material");
        rechazar("UPDATE respuesta_practica SET id_pregunta=2", "Cambiar pregunta fuera del material");
        rechazar("UPDATE respuesta_practica SET id_opcion_respuesta=2", "Opcion de otra pregunta practica");
        rechazar("UPDATE intento_practica SET id_material_practica=2 WHERE id_intento_practica=1", "Cambiar material del padre");
        rechazar("DELETE FROM material_pregunta WHERE id_material_practica=1 AND id_pregunta=1", "Eliminar pregunta respondida del material");
        sql("UPDATE intento_practica SET calificacion=100 WHERE id_intento_practica=1");
        rechazar("UPDATE intento_practica SET calificacion=100.1 WHERE id_intento_practica=1", "Porcentaje mayor a 100");
        rechazar("UPDATE intento_practica SET calificacion=-0.1 WHERE id_intento_practica=1", "Porcentaje negativo");
        rechazar("INSERT INTO curso(id_docente,nombre,nivel) VALUES (3,'Invalido','A1')", "Curso exige docente");
        rechazar("INSERT INTO banco_preguntas(id_docente,nombre) VALUES (3,'Invalido')", "Banco exige docente");
        rechazar("INSERT INTO evaluacion(id_docente,titulo,descripcion) VALUES (3,'Invalida','Prueba')", "Evaluacion exige docente");
        rechazar("INSERT INTO material_practica(id_docente,titulo) VALUES (3,'Invalido')", "Material exige docente");
        rechazar("INSERT INTO inscripcion(id_grupo,id_estudiante) VALUES (1,2)", "Inscripcion exige estudiante");
        rechazar("INSERT INTO intento_practica(id_material_practica,id_estudiante) VALUES (1,2)", "Practica exige estudiante");
    }

    private static void pruebasTokens(MysqlDataSource fuente) throws Exception {
        TokenApiDAO registro = new TokenApiDAO(fuente);
        ApiTokens.Token a = ApiTokens.emitir(3, registro), b = ApiTokens.emitir(3, registro);
        comprobar(!a.valor.equals(b.valor), "Tokens distintos emitidos en el mismo segundo");
        ApiTokens.TokenValido va = ApiTokens.validar(a.valor).orElseThrow();
        ApiTokens.TokenValido vb = ApiTokens.validar(b.valor).orElseThrow();
        comprobar(registro.vigente(va.idToken, 3, va.expiraEnSegundos), "Token registrado vigente");
        comprobar(!registro.vigente(va.idToken, 2, va.expiraEnSegundos), "Registro vinculado al usuario");
        comprobar(!registro.vigente(UUID.randomUUID().toString(), 3, va.expiraEnSegundos), "Token sin registro rechazado");
        registro.revocar(va.idToken, 2);
        comprobar(registro.vigente(va.idToken, 3, va.expiraEnSegundos), "Otro usuario no revoca token");
        registro.revocar(va.idToken, 3);
        comprobar(!new TokenApiDAO(fuente).vigente(va.idToken, 3, va.expiraEnSegundos), "Revocacion persiste en otra instancia DAO");
        comprobar(registro.vigente(vb.idToken, 3, vb.expiraEnSegundos), "Segundo token sigue vigente");
        ServletContext contexto = proxy(ServletContext.class, (m,args) -> {
            if (m.equals("getAttribute")) {
                if (args[0].equals(servlet.InicializadorAplicacion.USUARIO_DAO)) return new UsuarioDAO(fuente);
                if (args[0].equals(servlet.InicializadorAplicacion.TOKEN_API_DAO)) return registro;
            }
            return null;
        });
        FiltroApiToken filtro = new FiltroApiToken();
        filtro.init(proxy(FilterConfig.class, (m,args) -> m.equals("getServletContext") ? contexto : null));
        ApiRevocarTokenServlet revocar = new ApiRevocarTokenServlet();
        revocar.init(proxy(ServletConfig.class, (m,args) -> m.equals("getServletContext") ? contexto : null));
        Map<String,Object> atributos = new HashMap<>();
        HttpServletRequest peticion = proxy(HttpServletRequest.class, (m,args) -> {
            if (m.equals("getServletPath")) return "/api/v1/auth/revoke";
            if (m.equals("getHeader")) return "Authorization".equals(args[0]) ? "Bearer " + b.valor : null;
            if (m.equals("setAttribute")) atributos.put((String)args[0], args[1]);
            if (m.equals("getAttribute")) return atributos.get(args[0]);
            return null;
        });
        EstadoHttp revocacion = new EstadoHttp();
        filtro.doFilter(peticion, revocacion.respuesta(), (req,resp) -> revocar.doPost((HttpServletRequest)req,(HttpServletResponse)resp));
        comprobar(revocacion.codigo == 204, "Endpoint revoca Bearer autenticado");
        comprobar(!registro.vigente(vb.idToken,3,vb.expiraEnSegundos), "Revocacion del endpoint persistida");
        EstadoHttp denegada = new EstadoHttp();
        filtro.doFilter(peticion, denegada.respuesta(), (req,resp) -> {throw new AssertionError("Token revocado aceptado");});
        comprobar(denegada.codigo == 401, "Filtro rechaza token revocado");
        long pasado = System.currentTimeMillis()/1000 - 1;
        registro.registrar(UUID.randomUUID().toString(), 3, pasado);
        comprobar(!registro.vigente(va.idToken, 3, pasado), "Registro vencido rechazado");
    }

    private static void pruebasErrores(MysqlDataSource fuente) throws Exception {
        ServletContext contexto = proxy(ServletContext.class, (m,a) -> {
            if (m.equals("getAttribute")) {
                if (a[0].equals(servlet.InicializadorAplicacion.USUARIO_DAO)) return new UsuarioDAO(fuente);
                if (a[0].equals(servlet.InicializadorAplicacion.TOKEN_API_DAO)) return new TokenApiDAO(fuente);
            }
            return null;
        });
        ApiTokenServlet endpoint = new ApiTokenServlet();
        endpoint.init(proxy(ServletConfig.class, (m,a) -> m.equals("getServletContext") ? contexto : null));
        for (String cuerpo : List.of("{", "[]", "null", "{\"usuario\":{},\"contrasena\":\"a\"}")) {
            EstadoHttp estado = new EstadoHttp();
            endpoint.doPost(peticion(false, "127.0.0.1", null, cuerpo), estado.respuesta());
            comprobar(estado.codigo == 400, "JSON invalido devuelve 400");
        }
        // Un fallo de BD durante la autenticacion no debe presentarse como error de sintaxis.
        MysqlDataSource rota = new MysqlDataSource();
        rota.setURL("jdbc:mysql://127.0.0.1:1/no_existe?connectTimeout=1000");
        Field f = ApiTokenServlet.class.getDeclaredField("usuarioDAO"); f.setAccessible(true); f.set(endpoint, new UsuarioDAO(rota));
        EstadoHttp estado = new EstadoHttp();
        endpoint.doPost(peticion(false,"127.0.0.1",null,"{\"usuario\":\"e@test.invalid\",\"contrasena\":\"a\"}"),estado.respuesta());
        comprobar(estado.codigo == 500, "Fallo interno devuelve 500");
    }

    private static void pruebasFirma() {
        String id = UUID.randomUUID().toString();
        String token = ApiTokens.emitir(3, 200, id).valor;
        comprobar(ApiTokens.validar(token, 199).isPresent(), "Firma correcta");
        comprobar(ApiTokens.validar(token, 200).isEmpty(), "Expiracion exacta");
        String[] partes = token.split("\\.");
        partes[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(("2:200:"+id).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        comprobar(ApiTokens.validar(String.join(".",partes),199).isEmpty(), "Payload adulterado");
        comprobar(ApiTokens.validar("v1."+token.substring(3),199).isEmpty(), "Token antiguo invalidado");
        comprobar(ApiTokens.validar("x".repeat(10000),199).isEmpty(), "Token demasiado largo");
    }

    private static void pruebasHttp() throws Exception {
        System.setProperty("examlen.https.required","true");
        System.setProperty("examlen.behind.proxy","false");
        verificarHttp(false,"192.0.2.1","https",403,false);
        verificarHttp(true,"192.0.2.1",null,200,true);
        System.setProperty("examlen.behind.proxy","true");
        System.setProperty("examlen.trusted.proxies","127.0.0.1/32");
        verificarHttp(false,"192.0.2.1","https",403,false);
        verificarHttp(false,"127.0.0.1","https",200,true);
        verificarHttp(false,"127.0.0.1","http",403,false);
        System.setProperty("examlen.https.required","tru");
        try { new CabecerasSeguridadFiltro().init(null); throw new AssertionError("Configuracion invalida aceptada"); }
        catch (IllegalArgumentException esperada) { verificaciones++; }
        System.setProperty("examlen.https.required","true");
    }

    private static void verificarHttp(boolean seguro,String ip,String proto,int codigo,boolean pasa) throws Exception {
        CabecerasSeguridadFiltro filtro = new CabecerasSeguridadFiltro(); filtro.init(null);
        EstadoHttp estado = new EstadoHttp(); boolean[] ejecutado = {false};
        filtro.doFilter(peticion(seguro,ip,proto,""), estado.respuesta(), (req,resp)->ejecutado[0]=true);
        comprobar(estado.codigo==codigo && ejecutado[0]==pasa, "HTTPS y proxy: "+ip+" "+proto);
        if(pasa) comprobar(estado.headers.containsKey("Strict-Transport-Security"), "HSTS sobre conexion segura");
    }

    private static HttpServletRequest peticion(boolean seguro,String ip,String proto,String cuerpo) {
        return proxy(HttpServletRequest.class,(m,a)-> {
            switch(m) {
                case "isSecure": return seguro;
                case "getRemoteAddr": return ip;
                case "getHeader": return "X-Forwarded-Proto".equals(a[0]) ? proto : null;
                case "getContentType": return "application/json";
                case "getContentLengthLong": return (long)cuerpo.length();
                case "getReader": return new BufferedReader(new StringReader(cuerpo));
                default: return null;
            }
        });
    }
    private static final class EstadoHttp {
        int codigo=200; Map<String,String> headers = new HashMap<>(); StringWriter salida = new StringWriter();
        HttpServletResponse respuesta() { return proxy(HttpServletResponse.class,(m,a)-> {
            if(m.equals("setStatus") || m.equals("sendError")) codigo=(int)a[0];
            if(m.equals("setHeader")) headers.put((String)a[0],(String)a[1]);
            if(m.equals("getWriter")) return new PrintWriter(salida);
            return null;
        }); }
    }
    @FunctionalInterface interface Llamada { Object ejecutar(String metodo,Object[] args) throws Exception; }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> tipo,Llamada funcion) {
        return (T)Proxy.newProxyInstance(tipo.getClassLoader(),new Class<?>[]{tipo},(p,m,a)-> {
            Object valor=funcion.ejecutar(m.getName(), a==null ? new Object[0] : a);
            if(valor==null && m.getReturnType().isPrimitive()) {
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==long.class)return 0L;
                if(m.getReturnType()==int.class)return 0;
            }
            return valor;
        });
    }
    private static void sql(String texto) throws SQLException {
        try(Statement st=c.createStatement()) {
            boolean filas=st.execute(texto);
            while(true) {
                if(filas) {try(ResultSet rs=st.getResultSet()){while(rs.next()) {}}}
                else if(st.getUpdateCount()==-1)break;
                filas=st.getMoreResults();
            }
        }
    }
    private static int numero(String texto) throws SQLException {
        try(Statement st=c.createStatement();ResultSet rs=st.executeQuery(texto)){rs.next();return rs.getInt(1);}
    }
    private static void rechazar(String texto,String caso) throws SQLException {
        try {sql(texto);} catch(SQLException esperado) {
            if(!Set.of(1048,1451,1452,1062,3819,4025).contains(esperado.getErrorCode())) throw esperado;
            verificaciones++;return;
        }
        throw new AssertionError("Se acepto: "+caso);
    }
    private static void comprobar(boolean valido,String caso) {
        if(!valido)throw new AssertionError(caso);verificaciones++;
    }
}
