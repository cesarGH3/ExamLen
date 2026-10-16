# Trazabilidad — Diagrama de clases UML por capas (Curso, Grupo, Inscripcion)

- **Alcance del trío**: `logica.Curso`, `logica.Grupo`, `logica.Inscripcion`.
- **Método**: ingeniería inversa del código fuente (solo lectura; no se modificó ningún archivo del proyecto). Las rutas de evidencia son relativas a la raíz del proyecto `ExamLen`.
- **Salida**: `docs/uml/diagrama-capas.puml` (este trazado lo cubre elemento por elemento).
- **Cadena dibujada por entidad**: Vista → Servlet → Entidad → DAO → DaoBase → Conexion → BD.

| Capa | Elemento | Atributo/Método/Relación | Evidencia (archivo:línea) | Comentario |
|---|---|---|---|---|
| html o JSP | DocenteCursosHtml (interface) | interfaz de la vista | web/html/docente-cursos.html:1-59 | Página "Mis cursos" del docente (listado + alta). |
| html o JSP | DocenteCursosHtml | +registrarCurso() | web/html/docente-cursos.html:39 (form method="post"), :52 (botón "Crear curso") | POST {{ctx}}/app/docente/cursos → DocenteCursosServlet (@WebServlet). |
| html o JSP | DocenteCursosHtml | +consultarCursos() | web/html/docente-cursos.html:26-32 (tabla {{{filas}}}); render en src/java/servlet/DocenteCursosServlet.java:47 | GET /app/docente/cursos. |
| html o JSP | DocenteCursoHtml (interface) | interfaz de la vista | web/html/docente-curso.html:1-75 | Ficha de curso (datos, grupos, abrir grupo). |
| html o JSP | DocenteCursoHtml | +consultarCurso() | web/html/docente-curso.html:26-46; render en src/java/servlet/DocenteCursoServlet.java:88 | GET /app/docente/curso?id=… |
| html o JSP | DocenteCursoHtml | +actualizar() | web/html/docente-curso.html:55 (form POST), :57 (hidden accion=actualizar); servlet src/java/servlet/DocenteCursoServlet.java:98 | Valores reales del parámetro `accion`. |
| html o JSP | DocenteCursoHtml | +activar() | web/html/docente-curso.html:31 ({{{accionEstado}}}); botón en src/java/servlet/DocenteCursoServlet.java:73, caso :117 | El botón se compone con ServletBase.boton() (src/java/servlet/ServletBase.java:342-353). |
| html o JSP | DocenteCursoHtml | +desactivar() | web/html/docente-curso.html:31; botón en src/java/servlet/DocenteCursoServlet.java:72, caso :123-127 | Pasa por confirmación `confirmado=1` (:124). |
| html o JSP | DocenteCursoHtml | +abrirGrupo() | web/html/_form-abrir-grupo.html:1 (form POST), :4 (hidden accion=abrirGrupo); incluido en web/html/docente-curso.html:51 ({{{formGrupo}}}); caso src/java/servlet/DocenteCursoServlet.java:134 | Fragmento inyectado por el servlet (DocenteCursoServlet.java:84). |
| html o JSP | DocenteGrupoHtml (interface) | interfaz de la vista | web/html/docente-grupo.html:1-123 | Panel del grupo (inscritos, código, virtuales). |
| html o JSP | DocenteGrupoHtml | +consultarGrupo() | web/html/docente-grupo.html:28-54; render en src/java/servlet/DocenteGrupoServlet.java:108 | GET /app/docente/grupo?id=… |
| html o JSP | DocenteGrupoHtml | +cerrar() | web/html/docente-grupo.html:33 ({{{accionesGrupo}}}); botón en src/java/servlet/DocenteGrupoServlet.java:69, caso :124 | Valor real del parámetro `accion`. |
| html o JSP | DocenteGrupoHtml | +reabrir() | web/html/docente-grupo.html:33; botón en src/java/servlet/DocenteGrupoServlet.java:70, caso :130 | idem. |
| html o JSP | DocenteGrupoHtml | +regenerarCodigo() | web/html/docente-grupo.html:61 ({{{botonRegenerar}}}); botón en src/java/servlet/DocenteGrupoServlet.java:67, caso :118 | idem. |
| html o JSP | DocenteGrupoHtml | +quitar() | web/html/docente-grupo.html:51 ({{{filasActivos}}}) con web/html/_fila-inscrito.html:6 ({{{accion}}}); botón en src/java/servlet/DocenteGrupoServlet.java:45-46, caso :136 | Envía `idInscripcion` como campo extra. |
| html o JSP | DocenteGrupoHtml | +reincorporar() | web/html/docente-grupo.html:54 ({{{seccionRetirados}}}); botón en src/java/servlet/DocenteGrupoServlet.java:49-50, caso :155 | idem. |
| html o JSP | EstudianteGruposHtml (interface) | interfaz de la vista | web/html/estudiante-grupos.html:1-61 | Página "Mis grupos" del estudiante. |
| html o JSP | EstudianteGruposHtml | +consultarMisGrupos() | web/html/estudiante-grupos.html:26-39; render en src/java/servlet/EstudianteGruposServlet.java:62 | GET /app/estudiante/grupos. |
| html o JSP | EstudianteGruposHtml | +unirse() | web/html/estudiante-grupos.html:44 (form POST), :45 (hidden accion=unirse); caso src/java/servlet/EstudianteGruposServlet.java:69 | Primer paso del flujo CU-14. |
| html o JSP | EstudianteGruposHtml | +salir() | web/html/estudiante-grupos.html:55-56 (botón accion=salir); caso src/java/servlet/EstudianteGruposServlet.java:75 | Botón compuesto con boton() (RUTA :31). |
| html o JSP | EstudianteConfirmarGrupoHtml (interface) | interfaz de la vista | web/html/estudiante-confirmar-grupo.html:1-40 | Pantalla de confirmación de inscripción (CU-14). |
| html o JSP | EstudianteConfirmarGrupoHtml | +confirmarUnirse() | web/html/estudiante-confirmar-grupo.html:27 (form POST), :29 (accion=confirmarUnirse), :31 (confirmado=1); casos en src/java/servlet/EstudianteGruposServlet.java:73 y :109 | Segundo paso: revalida y crea la inscripción (clase :23-26). |
| Controlador (Servlets) | ServletBase (class, abstracta) | clase base de los servlets | src/java/servlet/ServletBase.java:51 | Servlet delgado; hereda de HttpServlet. |
| Controlador (Servlets) | ServletBase | # cursoDAO : CursoDAO | src/java/servlet/ServletBase.java:68 | Atributo inyectado en init() (:98). |
| Controlador (Servlets) | ServletBase | # grupoDAO : GrupoDAO | src/java/servlet/ServletBase.java:69 | :99. |
| Controlador (Servlets) | ServletBase | # inscripcionDAO : InscripcionDAO | src/java/servlet/ServletBase.java:70 | :100. |
| Controlador (Servlets) | ServletBase | # init() | src/java/servlet/ServletBase.java:91 | Toma Vistas y DAO del ServletContext. |
| Controlador (Servlets) | DocenteCursosServlet (class) | clase | src/java/servlet/DocenteCursosServlet.java:18 | @WebServlet("/app/docente/cursos") en :17. |
| Controlador (Servlets) | DocenteCursosServlet | # doGet() | src/java/servlet/DocenteCursosServlet.java:25 | Lista cursos del docente (:30) y renderiza (:47). |
| Controlador (Servlets) | DocenteCursosServlet | # doPost() | src/java/servlet/DocenteCursosServlet.java:51 | Crea curso con cursoDAO.crearCurso (:69). |
| Controlador (Servlets) | DocenteCursoServlet (class) | clase | src/java/servlet/DocenteCursoServlet.java:19 | @WebServlet("/app/docente/curso") en :18. |
| Controlador (Servlets) | DocenteCursoServlet | # doGet() | src/java/servlet/DocenteCursoServlet.java:27 | Ficha del curso, grupos y confirmación de desactivación (:31). |
| Controlador (Servlets) | DocenteCursoServlet | # doPost() | src/java/servlet/DocenteCursoServlet.java:92 | Acciones actualizar/activar/desactivar/abrirGrupo (:97-160). |
| Controlador (Servlets) | DocenteGrupoServlet (class) | clase | src/java/servlet/DocenteGrupoServlet.java:23 | @WebServlet("/app/docente/grupo") en :22. |
| Controlador (Servlets) | DocenteGrupoServlet | # doGet() | src/java/servlet/DocenteGrupoServlet.java:29 | Inscritos, código, virtuales, versiones; render :108. |
| Controlador (Servlets) | DocenteGrupoServlet | # doPost() | src/java/servlet/DocenteGrupoServlet.java:112 | Acciones regenerarCodigo/cerrar/reabrir/quitar/reincorporar (:117-167). |
| Controlador (Servlets) | EstudianteGruposServlet (class) | clase | src/java/servlet/EstudianteGruposServlet.java:28 | @WebServlet("/app/estudiante/grupos") en :27. |
| Controlador (Servlets) | EstudianteGruposServlet | # doGet() | src/java/servlet/EstudianteGruposServlet.java:37 | Lista y pantalla de confirmación (:39-42); render :62 y :142. |
| Controlador (Servlets) | EstudianteGruposServlet | # doPost() | src/java/servlet/EstudianteGruposServlet.java:66 | Acciones unirse/confirmarUnirse/salir (:68-92). |
| Logica | Curso (class) | clase | src/java/logica/Curso.java:5 | Entidad del trío. |
| Logica | Curso | - idCurso : int | src/java/logica/Curso.java:7 | Privado (getter/setter). |
| Logica | Curso | + nombreCurso : String | src/java/logica/Curso.java:8 | Público en código. |
| Logica | Curso | + descripcionCurso : String | src/java/logica/Curso.java:9 | idem. |
| Logica | Curso | + nivelCurso : NivelIngles | src/java/logica/Curso.java:10 | Enum `NivelIngles`. |
| Logica | Curso | + activoCurso : boolean | src/java/logica/Curso.java:11 | idem. |
| Logica | Curso | + docenteCurso : Docente | src/java/logica/Curso.java:12 | FK fk_curso_docente (web/WEB-INF/sql/migraciones/002_cursos_banco_preguntas.sql:23). Docente fuera del trío. |
| Logica | Curso | + gruposCurso : ArrayList<Grupo> | src/java/logica/Curso.java:13 | Inicializada en el constructor (:17). |
| Logica | Curso | + materialesPracticaCurso : ArrayList<MaterialPractica> | src/java/logica/Curso.java:14 | MaterialPractica fuera del trío. |
| Logica | Curso | + getIdCurso() : int | src/java/logica/Curso.java:21 | — |
| Logica | Curso | + setIdCurso(idCurso : int) | src/java/logica/Curso.java:25 | — |
| Logica | Grupo (class) | clase | src/java/logica/Grupo.java:5 | Entidad del trío. |
| Logica | Grupo | - idGrupo : int | src/java/logica/Grupo.java:7 | Privado. |
| Logica | Grupo | + nombreGrupo : String | src/java/logica/Grupo.java:8 | — |
| Logica | Grupo | + periodoGrupo : String | src/java/logica/Grupo.java:9 | — |
| Logica | Grupo | + codigoAccesoGrupo : String | src/java/logica/Grupo.java:10 | Código de acceso (CU-14). |
| Logica | Grupo | + estadoGrupo : EstadoGrupo | src/java/logica/Grupo.java:11 | Enum `EstadoGrupo`. |
| Logica | Grupo | + cursoGrupo : Curso | src/java/logica/Grupo.java:12 | FK fk_grupo_curso (web/WEB-INF/sql/migraciones/002_cursos_banco_preguntas.sql:42). |
| Logica | Grupo | + inscripcionesGrupo : ArrayList<Inscripcion> | src/java/logica/Grupo.java:13 | Inicializada en constructor (:17). |
| Logica | Grupo | + evaluacionesVirtualesGrupo : ArrayList<EvaluacionVirtual> | src/java/logica/Grupo.java:14 | EvaluacionVirtual fuera del trío. |
| Logica | Grupo | + getIdGrupo() : int | src/java/logica/Grupo.java:21 | — |
| Logica | Grupo | + setIdGrupo(idGrupo : int) | src/java/logica/Grupo.java:25 | — |
| Logica | Inscripcion (class) | clase | src/java/logica/Inscripcion.java:6 | Entidad del trío. |
| Logica | Inscripcion | - idInscripcion : int | src/java/logica/Inscripcion.java:8 | Privado. |
| Logica | Inscripcion | + fechaInscripcion : Date | src/java/logica/Inscripcion.java:9 | java.util.Date. |
| Logica | Inscripcion | + estadoInscripcion : EstadoInscripcion | src/java/logica/Inscripcion.java:10 | Enum `EstadoInscripcion`. |
| Logica | Inscripcion | + estudianteInscripcion : Estudiante | src/java/logica/Inscripcion.java:11 | FK fk_inscripcion_estudiante (002_…sql:60). Estudiante fuera del trío. |
| Logica | Inscripcion | + grupoInscripcion : Grupo | src/java/logica/Inscripcion.java:12 | FK fk_inscripcion_grupo (002_…sql:59). |
| Logica | Inscripcion | + intentosInscripcion : ArrayList<Intento> | src/java/logica/Inscripcion.java:13 | Intento fuera del trío. |
| Logica | Inscripcion | + getIdInscripcion() : int | src/java/logica/Inscripcion.java:19 | — |
| Logica | Inscripcion | + setIdInscripcion(idInscripcion : int) | src/java/logica/Inscripcion.java:23 | — |
| Modelo | CursoDAO (class) | clase | src/java/modelo/CursoDAO.java:12 | `extends DaoBase`. |
| Modelo | CursoDAO | + CursoDAO(dataSource : DataSource) | src/java/modelo/CursoDAO.java:16 | super(dataSource) :17. |
| Modelo | CursoDAO | + crearCurso(docente : Docente, nombre : String, descripcion : String, nivel : NivelIngles) : Curso | src/java/modelo/CursoDAO.java:20 | INSERT en curso (:29-35); devuelve la entidad creada (:40). |
| Modelo | CursoDAO | + actualizarCurso(curso : Curso, nombre : String, descripcion : String) | src/java/modelo/CursoDAO.java:43 | UPDATE (:45-49). |
| Modelo | CursoDAO | + activarCurso(curso : Curso) | src/java/modelo/CursoDAO.java:57 | UPDATE activo=TRUE (:61). |
| Modelo | CursoDAO | + desactivarCurso(curso : Curso) | src/java/modelo/CursoDAO.java:69 | UPDATE activo=FALSE (:73); CU-11 (no bloquea por grupos abiertos). |
| Modelo | CursoDAO | + buscarPorId(idCurso : int) : Optional<Curso> | src/java/modelo/CursoDAO.java:77 | SELECT (:78). |
| Modelo | CursoDAO | + buscarPorIdDeDocente(idCurso : int, idDocente : int) : Optional<Curso> | src/java/modelo/CursoDAO.java:82 | SELECT con id_docente (:83-86); RNF-02. |
| Modelo | CursoDAO | + listarPorDocente(idDocente : int) : List<Curso> | src/java/modelo/CursoDAO.java:89 | SELECT ORDER BY (:90-91). |
| Modelo | GrupoDAO (class) | clase | src/java/modelo/GrupoDAO.java:13 | `extends DaoBase`. |
| Modelo | GrupoDAO | + GrupoDAO(dataSource : DataSource) | src/java/modelo/GrupoDAO.java:20 | Delega al segundo constructor. |
| Modelo | GrupoDAO | + GrupoDAO(dataSource : DataSource, generadorCodigo : GeneradorCodigoAcceso) | src/java/modelo/GrupoDAO.java:24 | super(dataSource) :25. |
| Modelo | GrupoDAO | + abrirGrupo(curso : Curso, nombre : String, periodo : String) : Grupo | src/java/modelo/GrupoDAO.java:29 | Valida curso activo (:30), duplicado en periodo (:33), INSERT (:44-50); muta curso.gruposCurso (:52). |
| Modelo | GrupoDAO | + generarCodigoAccesoGrupo(grupo : Grupo) : String | src/java/modelo/GrupoDAO.java:61 | UPDATE codigo_acceso (:65-68). |
| Modelo | GrupoDAO | + cerrarGrupo(grupo : Grupo) | src/java/modelo/GrupoDAO.java:78 | UPDATE estado CERRADO (:82). |
| Modelo | GrupoDAO | + reabrirGrupo(grupo : Grupo) | src/java/modelo/GrupoDAO.java:86 | UPDATE estado ABIERTO (:93); valida curso activo (:90). |
| Modelo | GrupoDAO | + buscarPorId(idGrupo : int) : Optional<Grupo> | src/java/modelo/GrupoDAO.java:97 | SELECT (:98). |
| Modelo | GrupoDAO | + buscarPorIdDeDocente(idGrupo : int, idDocente : int) : Optional<Grupo> | src/java/modelo/GrupoDAO.java:102 | JOIN con curso (:103-106). |
| Modelo | GrupoDAO | + buscarPorCodigo(codigoAcceso : String) : Optional<Grupo> | src/java/modelo/GrupoDAO.java:109 | SELECT por código (CU-14). |
| Modelo | GrupoDAO | + listarPorCurso(idCurso : int) : List<Grupo> | src/java/modelo/GrupoDAO.java:113 | SELECT (:114-115). |
| Modelo | GrupoDAO | + existeEnPeriodo(idCurso : int, periodo : String, nombre : String) : boolean | src/java/modelo/GrupoDAO.java:118 | SELECT 1 LIMIT 1 (:119-123). |
| Modelo | GrupoDAO | + contarAbiertosPorCurso(idCurso : int) : int | src/java/modelo/GrupoDAO.java:126 | COUNT (:127-128). |
| Modelo | InscripcionDAO (class) | clase | src/java/modelo/InscripcionDAO.java:17 | `extends DaoBase`. |
| Modelo | InscripcionDAO | + InscripcionDAO(dataSource : DataSource) | src/java/modelo/InscripcionDAO.java:21 | super(dataSource) :22. |
| Modelo | InscripcionDAO | + crearInscripcion(estudiante : Estudiante, grupo : Grupo) : Inscripcion | src/java/modelo/InscripcionDAO.java:25 | Valida grupo ABIERTO (:26), duplicado (:29-34), INSERT (:41-45); muta listas en memoria (:50-51). |
| Modelo | InscripcionDAO | + eliminarInscripcion(inscripcion : Inscripcion) | src/java/modelo/InscripcionDAO.java:56 | DELETE (:58-59); puede lanzar RegistroEnUsoException (:55). |
| Modelo | InscripcionDAO | + retirarInscripcion(inscripcion : Inscripcion) | src/java/modelo/InscripcionDAO.java:62 | UPDATE estado RETIRADA (:67-68). |
| Modelo | InscripcionDAO | + reincorporarInscripcion(inscripcion : Inscripcion) | src/java/modelo/InscripcionDAO.java:72 | UPDATE estado ACTIVA (:77-78). |
| Modelo | InscripcionDAO | + buscarPorId(idInscripcion : int) : Optional<Inscripcion> | src/java/modelo/InscripcionDAO.java:82 | SELECT (:83). |
| Modelo | InscripcionDAO | + buscarPorIdDeDocente(idInscripcion : int, idDocente : int) : Optional<Inscripcion> | src/java/modelo/InscripcionDAO.java:87 | JOIN curso (:88-91). |
| Modelo | InscripcionDAO | + buscarPorIdDeEstudiante(idInscripcion : int, idEstudiante : int) : Optional<Inscripcion> | src/java/modelo/InscripcionDAO.java:95 | SELECT (:96-99). |
| Modelo | InscripcionDAO | + buscarPorGrupoYEstudiante(idGrupo : int, idEstudiante : int) : Optional<Inscripcion> | src/java/modelo/InscripcionDAO.java:102 | SELECT (:103-106). |
| Modelo | InscripcionDAO | + listarPorGrupo(idGrupo : int) : List<Inscripcion> | src/java/modelo/InscripcionDAO.java:109 | SELECT ORDER BY e.nombre (:110-111). |
| Modelo | InscripcionDAO | + listarActivasPorEstudiante(idEstudiante : int) : List<Inscripcion> | src/java/modelo/InscripcionDAO.java:114 | SELECT estado ACTIVA (:115-117). |
| Modelo | InscripcionDAO | + contarActivosPorGrupo(idGrupo : int) : int | src/java/modelo/InscripcionDAO.java:120 | COUNT estado ACTIVA (:121-122). |
| Modelo | DaoBase (class, abstracta) | clase | src/java/modelo/DaoBase.java:18 | Infraestructura común: conexiones, consultas, transacciones, errores. |
| Modelo | DaoBase | # dataSource : DataSource | src/java/modelo/DaoBase.java:41 | Se recibe y valida en el constructor (:43-45). |
| Modelo | DaoBase | # consultarUno(sql : String, parametros : Parametros, mapeador : Mapeador<T>) : Optional<T> | src/java/modelo/DaoBase.java:47 | Usa dataSource.getConnection() (:48). |
| Modelo | DaoBase | # consultarLista(sql : String, parametros : Parametros, mapeador : Mapeador<T>) : List<T> | src/java/modelo/DaoBase.java:58 | idem (:59). |
| Modelo | DaoBase | # existe(sql : String, parametros : Parametros) : boolean | src/java/modelo/DaoBase.java:67 | Delega en consultarUno (:68). |
| Modelo | DaoBase | # contar(sql : String, parametros : Parametros) : int | src/java/modelo/DaoBase.java:71 | getInt(1) (:72). |
| Modelo | DaoBase | # actualizar(sql : String, parametros : Parametros) : int | src/java/modelo/DaoBase.java:75 | executeUpdate (:76-80). |
| Modelo | DaoBase | # insertar(sql : String, parametros : Parametros) : int | src/java/modelo/DaoBase.java:84 | Clave generada (:119-129). |
| Modelo | Conexion (class) | clase | src/java/conexion/Conexion.java:12 | Paquete real `conexion`; el diagrama lo ubica en Modelo según la plantilla. |
| Modelo | Conexion | + JNDI_DATASOURCE : String | src/java/conexion/Conexion.java:14 | "java:comp/env/jdbc/ExamLenDB" (static final). |
| Modelo | Conexion | + obtenerFuente() : DataSource | src/java/conexion/Conexion.java:19 | InitialContext().lookup (:21). |
| Modelo | BD (database) | elemento de datos | web/META-INF/context.xml:14 (Resource jdbc/ExamLenDB, driverClassName com.mysql.cj.jdbc.Driver) y src/java/conexion/Conexion.java:14 | Base MySQL "ExamLen"; credenciales fuera del repositorio (context.xml:2-11). |
| Logica | Curso "1" — "0..*" Grupo | relación | Atributos: src/java/logica/Curso.java:13 (gruposCurso) y src/java/logica/Grupo.java:12 (cursoGrupo); FK: web/WEB-INF/sql/migraciones/002_cursos_banco_preguntas.sql:42 (fk_grupo_curso) | Un curso tiene 0..* grupos; cada grupo pertenece a 1 curso. |
| Logica | Grupo "1" — "0..*" Inscripcion | relación | Atributos: src/java/logica/Grupo.java:13 (inscripcionesGrupo) y src/java/logica/Inscripcion.java:12 (grupoInscripcion); FK: web/WEB-INF/sql/migraciones/002_cursos_banco_preguntas.sql:59 (fk_inscripcion_grupo) | Un grupo tiene 0..* inscripciones; cada inscripción pertenece a 1 grupo. |
| html o JSP | DocenteCursosHtml <--> DocenteCursoHtml | relación entre vistas | Ida: web/html/_fila-curso.html:2 (href {{url}}) con url armada en src/java/servlet/DocenteCursosServlet.java:32. Vuelta: web/html/docente-curso.html:21 (migas "Mis cursos") | Navegación listado ↔ ficha. |
| html o JSP | DocenteCursoHtml <--> DocenteGrupoHtml | relación entre vistas | Ida: web/html/_fila-grupo.html:2 con url en src/java/servlet/DocenteCursoServlet.java:54. Vuelta: web/html/docente-grupo.html:21-23 (migas) con cursoUrl en src/java/servlet/DocenteGrupoServlet.java:57 | Navegación ficha ↔ panel del grupo. |
| html o JSP | EstudianteGruposHtml <--> EstudianteConfirmarGrupoHtml | relación entre vistas | Ida: src/java/servlet/EstudianteGruposServlet.java:101 (redirect accion=confirmarUnirse) y render :142. Vuelta: web/html/estudiante-confirmar-grupo.html:34 (Cancelar → /app/estudiante/grupos) | Flujo de confirmación CU-14. |
| html o JSP → Controlador | DocenteCursosHtml <--> DocenteCursosServlet | dependencia de capa | web/html/docente-cursos.html:39 (action) == src/java/servlet/DocenteCursosServlet.java:17 (@WebServlet) | Bidireccional: GET devuelve la página (:47); POST envía la creación (:51). |
| html o JSP → Controlador | DocenteCursoHtml <--> DocenteCursoServlet | dependencia de capa | web/html/docente-curso.html:55 y web/html/_form-abrir-grupo.html:1 (action) == src/java/servlet/DocenteCursoServlet.java:18 (@WebServlet) | Bidireccional: GET ficha (:27); POST acciones (:92). |
| html o JSP → Controlador | DocenteGrupoHtml <--> DocenteGrupoServlet | dependencia de capa | Acciones compuestas por ServletBase.boton() (src/java/servlet/ServletBase.java:342) con destino RUTA (/app/docente/grupo, src/java/servlet/DocenteGrupoServlet.java:26) == @WebServlet :22 | Bidireccional: GET panel (:29); POST actos (:112). |
| html o JSP → Controlador | EstudianteGruposHtml <--> EstudianteGruposServlet | dependencia de capa | web/html/estudiante-grupos.html:44 (action) == src/java/servlet/EstudianteGruposServlet.java:27 (@WebServlet) | Bidireccional: GET lista (:37); POST unirse/salir (:66). |
| html o JSP → Controlador | EstudianteConfirmarGrupoHtml <--> EstudianteGruposServlet | dependencia de capa | web/html/estudiante-confirmar-grupo.html:27 (action) == src/java/servlet/EstudianteGruposServlet.java:27 (@WebServlet) | Bidireccional: GET confirmación (:39-41); POST confirmarUnirse (:73). |
| Controlador | DocenteCursosServlet --|> ServletBase | herencia | src/java/servlet/DocenteCursosServlet.java:18 | `extends ServletBase`. |
| Controlador | DocenteCursoServlet --|> ServletBase | herencia | src/java/servlet/DocenteCursoServlet.java:19 | idem. |
| Controlador | DocenteGrupoServlet --|> ServletBase | herencia | src/java/servlet/DocenteGrupoServlet.java:23 | idem. |
| Controlador | EstudianteGruposServlet --|> ServletBase | herencia | src/java/servlet/EstudianteGruposServlet.java:28 | idem. |
| Controlador → Logica | DocenteCursosServlet ..> Curso | dependencia de uso | src/java/servlet/DocenteCursosServlet.java:30 (listarPorDocente → Curso) y :69 (crearCurso → Curso) | No existe atributo de entidad (ver Inconsistencias #1). |
| Controlador → Logica | DocenteCursoServlet ..> Curso | dependencia de uso | src/java/servlet/DocenteCursoServlet.java:30 (cursoDelDocente), :111-112 (actualizarCurso), :119/:129 (activar/desactivar) | idem. |
| Controlador → Logica | DocenteCursoServlet ..> Grupo | dependencia de uso | src/java/servlet/DocenteCursoServlet.java:33 (listarPorCurso), :148 (abrirGrupo → Grupo) | idem. |
| Controlador → Logica | DocenteGrupoServlet ..> Grupo | dependencia de uso | src/java/servlet/DocenteGrupoServlet.java:32 (grupoDelDocente), :120, :126, :132 | idem. |
| Controlador → Logica | DocenteGrupoServlet ..> Inscripcion | dependencia de uso | src/java/servlet/DocenteGrupoServlet.java:37 (listarPorGrupo), :139-142 (eliminar), :145 (retirar), :158-160 (reincorporar) | idem. |
| Controlador → Logica | EstudianteGruposServlet ..> Grupo | dependencia de uso | src/java/servlet/EstudianteGruposServlet.java:45 (grupoInscripcion), :117 (validarCodigo → Grupo), :169 (buscarPorCodigo) | idem. |
| Controlador → Logica | EstudianteGruposServlet ..> Inscripcion | dependencia de uso | src/java/servlet/EstudianteGruposServlet.java:44 (listarActivasPorEstudiante), :78 (buscarPorIdDeEstudiante), :118 (crearInscripcion) | idem. |
| Logica ↔ Modelo | Curso <--> CursoDAO | dependencia de capa (CRUD) | Escritura: src/java/modelo/CursoDAO.java:20/:40 (crearCurso devuelve Curso), :43/:57/:69 (recibe Curso). Lectura: :77-92 (buscarPorId/buscarPorIdDeDocente/listarPorDocente devuelven Curso) | Bidireccional: hay consultas (los datos regresan a la vista). |
| Logica ↔ Modelo | Grupo <--> GrupoDAO | dependencia de capa (CRUD) | Escritura: src/java/modelo/GrupoDAO.java:29/:53 (abrirGrupo devuelve Grupo), :61/:78/:86 (reciben Grupo). Lectura: :97-116 | idem. |
| Logica ↔ Modelo | Inscripcion <--> InscripcionDAO | dependencia de capa (CRUD) | Escritura: src/java/modelo/InscripcionDAO.java:25/:52 (crearInscripcion devuelve Inscripcion), :56/:62/:72. Lectura: :82-122 | idem. |
| Modelo | GrupoDAO ..> Curso | dependencia de uso interno | src/java/modelo/GrupoDAO.java:29 (abrirGrupo(curso : Curso)), :30 (lee curso.activoCurso), :33 (curso.getIdCurso()), :52 (curso.gruposCurso.add(grupo)), :90 (lee grupo.cursoGrupo) | Usa instancias de Curso, no solo su id. |
| Modelo | InscripcionDAO ..> Grupo | dependencia de uso interno | src/java/modelo/InscripcionDAO.java:25 (crearInscripcion(…, grupo : Grupo)), :26 (lee estadoGrupo), :43 (grupo.getIdGrupo()), :50 (grupo.inscripcionesGrupo.add), :126 (lee grupoInscripcion) | idem. |
| Modelo | CursoDAO --|> DaoBase | herencia | src/java/modelo/CursoDAO.java:12 | Todos los DAO llegan a la BD a través de DaoBase → Conexion. |
| Modelo | GrupoDAO --|> DaoBase | herencia | src/java/modelo/GrupoDAO.java:13 | idem. |
| Modelo | InscripcionDAO --|> DaoBase | herencia | src/java/modelo/InscripcionDAO.java:17 | idem. |
| Modelo → Conexion | DaoBase --> Conexion | dependencia de capa | DaoBase recibe y guarda el DataSource (src/java/modelo/DaoBase.java:41, :43-45) y ejecuta SQL con él (:48, :59, :76, :85, :93). `Conexion.obtenerFuente()` devuelve ese DataSource (src/java/conexion/Conexion.java:19-21) y es invocado por src/java/servlet/InicializadorAplicacion.java:72, que construye cada DAO con él (CursoDAO :85, GrupoDAO :86, InscripcionDAO :87) | Cadena dibujada DaoBase --> Conexion --> BD por indicación expresa del usuario; en el código, la invocación de Conexion la realiza InicializadorAplicacion (ver Inconsistencias #2). |
| Modelo | Conexion --> BD | dependencia hacia la base de datos | src/java/conexion/Conexion.java:14 y :19-21 (lookup JNDI java:comp/env/jdbc/ExamLenDB) + web/META-INF/context.xml:14 (DataSource MySQL) | Unidireccional: Conexion solo tiene obtenerFuente() (sin métodos de registrar/consultar). |

## Verificación (PASO 6)

- **Elementos del .puml con fila en esta tabla**: 5 interfaces de vista, 13 clases (ServletBase, 4 servlets, Curso, Grupo, Inscripcion, 3 DAO, DaoBase, Conexion), 1 elemento `database BD`; todos sus atributos/métodos listados arriba tienen fila.
- **Relaciones del .puml con fila**: 31 de 31 (2 entre entidades + 3 entre vistas + 5 vista↔servlet + 4 herencias de servlets + 7 servlet→entidad + 3 entidad↔DAO + 2 DAO→entidad + 3 herencias de DAO + 1 DaoBase→Conexion + 1 Conexion→BD).
- **Validación de sintaxis PlantUML**: no fue posible con `plantuml.jar`: no está instalado en el sistema (no se encontró el comando `plantuml` ni ningún `plantuml*.jar`). Se hizo verificación estructural del archivo (marcas @startuml/@enduml, llaves balanceadas por paquete y nombres idénticos a los del código).

## Dudas

1. **PlantUML no instalado**: no se pudo ejecutar `java -jar plantuml.jar -syntax diagrama-capas.puml`; queda pendiente si se instala la herramienta.
2. **Botones de acción sin formulario literal en el HTML**: activar, desactivar, cerrar, reabrir, regenerarCodigo, quitar, reincorporar y salir se generan con los fragmentos web/html/_boton-accion.html:1-7 (y web/html/_fila-inscrito.html:6) desde `ServletBase.boton()` (src/java/servlet/ServletBase.java:342-353); el método de la interfaz de vista se dedujo del valor de `accion` que inyecta cada servlet (DocenteCursoServlet.java:72-73; DocenteGrupoServlet.java:45-70; EstudianteGruposServlet.java:55).
3. **Formulario fuera del trío**: web/html/docente-grupo.html:90-119 ("Programar evaluación virtual") hace POST a /app/docente/virtual, servlet de EvaluacionVirtual. No se dibuja por estar fuera del alcance.
4. **Multiplicidades**: deducidas de atributos `ArrayList` + claves foráneas del SQL (web/WEB-INF/sql/migraciones/002_cursos_banco_preguntas.sql:42 y :59). No se validaron contra la base en ejecución (análisis de solo lectura).
5. **Referencias fuera del trío no dibujadas**: Curso.docenteCurso : Docente (Curso.java:12), Curso.materialesPracticaCurso : MaterialPractica (:14), Grupo.evaluacionesVirtualesGrupo : EvaluacionVirtual (Grupo.java:14), Inscripcion.estudianteInscripcion : Estudiante (Inscripcion.java:11), Inscripcion.intentosInscripcion : Intento (:13). Existen en el código pero sus clases no forman parte de las 3 solicitadas.
6. **Relación Inscripcion–Estudiante (0..*—1)**: existe (FK fk_inscripcion_estudiante, 002_…sql:60) pero Estudiante está fuera del trío; por eso no aparece dibujada la lectura "Curso 0..* — 0..* Estudiante" que pasaría por Grupo+Inscripcion.

## Inconsistencias

1. **Servlets sin atributo de entidad**: el esqueleto pedía `- xObj : Entidad` en cada servlet; el código no tiene ese atributo: usa variables locales y los DAOs heredados de ServletBase (src/java/servlet/ServletBase.java:64-70, inicializados en :98-100). Se representó con dependencia `..>` Servlet→Entidad (indicación del usuario).
2. **Cadena de conexión distinta al esqueleto**: el esqueleto asumía `DAO --> Conexion` directo; en el código los DAO heredan de `DaoBase` (CursoDAO.java:12, GrupoDAO.java:13, InscripcionDAO.java:17) y `Conexion.obtenerFuente()` (Conexion.java:19) es invocado por `InicializadorAplicacion` (InicializadorAplicacion.java:72), que inyecta el DataSource en cada DAO (:85-87). Se dibujó `DAO --|> DaoBase --> Conexion --> BD` según indicación expresa; `InicializadorAplicacion` (ServletContextListener) es la clase puente que no se dibujó.
3. **DocenteCursoServlet usa Grupo y cuenta inscripciones sin manejar la entidad Inscripcion**: src/java/servlet/DocenteCursoServlet.java:57 (inscripcionDAO.contarActivosPorGrupo); por eso no se dibuja dependencia de ese servlet hacia Inscripcion.
4. **Métodos de las interfaces de vista**: son convención de trazabilidad (el código HTML no tiene métodos); se usan los valores reales del parámetro `accion` (actualizar, activar, desactivar, abrirGrupo, cerrar, reabrir, regenerarCodigo, quitar, reincorporar, unirse, salir, confirmarUnirse) y `registrarCurso()` para el formulario sin `accion` (docente-cursos.html:39).
5. **Paquete real de Conexion**: `conexion` (src/java/conexion/Conexion.java:1), no `modelo`; en el diagrama se ubicó dentro del paquete "Modelo" para respetar la plantilla del tablero.
6. **DaoBase expone más miembros que los dibujados**: `enTransaccion` (DaoBase.java:92), `insertarCon` (:118), `consultarUnoCon` (:131), `consultarListaCon` (:141), `actualizarCon` (:111), `enumeracion` (:149); el diagrama muestra solo los usados por los tres DAO del trío.
7. **Visibilidad protected en el controlador**: `doGet/doPost/init` son `protected` en el código (ServletBase.java:91; servlets:25/51, 27/92, 29/112, 37/66); en el .puml se dibujan como `#` (protegido), no como `+`.
8. **GrupoDAO tiene dos constructores** (GrupoDAO.java:20 y :24); ambos se listan en el diagrama.
