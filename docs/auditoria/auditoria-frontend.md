# Auditoría del frontend (Fase 2)

Fecha: 2026-10-15 · Rama: `auditoria-limpieza` · Modo: **solo lectura** (no se modificó ningún archivo de frontend).

> **Estado de ejecución (2026-10-15, tarde)**: las correcciones aprobadas por el autor se aplicaron en commits pequeños y se reverificaron. Ver sección "Estado de correcciones" al final. El cuerpo original del informe se conserva sin cambios (histórico).

## Metodología y cobertura

- **Estático**: escaneo de las 125 plantillas HTML (38 pantallas + 87 fragmentos), 46 CSS y 2 JS (labels e `id`/`name`, `required/min/max/pattern`, `disabled/hidden`, escapes `{{{ }}}`, UTF-8, botones y enlaces, acciones UI↔servidor en ambos sentidos, CSS/JS sin uso, hojas faltantes, consistencia entre roles).
- **Visual real (CDP)**: **Edge 155 headless** con `Emulation.setDeviceMetricsOverride` a **375 / 768 / 1280 px**, recorriendo todas las pantallas por rol con sesiones reales (admin, docente y estudiante temporales, creados y eliminados al terminar; baseline intacta 20/5/3). **82 capturas + métricas** (scrollWidth, desborde de tablas, errores de consola): `%TEMP%\opencode\fase2-evidencias\` (`metricas-*.jsonl` + PNG por rol/página/ancho).
- **Resultado global de la pasada visual**: `document.documentElement.scrollWidth == viewport` en todas las pantallas (sin scroll horizontal de página) y **0 errores/excepciones de consola** en las 82 capturas.

## Hallazgos

| # | A. Archivo/página | B. Hallazgo | C. Severidad | D. Tipo | E. Evidencia | F. Impacto | G. Propuesta mínima | H. Riesgo de corregir | I. Decisión recomendada |
|---|---|---|---|---|---|---|---|---|---|
| F-01 | `web/html/docente-version-pregunta.html` (+ fragmentos `_fila-opcion.html`, `_form-editar-opcion.html`, `_form-agregar-opcion.html`, `_pregunta-opciones.html`) | La página **no carga `docente-pregunta.css`**, pero renderiza fragmentos cuyas clases (`opcion`, `opcion-texto`, `opcion-acciones`, `formulario-opcion`, `opciones`) **solo están definidas en ese CSS** | MEDIA | INCONSISTENCIA | Head de la página sin `<link ...docente-pregunta.css>` (0 coincidencias); definiciones: `web/css/paginas/docente-pregunta.css` (`.opcion*`, `.opciones`, `.formulario-opcion`); los servlets inyectan esos fragmentos (`DocenteVersionPreguntaServlet.seccionOpciones`, evidenciado en `docs/TRAZABILIDAD.md` CU-22) | La pantalla CU-22 (editar copia de la versión) muestra la sección de opciones **sin estilos**: filas/botones/formularios de opción desalineados respecto a la pantalla hermana de preguntas | Añadir 1 línea `<link .../docente-pregunta.css>` en el head (después de verificar que no haya selectores en conflicto con `docente-version-pregunta.css`; las clases afectadas no se usan en el resto de esta página) | Bajo (CSS aditivo, misma familia ya usada en CU-16) | **Corregir** (1 línea, verificación visual inmediata) |
| F-02 | `web/html/admin-estudiantes.html` (tabla "Estudiantes registrados") | A ≤520 px la tabla se **aplasta** en columnas de ~15 px que envuelven el texto **letra a letra**; la página pasa a **5773 px** de alto y queda ilegible | MEDIA | ERROR FUNCIONAL (responsive) | Métrica CDP `tablasDesbordan=1` a 375/768/1280; PNG `admin-admin-estudiantes-375.png` (375×5773) y recorte `recorte-admin-estudiantes-375.png`; causa raíz: regla global `@media (max-width: 520px)` de `web/css/base.css` (`.tabla th, .tabla td { overflow-wrap: anywhere; word-break: break-word; }`) sobre 5 columnas con cadenas largas (correo/identificación) | La gestión de estudiantes (CU-07) es **inutilizable desde móvil** (RNF-09 se cumple en la métrica de scroll pero no en usabilidad real) | En el bloque ≤520, para esta tabla (o para tablas con >4 columnas) usar el patrón ya presente del proyecto: `white-space` normal + `min-width` por columna dentro de `.tabla-contenedor` (`overflow-x: auto`, `tabla.css:4`) en vez de aplastar columnas, o plegar columnas secundarias con `.dato-secundario` | Medio (toca la regla responsive común o el CSS de la pantalla; **repetir la verificación RNF-09 360/520** tras el cambio) | **Reportar (MEDIA) — decidir antes de corregir** |
| F-03 | `web/html/admin-administradores.html`, `admin-estudiantes.html` (columna de acciones) | En 768/1280 la tabla supera su tarjeta: los botones de acción quedan **cortados** en el borde ("Desactiv…") porque el `overflow-x` del contenedor no es evidente | MEDIA | INCONSISTENCIA (layout) | Métrica `tablasDesbordan=1` en admin-estudiantes (375/768/1280) y admin-administradores (768/1280); PNG `admin-admin-administradores-1280.png` (botón "Desactivar" cortado); `.celda-accion{white-space:nowrap}` (`tabla.css:20`) + contenedor con scroll interno | Acciones de CU-05/CU-07 (editar, restablecer, desactivar) parcialmente ocultas en escritorio; parece roto | Reducir anchos: botones `boton-pequeno` en esa columna, o `flex-wrap` para `.celda-accion` en escritorio, o `min-width` de la tabla dentro de la tarjeta | Bajo (CSS de componente/tabla; verificación visual 3 anchos) | **Reportar (MEDIA) — decidir** (se puede agrupar con F-02 como "tablas admin") |
| F-04 | `web/html/estudiante-historial-practica.html`, `web/html/politica-datos.html` | Usan `class="ayuda"` (y `acciones`) pero **no cargan `formulario.css`**, donde está definida `.ayuda` (también en `docente-grupo.css`); `.acciones` sí viene de `base.css` | BAJA | INCONSISTENCIA | Escaneo: ambos usan `.ayuda`/`.acciones` con `formulario.css` ausente; definición de `.ayuda`: `web/css/componentes/formulario.css` y `web/css/paginas/docente-grupo.css` (no en `base.css`) | Párrafos de ayuda con tipografía/color por defecto distintos al resto de la app (detalle visual menor) | Mover la regla `.ayuda` de `formulario.css` a `base.css` (es un estilo compartido) **o** añadir el link en las 2 páginas | Bajo (cambio de ubicación de 1 regla; revisar duplicado en `docente-grupo.css`) | Reportar (BAJA) — corregir en la fase de correcciones visuales |
| F-05 | `web/css/componentes/boton.css` | No define estado `:disabled` | BAJA | DEUDA TÉCNICA | 0 coincidencias de `:disabled` en el archivo; **0 usos de `disabled` en las 125 plantillas** (escaneo) | Sin impacto actual (no se usa el estado); si en el futuro se deshabilita un botón, se verá como habilitado | Añadir `.boton:disabled { opacity; cursor } ` cuando se necesite (o ahora, preventivo) | Ninguno | Reportar (BAJA) — preventivo |
| F-06 | 41 tablas en 27 plantillas (todas las listas del sistema) | **41/41 tablas sin `<caption>`** (el título visible está fuera de la tabla en un `<h2>`) | BAJA | DEUDA TÉCNICA (accesibilidad) | Escaneo de `<table>` vs `<caption>` = 41/0 | Lectores de pantalla sin nombre accesible de tabla | Añadir `<caption class="oculto">` con el título ya existente en cada lista (patrón `.oculto` ya usado: `docente-grupo.html` usa `class="oculto"`) | Bajo (marcado aditivo, sin efecto visual) | Reportar (BAJA) — lote accesibilidad |
| F-07 | `web/css/**` | **7 valores de breakpoint distintos**: 520 (×4), 640 (×3), 820 (×11), 860 (×2), 920, 980, 1040 | BAJA | DEUDA TÉCNICA | Escaneo de `max-width:*px` en 46 CSS | Mantenimiento impredecible; ajustes responsive dispersos | Consolidar en 3 valores (p. ej. 520/820/1040) en la fase visual, con reverificación por pantalla | Medio (regresiones sutiles posibles; requiere recorrer pantallas) | Reportar (BAJA) — planificar, no urgente |
| F-08 | `src/java/servlet/AdminRendimientoServlet.java` (`accion=coherencia`) | Acción servidor **sin acceso desde UI** (la única de todo el sistema tras el chequeo bidireccional de 39 servlets) | BAJA | DEUDA TÉCNICA | Chequeo bidireccional: `coherencia` solo aparece en el `case`; su Javadoc la documenta como endpoint JSON del runner ("no renderiza páginas… acciones JSON para el runner") | Ninguno funcional (herramienta temporal; el runner externo puede usarla) | Documentar en la fase de cierre o retirarla junto con la herramienta temporal | Ninguno | Reportar (BAJA) |
| F-09 | `web/html/_form-calificar.html:7`, `web/html/_respuesta-texto.html:1` | 2 controles **sin `<label>` ni `aria-label`** (solo `placeholder`): input "comentario" y textarea de respuesta | BAJA | DEUDA TÉCNICA (accesibilidad) | Escaneo de controles sin label/aria (único resultado del sistema; el resto de campos sí tiene label o `for`/`id` correctos) | Lectores de pantalla sin etiqueta del campo | `aria-label` o `<label class="oculto">` | Bajo | Reportar (BAJA) — lote accesibilidad |

## Verificaciones sin hallazgo (evidencia de ausencia)

| Verificación | Resultado |
|---|---|
| Overflow horizontal de página | **0/82 capturas** con `scrollWidth > viewport` (375/768/1280) |
| Errores de consola / excepciones JS | **0** en todas las pantallas |
| Recursos 404 | **0** tras corregir URLs de la sonda (los "404" iniciales eran rutas inexistentes usadas por el script: `/app/docente/practicas`, `/app/estudiante/practicas`, `/app/admin/rendimiento` sin acción — esta última es un endpoint JSON por diseño) |
| `id` duplicados dentro de página | 0 |
| `label for="..."` sin control | 0 |
| Acciones de UI (`accion=...` estáticas) sin manejador en servidor | 0 (Fase 1C) |
| Acciones de servidor sin acceso desde UI | **1** (F-08, endpoint interno documentado) |
| CSS o JS sin uso | 0 (fragmentos muertos ya eliminados en Fase 1) |
| "CSS de página vacíos (24/37)" sospechado en auditorías previas | **NO confirmado**: los 37 CSS de página tienen contenido real (mínimo 232 bytes); la sospecha previa queda descartada con evidencia |
| Codificación UTF-8 | 0 archivos con U+FFFD en `src` y `web` (Fase 1B) |
| Escapes/XSS | `{{{ }}}` inventariados (74 usos justificados de composición por servidor); prueba de XSS runtime E-01/D8 pasó (contenido escapado) |
| Responsive declarado | breakpoints y métricas de página OK (el defecto real de usabilidad es F-02, interno a la tabla) |

## Correcciones propuestas pendientes de decisión

- **MEDIAS (no corregir sin tu OK)**: F-01 (1 línea, estilo perdido en CU-22), F-02 (tabla de estudiantes ilegible ≤520), F-03 (acciones cortadas en tablas admin). Recomendación: aprobar F-01 de inmediato (bajo riesgo, arregla una pantalla rota); decidir enfoque para F-02/F-03 en conjunto (misma raíz de tablas).
- **BAJAS (solo documentadas)**: F-04 a F-09 — agrupables en un "lote accesibilidad/consistencia" futuro.
- No se encontraron hallazgos **CRÍTICOS ni ALTOS**: no aplica la parada de la regla 7 (no hay nada que requiera detener correcciones críticas).

## Evidencia temporal

`%TEMP%\opencode\fase2-evidencias\`: 82 PNG (rol-pantalla-ancho) + `metricas-<rol>.jsonl` (scrollWidth, desbordes, consola) + `recorte-admin-estudiantes-375.png`. No se copiaron capturas al repositorio (solo este informe); si quieres adjuntar alguna como evidencia versionada, dime cuál(es).

## Estado de correcciones (ejecución aprobada, 2026-10-15 tarde)

| Hallazgo | Estado | Detalle |
|---|---|---|
| F-01 | **CORREGIDO** | `docente-version-pregunta.html` carga `docente-pregunta.css`. Verificado por CDP: hoja cargada con reglas y `.formulario-opcion` con `border-top: 1px` (antes 0). |
| F-02 | **CORREGIDO** | Regla localizada ≤520 en las páginas admin: `.tabla { min-width: 40rem }` + `overflow-wrap: normal` en celdas; la tabla hace scroll dentro de `.tabla-contenedor` (que ahora es `position: relative` para recortar los `<span class="oculto">` absolutos). Verificado a **360/375/520/768/1280**: 0 overflow de página, tabla ≥640 px, columnas legibles (captura `verif-admin-admin-estudiantes-375.png`). |
| F-03 | **CORREGIDO** | `.tabla .celda-accion { white-space: normal; display:flex; flex-wrap:wrap; justify-content:flex-end; gap:.25rem }` en ambas páginas admin: acciones completas (0 recortes) a 768/1280. |
| F-04 | **CORREGIDO** | Regla `.ayuda` movida de `formulario.css` a `base.css` (sin duplicidad; `docente-grupo.css` conserva su override). Verificado: `historial` y `politica-datos` con `.ayuda` estilada. |
| F-05 (`:disabled`) | **POSPUESTO** | Sin usos actuales; se documenta como mejora futura (no se introduce código sin necesidad). |
| F-06 | **CORREGIDO** | `<caption class="oculto">` con texto propio en **41/41 tablas (27 archivos)**; `.oculto` usa clip accesible (no `display:none`). Verificado por DOM (1/7/3 captions según pantalla). |
| F-07 (breakpoints) | **POSPUESTO** | Sin error funcional demostrado; consolidación con riesgo de regresión → deuda técnica documentada. |
| F-08 (`accion=coherencia`) | **SOLO DOCUMENTADO** | Endpoint JSON interno del runner de rendimiento (su Javadoc lo declara); sin cambios, sin acceso UI. |
| F-09 | **CORREGIDO** | `aria-label` en el input de comentario (`_form-calificar.html`) y en el textarea de respuesta (`_respuesta-texto.html`), reutilizando el patrón accesible ya presente en los fragmentos (se renderizan repetidos; sin ids fijos). |

**Verificación (obligatoria) ejecutada**: `javac -Xlint` limpio · despliegue + smoke 8/8 · CDP **360/375/520/768/1280** sobre pantallas tocadas (30 mediciones): 0 overflow de página, tablas usables, 0 botones recortados/invisibles, 0 errores de consola, **0 recursos 404**, captions y labels comprobados por DOM · limpieza de datos temporales (baseline intacta 20/5/3/7/24/4/4). Evidencias: `%TEMP%\opencode\fase2-evidencias\verif*.png` + `verif.jsonl`.
