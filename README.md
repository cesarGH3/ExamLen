# ExamLen

Aplicación web Java (Jakarta Servlet 5) de evaluaciones y práctica para el Programa de Inglés.

## Estado de las entregas

| Entrega | Contenido | Estado |
|---|---|---|
| Base | Usuarios, autenticación, perfil, docentes, seguridad | ✅ |
| 1 | Cursos, grupos, inscripción con código, banco de preguntas | ✅ |
| 2 | Evaluaciones, versiones equivalentes, PDF (examen y clave) | ✅ |
| 3 | Evaluación virtual, intentos, respuestas, supervisión | ✅ |
| **4** | **Material de práctica e IA (Gemini + Groq, PDF y Word)** | ✅ **esta versión** |

## Tecnologías
Java 17 · Jakarta Servlet 5.0 · Apache Tomcat 10.0.23 · MySQL 8.4.9 · HTML · CSS · JavaScript · NetBeans 21 (Ant).
Librerías en `web/WEB-INF/lib/`: **OpenPDF 1.3.43** (PDF), **Gson 2.11.0** (JSON de la IA),
**PDFBox 2.0.31 + Fontbox** (lectura de PDF).

## Claves de la IA (Entrega 4)
La IA usa **Gemini** y **Groq**. Las claves **no van en el código**: crea el archivo
`{Catalina Base}/conf/ia.properties` con:
```properties
gemini.apiKey=TU_CLAVE
groq.apiKey=TU_CLAVE
# opcional: documentos.directorio=C:/ruta/a/documentos
```
También sirven las variables de entorno `GEMINI_API_KEY` y `GROQ_API_KEY`. Sin claves, la página de
generación lo avisa y las solicitudes quedan registradas como fallidas.

## Umbrales de supervisión (RF-50)
Los umbrales del análisis de supervisión **no van en el código**: crea el archivo
`{Catalina Base}/conf/supervision.properties` con:
```properties
supervision.similitud.umbral=0.85
supervision.tiempo.fraccion.mediana=0.25
```
También sirven las variables de entorno `EXAMENL_SUPERVISION_SIMILITUD` y
`EXAMENL_SUPERVISION_TIEMPO`. Sin archivo se usan 0,85 y 0,25 (los valores del ERS).

## Tratamiento de datos (RNF-13)
El registro de estudiantes exige aceptar la <a href="/politica-datos">política de tratamiento de
datos</a> y guarda la fecha en `estudiante.aceptacion_tratamiento_datos`. **La política es un texto
borrador pendiente de revisión legal**: el apartado del responsable del tratamiento queda en blanco
a propósito para que lo complete la institución que despliegue el sistema.

## Base de datos: credenciales fuera del repositorio
El recurso JNDI `jdbc/ExamLenDB` (el mismo que usan todos los DAO) toma la URL, el usuario y la
contraseña de **propiedades de Tomcat**, no del proyecto:
1. Crea el usuario de aplicación en MySQL con privilegios mínimos (ver `web/WEB-INF/sql/migraciones/001_usuarios.sql`):
   ```sql
   CREATE USER 'examlen_app'@'localhost' IDENTIFIED BY 'TU_CONTRASENA';
   GRANT SELECT, INSERT, UPDATE ON examlen.* TO 'examlen_app'@'localhost';
   FLUSH PRIVILEGES;
   ```
2. Copia las tres líneas de `config/catalina.properties.ejemplo` al final de
   `{Catalina Base}/conf/catalina.properties` y reemplaza los valores
   (`USUARIO_MYSQL`, `CAMBIAR_EN_SERVIDOR`). La URL completa y lista para copiar está en ese ejemplo;
   debe conservar `connectionTimeZone=America/Bogota` y `sessionVariables=time_zone='-05:00'` (RNF-15).
3. El descriptor versionable `web/META-INF/context.xml` usa los placeholders `${examlen.db.url}`,
   `${examlen.db.user}` y `${examlen.db.password}`. El descriptor **efectivo** del despliegue local es
   `{Catalina Base}/conf/Catalina/localhost/ExamLen.xml` (tiene prioridad sobre el de la aplicación) y
   usa los mismos placeholders: **ningún archivo del repositorio contiene credenciales**.
4. Reinicia Tomcat. Si `catalina.properties` no define las propiedades, el DataSource no se crea y la
   aplicación arranca sin conexión.

## Zona horaria (RNF-15)
La aplicación registra las fechas con la hora del servidor en `America/Bogota` (`logica/Fechas`) y la
URL de la conexión fuerza la sesión MySQL a `-05:00`, el desplazamiento fijo de Bogotá (Colombia no
tiene horario de verano desde 1993). Esa URL vive en `examlen.db.url` (`catalina.properties`) y debe
conservar `connectionTimeZone=America/Bogota` y `sessionVariables=time_zone='-05:00'`; **no dependas
de `time_zone=SYSTEM`** aunque el sistema operativo coincida. Verificación:
```sql
SELECT @@session.time_zone;   -- debe devolver -05:00
SELECT NOW(), UTC_TIMESTAMP();  -- NOW() debe ir 5 horas por detrás de UTC
```
Opcionalmente, para tener nombres de zona en MySQL, carga las tablas horarias:
```bash
mysql_tzinfo_to_sql /usr/share/zoneinfo | mysql -u root mysql
```
En Windows ese comando no viene con MySQL; con el desplazamiento `-05:00` de la conexión es
suficiente y equivalente.

## Apertura y cierre automáticos (RF-42)
Un programador de la aplicación sincroniza periódicamente los estados de las evaluaciones virtuales con
las fechas del servidor: abre las PROGRAMADAS al llegar la fecha de inicio, cierra las vencidas
(finalizando sus intentos EN_CURSO con la lógica de las Fases 3/3B y ejecutando el análisis de
supervisión RF-50 si está activa) y **nunca toca las CANCELADAS**. El intervalo se cambia sin recompilar
al final de `{Catalina Base}/conf/catalina.properties`:
```properties
examlen.scheduler.virtual.intervalSeconds=30
```
(o la variable de entorno `EXAMENL_SCHEDULER_VIRTUAL_INTERVAL`). Las comprobaciones de fecha de cada
operación siguen activas aunque el programador se retrase: un estudiante no puede iniciar fuera de la
ventana ni aunque el estado aún no se haya sincronizado.

## Generación con IA en segundo plano (RNF-08, Fase 6)
Registrar una solicitud de IA responde de inmediato (menos de 2 s) y la generación continúa en un
ejecutor acotado de la aplicación. El tamaño del ejecutor se cambia sin recompilar al final de
`{Catalina Base}/conf/catalina.properties`:
```properties
examlen.ia.workers=2
examlen.ia.queueCapacity=50
```
(o las variables de entorno `EXAMENL_IA_WORKERS` y `EXAMENL_IA_QUEUE_CAPACITY`). Si la cola se llena, la
solicitud queda FALLIDA con un aviso de reintento (nunca se ejecuta en el hilo del navegador). Al reiniciar
Tomcat, las solicitudes PENDIENTE/EN_PROCESO del proceso anterior se marcan FALLIDA con un mensaje
controlado: un reintento crea una solicitud nueva.

## Proveedores de IA (RNF-12)
Los proveedores se descubren con `ServiceLoader` desde `META-INF/services/servicio.ProveedorIAFabrica`:
hoy están registrados Gemini y Groq, y la pantalla construye sus opciones desde ese registro. Añadir un
proveedor nuevo solo requiere implementar `servicio.ProveedorIAFabrica` y registrarlo (no se toca el
servlet, el servicio ni las plantillas). Cada proveedor declara su lista permitida de modelos; cualquier
modelo fuera de ella se rechaza en el servidor antes de llamar al proveedor. La lista se puede ampliar sin
recompilar en `{Catalina Base}/conf/catalina.properties`:
```properties
gemini.modelos=gemini-3.1-flash-lite
groq.modelos=llama-3.3-70b-versatile
```
(o las variables `EXAMENL_IA_MODELOS_GEMINI` y `EXAMENL_IA_MODELOS_GROQ`, separando varios modelos por
comas). La carga de documentos valida el contenido real (no solo la extensión) y aplica límites de
descompresión seguros al DOCX; si subes más de 10 MB verás un rechazo controlado con mensaje.

## HTTPS y cookies (RNF-03)
El proyecto se entrega con la cookie de sesión **Secure** (`web/WEB-INF/web.xml`) y con la cabecera
`Strict-Transport-Security: max-age=31536000` en todas las respuestas, como exige RNF-03 (HTTPS).
- **Desarrollo local sin HTTPS**: pon temporalmente `<secure>false</secure>` en `web/WEB-INF/web.xml`.
  Si lo dejas en `true` y entras por HTTP, el ingreso *parece* correcto pero la pantalla siguiente te
  devuelve al formulario, en bucle y sin ningún mensaje de error (el navegador no envía la cookie).
- `http://localhost` funciona igual con `secure=true` en navegadores modernos (localhost se trata como
  contexto seguro); el síntoma aparece al usar otros hosts por HTTP.

---

## Cómo instalar esta entrega

### Si ya tenías instalada la base
1. En NetBeans, borra las carpetas `src/java` y `web` de tu proyecto y pega las de este zip.
2. Ejecuta como root **solo** `web/WEB-INF/sql/migraciones/005_intento_unico.sql`
   (garantiza un intento por estudiante y evaluación virtual). Tus datos se conservan.
3. *Clean and Build* y *Run*.

### Si es la primera vez
1. En `web/WEB-INF/sql/migraciones/001_usuarios.sql` reemplaza la contraseña de ejemplo de `examlen_app` por una
   propia **antes** de ejecutarlo (ese archivo no contiene la contraseña real) y usa la misma en
   `examlen.db.password` de `catalina.properties`.
2. Ejecuta como root, en orden: `001_usuarios.sql`, `002_cursos_banco_preguntas.sql`,
   `003_alineacion_diagrama_v3.sql`, `004_evaluacion_pregunta_orden_puntaje.sql` y `005_intento_unico.sql`.
3. Copia `mysql-connector-j-9.x.jar` a la carpeta `lib/` de Tomcat (no dentro del proyecto).
4. En NetBeans: *Services → Servers → Apache Tomcat → Properties*, anota la carpeta *Catalina Base*
   y agrega al final de `conf/catalina.properties` las líneas de `config/catalina.properties.ejemplo`
   con tus valores (ver *Base de datos: credenciales fuera del repositorio*).
5. Crea el proyecto: *File → New Project → Java with Ant → Java Web → Web Application*, nombre `ExamLen`,
   Tomcat 10.0, **Jakarta EE 9 Web**. Reemplaza `src/java` y `web`. En *Properties → Sources* usa **UTF-8**.
6. Ejecuta `logica/GenerarHashContrasena.java` (Shift + F6), pega el hash en
   `sql/crear_administrador.sql` y ejecútalo como root.
7. *Run Project* y abre `http://localhost:8080/ExamLen/`.

---

## Pruebas de esta entrega

**Preparación**
1. Como administrador, registra un docente y copia su contraseña temporal.
2. Cierra sesión. Crea una cuenta de estudiante en *Crear cuenta*.

**Docente: cursos y grupos**
3. Inicia sesión como docente: te obliga a cambiar la contraseña temporal.
4. *Mis cursos* → crea "Inglés B1" con nivel B1.
5. En el curso, abre el grupo "Grupo A" con periodo 2027-1. Copia el código de acceso.
6. Intenta abrir otro "Grupo A" en 2027-1: debe rechazarlo. "Grupo B" en 2027-1 sí se permite.
7. Intenta desactivar el curso: debe pedir cerrar primero los grupos abiertos.

**Estudiante: inscripción**
8. En otra ventana privada, inicia sesión como estudiante → *Mis grupos*.
9. Escribe el código en minúsculas o con espacios: debe unirte igual.
10. Repite el mismo código: "Ya perteneces a este grupo".
11. Escribe un código inventado varias veces: tras 10 errores bloquea por 15 minutos.

**Docente: gestión del grupo**
12. En el grupo aparece el estudiante. Genera un código nuevo: el anterior deja de funcionar.
13. Quita al estudiante: desaparece (no tenía actividad). El estudiante puede volver a unirse con el código nuevo.
14. Cierra el grupo: el estudiante lo ve cerrado y no puede salir ni inscribirse. Reábrelo.

**Docente: banco de preguntas**
15. *Bancos de preguntas* → crea "Unit 3 – Past Simple".
16. Crea una pregunta de **selección múltiple**: la página indica qué falta para aprobar.
    Agrega 3 opciones, marca una correcta y apruébala.
17. Edita el enunciado de la pregunta aprobada: vuelve a **borrador**.
18. Crea una de **verdadero o falso**: ya trae las dos opciones; marca la correcta.
19. Crea una de **respuesta corta** con `went; has gone` como respuestas aceptadas y apruébala.
20. Archiva una pregunta: queda de solo lectura. Restáurala: vuelve a borrador.
21. Usa los filtros Borradores, Aprobadas y Archivadas.

**Docente: evaluaciones (Entrega 2)**
22. *Mis evaluaciones* → crea "Unit 3 Test". En la pantalla de detalle configura 2 preguntas por versión,
    2 versiones y 30 minutos.
23. Selecciona preguntas aprobadas de dos grupos distintos (mismo nivel/competencia/dificultad). Ajusta
    el orden de los grupos con ↑/↓ y asigna un puntaje por grupo (ej. 10 y 5).
24. Intenta marcar la evaluación como lista con un número de grupos distinto al configurado: debe rechazarlo.
25. Con los grupos correctos, márcala como lista. Luego edita la configuración: debe volver a **borrador**.
26. Genera las versiones: aparecen V1 y V2 con **preguntas distintas pero equivalentes** y el mismo puntaje total.
27. Descarga "PDF examen" (con espacio para nombre y respuestas) y "Clave" (marca la opción correcta).
28. Reordena las preguntas de una versión con ↑/↓ en "Preguntas".
29. Edita la evaluación y regenera: las versiones anteriores se reemplazan. Archívala: queda final.

**Evaluación virtual (Entrega 3)**
30. Como docente, en el grupo programa una evaluación virtual (evaluación en estado lista, ventana de
    inicio/fin). Si la evaluación no tenía versiones, se aplica `V1 (original)`.
31. Abre la evaluación virtual. Como estudiante, en *Evaluaciones* aparece disponible → **Iniciar**.
32. Responde: las opciones y respuestas cortas se guardan al instante; el temporizador no se pausa.
    Si sales de la pestaña, el docente lo verá en los eventos de supervisión.
33. Finaliza (o espera a que el tiempo llegue a 0: se envía solo). Si hay respuestas abiertas, la nota
    queda **pendiente de calificación**.
34. Como docente, abre el intento → califica la respuesta abierta (puntaje + comentario) → la nota se
    recalcula y el intento pasa a **Calificado**.
35. Verifica que no puedes presentar la misma evaluación dos veces (un solo intento).
36. Cierra la evaluación virtual con un intento en curso: se auto-finaliza.
37. Descarga el PDF del examen y la clave desde la evaluación (Entrega 2) para verificar la versión asignada.

**Material de práctica (Entrega 4)**
38. Como docente, *Material de práctica* → crea "Unit 3 Practice", agrega 2 preguntas aprobadas y ofrécelo
    a un curso. Intenta publicar sin preguntas: debe rechazarlo (agrega una primero).
39. Publícalo. Edítalo (quita una pregunta): vuelve a **borrador**; publícalo de nuevo.
40. Descarga **PDF** (hoja imprimible), **Clave** y **Word**.
41. Como estudiante, en *Práctica* aparece el material → responde (intentos ilimitados) → verás el
    resultado con retroalimentación inmediata.

**Generación con IA (Entrega 4)**
42. Crea `conf/ia.properties` en la Catalina Base con `gemini.apiKey=...` y/o `groq.apiKey=...`
    (o define las variables de entorno `GEMINI_API_KEY` / `GROQ_API_KEY`). Sin claves, la página avisa.
43. En *Generación IA* sube un documento PDF/DOCX/TXT (opcional) y crea una solicitud tipo
    **Preguntas** con un banco destino: verás el estado pasar a **Completada** y las preguntas en el
    banco en **borrador** con origen "Generado por IA".
44. Prueba tipo **Variantes** (banco con preguntas) y **Material de práctica** (crea material + banco).
45. Si la clave es inválida o no hay internet, la solicitud queda **Fallida** con el mensaje de error.

**Seguridad**
46. Como docente, cambia el `id` de la URL de un curso, evaluación, intento o material por uno ajeno: 404.
47. Como estudiante, abre `/ExamLen/app/docente/cursos`: debe responder 403; y el intento de otro estudiante: 404.
48. Detén MySQL y recarga cualquier página: 503.

---

## Arquitectura en tres capas

| Capa | Paquetes / carpetas |
|---|---|
| Presentación | `servlet` (servlets, filtros, motor de plantillas, etiquetas, arranque), `web/html`, `web/css` |
| Lógica de negocio | `logica` (clases del diagrama con solo atributos, enumeraciones, seguridad, validación) |
| Acceso a datos | `modelo` (DAO de cada clase con los métodos del diagrama; `DaoBase` centraliza), `conexion` (`Conexion`, JNDI) |

Las reglas del dominio viven en los DAO con los nombres exactos del diagrama: `PreguntaDAO.aprobarPregunta()`
valida, `GrupoDAO.cerrarGrupo()` controla el estado, `CursoDAO.desactivarCurso()` exige cerrar los grupos,
`AdministradorDAO.crearAdministrador()` construye la cuenta. Los DAO verifican permisos de propiedad
(`buscarPorIdDeDocente`, `esDeDocente`), aplican la regla y persisten.

El paquete `servicio` está reservado para la comunicación con APIs externas (Entrega 4).
La trazabilidad completa diagrama ↔ código está en [`docs/MATRIZ-TRAZABILIDAD.md`](docs/MATRIZ-TRAZABILIDAD.md).

## Decisiones de esta entrega

| Decisión | Motivo |
|---|---|
| Un curso por docente con muchos grupos | El curso no se recrea cada periodo; cada grupo tiene sus propios estudiantes |
| Código de 8 caracteres sin 0/O ni 1/I/L | Fácil de dictar; 31^8 combinaciones; intentos limitados |
| Quitar estudiante: elimina si no hay actividad, retira si la hay | Sin basura en los datos y sin perder historial |
| Editar una pregunta aprobada la devuelve a borrador | Nada sin revisar llega a una evaluación |
| Opciones y pregunta se guardan en una transacción | Nunca queda una pregunta a medio guardar |
| Recursos ajenos responden 404, no 403 | No se revela qué IDs existen |
| Filas HTML con plantillas `_fila-*.html` | Todo valor se escapa automáticamente |

## Seguridad
Hereda todos los controles de la base (PBKDF2, CSRF, CSP, sesiones, bloqueo por intentos, errores genéricos)
y agrega: verificación de propiedad en cada servicio, límite de códigos de acceso incorrectos, `DELETE`
concedido solo en tres tablas y mensajes de error de base de datos traducidos a mensajes de negocio.
