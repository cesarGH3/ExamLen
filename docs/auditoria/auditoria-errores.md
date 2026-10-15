# Auditoría de errores (Fase 1B)

Fecha: 2026-10-15 · Rama: `auditoria-limpieza` · Método: auditoría en modo lectura primero (compilación, logs, estática) y pruebas en ejecución por rol; corrección solo de errores reales demostrados. Reglas aplicadas: sin cambios de arquitectura, sin tocar los 34 CU, sin tocar BD, sin tocar Web Services/ExamBridge (salvo el descriptor externo ya aprobado), sin Maven.

## 1. Errores encontrados

| # | Error | Dónde (archivo:línea o URL) | Cómo reproducirlo (pasos) | Resultado actual | Resultado esperado | Causa | Corrección propuesta | Severidad |
|---|---|---|---|---|---|---|---|---|
| E-01 | `accion=regenerarCodigo` no valida que el grupo esté ABIERTO en el servidor (solo la UI oculta el botón) | `src/java/servlet/DocenteGrupoServlet.java:117-123` (case `regenerarCodigo`); `src/java/modelo/GrupoDAO.java:61-76`; UI: `web/html/docente-grupo.html:67` | 1) Docente propio abre un grupo; 2) lo cierra (`accion=cerrar`); 3) POST directo a `/app/docente/grupo` con `id=<grupo>` y `accion=regenerarCodigo` (+CSRF de la página del propio grupo) | **Confirmado en runtime**: status 302, mensaje "Código nuevo: …" y el código cambia en BD estando CERRADO (evidencia: `BV9E6J9W` → `5WAXPKBH`, `codigo_acceso` persistido) | Rechazo controlado ("El grupo está cerrado…") o regeneración inhabilitada, igual que ya ocurre en `crearInscripcion` (`InscripcionDAO.java:26`) y en programar virtual (RF-15) | Validación de estado ABIERTO solo en la UI (el código ya lo marca como desviación baja en `docs/TRAZABILIDAD.md` §4 RF-14) | En `case "regenerarCodigo"` exigir `grupo.estadoGrupo == ABIERTO` antes de llamar al DAO (o guarda equivalente en `GrupoDAO.generarCodigoAccesoGrupo`), reutilizando el mensaje de grupo cerrado | baja |
| E-02 | Ruido en log: `GRAVE IntentoDAO... Intento sin maximo de puntos (id=948/950)` | `logs/catalina.2026-10-15.log` (01:59 y resto del día); guarda en `src/java/modelo/IntentoDAO.java` (`recalcularCalificacion`, lambda) | Los intentos 948/950 pertenecían a corridas de prueba (FINAL11) hoy ya eliminadas; el guard se disparó al recalcular con `SUM(version_pregunta.puntaje)=0` | Mensajes GRAVE en log por datos de prueba efímeros | Log limpio o nivel ADVERTENCIA para una condición de datos ya controlada (no rompe el flujo: FINALIZADO sin nota falsa) | Nivel de log elegido en el guard para una inconsistencia de datos esperable en pruebas | Opción (no aplicada): bajar a `warning` y/o incluir sugerencia de limpieza. Decisión del autor | baja (informativo) |
| E-03 | Churn externo de artefactos: NetBeans abierto (`netbeans64`, iniciado 16:07) compiló y regeneró `dist/ExamLen.war` (16:08:18), borró artefactos obsoletos de `build/` y dejó residuo `build/web/WEB-INF/classes/examlen/` | Working tree del repo (sin commit): `M dist/ExamLen.war`, `D build/.../FabricaProveedores.class`, `D build/web/html/admin-confirmar.html`, `D build/web/css/paginas/admin-confirmar.css`, `?? build/web/WEB-INF/classes/examlen/`, `M build/web/META-INF/context.xml` | Tener NetBeans abierto con el proyecto y editar fuentes: su build propio toca `build/` y `dist/` y puede reiniciar Tomcat | Working tree sucio con cambios ajenos a la auditoría; arranques de Tomcat no controlados (el de 16:08 usó aún el descriptor viejo) | Solo el build controlado (`recompilar.ps1`) toca los artefactos durante la auditoría | Dos mecanismos de build en paralelo (NetBeans y script) | Cerrar/pausar el auto-build de NetBeans durante auditorías; la política de versionar `build/`/`dist/` se decide en la fase de cierre (por ahora se conserva el commit `28fbf62`) | baja (proceso) |
| E-04 | `accion` desconocida con recurso ajeno/inexistente responde 404 (no 400) | `src/java/servlet/DocenteEvaluacionServlet.java:88-94` (resuelve `id` y dueño antes del `switch`) y `:222-223` (`default → 400`) | POST a `/app/docente/evaluacion` con `accion=accionQueNoExiste&id=1` (evaluación inexistente/ajena) | 404 | (diseño actual) 404 por resolución previa del recurso; el 400 por acción desconocida se alcanza con recurso propio | Orden de validaciones: primero propiedad/existencia, después la acción | Ninguna: es intencional (no revelar recursos ajenos). Documentado; sonda corregida | no es error |

## 2. Verificaciones sin hallazgo (evidencia)

| Verificación | Resultado |
|---|---|
| Compilación `javac -Xlint:all` | exit 0, 228 clases, **0 warnings en código propio** (solo 6 del jar gson) |
| Logs Tomcat del día | Sin excepciones de aplicación en `localhost.*.log`; en `catalina` solo: 6 `deployDescriptor` de ExamBridge **pre-fix** (resuelto y verificado), 3 `stopServer` normales al reiniciar, y E-02 (datos) |
| Parseos numéricos (`Integer.parseInt`, `Double.parseDouble`) | 13 usos revisados: **todos guardados** (helpers de servlet con try/catch → `-1`/`MIN_VALUE`; `Validador.parsearId` → `-1`→404; `HashContrasena.verificarLegacy` atrapa `IllegalArgumentException`; configs IA/correo/scheduler/supervisión → valor por defecto) |
| `Optional.get()` | 15 usos revisados: todos tras `isPresent()`, `isEmpty()` con cortocircuito o `orElseThrow`/`verificarPropiedad` |
| Recursos (streams) | Únicos `Files.newInputStream` (3, configs) en try-with-resources; `DaoBase` abre conexiones solo en try-con-recursos |
| Transacciones multi-paso | Ya auditadas (Fases 5/7/8/11); sin cambios en esta limpieza |
| Codificación | **0 archivos con U+FFFD** en `src/java` y `web` (bytes EF BF BD) |
| Seguridad/permisos (runtime, FASE1B) | docente→`/app/admin/*` 403; docente→curso/grupo de **otro** 403; inexistente 404; estudiante→`/app/docente/*` y rendimiento 403; anónimo→302 login; ruta inexistente con sesión→404 |
| XSS (runtime) | Nombre de curso `FASE1B <script>alert(1)</script>` se muestra **escapado** (`&lt;script&gt;…`), sin etiqueta cruda |
| Duplicados (runtime) | Doble submit de curso con el mismo nombre: 302 + mensaje "Ya tienes un curso con ese nombre" y **1 sola fila** en BD |
| Acciones desconocidas | `default → 400` presente (ver E-04) |
| Pruebas runtime Fase 1B (sonda `fase1b-runtime.ps1`) | **19 OK / 20** (la única "falla" fue la sonda D5, mal planteada: ver E-04); limpieza `FASE1B%` → 0 restos; baseline intacta 20/5/3/7/24/4/4/2/0/0 |
| Regresión FINAL11 (46 pruebas, vía HTTP) | **46/46 OK**, baseline intacta |

## 3. Correcciones realizadas en esta fase

**Ninguna corrección de código**: no se encontraron errores de severidad **crítica ni alta** que corregir. E-01 (baja, confirmada) y E-02/E-03 quedan **reportados para decisión** conforme a las reglas de la fase.

## 4. Dudas / pendientes para decisión

- **E-01**: ¿corrijo la guarda de estado en `regenerarCodigo` (cambio mínimo, ~3 líneas en el servlet + prueba antes/después)? Es baja y toca un flujo de CU-12 (no cambiaría el flujo feliz).
- **E-02**: ¿bajo el nivel de log del guard de `IntentoDAO` a `warning`? (opcional; no altera lógica).
- **E-03**: ¿cierro NetBeans durante el resto de la auditoría (Fases 2/3) para evitar churn y reinicios no controlados? Los cambios de `build/`/`dist/` que dejó **no los commiteé**; están en el working tree para tu decisión (política de artefactos para la fase de cierre).
- Residuo `build/web/WEB-INF/classes/examlen/` (clases de un paquete antiguo) — limpiar en la fase de cierre junto con la política de `build/`.
