# Auditoría de código (Fase 1) — archivos sin uso y código sucio

Fecha: 2026-10-15 · Rama: `auditoria-limpieza` · Métodos: escaneo estático de referencias cruzadas (Java/HTML/CSS/JS), `javac -Xlint:all` (exit 0, 228 clases, 6 warnings todos del jar gson) y verificación de rutas/acciones. **Ningún archivo se ha borrado ni modificado**: esta tabla es la propuesta, a la espera de aprobación.

## Tabla de hallazgos

| # | Tipo | Archivo:línea | Problema | Evidencia | Acción propuesta (borrar / limpiar / corregir) | Riesgo |
|---|---|---|---|---|---|---|
| 1 | Archivo sin uso | `web/html/_filtro.html` | Fragmento HTML que ninguna fuente referencia | 0 coincidencias de `_filtro` en `src/java`, `web/html`, `web/js` (escaneo completo); tampoco `componer()` dinámico | **Borrar** (y su copia en `build/web/html` al recompilar) | bajo |
| 2 | Archivo sin uso | `web/html/_pregunta-resumen.html` | Fragmento HTML que ninguna fuente referencia | 0 coincidencias de `_pregunta-resumen` en `src/java`, `web/html`, `web/js` | **Borrar** | bajo |
| 3 | Script obsoleto con credencial | `sql/crear_administrador.sql:13-15` | Script marcado obsoleto por el propio proyecto; inserta un hash precalculado de una credencial por defecto | `docs/TRAZABILIDAD.md:189` ("obsoleto"), `:383` ("Script obsoleto con credencial por defecto"), `:529`; el hash insertado no corresponde al formato BCrypt actual | **Borrar** (recomendado; el administrador ya existe y el alta de admins se hace por la app). Alternativa: conservarlo vaciado de credenciales | bajo |
| 4 | Residuo de compilación | `build/classes/servlet/ServicioEvaluacionVirtual.class`, `ServicioEvaluacionVirtual$Coincidencia.class`, `ConfiguracionScheduler.class` (+ espejos en `build/web/WEB-INF/classes/servlet/`) | Clases compiladas de versiones viejas (su fuente vive en `src/java/servicio/`); el copiado de compilación no purga archivos ausentes | Fuente: `src/java/servicio/ServicioEvaluacionVirtual.java`, `ConfiguracionScheduler.java`; `recompilar.ps1:16-19` usa `robocopy /E` sin `/PURGE` | **Limpiar** `build/` (borrar esas clases; recompilación limpia). Opcional: añadir `/PURGE` al script de copiado | bajo |
| 5 | Valor quemado | `src/java/servicio/RendimientoEscenarios.java:319` | Ruta absoluta de máquina en código: `"C:/Users/STEINER1TB/AppData/Local/Temp/opencode/perf.txt"` | grep de `[CD]:[\\/]` en `src/java` → único caso | **Corregir**: usar `System.getenv("TEMP")` o `Files.createTempFile` (solo afecta a la herramienta de rendimiento) | bajo |
| 6 | Imports sin usar | 21 casos (lista abajo) | Imports que no vuelven a aparecer en su archivo | Heurística archivo por archivo (1524 imports revisados); `javac -Xlint:all` no los señala (no es error) | **Limpiar** los 21 imports; recompilar después para confirmar | bajo |
| 7 | SQL concatenado (revisado) | `src/java/modelo/RespuestaPracticaDAO.java:117-125` | SQL que concatena el nombre de columna `agrupador` | Solo recibe literales internos: `"p.competencia"` (`:109`) y `"p.tema"` (`:114`); nunca entrada de usuario. El resto del concatenado de DAO (`+ Mapeos.COLUMNAS`, `+ ORIGEN`, `LIMIT + constante`) es con constantes | **Sin cambio** (opcional: comentario "lista blanca de valores") | bajo |
| 8 | Duplicación leve | `DocenteCursoServlet.java:163-167` y `DocenteGrupoServlet.java:170-174` (`cursoDelDocente` / `grupoDelDocente`) | Patrón repetido de carga + `verificarPropiedad` (4 líneas cada uno) | Ambos usan `verificarPropiedad` ya común en `ServletBase.java:151-159`; el resto difiere por DAO | **Sin cambio** (moverlo exigiría genéricos; limpiar no es rediseñar) | — |

### Lista del hallazgo 6 (imports sin usar)

| Archivo:línea | Import |
|---|---|
| `modelo/DocumentoFuenteDAO.java:9` | `java.util.Date` |
| `modelo/EventoSupervisionDAO.java:10` | `java.util.Date` |
| `modelo/InscripcionDAO.java:12` | `java.util.Date` |
| `modelo/IntentoDAO.java:17` | `java.util.Date` |
| `modelo/PreguntaDAO.java:17` | `java.sql.PreparedStatement` |
| `modelo/RespuestaDAO.java:13` | `java.util.Date` |
| `modelo/SolicitudGeneracionDAO.java:15` | `java.util.Date` |
| `modelo/VersionEvaluacionDAO.java:24` | `java.util.Date` |
| `servlet/AdminEstudiantesServlet.java:8` | `logica.ValidacionException` |
| `servlet/AdminRendimientoServlet.java:17` | `java.nio.file.Files` |
| `servlet/AdminRendimientoServlet.java:18` | `java.nio.file.Path` |
| `servlet/DocenteBancoServlet.java:12` | `logica.RecursoNoEncontradoException` |
| `servlet/DocenteBancoServlet.java:14` | `logica.ValidacionException` |
| `servlet/DocenteCursoServlet.java:6` | `logica.RecursoNoEncontradoException` |
| `servlet/DocenteIAServlet.java:32` | `java.nio.file.StandardCopyOption` |
| `servlet/DocentePracticaPdfServlet.java:7` | `logica.RecursoNoEncontradoException` |
| `servlet/DocenteVirtualServlet.java:14` | `logica.Pregunta` |
| `servlet/DocenteVirtualServlet.java:19` | `modelo.PreguntaDAO` |
| `servlet/EstudianteIntentoServlet.java:8` | `logica.RecursoNoEncontradoException` |
| `servlet/EstudianteIntentoServlet.java:10` | `logica.ValidacionException` |
| `servlet/LoginServlet.java:6` | `logica.Validador` |

## Falsos positivos revisados (NO se tocan)

| Elemento | Por qué parecía sin uso | Por qué se conserva |
|---|---|---|
| `servlet/HeadersSeguridadFiltro.java`, `DisponibilidadBDFiltro.java`, `CsrfFiltro.java`, `api/FiltroApiBasic.java` | Sin referencias en otros `.java` | Registrados por `web.xml` (`filter-class`: líneas 24, 28, 32, 40) y mapeados (44-64) |
| `logica/GenerarHashContrasena.java` | Sin referencias en `.java` | Herramienta de consola con `main()` documentada como paso 1 de `sql/crear_administrador.sql` (instrucciones líneas 1-10) y en su Javadoc; `System.out` es su interfaz legítima |
| Pares de archivos "duplicados" del escaneo | Aparecen 2-4 veces | Son el espejo `web/` → `build/web/` y `src/` → `build/classes/` (copia de despliegue), más `docs/TRAZABILIDAD.md` vs `docs/uml/trazabilidad.md` (documentos distintos) |
| `docs/PROMPTS.md` | Sin referencias en código ni TRAZABILIDAD | Historial de prompts; única "Duda" de limpieza del paquete `docs/` (ver Dudas) |

## Verificaciones sin hallazgos (evidencia de ausencia)

- **TODO/FIXME/XXX/HACK**: 0 (búsqueda sensible a mayúsculas en todo `src/java`).
- **Bloques de código comentado** (`// public`, `// if (`, etc.): 0.
- **Catch vacíos**: 0.
- **`printStackTrace`**: 0. `System.out/err` solo en la herramienta de consola (falso positivo arriba).
- **Archivos con nombre `-old/-copia/-v2/-backup/.bak`: 0**; archivos vacíos: 0.
- **CSS sin `<link>`: 0** (46/46 referenciados). **JS sin `<script src>`: 0**.
- **`executeStatement`/SQL sin PreparedStatement**: 0 (todo pasa por `DaoBase`).

## Errores funcionales encontrados (Fase 1C)

| Verificación | Resultado |
|---|---|
| Rutas `action`/`href` de HTML vs `@WebServlet` | **0 rotas** (24 rutas distintas de plantillas contra 39 URLs registradas) |
| Acciones estáticas `accion=...` de formularios vs `case/equals` del servlet | **0 sin manejador** (el único candidato era `{{accionForm}}`, variable dinámica del servlet con valores `crear`/`actualizar`, manejados en `AdminAdministradoresServlet.java:79,104`) |
| Llamadas `DAO.metodo()` sin definición | **0** (285 llamadas distintas resueltas por variable→tipo; compilación exit 0 como respaldo) |
| Referencias de `docs/TRAZABILIDAD.md` (§4/§5/§6) | 137 archivos:línea + 74 Clase.miembro → todas existen (ver `auditoria-trazabilidad.md`); **2 filas obsoletas** (RF-12:195, RF-16:199) a corregir en Fase 4.3 |

## Dudas

- **`sql/crear_administrador.sql` + `GenerarHashContrasena`**: el script está marcado obsoleto y contiene un hash de credencial por defecto; la clase de consola sigue documentada como su generador. Si el script se borra, conviene decidir si la clase también se retira (hoy es la única vía manual de generar un hash) o se conserva. Propuesta: borrar el SQL y conservar la clase con el Javadoc actualizado (o ambas) — decisión tuya.
- **`docs/PROMPTS.md`**: único archivo de `docs/` sin referencias; es historial del proceso. Se mantiene fuera de la tabla principal hasta tu decisión.
- La detección de imports sin usar es heurística de texto (el tipo no reaparece en el archivo); se limpiarán solo los 21 listados y la recompilación posterior confirmará que no se necesitaba ninguno.
