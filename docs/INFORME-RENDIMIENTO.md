# Informe de rendimiento — RNF-07 y RNF-08

Prueba de carga sobre el sistema real (Tomcat + MySQL + la misma máquina como cliente), ejecutada
desde el script `docs/pruebas/rendimiento-carga.ps1`. La evidencia queda en
`docs/pruebas/resultados-<marca>.json` y en los PDF de 50 preguntas de las corridas válidas.

## Criterios del ERS, citados textualmente

- **RNF-07 Concurrencia en evaluaciones virtuales**: «El sistema soporta al menos 40 estudiantes
  presentando simultáneamente una evaluación virtual (tamaño de una sala de cómputo). Criterio de
  aceptación: Con 40 usuarios concurrentes, el 95 % de las operaciones de guardar respuesta responde
  en menos de 2 segundos.»
- **RNF-08 Tiempo de respuesta general**: «[…] Criterio de aceptación: Páginas de consulta: < 3 s.
  Generación de un PDF de evaluación de hasta 50 preguntas: < 10 s. La generación con IA se ejecuta
  de forma asíncrona sin bloquear la interfaz.»

## Entorno de las mediciones

| Dato | Valor |
|---|---|
| Fecha | 2026-09-25, 14:51–14:56 (-05:00) |
| Máquina | Windows 11 Pro (10.0.26200), AMD Ryzen 5 7535HS, 6 núcleos / 12 hilos, 7,5 GB RAM |
| MySQL | 8.4.9 (localhost) |
| Tomcat | 10.0.23, único proceso Java en ejecución (`procesosJava=1`) |
| Pool de conexiones | `maxTotal=20` (`web/META-INF/context.xml`), **sin tocar en todo el ejercicio** |
| Generador de carga | PowerShell 5.1 con 40 runspaces, **en el mismo equipo** que el servidor y la base |

Medición conservadora: cliente, servidor y base compiten por los mismos 6 núcleos.

## Método

1. El script se niega a correr si encuentra restos de una corrida anterior
   (`usuario.correo LIKE 'carga-rendimiento-%'`) y limpia **siempre** al terminar, incluso si falla,
   borrando solo los registros de su manifiesto (al final: 0 usuarios de prueba restantes).
2. Crea docente, curso, grupo, banco con **50 preguntas**, evaluación, versión con esas 50 preguntas y
   una evaluación virtual ABIERTA; **40 estudiantes** con su intento EN_CURSO.
3. Los 40 runspaces inician sesión y esperan una barrera común; al liberarla, cada estudiante envía
   **10 guardados automáticos** (`accion=responder`), luego una carga de página de consulta.
4. Con la sesión del docente se genera **3 veces** el PDF de la versión de 50 preguntas.

## Diagnóstico: por qué las primeras corridas dieron ~2 s

La primera tanda midió guardados de ~1,8–2,2 s con p50 ≈ p95, es decir, un coste constante y no una
cola de saturación. Se diagnosticó **antes** de dimensionar nada:

1. **Guardado suelto, servidor en reposo, con `curl`** (cliente ajeno a .NET): **4–6 ms**.
   `GET /login`, que pasa por el filtro de disponibilidad de la base, tarda 3–60 ms.
   → El servidor y el filtro quedan descartados.
2. **Rampa de concurrencia con `curl` puro** (mismo guardado, peticiones simultáneas):

   | Concurrentes | Media | Máx |
   |---|---|---|
   | 1 | 4,0 ms | 4,0 ms |
   | 2 | 5,7 ms | 6,9 ms |
   | 5 | 8,5 ms | 9,5 ms |
   | 10 | 15,6 ms | 23,1 ms |
   | 20 | 23,1 ms | 35,9 ms |
   | 30 | 39,3 ms | 54,3 ms |
   | 40 | **58,7 ms** | **69,6 ms** |

   → El servidor atiende 40 guardados concurrentes en menos de 70 ms; el pool de 20 no es el cuello.
   Un `GET /login` × 40 concurrentes dio 11 ms de media.
3. **A/B del cliente PowerShell**: 40 runspaces haciendo un `GET` trivial → **2066 ms** de media;
   los mismos 40 con `$ProgressPreference = 'SilentlyContinue'` dentro del runspace → **7 ms**.
   → La latencia era de la barra de progreso de `Invoke-WebRequest`, que escribe en el host
   compartido y serializa los runspaces. El coste era del **generador de carga**, no del sistema.

**Corrección**: el script ahora silencia el progreso dentro de cada runspace y ajusta dos límites del
cliente (.NET: conexiones por host y mínimo de hilos del pool). **No se modificó nada del servidor**
ni del pool de conexiones.

### Corridas (todas con el mismo sistema; solo cambió el cliente)

| Corrida | Cliente | Guardados p95 | Páginas p95 | PDF | Veredicto |
|---|---|---|---|---|---|
| 1–2 | original (progreso activo) | 1883,7 / 1911,3 ms | 1887,2 / 1816,3 ms | 34–52 ms | artefacto del cliente |
| 3–4 | + límite de conexiones | 2208,9 / 2196,5 ms | 2101,5 / 2126,5 ms | 34–239 ms | artefacto del cliente |
| 5–6 | + preferencia en el padre (no llegaba al runspace) | 2139,9 / 2164,1 ms | 2107,7 / 2134,7 ms | 17–21 ms | artefacto del cliente |
| **7** | **corregido** | **100,4 ms** | **19,6 ms** | 19,6–20,8 ms | **válida** |
| **8** | **corregido** | **95,3 ms** | **45,9 ms** | 18,0–21,1 ms | **válida** |

## Resultados válidos (corridas 7 y 8)

| Métrica | Corrida 7 | Corrida 8 |
|---|---|---|
| Guardados (n=400) p50 | 30,2 ms | 33,3 ms |
| Guardados p95 | 100,4 ms | 95,3 ms |
| Guardados máx | 154,1 ms | 150,5 ms |
| Guardados con error | 0 | 0 |
| Página de consulta (n=40) p95 | 19,6 ms | 45,9 ms |
| PDF 50 preguntas (3 generaciones) | 20,8 / 19,6 / 19,7 ms | 20,2 / 18,0 / 21,1 ms |

## Veredictos

| Criterio del ERS | Umbral | Medido | ¿Cumple? |
|---|---|---|---|
| RNF-07: «el 95 % de las operaciones de guardar respuesta responde en menos de 2 segundos» | p95 < 2000 ms | 100,4 ms y 95,3 ms | **Sí**, con amplio margen |
| RNF-08: «Páginas de consulta: < 3 s» | p95 < 3000 ms | 19,6 ms y 45,9 ms | **Sí** |
| RNF-08: «Generación de un PDF […] de hasta 50 preguntas: < 10 s» | < 10 000 ms | ≤ 21 ms | **Sí** |

## Observaciones

- El PDF de 50 preguntas de la corrida 7 se verificó con la librería del proyecto: **4 páginas**,
  encabezado (título, versión y docente) y «Página N» en las cuatro, y **ninguna pregunta partida**.
- El pool quedó en `maxTotal=20`: ni el diagnóstico ni las corridas válidas muestran que haga falta
  más. Si en producción apareciera contención, la medición sería la misma metodología.
- La generación con IA no se mide aquí: depende de un servicio externo y ya corre fuera del hilo de
  la petición (RNF-08).

> **Nota fechada (2026-09-29, Fase 6)**: la afirmación "ya corre fuera del hilo de la petición" era
> aspiracional en el momento de este informe; la asincronía se implementó posteriormente en la Fase 6
> (`servicio.ServicioGeneracionIA` + `servlet.ProcesadorGeneracionIA`). Las mediciones históricas de
> este documento no se reescriben. Evidencia de la Fase 6: `fase6-evidencias/` (POST de registro < 2 s
> con proveedor simulado de 5–30 s).
- La primera tanda de mediciones queda documentada por honestidad: el error era del instrumento, y
  detectarlo exigió medir con un cliente distinto (curl) y una rampa de concurrencia.

## Cómo repetirlo

```powershell
# Con Tomcat en HTTP local (secure=false, ver README) y MySQL en marcha:
powershell -ExecutionPolicy Bypass -File docs\pruebas\rendimiento-carga.ps1
```

Parámetros opcionales: `-Usuarios`, `-GuardadosPorUsuario`, `-Preguntas`, `-BaseUrl`.
