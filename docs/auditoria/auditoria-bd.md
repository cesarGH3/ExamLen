# Auditoría y limpieza de la base de datos (Fase 3)

Fecha: 2026-10-15 · Rama: `auditoria-limpieza` · Base: `examlen` (MySQL 8.4) · Script aplicado: `docs/auditoria/limpieza-bd.sql` (transacción única, COMMIT exitoso).

## Salvaguardas aplicadas

- **Tomcat detenido** antes de cualquier DELETE (0 escrituras de la aplicación).
- **Respaldo completo previo** (fuera del repositorio, no versionado): `%TEMP%\opencode\backup-examlen-pre-limpieza.sql` (60.494 bytes, `--single-transaction --no-tablespaces`, sin credenciales en el archivo).
- **Conteos del inventario revalidados DOS veces** inmediatamente antes de ejecutar (coincidencia exacta con la lista aprobada).
- Script **determinista** con identificadores concretos verificados (usuarios demo `2,3,4,5,6,8..21`; documentos `1,2`; solicitudes `2..6`), **una transacción**, sin `SET FOREIGN_KEY_CHECKS=0`, sin `TRUNCATE`, sin `DROP`; ante error el cliente aborta sin COMMIT (rollback implícito).

## Inventario ANTES (conteos exactos)

| Tabla | Filas | Clasificación | Origen |
|---|---|---|---|
| opcion_respuesta | 46 | DATO DE PRUEBA | opciones de preguntas demo |
| version_opcion | 30 | DATO DE PRUEBA | copias en versiones demo |
| pregunta | 24 | DATO DE PRUEBA | bancos demo |
| usuario | 20 | 1 CONSERVAR + 19 DATO DE PRUEBA | admin funcional + cuentas demo de desarrollo |
| esquema_migracion | 17 | DATO TÉCNICO — CONSERVAR | control de migraciones |
| version_pregunta | 13 | DATO DE PRUEBA | — |
| docente / estudiante | 10 / 9 | DATO DE PRUEBA | hijas de las cuentas demo |
| evaluacion_pregunta | 9 | DATO DE PRUEBA | — |
| banco_preguntas | 7 | DATO DE PRUEBA | — |
| curso | 5 | DATO DE PRUEBA | — |
| solicitud_generacion | 5 | DATO DE PRUEBA | historial IA demo (4 COMPLETADA, 1 FALLIDA) |
| administrador | 1 | CONSERVAR | el administrador funcional |
| evaluacion / version_evaluacion | 4 / 4 | DATO DE PRUEBA | — |
| grupo | 3 | DATO DE PRUEBA | — |
| documento_fuente | 2 | DATO DE PRUEBA + HUÉRFANO | rutas físicas ya inexistentes (verificado) |
| material_practica / material_pregunta | 2 / 2 | DATO DE PRUEBA | — |
| curso_material | 1 | DATO DE PRUEBA | — |
| 9 tablas restantes (intentos, respuestas, virtuales, inscripciones, eventos, tokens) | 0 | CONSERVAR estructura | — |
| Prefijos de harness (FINAL11/FASE/PERF/BR/AUD) | 0 | — | los harness ya habían limpiado |
| Huérfanos estructurales | 0 | — | FK garantizan integridad |

Estructura: **29 tablas físicas** (28 funcionales + `esquema_migracion`), **47 FK** (12 RESTRICT + 30 NO ACTION + 5 CASCADE).

## Filas eliminadas (COMMIT exitoso, exit code 0)

| Tabla | Eliminadas |
|---|---|
| opcion_respuesta | 46 |
| version_opcion | 30 |
| pregunta | 24 |
| usuario (demo) | 19 (arrastra docente/estudiante por CASCADE) |
| version_pregunta | 13 |
| docente / estudiante | 10 / 9 (implícitas por CASCADE) |
| evaluacion_pregunta | 9 |
| banco_preguntas | 7 |
| curso | 5 |
| solicitud_generacion | 5 |
| evaluacion / version_evaluacion | 4 / 4 |
| grupo | 3 |
| documento_fuente | 2 |
| material_practica / material_pregunta | 2 / 2 |
| curso_material | 1 |
| **Total filas explícitas** | **176** (+ docente/estudiante por CASCADE) |

## Estado DESPUÉS (verificado por el propio script y reconsultado)

`usuario 1 · administrador 1 · docente 0 · estudiante 0 · curso 0 · grupo 0 · banco_preguntas 0 · pregunta 0 · opcion_respuesta 0 · evaluacion 0 · evaluacion_pregunta 0 · version_evaluacion 0 · version_pregunta 0 · version_opcion 0 · material_practica 0 · material_pregunta 0 · curso_material 0 · solicitud_generacion 0 · documento_fuente 0 · esquema_migracion 17 · tablas 29 · FK 47`

- **Administrador conservado**: `1000000000 / admin@examlen.local` (activo; contraseña, correo, rol y datos intactos).
- **`esquema_migracion` conservada**: 17 registros de control técnico.

## Verificación funcional (8/8 OK)

1. Tomcat arrancó con la BD limpia (aplicación al día). 2. Login admin → inicio. 3. Inicio administrativo carga. 4. Alta de docente temporal + cambio obligatorio + login (flujo normal, CU-06/CU-09). 5. Creación de curso temporal y persistencia. 6. Desactivación del curso por el flujo normal. 7. Limpieza de los datos de esa prueba por ids conocidos → estado final `1/1/0/0/0/17`. 8. **0 SEVERE nuevos** en los logs.

## Seeds / prevención

- Revisadas las migraciones 001–017: **ninguna inserta datos demo**. En `001_usuarios.sql` el "INSERT" detectado es un `GRANT SELECT, INSERT, UPDATE`; los tres `INSERT` de `007_herencia_usuario.sql` son transformaciones de herencia (`usuario` → `administrador`/`docente`/`estudiante`), no datos de prueba. **No se modificó ninguna migración.**
- Por tanto, los datos eliminados provenían del **desarrollo y las pruebas manuales** (cuentas y contenidos demo), no de migraciones productivas.

## Dudas

- El `GRANT ... TO 'examlen_app'@'localhost'` dentro de `001_usuarios.sql` es histórico (el usuario real del entorno es `@127.0.0.1`); no es dato demo y no se tocó. Si se desea modernizar, sería otro cambio con su propia aprobación.
- Los 2 `documento_fuente` eran metadatos de archivos ya inexistentes en disco; no había archivos físicos que borrar.
