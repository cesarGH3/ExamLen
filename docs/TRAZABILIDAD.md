# Trazabilidad — Auditoría del estado actual de ExamLen

## 1. Fecha y alcance de la auditoría

- **Fecha**: 2026-09-28 (zona America/Bogota).
- **Alcance**: auditoría integral del código y la base de datos del proyecto ExamLen.
- **Fuente de verdad**: `docs/ERS-resumen.md` (63 RF, 18 RNF, 34 CU, diccionario de 28 tablas).
- **Método**: lectura del 100 % de los requisitos en la ERS; trazado de flujos (endpoint → servlet → DAO → tabla →
  plantilla); consultas de solo lectura a `information_schema`; compilación del proyecto; pruebas funcionales de
  runtime con datos temporales prefijados `AUDITORIA_20260928_` (creados y eliminados, verificado 0 restos);
  re-ejecución de la prueba de carga RNF-07/08. No se modificó funcionalidad, ni la BD, ni la ERS, ni migraciones.
- **Este documento sustituye cualquier trazabilidad anterior** y describe únicamente el estado comprobado hoy.
- **No se declara el 100 %**: el cumplimiento medido está en la sección 3.
- **Fase 11 de auditoría final (2026-09-29)**: auditoría integral desde cero con regresión
  FINAL11 (46/46), carga RNF-07 reconstruida (40 autosaves concurrentes: 40/40 HTTP 200,
  p95 = 347 ms, máx 362 ms, 40 respuestas persistidas), RNF-09 revalidado en Chrome 154 y
  Edge 154 a 360/768/1366 px (`scrollWidth = viewport`), RNF-08 muestreado (páginas 3–10 ms;
  PDFs reales de CU-23/24/33 verificados con texto extraído y paginación) y smoke de la
  herramienta de rendimiento (catálogo 34/34, CU-01/CU-16 OK, CU-23/24/33 devuelven %PDF real,
  limpieza PERF = 0). **RF-15 reauditado y cerrado (✅)**: grupo ABIERTO permite programar,
  CERRADO rechaza programar e inscribir, cerrar/reabrir funcionan y reabrir vuelve a permitir.
  Sin desviaciones internas nuevas que corregir; sin cambios de código en esta fase. RF-26 se corrige a ✅: su desviación histórica (reintento sin proveedor/bases) quedó cerrada en Fase 10 y se revalidó en FINAL11.
  Estado final: RF **62/63**, RNF **13/18**, CU **33/34**; pendientes por dependencias externas
  (SMTP real, TLS de despliegue, Firefox, prueba con usuario, VPS y 19 hashes legacy).
- **Fase 10 de correcciones (2026-09-29)**: cierre de usuarios, acceso, seguridad y CU residuales:
  login por **correo o identificación** con búsqueda controlada y mensaje específico de cuenta
  desactivada tras verificar credenciales (RF-01/CU-01 ✅); política de contraseña **exactamente ERS**
  (≥8, una letra y un número, sin símbolos obligatorios; RF-02 ✅); **BCrypt coste 12** para todo hash
  nuevo con migración automática del hash legacy PBKDF2 en el primer login correcto (RNF-01 ⚠️: 19
  hashes legacy reales de cuentas sin credencial conocida quedan pendientes de su próximo ingreso o
  reset); activar/desactivar **cualquier rol** con confirmación también para docentes y sin
  auto-desactivación (RF-09/CU-08 ✅); reset administrativo para cualquier rol con marcador genérico
  `usuario.debe_cambiar_contrasena` (migración idempotente **017**), limpieza de bloqueo/intentos,
  invalidación de tokens y cierre de sesiones (RF-10/CU-09 ✅); recuperación sin enumeración con
  límite 3/hora, token 256 bits con hash, 30 min, uso único y compensación del token si SMTP falla
  (RF-11/CU-10 ⚠️: código completo y probado, **entrega SMTP real no verificada** por no haber
  servidor de correo); **403 para recurso ajeno existente** en los servlets docente/estudiante con 404
  solo para inexistentes (RNF-02 ✅, S01–S10); sesión de examen sin expirar por la regla de 30 min
  (tiempo restante + 15 min de margen, restauración a 1800 s) y política **HTTPS configurable** con
  rechazo sin redirección, `X-Forwarded-Proto` solo tras proxy declarado, cookie Secure+HttpOnly+
  SameSite=Strict y HSTS únicamente sobre HTTPS (RNF-03 ⚠️: comportamiento probado con proxy
  simulado; TLS real de despliegue no configurado aquí); CU-11 permite desactivar el curso con
  advertencia de grupos abiertos (✅), CU-14 exige confirmación con datos reales revalidados (✅),
  CU-19 persiste y muestra **inválidas y no devueltas por separado** con reintento que conserva el
  contexto real (✅) y CU-27 incorpora cola offline de respuestas con aviso, reenvío al reconectar y
  limpieza (✅). RNF-09 ✅ con medición real en Chrome y Edge (360/768/1366 px, `scrollWidth =
  viewport`); RNF-11 ⚠️ (Chrome y Edge probados; Firefox no instalado); RNF-10 y RNF-16 siguen ❓.
  Totales: RF **61/63**, RNF **13/18**, CU **33/34**.
- **Fase 1 de correcciones (2026-09-28)**: se cerró la fuga de contenido no aprobado (RF-30 y regla de
  publicación de RF-57); ver las filas actualizadas y las secciones 9, 16 y 17. El resto de brechas sigue pendiente.
- **Fase 1B de correcciones (2026-09-28)**: toda modificación pedagógica de una pregunta APROBADA (enunciado,
  respuesta esperada u opciones) la devuelve a BORRADOR de forma atómica; refuerza RF-22 y la garantía de RF-30.
- **Fase 1C de correcciones (2026-09-28)**: marcar LISTA y generar versiones exigen que todas las preguntas
  seleccionadas estén APROBADAS en ese momento (validación bloqueada en la transacción); las versiones ya
  generadas siguen intactas.
- **Fase 2 de correcciones (2026-09-28)**: credenciales de BD externalizadas a `{Catalina Base}/conf/catalina.properties`
  (los descriptores usan `${examlen.db.url|user|password}` y no contienen secretos) y conexión efectiva con
  sesión MySQL `-05:00` verificada por JNDI; RNF-15 pasa a ✅.
- **Fase 3 de correcciones (2026-09-28)**: la calificación final del intento se normaliza a la escala 0,0–5,0
  ((puntos obtenidos × 5) / puntos máximos de la versión, con BigDecimal y una cifra decimal); migración 014
  alinea `intento.calificacion` con `DECIMAL(3,1)`; RF-53 pasa a ✅.
- **Fase 3B de correcciones (2026-09-29)**: toda terminación real de un intento (entrega, vencimiento, cierre
  o cancelación) fija `fecha_fin` con la hora del servidor, sin sobrescribirla después
  (`COALESCE(fecha_fin, LEAST(NOW(), inicio+duración, fin de la virtual))`); recalificar no la altera.
- **Fase 4 de correcciones (2026-09-29)**: programador de la aplicación (un hilo, intervalo configurable,
  por defecto 30 s) que abre y cierra evaluaciones virtuales por fechas sin intervención humana, finaliza
  intentos EN_CURSO y ejecuta RF-50 también en el cierre automático; RF-42 y RF-50 pasan a ✅.
- **Fase 4B de correcciones (2026-09-29)**: el mismo programador finaliza también los intentos cuyo
  **límite individual** vence con la virtual aún ABIERTA; toda escritura de respuesta se valida de forma
  atómica contra el límite efectivo (sin la gracia de 10 s); `fecha_fin` = límite exacto; RF-47 pasa a ✅.
  `ServicioEvaluacionVirtual` se movió a la capa `servicio`.
- **Fase 5 de correcciones (2026-09-29)**: módulo de evaluaciones y versiones cerrado de extremo a extremo:
  alta con la primera pregunta y mínimo 1 (RF-31), configuración 1–10 versiones y LISTA con "cuántas faltan"
  (RF-32), selección con "Quitar", filtros y resumen por tema/competencia/dificultad (RF-33), generación solo
  con LISTA (RF-34), versiones equivalentes por construcción (misma cantidad, distribución y puntaje total)
  con rotación aleatoria (RF-35), sin borrado físico y códigos continuados (RF-36), comparativa que resalta
  cualquier diferencia (RF-37), edición bloqueada en solo lectura (RF-38), PDF de examen y clave con curso
  de contexto y ZIP de todas las versiones (RF-39/40, RNF-17). RF-31…RF-37, RF-39 y RNF-17 pasan a ✅
  (42/63 y 8/18) y CU-20…CU-24 pasan a ✅ (17/34).
- **Fase 5B de correcciones (2026-09-29)**: se eliminaron los límites no definidos en RF-32 (máximo 100 de
  preguntas por versión, mínimo 5 y máximo 300 de duración y mínimo artificial de 3 en el título); quedan
  P ≥ 1, V 1–10, duración ≥ 1 y `seleccionadas ≥ P`, idénticos en servidor y HTML. 22/22 comprobaciones
  nuevas; RF-32 y CU-20 confirmados ✅.
- **Fase 5C de correcciones (2026-09-29)**: alineación exacta con el ERS de `evaluacion.titulo`
  (VARCHAR(150) NOT NULL, servidor y HTML con máximo 150) y `evaluacion.descripcion` (TEXT NULL, sin límite
  artificial, acepta vacía y >500): migración idempotente **015** (los máximos 200/500 eran desviaciones del
  esquema, ya corregidas), 17/17 comprobaciones (casos A–H); RF-31, RF-32 y CU-20 continúan ✅.
- **Fase 9 de correcciones (2026-09-29)**: cierre funcional de virtuales, supervisión, calificación,
  resultados y práctica: programación con **versiones habilitadas y supervisión configurable** (RF-41/CU-25),
  cancelación con **doble confirmación** y terminal (CU-26), cola **offline** de eventos con
  offline/online y RECONEXION sin duplicados cercanos (RF-49/CU-28), normalización **con tildes** en
  respuesta corta (RF-51), listado con "No presentó" (RF-54), **feedback inmediato por pregunta** con
  persistencia por respuesta y repeticiones **barajadas** (RF-60/61/CU-34), material sin preguntas
  bloqueado por flujo (RF-57), **PDF de práctica para el estudiante** con/sin respuestas y sección final
  (RF-63/CU-33) y compatibilidad del editor de versiones con enunciados de 5000 caracteres y 7 opciones.
  RF-41, 49, 51, 57, 60, 61 y 63 pasan a ✅ (57/63) y CU-25, 26, 28, 32, 33 y 34 a ✅ (26/34 conservador:
  CU-19 se devuelve a ⚠️ tras la reevaluación de 5b porque su mensaje mezcla inválidas con no devueltas).
- **Fase 8 de correcciones (2026-09-29)**: banco de preguntas cerrado: conteo por estado en CU-15,
  búsqueda por texto y filtros combinables (banco/tipo/nivel/dificultad/competencia/tema/estado/origen)
  en CU-15/CU-16 con página transversal nueva y reutilización en CU-20 (prepared statement dinámico,
  única consulta), edición de texto de opciones con UI (RF-20) y sin máximo artificial de 6, V/F
  revalidado en servidor como exactamente 2 con una correcta, eliminación con comprobación explícita de
  referencias (versión/evaluación/material/práctica) y archivado controlado sin HTTP 500 (RF-21),
  reevaluación de RF-23 (excluida de selección, materiales y bases de variantes; visible en gestión;
  historial intacto; restaurar → BORRADOR), `pregunta.tema` a VARCHAR(120) NOT NULL vía migración 016 y
  enunciado sin máximo académico de 4000 (TEXT). RF-20/21/23/24 pasan a ✅ (50/63) y CU-15/CU-16 a ✅
  (21/34).
- **Fase 7 de correcciones (2026-09-29)**: módulo IA cerrado funcionalmente: carga de documentos con
  validación de contenido real (no solo extensión), límites anti zip-bomb del DOCX, eliminación segura de
  documentos referenciados con carrera protegida (RF-25, CU-18); generación con contrato completo
  (validación por tipo antes de persistir, V/F con una sola correcta, `id_solicitud_generacion` en preguntas
  y material, metadatos de docente/base), CU-19 5b con válidas/descartadas informadas, variantes con
  selección explícita y metadatos copiados de la base (RF-27) y material con dos modos y exclusión de
  repetidas (RF-28); registro de proveedores por **ServiceLoader** con lista permitida de modelos, UI sin
  opciones cableadas y prueba real de un tercer proveedor añadido solo por implementación+registro
  (RNF-12), más HTTP sin `HttpClient` estático (sin hilos del classloader). RF-25…RF-28, CU-18/19 y
  RNF-12 pasan a ✅ (46/63, 10/18, 19/34).
- **Fase 6 de correcciones (2026-09-29)**: generación con IA **asíncrona real** (RNF-08): el POST valida,
  inserta la solicitud PENDIENTE, entrega un comando inmutable a `servicio.ServicioGeneracionIA` y responde
  en menos de 2 s (medido con proveedor simulado de 5–30 s); el trabajo corre en un `ThreadPoolExecutor`
  acotado y configurable (`examlen.ia.workers`, `examlen.ia.queueCapacity`), sin transacción abierta durante
  la llamada externa, con saturación controlada, recuperación de abandonadas al arranque y parada limpia en
  el undeploy; RNF-08 pasa a ✅ (9/18) y RF-29/RF-30/RNF-06/CU-19 se revalidan sin cambiar de estado
  (CU-19 sigue ⚠️ por las desviaciones de RF-26/27/28, ahora explícitamente fuera de esta fase).

## 2. Inventario técnico actual (recalculado en esta auditoría)

| Elemento | Valor verificado |
|---|---|
| JDK / servidor | Eclipse Adoptium 17.0.20.101 / Apache Tomcat 10.0.23 (Jakarta EE 10) |
| Base de datos | MySQL 8.4.9, base `examlen`, usuario de aplicación `examlen_app` |
| Librerías | mysql-connector-j 9.6.0, OpenPDF 1.3.43, PDFBox 2.0.31, Gson 2.11.0, jakarta.mail 2.0.1, jbcrypt 0.4 (BCrypt), Apache POI 5.2.5 + xmlbeans/commons (herramienta temporal de rendimiento) |
| Archivos Java | **168** (`conexion` 1, `logica` 56, `modelo` 29, `servicio` 22, `servlet` 57, `org.apache.commons.logging` 2) |
| Clases compiladas | **221** (compilación verificada en Fase 10, exit 0) |
| Vistas | **38 pantallas** + **89 fragmentos** `_*.html` (127 HTML) |
| Estilos | `base.css` + **8 componentes** + **37 hojas de pantalla** (46 CSS) |
| JavaScript | **2 archivos** (`web/js/examen.js` con cola offline de respuestas y eventos; `web/js/rendimiento.js`, herramienta temporal) |
| Migraciones | **17** (`sql/migraciones/001…017`, todas aplicadas; 017 en Fase 10) + `sql/crear_administrador.sql` |
| Tablas | **28** (coinciden con el diccionario) |
| Claves foráneas | **47** (información en la sección 7) |
| Datos reales | 20 usuarios, 5 cursos, 3 grupos, 7 bancos, 24 preguntas, 4 evaluaciones, 4 versiones, 2 materiales, 0 intentos |

## 3. Resumen de cumplimiento

### Requisitos funcionales (RF-01 → RF-63)

| Estado | Cantidad |
|---|---|
| ✅ COMPLETO | **62 / 63** |
| ⚠️ PARCIAL | **1 / 63** (RF-11 por entrega SMTP real no verificada) |
| ❌ AUSENTE | **0 / 63** |
| ❓ NO VERIFICABLE | **0 / 63** |

**CUMPLIMIENTO FUNCIONAL = 62 / 63**

### Requisitos no funcionales (RNF-01 → RNF-18)

| Estado | Cantidad |
|---|---|
| ✅ COMPLETO | **13 / 18** (RNF-02, 04, 05, 06, 07, 08, 09, 12, 13, 14, 15, 17, 18) |
| ⚠️ PARCIAL | **3 / 18** (RNF-01 por 19 hashes legacy reales pendientes de migración; RNF-03 por TLS real de despliegue no configurado; RNF-11 por Firefox no instalado) |
| ❌ AUSENTE | **0 / 18** |
| ❓ NO VERIFICABLE | **2 / 18** (RNF-10 sin prueba con usuario; RNF-16 sin VPS) |

**CUMPLIMIENTO NO FUNCIONAL = 13 / 18**

### Casos de uso (CU-01 → CU-34)

| Estado | Cantidad |
|---|---|
| ✅ COMPLETO y verificable | **33 / 34** (CU-01, 02, 03, 04, 05, 06, 07, 08, 09, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34) |
| ⚠️ PARCIAL | **1 / 34** (CU-10: código completo; entrega SMTP real no verificada) |
| ❌ NO IMPLEMENTADO | **0 / 34** |
| ❓ NO VERIFICABLE | **0 / 34** |

## 4. Matriz RF-01 → RF-63

Columnas: **ID · Nombre · Estado** (✅/⚠️/❌/❓) · **Exigencia ERS** (resumen fiel) · **Implementación actual** ·
**Evidencia** (archivo:línea / Clase.metodo / endpoint / tabla) · **Verificación** (CÓDIGO / BASE DE DATOS / RUNTIME /
CÓDIGO + RUNTIME / DOCUMENTAL / NO VERIFICADO) · **Falta / desviación** · **Severidad** · **Dependencias**.

| ID | Nombre | Estado | Exigencia ERS | Implementación actual | Evidencia | Verificación | Falta / desviación | Severidad | Dependencias |
|---|---|---|---|---|---|---|---|---|---|
| RF-01 | Autenticar usuario | ✅ | Login con **correo o identificación**; cuenta inactiva no entra; cierre de sesión | Login con `acceso` (correo o identificación) mediante búsqueda controlada con prepared statements; mensaje genérico para credenciales incorrectas y mensaje específico "La cuenta está desactivada. Contacte al administrador." solo con contraseña correcta; bloqueo 5/15 min; rehash BCrypt del hash legacy en login correcto; sesión nueva al autenticar; logout por POST con CSRF | `LoginServlet.java`; `UsuarioDAO.buscarPorCorreoOIdentificacion`; `UsuarioDAO.rehashSiLegacy`; `login.html`; **RUNTIME**: F01/F02/F03/F04/F05 y F14/F15 de Fase 10 | CÓDIGO + RUNTIME | — | — | RF-03, RNF-02, RNF-03 |
| RF-02 | Cambiar contraseña | ✅ | Mínimo **8 con letras y números**, distinta de la actual | Política única `PoliticaContrasena`: 8–128, al menos una letra y un número; sin mayúscula/símbolo obligatorios; "distinta de la actual" en el cambio autenticado; aplicada en registro, cambio, restablecer por token y validada por las temporales de reset; centralizada y sin reglas divergentes | `PoliticaContrasena.java`; `CambiarContrasenaServlet.java`; `RegistroServlet.java`; `RestablecerContrasenaServlet.java`; **RUNTIME**: F06–F10, F40 | CÓDIGO + RUNTIME | — | — | RF-03, RF-05, RF-11, RNF-01 |
| RF-03 | Exigir cambio inicial del docente | ✅ | Obligar al docente a cambiar la temporal en su primer ingreso | `debe_cambiar_contrasena=TRUE` al registrar; login redirige; filtro bloquea el resto de `/app/*`; al cambiar se marca FALSE | `DocenteDAO.java:42-45`; `LoginServlet.java:67-68`; `AutenticacionFiltro.java:68-71`; `UsuarioDAO.java:95-98` | CÓDIGO | — | — | RF-02, RF-07 |
| RF-04 | Actualizar datos personales | ✅ | Actualizar nombre y correo; correo único | Perfil edita nombre y correo con validación de duplicado | `PerfilServlet.java:21-45`; `UsuarioDAO.java:108-123` | CÓDIGO | Tipos de columna menores (sección 7) | baja | CU-03 |
| RF-05 | Registrar estudiante | ✅ | Identificación, nombre, correo, institución, nivel y contraseña; sin duplicados | Registro público transaccional (usuario + estudiante) con consentimiento | `RegistroServlet.java:35-80`; `EstudianteDAO.java:21-58` | CÓDIGO + RUNTIME (high de hoy y auditorías previas) | Hereda política estricta de RF-02 | baja | RF-02, RNF-13 |
| RF-06 | Gestionar administradores | ✅ | Crear, actualizar y eliminar; no borrar al último activo ni a sí mismo | CRUD con confirmación; bloqueos de último activo y auto-eliminación/desactivación | `AdminAdministradoresServlet.java:78-190`; `AdministradorDAO.java:21-109` | CÓDIGO | `sql/crear_administrador.sql` obsoleto (ver sección 9) | baja | RF-09, RF-10 |
| RF-07 | Registrar docente | ✅ | Solo el administrador; contraseña temporal visible una vez; `debeCambiar=true` | Alta con temporal de 16 caracteres (SecureRandom), mostrada por flash una vez | `AdminDocentesServlet.java:84-91`; `DocenteDAO.java:20-54`; `GeneradorContrasenaTemporal.java:11-35` | CÓDIGO | — | — | RF-03 |
| RF-08 | Gestionar estudiantes | ✅ | Consultar, actualizar nivel y eliminar; con intentos no se elimina | Listado con búsqueda; nivel editable; eliminación con confirmación y conteo de intentos (eval. y práctica) | `AdminEstudiantesServlet.java:68-142`; `EstudianteDAO.java:60-100` | CÓDIGO | Búsqueda limitada a 100 resultados (no exigido) | — | RF-09, RNF-14 |
| RF-09 | Activar y desactivar cuentas | ✅ | Activar/desactivar cualquier cuenta; desactivar no borra historial; CU-08 pide confirmación | Tres roles: docentes (con confirmación previa vía `confirmarDesactivar`), estudiantes y administradores; auto-desactivación bloqueada; activación directa; efecto inmediato por recarga en el filtro; el servidor revalida confirmación (`confirmado=1`) y rol | `AdminEstadoDocenteServlet.java`; `AdminDocentesServlet.java`; `AdminEstudiantesServlet.java`; `AdminAdministradoresServlet.java`; `AutenticacionFiltro.java:54-59`; **RUNTIME**: F16–F22 | CÓDIGO + RUNTIME | — | — | RF-01 |
| RF-10 | Restablecer contraseña | ✅ | Temporal visible una vez y **cambio obligatorio en el siguiente ingreso**; respaldo de RF-11 | Temporal visible una sola vez por flash para docentes, estudiantes y administradores; marcador genérico `usuario.debe_cambiar_contrasena` (migración 017, idempotente) con enforcement en login y filtro; limpia `intentos_fallidos`/`bloqueado_hasta`; invalida tokens de recuperación y cierra sesiones abiertas del afectado | `UsuarioDAO.restablecerContrasenaUsuario`; `TokenRecuperacionDAO.invalidarTokensDeUsuario`; `SesionesActivas.cerrarSesionesDe`; tres servlets `restablecer`; **RUNTIME**: F23–F30 | CÓDIGO + RUNTIME | — | — | RF-02, RF-03, RF-11 |
| RF-11 | Recuperar contraseña olvidada | ⚠️ | Token de un solo uso, 30 min, invalida anteriores, mensaje genérico; excepciones 2a (3/hora) y 5a (fallo SMTP) | Token 256 bits SecureRandom, hash SHA-256 en BD, 30 min, uso único (FOR UPDATE), invalida anteriores, mensaje único no enumerable que orienta reintentar o pedir el reset administrativo; límite 3/hora sin revelar; si SMTP falla se compensa invalidando el token recién creado y se registra sin contraseña/token; cierra sesiones y limpia bloqueo al consumir | `TokenRecuperacion.java`; `TokenRecuperacionDAO.java`; `RecuperarContrasenaServlet.java`; `RestablecerContrasenaServlet.java`; **RUNTIME**: F31–F40 | CÓDIGO + BD + RUNTIME; **entrega SMTP real NO VERIFICADA** (sin servidor de correo) | Solo la entrega real por un SMTP configurado | media | RF-02, RF-10, RNF-04 |
| RF-12 | Gestionar cursos | ✅ | Crear/actualizar nombre, descripción y nivel; activar/desactivar; curso inactivo no abre grupos | CRUD con nivel inmutable tras crear; grupo nuevo bloqueado si el curso está inactivo | `DocenteCursosServlet.java:50-77`; `DocenteCursoServlet.java:80-110`; `GrupoDAO.abrirGrupo:30-32` | CÓDIGO | CU-11 5a: el sistema **impide** desactivar con grupos abiertos en vez de advertir | media (CU-11) | RF-13 |
| RF-13 | Abrir grupos | ✅ | Nombre único por curso+periodo; salida ABIERTO con código | Valida periodo `^\d{4}-[1-4]$`, nombre y unicidad (app + UNIQUE BD) | `DocenteCursoServlet.java:111-134`; `Validador.java:17-18,79-89`; `002_...sql:40` | CÓDIGO + BD | Carrera app/BD: duplicado puede terminar en mensaje genérico | baja | RF-14 |
| RF-14 | Generar código de acceso | ✅ | Único, regenerable; alfanumérico 6–8 | 8 caracteres de alfabeto sin ambiguos, `SecureRandom`, UNIQUE en BD, regeneración por UPDATE | `GeneradorCodigoAcceso.java:13-41`; `GrupoDAO.java:36-76`; `002_...sql:39` | CÓDIGO + BD | `regenerarCodigo` no valida estado ABIERTO en servidor (solo UI) | baja | RF-13, RF-16 |
| RF-15 | Cerrar y reabrir grupos | ✅ | Grupo cerrado **no admite inscripciones ni nuevas evaluaciones virtuales** | Cerrar/reabrir OK; grupo ABIERTO permite programar; CERRADO rechaza programar (`Solo se puede programar en un grupo abierto.`) y rechaza inscripciones (servlet y `crearInscripcion`); reabrir vuelve a permitir programar; runtime FINAL11 RF15-A/B/C/D/E | `DocenteVirtualServlet.java`; `InscripcionDAO.crearInscripcion`; `GrupoDAO.cerrarGrupo/reabrirGrupo`; `EstudianteGruposServlet` | CÓDIGO + RUNTIME | — (desviación histórica cerrada en Fase 9 y revalidada en Fase 11) | — | RF-41 |
| RF-16 | Inscribirse en un grupo | ✅ | Código, grupo ABIERTO, sin duplicados | Normalización y formato; grupo ABIERTO; diferenciación RETIRADA; UNIQUE | `EstudianteGruposServlet.java:84-118`; `InscripcionDAO.java:25-53`; `002_...sql:57` | CÓDIGO + RUNTIME (altas de hoy) | CU-14 paso 4: sin pantalla de confirmación con curso/grupo/periodo/docente | baja (CU-14) | RF-14, RF-17 |
| RF-17 | Gestionar inscripciones | ✅ | Consultar, retirar, reincorporar, eliminar; con intentos no se elimina, se retira | Listado activos/retirados; DELETE con fallback automático a RETIRADA si hay intentos (FK) | `DocenteGrupoServlet.java:35-53,135-163`; `InscripcionDAO.java:55-80`; `DaoBase.java:158-160` | CÓDIGO | Regla añadida no pedida: no opera en grupo cerrado; UI no distingue Eliminar/Retirar de antemano | baja | RF-15, RF-44 |
| RF-18 | Gestionar bancos | ✅ | Crear/renombrar/describir; pertenece a un docente | CRUD con pertenencia por `id_docente` y UNIQUE por docente | `DocenteBancosServlet.java:28-68`; `BancoPreguntasDAO.java:17-64`; `002_...sql:66-76` | CÓDIGO | Tipos de columna menores | baja | RF-19 |
| RF-19 | Crear preguntas | ✅ | Enunciado, tipo, nivel, dificultad, competencia, tema y respuesta esperada; BORRADOR/MANUAL; esperada obligatoria en corta | Alta en BORRADOR/MANUAL; VF autoinserta Verdadero/Falso; respuesta esperada en segundo paso; obligatoriedad validada al aprobar; `tema` hasta 120 (migración 016) y enunciado TEXT sin máximo académico (solo técnico 65535) | `DocenteBancoServlet`; `PreguntaDAO`; `sql/migraciones/016_pregunta_tema_120.sql` | CÓDIGO + **RUNTIME (Fase 8, 2026-09-29)**: tema 120 permitido / 121 rechazado; enunciado de 5000 caracteres aceptado | CU-16 describe captura en un solo paso (desviación de flujo, no funcional) | baja | RF-20, RF-22 |
| RF-20 | Gestionar opciones de respuesta | ✅ | Agregar, **editar**, eliminar y marcar correcta; SM mínimo 2 y una correcta; VF exactamente 2 | Agregar/editar texto/eliminar/marcar correcta con UI; cada mutación y la regla APROBADA→BORRADOR en una transacción; sin máximo artificial de opciones (solo mínimo 2 + una correcta); VF revalidado en servidor como exactamente 2 con una correcta al aprobar | `DocentePreguntaServlet` (`actualizarOpcion`); `OpcionRespuestaDAO.actualizarOpcionRespuesta`; `PreguntaDAO.faltantesPregunta` | CÓDIGO + **RUNTIME (Fase 8)**: edición de opción (13), APROBADA→BORRADOR atómico al editar opción (14), rollback con fallo inducido (15), SM 2+1 y 7+1 aprobables (9/10), 1 opción y 2 correctas rechazadas (11/12), VF 3 opciones rechazado (17) | — | — | RF-19, RF-22 |
| RF-21 | Actualizar y eliminar preguntas | ✅ | Pregunta usada en versión/material **solo se archiva**; editar no altera versiones | `eliminarSiNoUsada` comprueba referencias (versión, evaluación, material, respuesta de práctica) con la fila bloqueada y DELETE en la misma transacción; si está en uso devuelve controlado y la UI sugiere archivarla; editar no toca `version_pregunta` | `PreguntaDAO.eliminarSiNoUsada/tieneReferencias`; `DocentePreguntaServlet.eliminar` | CÓDIGO + **RUNTIME (Fase 8)**: no usada → DELETE real (20); usada → rechazo controlado sin 500 (21/22); versión histórica intacta al editar el banco (43) | — | — | RF-22, RF-23, RNF-14 |
| RF-22 | Validar y aprobar preguntas | ✅ | Validar completitud y aprobar; solo APROBADAS usables; APROBADA equivale al contenido validado | `faltantesPregunta` por tipo (SM ≥2 y 1 correcta; **VF exactamente 2** y una correcta; corta con esperada; abierta libre); solo APROBADAS seleccionables; cualquier cambio pedagógico posterior (datos, respuesta esperada u opciones) devuelve la pregunta a BORRADOR en la misma transacción | `PreguntaDAO.exigirEditable/desaprobarSiAprobada/definirRespuestaEsperada/faltantesPregunta`; `OpcionRespuestaDAO`; `DocentePreguntaServlet.mensaje` | CÓDIGO + **RUNTIME (Fase 1B, 2026-09-28)**: 31/31. **Fase 1C (2026-09-28)**: 23/23. **Fase 8 (2026-09-29)**: VF 3 opciones ya no puede aprobarse (17) y SM de 7 opciones sí (10) | — | — | RF-19, RF-20 |
| RF-23 | Archivar preguntas | ✅ | Archivada no aparece en selección; conserva historial | Archivar/restaurar (restaurar → BORRADOR, sin aprobación automática); excluida de selección de evaluaciones, materiales y **bases de variantes IA** (Fase 7); visible en gestión con el filtro ARCHIVADA; versiones/intentos históricos intactos | `DocentePreguntaServlet.archivar/restaurar`; `PreguntaDAO.buscarDeDocente`; `DocenteBancoServlet.criteriosDe`; `PreguntaDAO.listarDeDocente` | CÓDIGO + **RUNTIME (Fase 8)**: archivada fuera de evaluación (24), material (25) y variantes (26); visible con filtro (27); restaurar deja BORRADOR (28); archivar una usada conserva la copia histórica (23) | — | — | RF-21, RF-24 |
| RF-24 | Buscar y filtrar preguntas | ✅ | Texto y filtros: banco, tipo, nivel, dificultad, competencia, tema, estado y origen | Búsqueda por texto (enunciado o tema, parcial e insensible a mayúsculas, comodines escapados) y los 8 filtros combinables en UNA consulta preparada (`buscarDeDocente`); disponible en CU-15/CU-16 (banco y página transversal `docente/preguntas`) y reutilizada por CU-20 con estado fijo APROBADA | `PreguntaDAO.buscarDeDocente`; `logica/CriteriosBusquedaPregunta`; `DocenteBancoServlet`; `DocentePreguntasServlet`; `DocenteEvaluacionServlet.construirDisponibles` | CÓDIGO + **RUNTIME (Fase 8)**: casos 29–38 (texto y cada filtro, todos combinados), 39 (banco ajeno → 0), 44 (inyección `%', _, --` neutra), 45 (300+ preguntas <2 s) | — | — | RF-18, CU-15, CU-20 |
| RF-25 | Cargar documento fuente | ✅ | PDF/DOCX/TXT, máx 10 MB, aislamiento por docente | Multipart con rechazo controlado >10 MB (mensaje propio, sin 500); validación de **contenido**: cabecera PDF + carga PDFBox, DOCX como ZIP OOXML con `[Content_Types].xml` y `word/document.xml` y límites anti zip-bomb (512 entradas, 20 MB/entrada, 40 MB total), TXT UTF-8 estricto sin binarios; UUID + directorio externo; propiedad verificada; eliminación segura (fila primero y solo si no está referenciado; archivo después) | `DocenteIAServlet.subirDocumento/eliminarDocumento`; `servicio.ValidadorDocumento`; `DocumentoFuenteDAO.eliminarSiNoReferenciado`; `CsrfFiltro` (413 si excede maxRequestSize) | CÓDIGO + **RUNTIME (Fase 7, 2026-09-29)**: 13/13 (PDF/DOCX/TXT válidos; >10 MB, .pdf falso, DOCX malformado y zip-bomb rechazados sin fila ni archivo; borrado referenciado rechazado; carrera con 25 iteraciones sin huérfanas) | `HttpJson` usa `HttpURLConnection`; el shim propio `org.apache.commons.logging` cubre la dependencia de PDFBox sin añadir jar externo | baja | CU-18, RF-29 |
| RF-26 | Generar preguntas con IA | ✅ | Instrucciones, cantidad (máx 30), nivel, documento opcional, banco destino; BORRADOR/GENERADO_IA; CU-19 incluye tema y competencia | Campos tema/competencia obligatorios (PREGUNTAS) y aplicados a lo creado; nivel del formulario; cantidad 1–30 (servidor y HTML); banco propio; contrato validado **por tipo antes de persistir** (SM 2–6 opciones y exactamente una correcta; VF exactamente Verdadero/Falso con una correcta; corta con respuesta esperada; abierta opcional); `id_solicitud_generacion` en cada pregunta; metadatos del docente mandan (nivel/tema/competencia) | `DocenteIAServlet.generar`; `servicio.ValidadorPreguntasGeneradas`; `modelo.PersistenciaGeneracionDAO`; `docente-ia.html` | CÓDIGO + **RUNTIME (Fase 7, 2026-09-29, proveedor simulado + prueba real Gemini)**: 20/20 (cantidad 30/31, metadatos, SM/VF/corta/abierta, 8 válidas+2 inválidas, 0 válidas→FALLIDA, modelos permitidos/arbitrarios, trazabilidad) | — (reintento con contexto completo desde Fase 10: proveedor, tema, competencia, referencia y bases; runtime F52/F53 y FINAL11 L2) | — | RF-29, RF-30, RNF-06, RNF-08 |
| RF-27 | Generar variantes | ✅ | Variantes que **conserven contenido, competencia, tipo y dificultad**; no reemplazan base | El docente **selecciona explícitamente** una o varias bases (solo de sus bancos, no archivadas); la cantidad es TOTAL y se reparte round-robin; cada variante copia tipo/nivel/dificultad/competencia/tema de su base (los del modelo se ignoran); objetivo completo por tipo (SM/VF con opciones y una correcta); base intacta | `DocenteIAServlet.generar` (bases + validación de propiedad); `ServicioGeneracionIA.generarVariantes/repartir`; `docente-ia.html` (multiselección + cantidad total) | CÓDIGO + **RUNTIME (Fase 7, 2026-09-29)**: 10/10 (2 bases→4 variantes 2+2, metadatos de la base, VF objetivo, base ajena/mezclada rechazada, bases intactas tras generar) | Un fallo por base cuenta como descartado (no aborta la solicitud completa) | baja | RF-26, RF-30 |
| RF-28 | Generar material de práctica con IA | ✅ | Material desde evaluación de referencia **o** tema/nivel/competencia, **sin repetir** preguntas de la evaluación | Dos modos validados en servidor: evaluación propia (con preguntas) o tema/nivel/competencia; el prompt pide no copiar la referencia y el servidor **descarta** las idénticas (normalización trim/espacios/case-insensitive) contándolas en CU-19 5b; material + preguntas + asociaciones en una transacción, BORRADOR/GENERADO_IA con `id_solicitud_generacion` | `DocenteIAServlet.generar` (modo A/B); `ServicioGeneracionIA.generarMaterial`; `ValidadorPreguntasGeneradas.normalizarEnunciado`; `PersistenciaGeneracionDAO` | CÓDIGO + **RUNTIME (Fase 7, 2026-09-29)**: 11/11 (evaluación propia→material con 1 descartada por repetida, ajena rechazada, modo tema funciona, trazabilidad y no publicación) | — | — | RF-26, RF-30, RF-57 |
| RF-29 | Seguir estado de solicitudes | ✅ | Estado PENDIENTE/EN_PROCESO/COMPLETADA/FALLIDA, modelo y error; reintento creando nueva | Estados completos y ahora **observables de verdad** (PENDIENTE→EN_PROCESO→COMPLETADA/FALLIDA en segundo plano); el modelo efectivo se resuelve y persiste **antes** de responder y el worker usa ese modelo; FALLIDA con mensaje; reintento crea solicitud nueva (la fallida no se toca) | `SolicitudGeneracionDAO` (`reclamar/completar/marcarFallida/marcarAbandonadas`); `DocenteIAServlet`; `FabricaProveedores.modeloEfectivo`; `ServicioGeneracionIA` | CÓDIGO + **RUNTIME (Fase 6, 2026-09-29)**: transición observada, modelo `gemini-3.1-flash-lite` en el listado, mensajes de error sin secretos, reintento A→B (A queda FALLIDA). **Fase 7 (2026-09-29)**: botón "Reintentar" que precarga los datos persistidos (tipo, cantidad, nivel, instrucciones, modelo, documento y banco) y avisa lo que no se conserva (proveedor/bases); la solicitud fallida no cambia | Sin navegación directa a lo generado (sin cambio) | baja | RNF-08 |
| RF-30 | Revisión obligatoria del contenido IA | ✅ | Nada GENERADO_IA llega a estudiantes sin aprobación | Preguntas/material nacen BORRADOR; publicar exige que **todas** las preguntas estén APROBADAS y el estudiante solo recibe materiales íntegros (los inconsistentes se ocultan, se bloquean y se registran en log del servidor) | `MaterialPracticaDAO.publicarMaterialPractica:61`, `esIntegro:174`, `contarPreguntasNoAprobadas:163`; `EstudiantePracticaServlet.materialNoDisponible:124` (guardas en `:53,:107`); `DocentePracticaServlet.avisoPublicacion:260` | CÓDIGO + **RUNTIME (Fase 1, 2026-09-28)**: 29/29 comprobaciones con datos `FASE1_RF30_`; publicación rechazada con BORRADOR/ARCHIVADA y acceso del estudiante bloqueado sin fuga. **Fase 1B (2026-09-28)**: modificar opciones o respuesta esperada de una pregunta en un material publicado la devuelve a BORRADOR y el material se bloquea y recupera solo (casos 9-10) | — | — | RF-26, RF-57, RF-58, RF-59 |
| RF-31 | Crear evaluación | ✅ | Guardar **al seleccionar la primera pregunta**; multiplicidad 1..* | El formulario de alta exige título, configuración (P, V, duración) y la **primera pregunta APROBADA** (propia); `crearEvaluacionConPregunta` inserta evaluación + `evaluacion_pregunta` (orden 1, puntaje 0) en una transacción; quitar la última pregunta se rechaza ("La evaluación debe conservar al menos una pregunta.") | `DocenteEvaluacionesServlet.java`; `EvaluacionDAO.crearEvaluacionConPregunta`; `DocenteEvaluacionServlet` (guardas `quitarPregunta`/`marcarLista`) | CÓDIGO + **RUNTIME (Fase 5, 2026-09-29)**: CASOS 1–3, 6–13 y 21 (creación válida persistida 3/4/30 + primera pregunta; seis altas inválidas rechazadas sin persistir; no se puede quitar la última) | `evaluacion_pregunta` guarda orden/puntaje por fila (columnas no exigidas por el diccionario) | baja | RF-33 |
| RF-32 | Configurar evaluación | ✅ | Preguntas por versión, **versiones 1–10**, duración; seleccionadas ≥ por versión | Configura P (≥ 1, sin máximo artificial), V (**1–10**, servidor y UI) y duración (≥ 1 min, sin máximo artificial); marcar LISTA revalida y exige `seleccionadas ≥ P` con "Faltan N preguntas para completar la evaluación." | `DocenteEvaluacionServlet` (`configurar`, `marcarLista`); `DocenteEvaluacionesServlet`; `docente-evaluacion.html` | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 7–9, 22, 23, 25; **Fase 5B (2026-09-29)**: 22/22 (P=1 y P=101 aceptados; duración 1 y 301 aceptadas; V=0/11 rechazados y no persisten; seleccionadas < P con "Faltan N"; HTML sin `max` en P/duración y 1–10 en versiones); **Fase 5C (2026-09-29)**: 17/17 (título 150/151, descripción vacía/500/1000, flujo normal y tipos ERS) | El puntaje por grupo permite 0–1000 (fuera del diccionario, decisión previa) | baja | RF-33, RF-34 |
| RF-33 | Seleccionar preguntas | ✅ | Seleccionar **y quitar** aprobadas; resumen por tema, competencia y dificultad | Solo APROBADAS propias; "Quitar" por pregunta en UI; resumen por tema, competencia y dificultad de lo seleccionado; filtros por tema/nivel/dificultad/competencia (alcance CU-20; RF-24 global sigue ⚠️); la integridad se revalida al marcar LISTA (Fase 1C) | `DocenteEvaluacionServlet` (`construirDisponibles`, `resumenDistribucion`, `opcionesFiltroTema`); `_fila-disponible.html`; `_pregunta-quitar.html`; `docente-evaluacion.html` | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 14–21 (filtros por tema/nivel/dificultad/competencia; quitar; quitar última rechazado; duplicada/borrador rechazadas) | Búsqueda por texto y filtros de otras pantallas quedan para RF-24 | baja | RF-24, RF-31 |
| RF-34 | Marcar LISTA y archivar | ✅ | Solo LISTA genera versiones definitivas y se programa; archivar | `exigirLista` bloquea la fila de la evaluación (FOR UPDATE) dentro de la transacción de generación: sin LISTA no hay versiones (ni con rollback); marcar LISTA exige aprobadas + P cubierto; archivar bloquea edición | `VersionEvaluacionDAO.exigirLista`; `DocenteEvaluacionServlet.marcarLista/archivar`; `DocenteVirtualServlet:75-77` | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 26, 27, 40 (LISTA ok; generar en BORRADOR rechazado; archivada no se edita) | — | — | RF-35 |
| RF-35 | Generar versiones | ✅ | Versiones A,B,C…; aleatorizar preguntas y opciones; misma distribución de competencias/dificultad; **puntaje total igual** | Perfil canónico de P slots (clave `tema\|competencia\|dificultad\|puntaje`; muestreo sistemático `i*N/P` al ordenar el conjunto); cada versión toma un representante distinto por slot con rotación `(desplazamiento aleatorio + versión + ocurrencia) % pool` y `Collections.shuffle` de preguntas y opciones; copias independientes y orden congelado; igualdad de cantidad/distribución/puntaje **garantizada por construcción** | `VersionEvaluacionDAO.construirPerfil/crearVersion`; **detalle en sección 11** | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 28–36 y 41–43 (4 versiones A–D con 3 preguntas 1/1/1 y puntaje 6 cada una; sin repetidas intraversión; las 4 preguntas de cada tipo rotan; regeneración E–H con 50 preguntas en 0,08 s; "No hay suficientes preguntas equivalentes" es defensa inalcanzable por el muestreo) | Si el pool de un tipo es menor que las versiones, la misma pregunta se repite entre versiones (avisado en pantalla) | baja | RF-32, RF-34, RF-36 |
| RF-36 | Reutilizar para nuevas versiones | ✅ | Nuevas versiones **conservando anteriores**, códigos no repetidos | INSERT transaccional; códigos A…Z/AA… continuando el mayor (no se reutilizan porque **se eliminó el borrado físico**: no hay acción de eliminar versión); UNIQUE (evaluación, código); anteriores intactas | `VersionEvaluacionDAO.generarVersionEvaluacion/ultimoCodigo`; `003_...sql:101` | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 28, 35, 36 (A–D y luego E–H sin tocar las previas) | — | — | RF-35, RNF-14 |
| RF-37 | Comparar versiones | ✅ | Cuadro con cantidad, distribución por tema/competencia/dificultad y puntaje total; **resaltar diferencias** | Tabla con la distribución más frecuente como referencia; se resalta **cualquier** celda que difiera (sin umbral ni mínimo de versiones); el tema/competencia/dificultad se leen del banco por `id_pregunta` (las copias conservan el vínculo y la pregunta aprobada no cambia) | `DocenteEvaluacionServlet.construirComparativa`; `VersionEvaluacionDAO.listarDistribucionPorVersion`; `_tabla-comparativa.html`; `docente-evaluacion.css` | CÓDIGO + **RUNTIME (Fase 5)**: CASO 37 (8 versiones equivalentes → ninguna celda resaltada) | — | — | RF-35 |
| RF-38 | Editar versión | ✅ | Editar enunciado, respuesta, orden, puntaje y opciones **sin tocar el banco**; no editar con intentos | Servlets y DAOs solo tocan `version_pregunta`/`version_opcion`; bloqueos: intentos>0, virtual ABIERTA o CERRADA; con bloqueo la pantalla se muestra **en solo lectura** (opciones y puntajes visibles) y el POST sigue rechazado; en el detalle de la evaluación se ven `#`, enunciado y puntaje de cada copia | `DocenteVersionPreguntaServlet.mostrar/seccionDatosLectura/seccionOpcionesLectura`; `_version-pregunta-datos-lectura.html`; `DocenteEvaluacionServlet.construirDetalleVersion` | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 44–45 (versión con intentos en solo lectura; POST no modifica) | — | — | RF-35, RNF-14 |
| RF-39 | Descargar versión en PDF | ✅ | PDF con encabezado **con curso, título, versión** y datos del estudiante; sin respuestas | PDF carta con encabezado `título · Curso · Versión X · Docente` y "Página N"; línea de estudiante con Nombre/Identificación/Fecha; el curso es contexto de impresión elegido por el docente (no se persiste); descarga individual y "Todas (ZIP)" | `DocenteVersionPdfServlet`; `VersionEvaluacionDAO.construirPDF(version, clave, curso)`; `PdfFormato.java` | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 46–53, 60–64 (PDF con curso y sin curso válidos; ZIP de 8 y de 10 versiones; entradas correctas y sin vacías) | El texto interno de los PDF comprimidos no se inspecciona carácter a carácter (se validó estructura, encabezado generado y tamaño) | baja | RNF-17, CU-23 |
| RF-40 | Descargar clave de respuestas | ✅ | PDF separado con respuestas y puntaje; solo el propietario | Clave individual y "Todas las claves (ZIP)"; marca correctas y respuesta esperada con puntaje; autorización por dueño (404 si no es suya); errores controlados con aviso (sin archivo parcial) | `DocenteVersionPdfServlet.generarVersion/generarTodas`; `VersionEvaluacionDAO.generarClaveRespuestasPDFVersionEvaluacion` | CÓDIGO + **RUNTIME (Fase 5)**: CASOS 47–49, 55–59 (clave distinta del examen; ZIP de claves; 404 versión ajena; 400 destino inválido; error controlado sin versiones) | — | — | RF-39 |
| RF-41 | Programar evaluación virtual | ✅ | Evaluación LISTA, grupo, fechas, **versiones a usar y supervisión sí/no**; fin>inicio; grupo ABIERTO | Valida en servidor: grupo propio y **ABIERTO**, evaluación propia y **LISTA** con versiones, selección ≥1 de versiones **de esa evaluación**, fin>inicio; persiste `supervision_activa` tal cual y asocia SOLO las versiones elegidas | `DocenteVirtualServlet.programar/idsVersionesSeleccionadas`; `EvaluacionVirtualDAO.programarEvaluacionVirtual(..., supervision)`; `docente-grupo.html` (checkboxes de versiones + casilla de supervisión) | CÓDIGO + **RUNTIME (Fase 9, 2026-09-29)**: V1 grupo cerrado, V2 BORRADOR, V3 sin versiones, V5 versión ajena y V8 fechas rechazados; V4 asocia solo B,D; V6/V7 supervisión false/true persistidos | — | — | RF-15, RF-34 |
| RF-42 | Abrir, cerrar y cancelar (automático y manual) | ✅ | **Apertura y cierre automáticos por fechas** + manuales; al cerrar finaliza EN_CURSO; cancelada no se reabre | Programador de la aplicación (hilo único, intervalo configurable, por defecto 30 s) que abre PROGRAMADAS en ventana, cierra vencidas (PROGRAMADA o ABIERTA) finalizando intentos EN_CURSO con la lógica de Fases 3/3B y ejecuta RF-50; transiciones condicionales en BD (idempotentes); manuales conservados (abrir/cerrar/cancelar); CANCELADA nunca vuelve a otro estado | `ProgramadorEvaluacionesVirtuales.java`; `ServicioEvaluacionVirtual.java`; `EvaluacionVirtualDAO.abrirProgramada/reclamarCierre/reclamarCancelacion`; `DocenteVirtualServlet.java` | CÓDIGO + **RUNTIME (Fase 4, 2026-09-29)**: 24/24 comprobaciones (20 casos) con intervalo de prueba 2 s; reinicios, concurrencia e idempotencia verificados | Cancelar sin doble confirmación (CU-26 3a) y cerrar no valida estado previo (menores, sin cambio) | baja | RF-47, RF-50 |
| RF-43 | Consultar evaluaciones disponibles | ✅ | Listar virtuales de grupos con inscripción activa con estado y fechas; solo inicia ABIERTA | Lista con curso/grupo/ventana/duración/estado; enlace Iniciar solo si `disponibleAhora`; oculta canceladas sin intento | `EstudianteEvaluacionesServlet.java:25-82`; `_fila-evaluacion-estudiante.html` | CÓDIGO | — | — | RF-41, RF-42 |
| RF-44 | Iniciar intento y asignar versión | ✅ | Intento EN_CURSO con versión equilibrada; un único intento | Elige la versión con menos usos; unicidad por UQ de BD; retoma EN_CURSO | `EstudianteInicioIntentoServlet.java:87-124`; `IntentoDAO.java:30-46,136-143`; `005_intento_unico.sql` | CÓDIGO + RUNTIME (2 intentos en versiones distintas) | — | — | RF-43 |
| RF-45 | Responder y guardar automáticamente | ✅ | Guardar al instante; modificar mientras EN_CURSO; continuar tras reconexión | Radios al `change`, textarea con debounce 1,2 s; upsert por pregunta; recarga de previas al volver | `examen.js:52-69`; `EstudianteIntentoServlet.java:223-279`; `RespuestaDAO.java:28-50` | CÓDIGO + RUNTIME (autoguardado y persistencia de marcada al volver) | Reintento offline no existe (RNF-05/CU-27 5a) | — | RF-44 |
| RF-46 | Ocultar retroalimentación durante el examen | ✅ | Nada de correctas/puntajes mientras EN_CURSO; visible con CALIFICADO y virtual CERRADA | Examen sin correctas ni puntaje; resultado "Pendiente de calificación" si ABIERTA o no CALIFICADO; cancelada sin puntajes | `EstudianteIntentoServlet.java:151-187`; `EstudianteEvaluacionesServlet.java:51-53`; `_resultado-pendiente.html` | CÓDIGO + RUNTIME (nota oculta antes de calificar) | — | — | RF-42, RF-53, CU-31 |
| RF-47 | Controlar el tiempo del intento | ✅ | Mostrar restante y finalizar al vencer duración o fecha fin (lo primero); hora del servidor | Límite canónico `LEAST(fecha_fin virtual, inicio+duración)` en `IntentoDAO.LIMITE_EFECTIVO` (una sola fórmula); el programador finaliza los intentos vencidos aunque la virtual siga ABIERTA; **toda escritura pasa por `RespuestaDAO.guardarRespuestaVigente`**, que bloquea la fila del intento y exige `estado=EN_CURSO AND límite > NOW()` (sin gracia); al volver, la primera petición sincroniza; `fecha_fin` = límite exacto | `IntentoDAO.LIMITE_EFECTIVO/listarVencidos/reclamarVencido`; `RespuestaDAO.guardarRespuestaVigente`; `servicio.ServicioEvaluacionVirtual.finalizarIntentoVencido`; `examen.js` (contador + bloqueo al 409) | CÓDIGO + **RUNTIME (Fase 4B, 2026-09-29)**: 24/24 comprobaciones (22 casos) con intervalo 2 s; escritura a T+1 s y T+1 min rechazada sin fila; carrera guardado/expiración sin escrituras tardías; reinicio finaliza vencidos | — | — | RF-42, RNF-15 |
| RF-48 | Finalizar intento | ✅ | Enviar antes del límite advirtiendo si hay sin responder; no reabrible | Guarda, cuenta faltantes y muestra confirmación con **números**; confirmar finaliza; si el tiempo venció, directo; **toda terminación fija `fecha_fin`** (entrega = momento de entrega; vencimiento = límite efectivo; Fase 3B) | `EstudianteIntentoServlet.java:60-80,296-316`; `IntentoDAO.finalizarIntento`/`CIERRE_FECHA_FIN`; `confirmar.html` | CÓDIGO + RUNTIME (mensaje "sin responder: 2"; Fase 3B: 17/17 casos con `fecha_fin` coherente) | — | — | RF-45, RF-47 |
| RF-49 | Registrar eventos de supervisión | ✅ | Con supervisión activa, registrar con hora del servidor los 5 tipos; aviso previo | JS con **cola offline en localStorage** por intento (`examlen.eventos.<id>`): encola cada evento, reintenta con cronómetro corto y solo borra lo confirmado (204); al volver `online` reenvía pendientes y registra RECONEXION; eventos `offline`/`online` nativos del navegador (detección real de desconexión, no solo `beforeunload`); servlet valida dueño, EN_CURSO y supervisión, hora del servidor y **suprime duplicados cercanos** (mismo tipo+descripción ≤3 s) | `examen.js` (cola+flush+offline/online); `EstudianteEventoServlet`; `EventoSupervisionDAO.existeSimilarReciente`; `EstudianteInicioIntentoServlet` (aviso) | CÓDIGO + **RUNTIME (Fase 9)**: V13 no supervisada → 0 eventos; V14 3 tipos; V16 pendiente reenviado + RECONEXION y duplicado cercano suprimido; V17 hora servidor ≤60 s; cola localStorage verificada (V15) | La deduplicación es por ventana de 3 s (sin esquema adicional, documentado) | baja | RF-41, RNF-18 |
| RF-50 | Detectar tiempos atípicos y similitud | ✅ | Al **cerrarse** la virtual supervisada: duración < 25 % mediana y similitud ≥ 0,85 (umbrales configurables) | Al cerrar (manual **o automático**, Fase 4): mediana de duraciones y umbral configurable; Jaccard de trigramas con exclusión de respuestas correctas y mínimo 40 caracteres; un evento por par espejado; idempotente (una sola ejecución efectiva aunque el programador y el docente coincidan) | `ServicioEvaluacionVirtual.analizarSupervision`; `AnalisisSupervision.java`; `ConfiguracionSupervision.java` | CÓDIGO + RUNTIME (eventos 90 % espejados; Fase 3B: duraciones 1/10/10 min; **Fase 4**: cierre automático genera TIEMPO_ATIPICO y SIMILITUD sin duplicados al reprocesar) | — | — | RF-42, RF-55, RNF-18 |
| RF-51 | Calificar automáticamente objetivas | ✅ | SM, V/F y corta al finalizar; corta "ignorando mayúsculas, **tildes** y espacios sobrantes" | Califica opciones (práctica) y corta con **normalización Unicode NFD** que elimina marcas combinantes, recorta y colapsa espacios y compara en minúsculas estables (RF-51 literal); abiertas sin autocalificar | `RespuestaPracticaDAO.evaluar/normalizar`; `RespuestaDAO` (intentos virtuales) | CÓDIGO + **RUNTIME (Fase 9)**: harness "CAFE"≡"Café", "ARBOL"≡"árbol", "  Past   Simple "≡"past simple"; flujo real con "  CAFE " → Correcta (P15) | — | — | RF-52, RF-53 |
| RF-52 | Calificar manualmente abiertas | ✅ | Puntaje y comentario a abiertas; ajustar automática; tope = puntaje de la pregunta en la versión | Formulario por respuesta (Recalificar si ya tiene puntaje); tope contra `version_pregunta.puntaje`; recalcula la **nota normalizada** o marca CALIFICADO (Fase 3) | `DocenteIntentoServlet.java:60-101,104-141`; `RespuestaDAO.java:80-93`; `IntentoDAO.recalcularCalificacion`; `_form-calificar.html` | CÓDIGO + RUNTIME (abierta 6 y 7; tope; Fase 3: 10/30→1,7, 17/30→2,8, 20/30→3,3) | — | — | RF-51, RF-53 |
| RF-53 | Calcular calificación del intento | ✅ | Suma de puntajes; CALIFICADO cuando todas tienen puntaje; sin responder = 0; **salida 0,0–5,0** | `calificacion = (SUM(respuesta.puntaje) × 5) / SUM(version_pregunta.puntaje)`, con `BigDecimal` y HALF_UP a 1 cifra; CALIFICADO por `contarPendientes==0`; máximo 0 u obtenidos>máximo → sin nota falsa, FINALIZADO y log (Fase 3) | `IntentoDAO.recalcularCalificacion`, `puntosDeIntento`; `logica.Calificacion`; `_resultado-examen.html` | CÓDIGO + **RUNTIME (Fase 3, 2026-09-28)**: 24/24 comprobaciones (30/30→5,0; 13/30→2,2; 18/30→3,0; 21/30→3,5; 0/30→0,0; 20/30→3,3; decimales→5,0; máximo 0 e inconsistencia controlados). **Fase 3B (2026-09-29)**: sin regresión (5,0 / 1,7 / 4,0 / 4,5 tras los cambios de fecha_fin) | — | — | RF-51, RF-52 |
| RF-54 | Consultar intentos de la virtual | ✅ | Estudiante, versión, inicio, fin, tiempo, estado, nota e indicadores; incluye no presentados | Tabla con todos los campos y fila "**No presentó**" para inscritos activos sin intento; nota en escala 0,0–5,0 y "Pendiente" mientras no esté CALIFICADO | `DocenteVirtualServlet.construirFilasIntentos`; `_fila-intento-docente.html` | CÓDIGO + RUNTIME (Fase 3/3B) + **Fase 9**: V30 todos los campos, V31 "No presentó" | — | — | RF-44, RF-55 |
| RF-55 | Revisar indicadores de supervisión | ✅ | Por intento, agrupados por tipo y con línea de tiempo; nota de no-fraude | Resumen por tipo con conteo + línea de tiempo + nota literal siempre; descripciones de similitud citan al otro intento y la pregunta | `DocenteIntentoServlet.java:53-54,140-168`; `EventoSupervisionDAO.java:42-64`; `docente-intento.html` | CÓDIGO + RUNTIME (nota, resumen y línea de tiempo) | — | — | RF-50, RNF-18 |
| RF-56 | Consultar resultados propios | ✅ | Calificación y comentarios de intentos calificados; sin eventos ni datos de otros | Detalle por pregunta (respuesta, puntaje, comentario) visible con CALIFICADO+CERRADA; **nota final "X,X de 5,0"** y listado "Nota: X,X / 5,0"; pendiente/cancelada según estado; sin correctas | `EstudianteIntentoServlet.mostrarResultado`; `EstudianteEvaluacionesServlet`; `_resultado-examen.html` | CÓDIGO + RUNTIME (Fase 3: páginas con "5,0" y "Pendiente de calificación"; recarga persistente) | — | — | RF-46, RF-53 |
| RF-57 | Crear material de práctica | ✅ | Título, descripción y **nivel**; preguntas aprobadas; BORRADOR/MANUAL; **al publicar todas aprobadas**; multiplicidad 1..* | Crea con nivel (obligatorio), BORRADOR/MANUAL; asociar/quitar preguntas (solo APROBADAS) y cursos; publicar exige **≥1 pregunta y todas APROBADAS** en una transacción; el flujo normal ya no deja materiales sin preguntas: **quitar la última se rechaza** ("El material debe conservar al menos una pregunta") y publicar sin preguntas se rechaza; material publicado no íntegro queda bloqueado para estudiantes | `DocentePracticaServlet` (`quitarPregunta`, `publicar`); `MaterialPracticaDAO.publicarMaterialPractica/listarPublicadosDeEstudiante` | CÓDIGO + **RUNTIME (Fase 9)**: P1, P2, P3, P4 (no quitar la última), P5 (BORRADOR asociada rechaza y la señala), P6 (todas aprobadas publica), P34 (GENERADO_IA revisada y publicada) | — | — | RF-30, RF-58 |
| RF-58 | Publicar y archivar material | ✅ | Asignar a cursos, publicar y archivar; archivado no visible y conserva intentos | Curso(s) del docente; publicar/archivar; listado y acceso exigen PUBLICADO; no borra | `DocentePracticaServlet.java:101-131`; `MaterialPracticaDAO.java:64-66,164-192`; `IntentoPracticaDAO.java:127-138` | CÓDIGO | Sin desarchivar (no exigido); publicar sin cursos permitido (inocuo) | baja | RF-12, RF-57 |
| RF-59 | Consultar material disponible | ✅ | Publicados en cursos de inscripción activa, **filtrables por nivel** | SQL con inscripción ACTIVA + PUBLICADO + filtro por nivel; excluye materiales publicados no íntegros (sin preguntas aprobadas o con preguntas en otro estado); formulario GET; columnas con nivel | `MaterialPracticaDAO.listarPublicadosDeEstudiante:218`; `EstudiantePracticaServlet.java:204`; `estudiante-practicas.html:26-35` | CÓDIGO + RUNTIME (filtro visible y material listado; Fase 1: inconsistencias ocultas) | — | — | RF-16, RF-58 |
| RF-60 | Resolver con retroalimentación inmediata | ✅ | **Por cada respuesta**: correcta/incorrecta, respuesta correcta y explicación; abiertas con esperada y **sin** autocalificar | Flujo por pregunta: cada respuesta se guarda y persiste sola y la página muestra el feedback **de inmediato** (correcta/incorrecta, respuesta correcta/esperada y "Repasa: tema (competencia)"); abiertas "Sin calificar (respuesta abierta)" con la esperada como referencia; al terminar calcula el resultado | `EstudiantePracticaServlet.mostrarIntento/feedbackDe/respuestaDe`; `_pregunta-practica.html`; `RespuestaPracticaDAO.evaluar` | CÓDIGO + **RUNTIME (Fase 9)**: P13 correcta, P14 incorrecta con correcta+Repasa, P15 corta normalizada, P16 abierta sin autocalificar con referencia, P17 persistida antes de terminar | La "explicación" es la guía por tema/competencia (el modelo no tiene campo de explicación) | baja | RF-19, RF-59 |
| RF-61 | Calcular resultado y repetir | ✅ | Porcentaje de aciertos; repetir ilimitado; **repeticiones en orden aleatorio** | Cada práctica abre un intento propio; el orden de preguntas se **baraja con `Collections.shuffle(preguntas, new Random(idIntento))`** (estable por intento, distinto entre intentos); porcentaje sobre calificables (NULL si ninguna) al terminar; intentos ilimitados e independientes | `EstudiantePracticaServlet.mostrarIntento`; `IntentoPracticaDAO.crearIntentoEnCurso/finalizarIntento` | CÓDIGO + **RUNTIME (Fase 9)**: P18 75 % (4 de 5 calificables), P19 segundo intento, P20 órdenes distintos entre dos intentos, P21 intentos independientes | El seed es el id del intento (no hay columna de orden; sin esquema adicional) | baja | RF-60 |
| RF-62 | Consultar historial de práctica | ✅ | Intentos con fecha y resultado; errores por competencia y tema | Historial con fecha/material/resultado y dos tablas (competencia y tema: respondidas/correctas/%) excluyendo no calificadas; enlace en menú | `EstudianteHistorialPracticaServlet.java`; `IntentoPracticaDAO.java:127-138`; `RespuestaPracticaDAO.java:108-133`; `estudiante-historial-practica.html` | CÓDIGO + RUNTIME (2 intentos, resúmenes) | — | — | RF-60, RF-61 |
| RF-63 | Descargar material en PDF | ✅ | PDF imprimible con **sección final opcional de respuestas**; actores docente y estudiante; estudiante solo publicados de sus cursos | Docente: PDF (con/sin respuestas) y clave; estudiante: nuevo servlet que solo permite materiales **PUBLICADOS de cursos con inscripción activa** y ofrece `respuestas=0/1`; el modo con respuestas añade una **sección final "Respuestas correctas / esperadas"** al PDF (nuevo generador) además de la clave existente; BORRADOR/ARCHIVADO/ajenos → 404 | `EstudiantePracticaPdfServlet`; `DocentePracticaPdfServlet`; `MaterialPracticaDAO.generarPDFMaterialPracticaConRespuestas/construirPDF` | CÓDIGO + **RUNTIME (Fase 9)**: P26/P27 docente sin/con respuestas; P28/P29 estudiante sin/con respuestas; P30 BORRADOR 404; P31 ARCHIVADO 404; P32 curso ajeno 404; P33 IA/BORRADOR protegido | El "Word" sigue siendo HTML con extensión .doc (deuda menor documentada) | baja | RF-57, RF-58, CU-33 |

## 5. Matriz RNF-01 → RNF-18

| ID | Nombre | Estado | Exigencia ERS | Implementación actual | Evidencia | Verificación | Falta / desviación | Severidad | Dependencias |
|---|---|---|---|---|---|---|---|---|---|
| RNF-01 | Almacenamiento seguro de contraseñas | ⚠️ | **BCrypt costo ≥10 o Argon2id**; sin texto plano | **BCrypt coste 12** (jbcrypt) para todo hash nuevo (registro, cambio, altas, reset, token); verificación de hashes legacy PBKDF2 y **rehash automático a BCrypt en el login correcto** de forma atómica; nunca texto plano en BD ni logs | `HashContrasena.java`; `UsuarioDAO.rehashSiLegacy`; `LoginServlet`; **RUNTIME**: F11/F12/F13/F14/F15 | CÓDIGO + BD + RUNTIME | Quedan **19 hashes legacy reales** de cuentas baseline sin credencial conocida: se migrarán en su próximo login correcto (o con reset administrativo); no se inventan contraseñas | media | RF-02, RF-05, RF-10 |
| RNF-02 | Control de acceso por usuario | ✅ | Rol y propiedad validados en servidor; **acceso ajeno → 403** | Rol por prefijo → 403; recurso existente de otro usuario → **403** (`OperacionNoPermitidaException`), inexistente → 404, en curso/banco/pregunta/evaluación/versión/virtual/intento/material/solicitud/documento para docente y estudiante; sin 200 con mensaje de error | `ServletBase.verificarPropiedad`; servlets docente/estudiante; `ErrorServlet` (403); **RUNTIME**: S01–S10 | CÓDIGO + RUNTIME | — | — | RF-01, RNF-03 |
| RNF-03 | HTTPS, cookies y sesión | ⚠️ | HTTPS; cookie Secure/HttpOnly; sesión 30 min **salvo durante un intento en curso** | `session-timeout 30`; cookie Secure+HttpOnly+SameSite=Strict (verificados); **sesión de examen extendida** a restante+15 min (mín 30 min, tope 24 h) al iniciar/retomar y restaurada a 1800 s al salir; política `EXAMLEN_HTTPS_REQUIRED` que rechaza HTTP sin redirigir (403), `X-Forwarded-Proto` aceptado solo con `EXAMLEN_BEHIND_PROXY=true`, HSTS únicamente sobre HTTPS | `web.xml`; `HeadersSeguridadFiltro.java`; `EstudianteInicioIntentoServlet.sesionDeExamen/sesionNormal`; `EstudianteIntentoServlet`; **RUNTIME**: S14 (HTTP local OK), S15 (403 sin redirección), S16 (XFP tras proxy), S17–S19 (cookies/HSTS) | CÓDIGO + RUNTIME (proxy simulado) | TLS real de despliegue no configurado en este entorno (ngrok/VPS); expiración temporal real de 30 min no esperada en prueba | media | RF-45 |
| RNF-04 | Seguridad del token de recuperación | ✅ | Token ≥128 bits, hash, 30 min, un solo uso, ≤3/hora, respuesta que no revele el correo | 256 bits SecureRandom, solo hash SHA-256 en BD, 30 min, uso único transaccional `FOR UPDATE`, token nuevo invalida anteriores, límite 3/hora sobre `token_recuperacion`, misma respuesta externa exista o no el correo (también con SMTP caído y con límite superado) | `TokenRecuperacion.java`; `TokenRecuperacionDAO.java`; `RecuperarContrasenaServlet.java`; **RUNTIME**: F31–F39 | CÓDIGO + BD + RUNTIME | — | — | RF-11 |
| RNF-05 | Tolerancia a desconexiones | ✅ | Ninguna respuesta **confirmada** por el servidor se pierde | Cada guardado se persiste antes de responder; al volver, el intento EN_CURSO se retoma con las respuestas guardadas | `EstudianteIntentoServlet.java:223-279`; `EstudianteInicioIntentoServlet.java:43-48`; `examen.js:36-69` | CÓDIGO + RUNTIME (retoma y marcada) | Sin reintento/aviso para respuestas no confirmadas (CU-27 5a) | — | RF-45 |
| RNF-06 | Independencia del servicio de IA | ✅ | Con IA deshabilitada, todo excepto CU-19 funciona | La IA solo se usa en `DocenteIAServlet`/`ProcesadorGeneracionIA`; sin claves la página responde 200 con aviso y la solicitud termina FALLIDA en segundo plano sin afectar al resto | `DocenteIAServlet` (doGet/doPost); `ProcesadorGeneracionIA`; grep sin `ConfiguracionIA` fuera de IA | CÓDIGO + **RUNTIME (Fase 6, 2026-09-29)**: sin `ia.properties`, el POST responde <2 s, la solicitud termina FALLIDA con mensaje de clave y cursos/evaluaciones siguen en 200 (CASO 13) | — | — |
| RNF-07 | Concurrencia en evaluaciones | ✅ | 40 concurrentes; **p95 de guardar < 2 s** | Prueba de carga re-ejecutada hoy: 400 guardados, p95 **197,6 ms**, 0 errores | `docs/pruebas/rendimiento-carga.ps1`; `resultados-20260928-211444.json` | RUNTIME | — (pool `maxTotal=20` sin tocar) | — | RF-45 |
| RNF-08 | Tiempo de respuesta general | ✅ | Consultas < 3 s; PDF 50 preguntas < 10 s; **IA asíncrona** | Páginas p95 15,4 ms y PDF 41–58 ms (runtime); **IA asíncrona real (Fase 6)**: el POST responde <2 s (0,02–0,3 s medidos con proveedor simulado de 5–30 s), el trabajo corre en `ServicioGeneracionIA` (pool acotado), sin transacción durante la llamada externa y con estados observables PENDIENTE→EN_PROCESO→COMPLETADA/FALLIDA | `resultados-20260928-211444.json`; **sección 10**; `servicio/ServicioGeneracionIA.java`; `servlet/ProcesadorGeneracionIA.java`; `fase6-evidencias/` | CÓDIGO + **RUNTIME (Fase 6, 2026-09-29)**: 20 casos HTTP + harness (doble encolado, sin conexiones activas durante el sleep, ciclo iniciar/detener sin hilos residuales) | — | — | RF-26, CU-19 |
| RNF-09 | Diseño adaptable | ✅ | Funciones del estudiante completables desde 360 px sin desplazamiento horizontal | Breakpoint 360/520 que envuelve celdas y acciones (sin `nowrap` en celdas de acción/insignias) y medición real en **Chrome 154 y Edge 154** a 360×800, 768×1024 y 1366×768: `document.documentElement.scrollWidth = viewport` en grupos, evaluaciones, práctica e historial (pantallas de intento/resultado con revisión estática) | `base.css` (`@media max-width: 520px`); `web/js` de medición temporal; **RUNTIME**: S20 | CÓDIGO + RUNTIME (navegadores reales) | Intento/resultado verificados estáticamente (sin intento real en la prueba de viewport) | baja | — |
| RNF-10 | Facilidad de uso | ❓ | Docente sin capacitación crea evaluación de 10 preguntas y 2 versiones en <20 min | Interfaz en español, flujo guiado; **sin prueba con usuario** | — | NO VERIFICADO (no se realizó prueba con docente) | Sin evidencia de prueba de usuario (no se inventa tiempo) | media | — |
| RNF-11 | Compatibilidad con navegadores | ⚠️ | Chrome/Firefox/Edge (2 últimas); supervisión con APIs estándar | **Chrome 154 y Edge 154 probados realmente** (login + 4 vistas de estudiante a 360/768/1366, sin errores); `examen.js` usa Page Visibility, focus/blur, online/offline, localStorage y fetch keepalive estándar | `examen.js`; **RUNTIME**: medición S20 en ambos navegadores | CÓDIGO + RUNTIME (2 de 3 navegadores) | **Firefox no instalado**: componente NO VERIFICADO; supervisión no ejercitada en navegador real | media | RF-49 |
| RNF-12 | Arquitectura mantenible | ✅ | HTML/CSS sin frameworks; Java Servlets por capas; **proveedor IA intercambiable con solo una implementación nueva** | Capas respetadas; sin frameworks; los proveedores se descubren con **`ServiceLoader`** (`ProveedorIAFabrica` en `META-INF/services`): el servlet, el servicio y la pantalla consultan solo `RegistroProveedoresIA` (opciones de proveedor/modelo construidas del registro, sin `GEMINI/GROQ` cableados); lista permitida de modelos por proveedor; HTTP con `HttpURLConnection` (sin hilos del classloader) | `servicio/ProveedorIAFabrica`, `RegistroProveedoresIA`, `FabricaGemini`, `FabricaGroq`; `src/java/META-INF/services/servicio.ProveedorIAFabrica`; `DocenteIAServlet` | CÓDIGO + **RUNTIME (Fase 7, 2026-09-29)**: un tercer proveedor (`PRUEBA`) se añadió SOLO por implementación + registro en un jar de pruebas, apareció en la UI y funcionó; al retirarlo, producción volvió a GEMINI/GROQ sin tocar servlet ni plantillas | El shim `org.apache.commons.logging` cubre a PDFBox sin jar externo | baja | RF-26 |
| RNF-13 | Protección de datos personales | ✅ | Ley 1581; consentimiento en registro; ningún estudiante accede a datos de otros | Casilla obligatoria y fecha en `estudiante.aceptacion_tratamiento_datos`; política en `/politica-datos` (borrador con responsable en blanco); eventos solo visibles al docente | `registrarse.html:75-81`; `RegistroServlet.java:57-59`; `EstudianteDAO.java:32-50`; `012_...sql:15` | CÓDIGO + RUNTIME (registro con consentimiento en auditorías) | Columna fuera del diccionario; política borrador | baja | RF-05 |
| RNF-14 | Integridad histórica | ✅ | Nada referenciado se borra físicamente; FKs lo impiden | 47 FK: 42 RESTRICT/NO ACTION; 5 CASCADE (3 herencia + `opcion_respuesta` ERS + `token_recuperacion` por decisión); **el borrado físico de versiones se eliminó en Fase 5** (las versiones quedan como evidencia; los códigos no se reutilizan) | `information_schema` (2026-09-28); `013_foraneas_restrict.sql`; `VersionEvaluacionDAO` (sin `eliminarVersion`) | BD + RUNTIME (borrado de pregunta usada → 500 y fila persiste) | CASCADE de `token_recuperacion` no exigido (decisión documentada) | — | RF-21, RF-36 |
| RNF-15 | Trazabilidad temporal | ✅ | Fechas con hora del servidor en **America/Bogota** | Java usa `ZoneId.of("America/Bogota")`; la conexión efectiva fuerza la sesión MySQL `-05:00` vía `examlen.db.url` externalizada (`connectionTimeZone=America/Bogota` + `sessionVariables=time_zone='-05:00'`); SQL `NOW()` en zona; BD sin tablas de zona cargadas (0 filas) | `Fechas.java:19-30`; `config/catalina.properties.ejemplo`; `web/META-INF/context.xml` y `conf/Catalina/localhost/ExamLen.xml` (placeholders `${examlen.db.*}`) | CÓDIGO + **RUNTIME (Fase 2, 2026-09-28)**: desde el DataSource JNDI, `@@session.time_zone=-05:00`, `NOW()` a −300 min de `UTC_TIMESTAMP()`, timestamps de intento/evento/respuesta coherentes y sesión conservada tras reiniciar Tomcat (9/9). **Fase 3B (2026-09-29)**: `fecha_fin` se calcula con `NOW()`/`LEAST` en esa misma sesión (−05:00) | — (nota: `EvaluacionVirtualDAO.estaDisponibleEvaluacionVirtual` usa `new java.util.Date()` para disponibilidad lógica por instantes, fuera de los flujos de registro; ver Fase 2 §J) | — | RF-47, RF-50 |
| RNF-16 | Despliegue en infraestructura convencional | ❓ | Servidor 2–8 GB sin GPU; opera en 4 GB cumpliendo RNF-07 | Se midió en equipo de 7,5 GB (ver RNF-07); sin despliegue real en VPS de 4 GB | `docs/INFORME-RENDIMIENTO.md` | NO VERIFICADO (sin VPS 4 GB) | Sin evidencia en la infraestructura objetivo | media | RNF-07 |
| RNF-17 | Formato de documentos imprimibles | ✅ | Carta, márgenes estándar, **encabezado con curso, título y versión**, numeración, sin cortes ni preguntas partidas | Carta + márgenes 56/56/80/56; encabezado `título · Curso · Versión X · Docente` y "Página N" por página; bloques `setKeepTogether`; material con curso; examen y clave ahora también con curso (contexto de impresión elegido por el docente) | `VersionEvaluacionDAO.construirPDF`; `PdfFormato.java:31-60`; `MaterialPracticaDAO.java:252-311` | CÓDIGO + **RUNTIME (Fase 5, 2026-09-29)**: CASOS 46–53 y 60–64 (PDFs válidos con y sin curso; estructura `%PDF`, tamaños y nombres correctos) | `keepTogether` no garantiza bloques mayores que una página (nota, sin cambio) | baja | RF-39, RF-40, RF-63 |
| RNF-18 | Transparencia de la supervisión | ✅ | Aviso previo al estudiante; nota literal en la vista de indicadores | Pantalla previa con lista de lo registrado y nota informativa; vista docente con nota **"Los indicadores no determinan por sí solos una conducta indebida."** siempre; eventos ocultos al estudiante | `EstudianteInicioIntentoServlet.java:71-78`; `DocenteIntentoServlet.java:53-54`; `docente-intento.html:54` | CÓDIGO + RUNTIME (nota y resumen visibles; nada en estudiante) | — | — | RF-49, RF-55 |

## 6. Casos de uso CU-01 → CU-34

| CU | Nombre | Estado | Qué existe | Qué falta / desviación | Evidencia |
|---|---|---|---|---|---|
| CU-01 | Iniciar sesión | ✅ | Pasos 1–5, cierre, excepciones 3a/5a | Login por correo o identificación con búsqueda controlada; credenciales incorrectas genéricas; cuenta inactiva con contraseña correcta informa "La cuenta está desactivada. Contacte al administrador." sin permitir ingreso; bloqueo 5/15; rehash legacy; runtime F01–F05 y F14/F15 | `LoginServlet.java`; `UsuarioDAO.buscarPorCorreoOIdentificacion`; `login.html` |
| CU-02 | Cambiar contraseña | ✅ | Pasos y excepciones; flag del docente a FALSE | Política ERS exacta (8–128, letra+número, sin símbolos obligatorios) y "distinta de la actual"; marcador genérico de cambio obligatorio a FALSE; runtime F06–F10/F13 | `CambiarContrasenaServlet.java`; `PoliticaContrasena.java` |
| CU-03 | Actualizar datos | ✅ | Pasos y excepción de correo duplicado | — | `PerfilServlet.java:21-45` |
| CU-04 | Registrarse | ✅ | Pasos, duplicados y consentimiento | — | `RegistroServlet.java:35-80` |
| CU-05 | Gestionar administradores | ✅ | CRUD, confirmaciones, último activo y auto-protección | — | `AdminAdministradoresServlet.java:30-190` |
| CU-06 | Registrar docente | ✅ | Temporal visible una vez y cambio obligatorio | — | `AdminDocentesServlet.java:64-95` |
| CU-07 | Gestionar estudiantes | ✅ | Listado, nivel, eliminación con intentos | — | `AdminEstudiantesServlet.java:66-146` |
| CU-08 | Activar/desactivar | ✅ | Tres roles; auto-desactivación bloqueada | Docentes con confirmación previa (nuevo `confirmarDesactivar`), estudiantes y administradores ya con confirmación; activación directa; servidor revalida `confirmado`; runtime F16–F22 | `AdminEstadoDocenteServlet.java`; `AdminDocentesServlet.java`; `AdminEstudiantesServlet.java`; `AdminAdministradoresServlet.java` |
| CU-09 | Restablecer contraseña | ✅ | Temporal y confirmación | Temporal mostrada una sola vez para **cualquier rol**; cambio obligatorio genérico (`usuario.debe_cambiar_contrasena`, migración 017); limpia bloqueo/intentos, invalida tokens y cierra sesiones del afectado; runtime F23–F30 | `UsuarioDAO.restablecerContrasenaUsuario`; `SesionesActivas`; `TokenRecuperacionDAO` |
| CU-10 | Recuperar contraseña | ⚠️ (SMTP) | Pasos 1–8, 3a/6a/7a/8a | Token 256 bits con hash, 30 min, un solo uso, invalida anteriores; límite 3/hora; mensaje único no enumerable que orienta reintentar o pedir el reset administrativo; si SMTP falla se compensa el token; runtime F31–F40; **entrega real por SMTP no verificada** | `RecuperarContrasenaServlet.java`; `TokenRecuperacionDAO.java` |
| CU-11 | Gestionar cursos | ✅ | Pasos 1–5 | 5a: desactivar con grupos abiertos **advierte y confirma** (los grupos siguen; no se abren nuevos); reactivar lo permite; runtime F41–F45 | `CursoDAO.desactivarCurso`; `DocenteCursoServlet.java` |
| CU-12 | Gestionar grupos | ✅ | Abrir, código, regenerar, cerrar, reabrir y excepciones | — | `DocenteGrupoServlet.java:116-163` |
| CU-13 | Gestionar inscripciones | ✅ | Listar/retirar/reincorporar/eliminar; 3a con fallback | — | `DocenteGrupoServlet.java:35-163` |
| CU-14 | Inscribirse | ✅ | Código, ABIERTO, 3a/3b/5a | Código validado → pantalla de confirmación con curso/grupo/periodo/docente reales → confirmación crea la inscripción; el servidor revalida todo (código, ABIERTO, curso activo, sin duplicado) y no confía en datos ocultos; runtime F46–F50 | `EstudianteGruposServlet.java`; `estudiante-confirmar-grupo.html` |
| CU-15 | Gestionar bancos | ✅ | Listar/crear/editar/abrir | Listado con cantidad por estado (borrador/aprobadas/archivadas/total, una sola consulta); apertura del banco con la búsqueda completa RF-24 y enlace a la búsqueda transversal; runtime Fase 8 (1–4, 29–39) | `DocenteBancosServlet`; `BancoPreguntasDAO.contarPorEstadoDeDocente`; `DocenteBancoServlet`; `DocentePreguntasServlet` |
| CU-16 | Gestionar preguntas | ✅ | Crear/editar/eliminar; opciones agregar/editar/eliminar/marcar; 3a | Edición de texto de opciones con UI; eliminar controlado (no usada → DELETE; usada → mensaje "…puedes archivarla", sin 500); borrador incompleto permitido; runtime Fase 8 (5–22, 43, 44) | `DocentePreguntaServlet`; `OpcionRespuestaDAO`; `PreguntaDAO.eliminarSiNoUsada` |
| CU-17 | Revisar y aprobar | ✅ | Flujo y excepciones; validaciones por tipo | Filtro por origen disponible (RF-24): BORRADOR + GENERADO_IA (runtime 41); 4a sigue listando errores y la pregunta queda BORRADOR; 3b elimina la generada no usada; flujo IA→editar→aprobar→usar verificado (42) | `DocentePreguntaServlet`; `DocentePreguntasServlet`; `PreguntaDAO.faltantesPregunta/aprobarPregunta` |
| CU-18 | Cargar documento | ✅ | Subida, tipos, 10 MB, listado/eliminar | 3a controlada (mensaje propio, sin 500, sin archivo parcial); contenido real validado (PDF/DOCX/TXT) con límites anti zip-bomb; borrado seguro (referenciado rechazado, carrera protegida); runtime Fase 7 13/13 | `DocenteIAServlet`; `servicio.ValidadorDocumento`; `DocumentoFuenteDAO.eliminarSiNoReferenciado` |
| CU-19 | Generar con IA | ✅ | Tipos, estados, BORRADOR, 5a; 5b "informa cuántos se descartaron" | 5b cerrado: `ServicioGeneracionIA` descarta solo los inválidos y persiste válidos, y guarda el desglose exacto (`cantidad_recibida`, `cantidad_valida`, `cantidad_invalida`, `cantidad_no_devuelta`, migración 017); la UI muestra "Se descartaron X ítem(s) inválido(s)" y "N ítem(s) no devuelto(s) por el proveedor" **por separado**, sin mezclarlas; el reintento crea una solicitud nueva conservando proveedor, tema, competencia, documento, banco, referencia y bases persistidos; runtime F51–F53 (desglose verificado con conteos persistidos: no se logró forzar una respuesta parcial real del proveedor) | `ServicioGeneracionIA.registrarConteosSeguro`; `SolicitudGeneracionDAO.registrarConteos`; `DocenteIAServlet.mensajeDeSolicitud` |
| CU-20 | Gestionar evaluaciones | ✅ | Crear/configurar/seleccionar/LISTA/archivar | — (alta con primera pregunta, filtros por tema/nivel/dificultad/competencia, "Quitar" por pregunta, "Faltan N preguntas…"; runtime Fase 5 CS1–CS26/CS39/CS40) | `DocenteEvaluacionesServlet`; `DocenteEvaluacionServlet` |
| CU-21 | Generar versiones | ✅ | Genera, compara, excepción 1a | Generar en BORRADOR rechazado ("…en estado LISTA"); comparativa presente (runtime Fase 5 CS27–CS28, CS37) | `VersionEvaluacionDAO.exigirLista`; `DocenteEvaluacionServlet.construirComparativa` |
| CU-22 | Editar versión | ✅ | Edita y bloquea con intentos/abierta/cerrada | La versión bloqueada se muestra en solo lectura con opciones y puntajes; el POST no modifica (runtime Fase 5 CS44–CS45) | `DocenteVersionPreguntaServlet`; `ServletBase.motivoBloqueoVersion` |
| CU-23 | Descargar evaluación PDF | ✅ | Un PDF por versión, sin respuestas | Individual con curso de contexto y "Todas (ZIP)" (runtime Fase 5 CS46–CS57) | `DocenteVersionPdfServlet` |
| CU-24 | Descargar clave | ✅ | Clave por versión con puntaje | Individual y "Todas las claves (ZIP)"; solo el propietario (runtime Fase 5 CS47–CS49, CS55, CS58) | `VersionEvaluacionDAO`; `DocenteVersionPdfServlet` |
| CU-25 | Programar virtual | ✅ | Formulario, LISTA, fechas, versiones y supervisión, PROGRAMADA (apertura automática cubierta por RF-42) | Selección explícita de versiones con validación de pertenencia, casilla de supervisión real, grupo ABIERTO validado; runtime Fase 9 V1–V11 | `DocenteVirtualServlet`; `EvaluacionVirtualDAO`; `docente-grupo.html` |
| CU-26 | Controlar virtual | ✅ | Abrir/cerrar/cancelar manuales y automáticos; finalización de EN_CURSO | 3a implementada: cancelar con intentos exige **doble confirmación** con aviso de que quedarán sin efecto (sin borrar historia); CANCELADA terminal (scheduler no la toca); runtime Fase 9 V11 | `DocenteVirtualServlet.cancelar/confirmarCancelacion`; `_confirmar-cancelar-virtual.html`; `ServicioEvaluacionVirtual.cancelar` |
| CU-27 | Presentar evaluación | ✅ | Pasos 1–7, 4a | 5a cerrada: cola offline de respuestas en `localStorage` por intento (la más reciente reemplaza a la pendiente), avisos "Sin conexión…", "Pendiente de guardar (N)" y "Guardado", reenvío al evento `online` y al cargar, solo se elimina lo confirmado con 2xx y el 409 de vencimiento limpia la cola y bloquea el formulario (el servidor sigue siendo la autoridad temporal); runtime F54–F59 (F58: 409 conforme en servidor; sin simulación real de corte de red) | `examen.js`; `EstudianteIntentoServlet.java` |
| CU-28 | Registrar eventos | ✅ | Pasos 1–4; 2a con cola local y reenvío al reconectar | Cola localStorage por intento, reenvío de pendientes al volver online + RECONEXION, eventos nativos offline/online, hora del servidor y supresión de duplicados cercanos; runtime Fase 9 V13–V17 | `examen.js`; `EstudianteEventoServlet`; `EventoSupervisionDAO.existeSimilarReciente` |
| CU-29 | Calificar intentos | ✅ | Lista, calificación/recalificación, CALIFICADO, postcondición de visibilidad | Salvedad de escala (RF-53) | `DocenteIntentoServlet.java`; `IntentoDAO.java` |
| CU-30 | Revisar indicadores | ✅ | Conteo, agrupación, línea de tiempo, nota y comparación de similitud | — | `DocenteIntentoServlet.java:140-168` |
| CU-31 | Consultar resultados | ✅ | Lista, calificación, comentarios, 3a "Pendiente de calificación" | — | `EstudianteIntentoServlet.java:151-187` |
| CU-32 | Gestionar material | ✅ | Crear, asociar preguntas aprobadas, cursos, publicar y archivar; publicar exige ≥1 pregunta y todas APROBADAS; aviso al docente y bloqueo al estudiante ante material publicado no íntegro | El flujo normal ya no deja materiales sin preguntas (no se puede quitar la última; publicar vacío rechazado); runtime Fase 9 P1–P12, P34 | `MaterialPracticaDAO.publicarMaterialPractica`; `DocentePracticaServlet` |
| CU-33 | Descargar práctica PDF | ✅ | Docente y estudiante con/sin respuestas | Estudiante solo PUBLICADOS de cursos con inscripción activa (404 en BORRADOR/ARCHIVADO/ajeno); PDF con sección final de respuestas; runtime Fase 9 P26–P33 | `EstudiantePracticaPdfServlet`; `DocentePracticaPdfServlet`; `MaterialPracticaDAO.construirPDF` |
| CU-34 | Resolver práctica | ✅ | Listar, resolver, corregir, resultado, historial | Feedback inmediato por pregunta con persistencia por respuesta; repeticiones con orden barajado e intentos independientes; historial con resúmenes; runtime Fase 9 P13–P25 | `EstudiantePracticaServlet`; `IntentoPracticaDAO`; `RespuestaPracticaDAO` |

## 7. Modelo de datos

- **28 tablas** verificadas contra el diccionario de la ERS; **47 FK**; 5 en CASCADE:
  `administrador→usuario`, `docente→usuario`, `estudiante→usuario` (herencia, correcto),
  `opcion_respuesta→pregunta` (la ERS lo exige) y `token_recuperacion→usuario` (**decisión del autor**, no
  exigida por la ERS). El resto RESTRICT/NO ACTION (migración 013).
- **Columnas adicionales respecto a la ERS** (clasificación A–E pedida):
  - **A. Técnica sin conflicto**: `fecha_creacion` y `fecha_actualizacion` (con `DEFAULT/ON UPDATE`) en 15 tablas.
  - **B. Extensión para cumplir un RNF/funcionalidad**: `usuario.intentos_fallidos` y `usuario.bloqueado_hasta`
    (seguridad de login); `estudiante.aceptacion_tratamiento_datos` (RNF-13, hueco del diccionario);
    `evaluacion_pregunta.orden_evaluacion_pregunta` y `puntaje_evaluacion_pregunta` (RF-32/35, no en el diccionario).
  - **C. Desviación funcional menor de tipos/NULL**: `material_practica.nivel` admite NULL (ERS: NOT NULL; la
    app lo exige); `material_practica.descripcion VARCHAR(500) NOT NULL DEFAULT ''` (ERS: TEXT nulo);
    `evento_supervision.descripcion NOT NULL DEFAULT ''`;
    `intento_practica.calificacion DOUBLE` (ERS: DECIMAL(5,2)); `version_pregunta.puntaje` y `respuesta.puntaje`
    DOUBLE (ERS: DECIMAL(5,2)); `respuesta.comentario_docente` y `respuesta_practica.retroalimentacion`
    VARCHAR(1000) (ERS: TEXT); `solicitud_generacion.mensaje_error VARCHAR(1000)` (ERS: TEXT);
    `pregunta.tema VARCHAR(100)` y `respuesta_esperada VARCHAR(500)` (ERS: 120/TEXT);
    `documento_fuente.tipo_archivo VARCHAR(100)` (ERS: 10); `solicitud_generacion.modelo VARCHAR(100)` (ERS: 80);
    `curso.nombre VARCHAR(100)` (ERS: 120);
    `curso.descripcion VARCHAR(500) NOT NULL DEFAULT ''` (ERS: TEXT nulo); `banco_preguntas.nombre VARCHAR(100)`
    (ERS: 120); `banco_preguntas.descripcion VARCHAR(500) NOT NULL DEFAULT ''` (ERS: TEXT nulo);
    `grupo.nombre VARCHAR(50)` (ERS: 60); `grupo.codigo_acceso CHAR(8)` (ERS: VARCHAR(8));
    `usuario.nombre VARCHAR(100)` (ERS: 120); `usuario.correo VARCHAR(254)` (ERS: 120).
  - **D. Posible deuda técnica**: `usuario.tipo`, `usuario.nivel_ingles_estudiante`,
    `usuario.fecha_nacimiento_administrador` y `usuario.debe_cambiar_contrasena_docente`: columnas heredadas de
    antes de la migración 007, **sin uso en código** (grep sin referencias); la ERS las ubica en las tablas hijas.
  - **E. No determinada**: `usuario.registrado_por` (FK a `usuario`, sin escrituras en código).
- **Tipos alineados**: `respuesta_practica.es_correcta` (NULL-able desde 010, ERS), `material_practica.titulo`
  VARCHAR(150) (011), `token_recuperacion` completo, ENUM de estados de todas las tablas coinciden;
  **`intento.calificacion` pasó de DOUBLE a `DECIMAL(3,1)` en la migración 014 (Fase 3), como pide el ERS**;
  **`evaluacion.titulo` pasó de VARCHAR(200) a VARCHAR(150) NOT NULL y `evaluacion.descripcion` de
  VARCHAR(500) NOT NULL a TEXT NULL en la migración 015 (Fase 5C), exactamente como pide el ERS** (sin
  truncar datos: máximo existente 11/13 caracteres antes del cambio); **`pregunta.tema` pasó de
  VARCHAR(100) a VARCHAR(120) NOT NULL en la migración 016 (Fase 8)** (máximo existente 24).
- **Sin tablas ausentes ni sobrantes.**

## 8. Arquitectura

- **Frontend**: HTML + CSS sin frameworks; sin etiquetas de marcado en `.java` (grep `<div|<table|<form|<span|<option|<p>|style=|class="` → 0 coincidencias); estilos en `base.css` + 8 componentes + 36 pantallas.
- **JavaScript**: un solo archivo, `examen.js`, referenciado únicamente por `estudiante-intento.html:39`; cubre temporizador, autoguardado y eventos de supervisión (lo permitido).
- **Capas**: sin imports `logica→modelo/servlet/servicio` ni `modelo→servlet/servicio` (grep 0); `DaoBase` centraliza `PreparedStatement`, transacciones y traducción de errores.
- **Motor de plantillas**: `Vistas` escapa `{{…}}` y deja crudo `{{{…}}}` para fragmentos compuestos por servlets (7 usos justificados en el informe CSS).
- **Despliegue**: descriptor efectivo `conf/Catalina/localhost/ExamLen.xml` con `docBase=build/web`; **Fase 2
  (2026-09-28)**: unificado con el descriptor versionable `web/META-INF/context.xml`, ambos con placeholders
  `${examlen.db.url|user|password}` y la misma URL con zona `-05:00`; las credenciales reales viven solo en
  `{Catalina Base}/conf/catalina.properties` (ver RNF-15 y sección 16).

## 9. Seguridad

**Vulnerabilidades confirmadas**
1. **Fuga de contenido sin aprobar (crítica, RUNTIME)**: material PUBLICADO con preguntas BORRADOR y pregunta BORRADOR servida al estudiante. Ruta de ataque: docente publica material cuyas preguntas no están aprobadas (o edita una pregunta aprobada dentro de un material publicado) → estudiante inscrito abre `/app/estudiante/practica?id=N` y ve el contenido. Evidencia: `MaterialPracticaDAO.publicarMaterialPractica:56-62`, `EstudiantePracticaServlet.java:43-48,99`; prueba de hoy. **Corregida en Fase 1 (2026-09-28)**: la publicación exige todas aprobadas, el estudiante solo recibe materiales íntegros y el docente ve el aviso (ver RF-30).
2. **Generación de versiones sin evaluación LISTA (alta, RUNTIME)**: botón siempre visible; `exigirEditable` solo bloquea ARCHIVADA (`DocenteEvaluacionServlet.java:73,188-198,237-241`).
3. **Calificación fuera de la escala 0,0–5,0 (alta, RUNTIME)**: nota = suma cruda de puntajes hasta 1000 por pregunta (`IntentoDAO.java:56-82`). **Corregida en Fase 3 (2026-09-28)**: nota final = (obtenidos × 5) / máximos de la versión, con BigDecimal a 1 decimal, persistida en `DECIMAL(3,1)`.
4. **Credencial de BD en el repositorio (alta, CONFIRMADA)**: `web/META-INF/context.xml:8` incluye usuario y contraseña reales de desarrollo (`examlen_app`). Mitigación parcial: usuario dedicado (no root) y README que indica moverla a `catalina.properties` en producción. **Corregida en Fase 2 (2026-09-28)**: los tres descriptores usan placeholders `${examlen.db.*}` y las credenciales viven solo en `{Catalina Base}/conf/catalina.properties` (fuera del repositorio); 0 secretos reales en archivos versionables (verificado).
5. **Restablecer contraseña sin cerrar sesiones ni limpiar bloqueo (media, CONFIRMADA)**: `UsuarioDAO.restablecerContrasenaUsuario:138-156`; el autoservicio sí cierra (`RestablecerContrasenaServlet.java:57`).
6. **Borrado de documento referenciado deja archivo eliminado y error 500 (media, CONFIRMADA)** — **corregida en Fase 7 (2026-09-29)**: eliminación transaccional con bloqueo; referenciado → rechazo controlado; carrera protegida (25 iteraciones sin huérfanas).
7. **Subida >10 MB → 500 (media, CONFIRMADA por contrato de contenedor)** — **corregida en Fase 7**: rechazo controlado con mensaje propio y sin archivo parcial; el filtro CSRF responde 413 si excede el máximo del contenedor.
8. **Modelo de IA sin lista blanca concatenado en URL con la clave institucional (media, CONFIRMADA)** — **corregida en Fase 7**: cada proveedor expone su lista permitida; el modelo se valida en servidor antes de crear/ejecutar y el efectivo se persiste.
9. **DOCX sin límite de descompresión (media, RIESGO)** — **corregida en Fase 7**: límites de seguridad del parser (512 entradas, 20 MB/entrada, 40 MB total) contando bytes reales; zip-bomb de prueba rechazado sin agotar memoria.
10. **Bloqueo de cuenta como DoS (media, RIESGO)**: 5 intentos/15 min sin límite por IP (`LoginServlet.java:19-20,46-51`).

**Riesgos menores**: límite de intentos del código de grupo por sesión (se reinicia con sesión nueva,
`EstudianteGruposServlet.java:27-29`); token de recuperación en la URL (mitigado con `Referrer-Policy: no-referrer`);
`SesionesActivas` en memoria de una sola JVM.

**Buenas prácticas presentes**: `PreparedStatement` en todas las consultas (revisadas; las concatenaciones son
constantes internas → **falsos positivos de SQLi**); escapado por defecto y `{{{…}}}` solo con fragmentos del
servidor (doble escapado de `Html.resumir` es defecto de presentación, **no XSS**); CSRF global con comparación en
tiempo constante; sesión nueva al autenticar y `changeSessionId` al cambiar contraseña; cookie Secure/HttpOnly/
SameSite=Strict; HSTS/CSP/nosniff/X-Frame-Options/no-referrer; `/html/*` bloqueado; subidas fuera del webapp con
nombre UUID (path traversal mitigado); sin contraseñas ni tokens en logs; claves de IA fuera del repositorio.

**Script obsoleto con credencial por defecto**: `sql/crear_administrador.sql:13-15` inserta un hash precalculado y
usa columnas heredadas de `usuario` (fallaría con el esquema actual).

## 10. Integración con IA — veredicto de asincronía (actualizado en Fase 6)

**Pre-Fase 6 (auditoría): SÍNCRONA.** `doPost` llamaba a `generar` en el hilo de la petición; el proveedor
usaba `HttpClient.send` bloqueante con timeouts de 15/60 s y un reintento idéntico (hasta ~120 s); la
respuesta HTTP se devolvía al final y EN_PROCESO era inobservable.

**Post-Fase 6: ASÍNCRONA REAL.** Secuencia implementada:

1. `DocenteIAServlet.generar` valida y crea la solicitud **PENDIENTE**; resuelve el modelo efectivo sin
   red (`FabricaProveedores.modeloEfectivo`) y lo persiste.
2. Construye un **comando inmutable** (`TrabajoGeneracionIA`: id, docente, tipo, instrucciones, cantidad,
   nivel, proveedor, modelo, ids de documento/banco, tema y competencia elegidos) y lo entrega a
   `servicio.ServicioGeneracionIA.encolar`.
3. El POST responde con redirección (POST-Redirect-GET) en **menos de 2 s**; medido con proveedor simulado
   de 5–30 s (0,02–0,3 s reales).
4. El worker reclama con `UPDATE ... WHERE estado='PENDIENTE'` (solo uno gana), lee el documento desde la
   ruta persistida (no usa `Part`), llama al proveedor **sin transacción abierta**, persiste el contenido
   en BORRADOR/GENERADO_IA y solo entonces marca COMPLETADA; cualquier error deja FALLIDA con mensaje
   seguro.
5. Al arrancar la aplicación, las solicitudes PENDIENTE/EN_PROCESO de un proceso anterior se marcan
   FALLIDA ("reinicio del servicio") porque el proveedor y los parámetros derivados no están todos
   persistidos: **no se inventa persistencia fuera del ERS ni se reanuda a ciegas**.

Evidencia: sección de casos de `fase6-evidencias/` (20 casos HTTP con proveedor simulado, harness de
doble encolado/conexiones/ciclo de vida, shutdown/reinicio, sin clave de API y regresión).

**Post-Fase 7: contrato y proveedores.** El worker valida cada ítem **antes de persistir** (SM/VF/corta/
abierta), normaliza V/F a Verdadero/Falso con una sola correcta (sin azar), copia los metadatos del docente
o de la pregunta base, descarta las repetidas de la evaluación de referencia y persiste todo en UNA
transacción corta (`PersistenciaGeneracionDAO`) con `id_solicitud_generacion` en preguntas y material. Los
proveedores se descubren por **ServiceLoader** (`ProveedorIAFabrica`) con lista permitida de modelos; el
servlet, el servicio y la UI no contienen `GEMINI/GROQ` cableados. CU-19 5b se informa con "Se descartaron
N de las M solicitudes" (conteo derivado de las asociaciones; desglose exacto en el log) y el reintento desde
la UI precarga lo persistido. Evidencia: `fase7-evidencias/` (80 comprobaciones en 7 bloques, harness de
carrera documento↔solicitud con 25 iteraciones y prueba mínima real con Gemini que completó una solicitud).

## 11. Versiones de evaluación — algoritmo real (RF-35, actualizado en Fase 5)

1. **Perfil canónico**: se ordenan las preguntas seleccionadas por `(tema, competencia, dificultad, puntaje, id)`
   y se muestrean sistemáticamente los índices `i*N/P` para obtener exactamente **P slots**. Un slot es el tipo
   `tema|competencia|dificultad|puntaje` (`VersionEvaluacionDAO.cargarFuentes/construirPerfil`); `P` = numero de
   preguntas por versión; si hay menos de `P` seleccionadas → `Faltan preguntas para completar la evaluación…`.
2. **Reparto por versión**: cada uno de los `P` slots toma un representante **distinto** del pool de su tipo con
   rotación `(desplazamientoAleatorio + índiceVersión + ocurrencia) % tamaño`; como las ocurrencias de un tipo en
   el perfil nunca superan el tamaño de su pool, no hay repetidas **dentro** de una versión
   (`VersionEvaluacionDAO.crearVersion`).
3. **Aleatoriedad de orden**: desplazamientos con `SecureRandom`; `Collections.shuffle` de preguntas y de opciones
   por versión; el orden queda congelado al insertar.
4. **Equivalencia garantizada**: todas las versiones de la operación comparten el mismo perfil → misma cantidad,
   **misma distribución exacta de tema/competencia/dificultad y mismo puntaje total por construcción**
   (la comparativa lo refleja: ninguna celda resaltada). Si un tipo tiene menos preguntas que versiones, alguna
   se repite **entre** versiones (la pantalla lo avisa antes de generar).
5. **Orden congelado**: `version_pregunta.orden` y `version_opcion.orden` fijados al crear y leídos con
   `ORDER BY orden`.
6. **Regeneración**: INSERT transaccional; códigos `A…Z`, `AA…` continuando el mayor; UNIQUE de BD
   (`003_...sql:101`); **el borrado físico de versiones se eliminó**, así que los códigos no se reutilizan.
7. **Precondición LISTA**: `exigirLista` bloquea la fila de la evaluación (`SELECT … FOR UPDATE`) dentro de la
   misma transacción; sin LISTA se rechaza todo (runtime: generar en BORRADOR → error, sin filas nuevas).
8. **Nota defensiva**: la comprobación "No hay suficientes preguntas equivalentes" es inalcanzable con el
   muestreo actual (las ocurrencias de cada tipo jamás superan su pool); se conserva como defensa.

## 12. Evaluación virtual (RF-42) — separación pedida

| Aspecto | Estado real (tras Fase 4) | Evidencia |
|---|---|---|
| (a) Disponibilidad lógica por fecha | Sí: `estado='ABIERTA' AND NOW() BETWEEN fecha_inicio AND fecha_fin` (protege al estudiante aunque el programador se retrase) | `EvaluacionVirtualDAO.disponibleAhora` |
| (b) PROGRAMADA→ABIERTA automática | Sí: el programador abre solo si `fecha_inicio <= NOW() < fecha_fin` (UPDATE condicional) | `ProgramadorEvaluacionesVirtuales`; `EvaluacionVirtualDAO.abrirProgramada` |
| (c) ABIERTA→CERRADA automática | Sí: al llegar `fecha_fin`; también cierra PROGRAMADAS vencidas sin abrirlas | `ServicioEvaluacionVirtual.sincronizar/cerrar` |
| (d) Finalización de EN_CURSO | Al vencer el límite individual (programador, Fase 4B), al cerrar/cancelar la virtual (manual o automático) y, de forma perezosa, al volver el estudiante; `fecha_fin` con el límite efectivo (Fase 3B) | `servicio.ServicioEvaluacionVirtual.finalizarIntentoVencido/finalizarIntentosEnCurso`; `IntentoDAO.CIERRE_FECHA_FIN` |
| (e) Análisis RF-50 | En cierre manual y automático, si la supervisión está activa; idempotente | `ServicioEvaluacionVirtual.analizarSupervision` |

El programador es un `@WebListener` con un único `ScheduledExecutorService` de un hilo nombrado
(`examlen-virtual-scheduler`), intervalo configurable (`examlen.scheduler.virtual.intervalSeconds`,
por defecto 30 s) y parada limpia en `contextDestroyed`. Supervisión **forzada a TRUE** al programar
(`EvaluacionVirtualDAO.java:29,39,45`; runtime `supervision=1`). Tampoco se valida que el grupo esté
ABIERTO al programar (RF-41, sin cambio).

## 13. Supervisión

- **Eventos** (5 tipos) con hora del servidor, solo si EN_CURSO y supervisión activa; aviso previo al estudiante
  (RNF-18). Sin cola offline (CU-28 2a).
- **Cierre** (RF-50): tiempo atípico (< fracción configurable de la mediana del grupo, default 0,25) y similitud
  (Jaccard de trigramas ≥ umbral configurable, default 0,85) con exclusión de respuestas que coinciden con la
  esperada y mínimo 40 caracteres; un evento por par espejado; idempotente por tipo. **Runtime verificado**:
  evento "Similitud del 90 %…" en ambos intentos, "Duró 1 min: menos del 25 %… (6 min)" en el intento corto, sin
  duplicados al re-cerrar.
- **Umbrales configurables** en `{Catalina Base}/conf/supervision.properties` o variables de entorno
  (`ConfiguracionSupervision.java`); el mínimo de 40 caracteres es constante documentada (no configurable, por
  decisión contra falsos positivos).
- **Privacidad**: eventos y nombres de compañeros solo en la vista docente; verificado en runtime que el
  estudiante no ve indicadores, mediana ni la nota ética.

## 14. Práctica

- Material con nivel, cursos, publicación/archivo; listado filtrable por nivel (runtime ✓).
- Publicación **no revalida aprobación** (fuga runtime ✓); material publicable con 0 preguntas.
- Resultado con respuesta correcta y retroalimentación solo en incorrectas ("Repasa: tema (competencia)");
  abiertas "Sin calificar" (runtime ✓) y porcentaje "sobre N de M calificables".
- Repeticiones ilimitadas **sin orden aleatorio** (runtime: dos intentos con orden idéntico).
- Historial con fecha/resultado y resúmenes de competencia y tema (runtime ✓).
- PDF/clave del docente con curso; **el estudiante no puede descargar**; el "Word" es HTML con extensión `.doc`.

## 15. Rendimiento

- **Medición nueva (2026-09-28, código actual, servidor sin cambios, `maxTotal=20` intacto)**:
  guardados n=400 → p50 **36,6 ms**, p95 **197,6 ms**, máx 429,6 ms, 0 errores; páginas n=40 → p95 **15,4 ms**;
  PDF 50 preguntas → 41–58 ms. Entorno: Windows 11, Ryzen 5 7535HS (6C/12T), 7,5 GB, MySQL 8.4.9,
  Tomcat solo, generador en el mismo equipo.
- Contra las corridas válidas anteriores (p95 100,4/95,3 ms), la nueva es algo mayor pero **muy por debajo del
  umbral (2 s)**; la diferencia se atribuye a la variabilidad del equipo compartido (cliente+servidor+BD).
- Cumple RNF-07 (95 % < 2 s) y RNF-08 (páginas < 3 s; PDF < 10 s). Evidencia: `docs/pruebas/` (script, JSON y PDF).
  **Fase 6 (2026-09-29)**: el registro de una generación con IA responde en 0,02–0,3 s con proveedor
  simulado de 5–30 s; el POST no incluye el tiempo del proveedor (`fase6-evidencias/fase6-resultados-a.txt`).
- La prueba de carga se auto-limpia y se negó a correr si hay restos suyos; hoy terminó con 0 restos.

## 16. Brechas ordenadas por prioridad

**Crítica**
1. Contenido BORRADOR/GENERADO_IA accesible a estudiantes vía material publicado (RF-30, RF-57, CU-32 5a) — **corregida en Fase 1 (2026-09-28)**; queda la matización 1..* de RF-57.
2. Credencial de la base de datos versionada en `web/META-INF/context.xml` (seguridad) — **corregida en Fase 2 (2026-09-28)**: placeholders en los descriptores y credenciales solo en `catalina.properties` externo.

**Alta**
3. Calificación sin escala 0,0–5,0 (RF-53) — **corregida en Fase 3 (2026-09-28)**.
4. Sin apertura/cierre automáticos ni finalización de intentos al vencer (RF-42; arrastra RF-47/50) — **corregida en Fase 4 (2026-09-29)** y **cerrada en Fase 4B**: RF-42, RF-47 y RF-50 quedan ✅; el límite individual vence y finaliza aunque la virtual siga ABIERTA, y el servidor rechaza escrituras tardías.
5. Generación de versiones sin exigir evaluación LISTA (RF-34/35) — **corregida en Fase 5 (2026-09-29)**;
   RF-34/35/36/37/39 y RNF-17 cerrados en esa fase.
6. Generación IA síncrona (RNF-08) — **corregida en Fase 6 (2026-09-29)**: async real, cola acotada,
   recuperación al arranque y parada limpia; RNF-08 queda ✅.
7. RNF-15: descriptor de Tomcat con URL antiguo (sesión MySQL en `SYSTEM`).
8. RF-10: cambio obligatorio de temporal solo para docentes; sin cerrar sesiones ni limpiar bloqueo.
9. RF-24: búsqueda y filtros de preguntas casi ausentes — **corregida en Fase 8 (2026-09-29)**.
10. RF-15: grupo cerrado no impide programar virtuales — **corregida en Fase 9 (RF-41 valida grupo ABIERTO) y revalidada en Fase 11 (RF15-A/B/C/D/E); RF-15 queda ✅**.
11. RF-28: sin evaluación de referencia ni exclusión de repetidas — **corregida en Fase 7 (2026-09-29)**.

**Media**
12. RF-01 login sin identificación; RF-02 política más estricta; RNF-02 403 vs 404;
    RNF-04 (SMTP no verificable); DoS por bloqueo de cuenta.
    (RF-25…28 y RNF-12 en **Fase 7**; RF-20/21/24 en **Fase 8**; RF-41, RF-49, RF-51, RF-57, RF-60, RF-61 y
    RF-63 en **Fase 9**.)

**Baja**
13. RF-09 confirmación docente; RF-23 archivadas en variantes IA; CU-14 confirmación; doble escapado de
    `Html.resumir`; tipos de columna de la sección 7; comentarios obsoletos en `AdministradorDAO` y
    `sql/crear_administrador.sql`; EN_PROCESO inobservable; extensión `.doc` del Word.

**No verificables pendientes de entorno/pruebas**
14. RNF-04 (SMTP), RNF-09/10/11 (navegador/usuario), RNF-16 (VPS 4 GB), HTTPS de RNF-03.

## 17. Recomendación del orden de implementación

1. **RNF-15 (configuración)**: sincronizar el descriptor de Tomcat con el URL del proyecto y verificar la sesión
   `-05:00`. Sin código; elimina una desviación de auditoría. — **Hecho en Fase 2 (2026-09-28)**: descriptor
   unificado con placeholders y sesión `-05:00` verificada por JNDI (persiste tras reinicio).
2. **RF-30/RF-57 (crítica)**: validar "todas aprobadas" al publicar material + filtrar `pregunta.estado='APROBADA'`
   al servir práctica; quitar la credencial del repositorio (seguridad crítica). — Validación de publicación e
   integridad implementada en Fase 1 (2026-09-28); **queda pendiente retirar la credencial del repositorio**.
3. **RF-53 (alta)**: definir con el autor la escala (puntaje por pregunta y conversión a 0,0–5,0) e implementarla.
   — **Hecho en Fase 3 (2026-09-28)**: escala 0,0–5,0 con denominador de la versión, recalificación y
   presentación; migración 014.
4. **RF-42 (alta)**: job de apertura/cierre por fechas que además finalice EN_CURSO y dispare RF-50; habilita RF-47.
   — **Hecho en Fase 4 (2026-09-29)**: programador con intervalo configurable, cierres idempotentes y RF-50 en
   automático; **cerrado en Fase 4B**: expiración individual de intentos y rechazo atómico de escrituras tardías.
5. **RF-34/35 (alta)**: exigir LISTA para generar versiones y (si se aprueba) incorporar tema al agrupamiento.
   — La integridad de preguntas APROBADAS al marcar LISTA y al generar quedó cerrada en Fase 1C (2026-09-28).
   — **Hecho en Fase 5 (2026-09-29)**: LISTA obligatoria dentro de la transacción y agrupamiento por
   `tema|competencia|dificultad|puntaje` con perfiles equivalentes; RF-31…RF-37 y RF-39 cerrados.
6. **RNF-08 + RF-29 (alta)**: cola asíncrona de generación con estados reales y reintento precargado.
   — **Hecho en Fase 6 (2026-09-29)**: asincronía real con executor acotado y recuperación de abandonadas;
   queda solo el reintento precargado (UX) como pendiente menor de RF-29.
7. **RF-28 y RF-27 (alta/media)**: evaluación de referencia con exclusión de repetidas; variantes con metadatos base.
   — **Hecho en Fase 7 (2026-09-29)**: dos modos de material con exclusión server-side y variantes con
   selección explícita de bases y metadatos copiados.
8. **RF-24, RF-20, RF-21 (alta/media)**: filtros/búsqueda de preguntas, editar opciones, archivar en vez de 500.
9. **RF-10, RF-09, RF-01, RF-02 (media)**: restablecimiento universal con cierre de sesiones y limpieza de bloqueo,
   confirmaciones, login por identificación y decisión sobre la política de contraseñas.
10. **RF-63, RF-39, RF-40, RNF-17 (media)**: PDF del estudiante, sección final opcional, curso en el encabezado.
    — **RF-63 hecho en Fase 9 (2026-09-29)**: descarga del estudiante con/sin sección final de respuestas,
    solo publicados de cursos con inscripción activa.
    — **RF-39/RF-40/RNF-17 hechos en Fase 5 (2026-09-29)**: curso de contexto, ZIP de todas las versiones/claves
    y errores controlados; queda RF-63 (descarga del estudiante).
11. **RF-60, RF-61 (media)**: feedback por pregunta (JS permitido) y orden aleatorio en repeticiones.
    — **Hecho en Fase 9 (2026-09-29)**: feedback inmediato por pregunta con persistencia por respuesta y
    barajado determinista por intento en las repeticiones.
12. **RF-25/26 y seguridad restante (media)**: excepciones de subida/borrado, whitelist de modelo, límite DOCX,
    manejo de 10 MB, V/F generadas. — **Hecho en Fase 7 (2026-09-29)**: ValidadorDocumento con límites
    anti zip-bomb, rechazo controlado >10 MB, lista permitida de modelos y V/F canónico; queda como deuda
    menor el antivirus real (no exigido por el ERS).
13. **RNF-03/RNF-05 (media)**: excepción de sesión durante intento y reintento offline con aviso.
14. **RNF-09/10/11/16 (no verificables)**: pruebas de navegador a 360 px, prueba con docente real y despliegue en
    VPS de 4 GB para cerrar la evidencia.
15. **Deuda menor**: columnas heredadas de `usuario` (evaluar limpieza en una migración futura), tipos de columna,
    doble escapado, comentarios y `sql/crear_administrador.sql`.

*Fin del documento.*
