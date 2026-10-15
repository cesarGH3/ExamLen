# Informe de auditoría — ExamLen

**Proyecto**: ExamLen, sistema de evaluación y práctica de inglés.
**Ubicación**: `C:\Users\STEINER1TB\Documents\NetBeansProjects\ExamLen`
**Fecha del informe**: 2026-09-28 (zona America/Bogota).
**Documento fuente de verdad**: `docs/ERS-resumen.md` (63 RF, 18 RNF, 34 CU). Todo lo demás cede ante él.
**Propósito**: entregar a una IA auditora el estado real del sistema, sus decisiones, su evidencia de pruebas
y sus brechas conocidas, sin adornos.

---

## 1. Resumen ejecutivo

ExamLen es una aplicación web de evaluación de inglés con tres roles (administrador, docente, estudiante),
construida con **Java 17 + Servlets (Jakarta EE 10) + MySQL 8.4**, interfaz **solo HTML/CSS** con un motor de
plantillas propio (`servlet/Vistas.java`), sin frameworks de frontend y con JavaScript mínimo (un solo archivo,
el del examen). El proyecto se desarrolló por fases siguiendo ocho reglas fijas (sección 2) y cada fase terminó
con pruebas funcionales sobre HTTP con datos temporales que luego se borraban.

Estado general: **funcionalidad amplia y probada**, con zodocumentación interna (`TRAZABILIDAD.md`), una
desviación pendiente de decisión del autor (hash de contraseñas), un requerimiento sin implementar
(cierre automático de evaluaciones virtuales, RF-42), y un **hallazgo de configuración detectado en esta
auditoría**: el descriptor de despliegue de Tomcat no refleja el URL de conexión del proyecto (sección 11.1).

---

## 2. Reglas de trabajo con las que se construyó el código

1. La ERS manda; si el código difiere, el código es el que está mal. Los desacuerdos se reportan, no se
   “arreglan” cambiando el documento.
2. Cero interfaz en Java: ningún `.java` contiene HTML, estilos ni cadenas que construyan marcado.
3. Una pantalla, un HTML (`web/html/`) y una hoja (`web/css/paginas/`); fragmentos reutilizables en
   `web/html/_*.html` y estilos compartidos en `web/css/componentes/`.
4. Solo HTML y CSS en el cliente, sin frameworks. Librerías del servidor permitidas.
5. El estilo visual se conserva.
6. JavaScript mínimo: temporizador, autoguardado y eventos de supervisión del examen.
7. Todas las consultas con `PreparedStatement`; operaciones multi-tabla dentro de transacción.
8. Nada fuera del alcance; los defectos encontrados se anotan en el informe en vez de arreglarse de paso.

---

## 3. Ficha técnica e inventario

| Elemento | Valor |
|---|---|
| JDK | Eclipse Adoptium 17.0.20.101 |
| Servidor | Apache Tomcat 10.0.23 (Jakarta EE 10) |
| Base de datos | MySQL 8.4.9, base `examlen`, InnoDB, utf8mb4 |
| Librerías (servidor) | mysql-connector-j 9.6.0, OpenPDF 1.3.43, PDFBox 2.0.31, Gson 2.11.0, jakarta.mail 2.0.1 |
| Usuario de BD | `examlen_app` (usuario dedicado; ya no se usa root) |
| Archivos Java | 146 (`src/java`), 173 clases compiladas |
| Vistas | 36 pantallas HTML + 79 fragmentos `_*.html` |
| Estilos | `base.css` + 8 hojas de componente + 36 hojas de pantalla |
| JavaScript | 1 archivo (`web/js/examen.js`) |
| Migraciones | 13 (`sql/migraciones/001…013`), todas aplicadas |
| Tablas | 28 (coinciden exactamente con el diccionario de la ERS) |
| Claves foráneas | 47: 5 en CASCADE (3 de herencia + `opcion_respuesta` + `token_recuperacion`), el resto RESTRICT/NO ACTION |
| Datos reales actuales | 20 usuarios, 5 cursos, 3 grupos, 7 bancos, 24 preguntas, 4 evaluaciones, 4 versiones, 2 materiales de práctica, 0 intentos |

**Estructura de paquetes** (arquitectura por capas):

- `conexion/` — acceso al `DataSource` por JNDI (`jdbc/ExamLenDB`).
- `servicio/` — integraciones y utilidades de servidor: `ProveedorIA` + `ClienteGemini`/`ClienteGroq`,
  `ConfiguracionIA`, `ConfiguracionSupervision`, `ExtractorTexto`, `ServicioCorreo`, `ConfiguracionCorreo`,
  `GeneradorWordPractica`, `GeneradorContrasenaTemporal`.
- `logica/` — entidades y reglas: usuarios, cursos, bancos, evaluaciones, versiones, intentos, práctica,
  `Fechas` (zona horaria), `AnalisisSupervision` (similitud/tiempos), `HashContrasena`, `PoliticaContrasena`,
  `Validador`, excepciones de dominio.
- `modelo/` — DAOs JDBC sobre `DaoBase` (consultas parametrizadas, `enTransaccion`, traducción de errores
  de duplicado/referencia en uso), `Mapeos`, `VerificadorBaseDatos`, `PdfFormato` (encabezado/pie de PDF).
- `servlet/` — 49 clases: servlets, filtros y soporte (`Vistas`, `Html`, `TokenCsrf`, `SesionesActivas`,
  `Formulario`, `Etiquetas`, `ServletBase`).

**Despliegue**: `docBase=C:/…/ExamLen/build/web` (descriptor
`conf/Catalina/localhost/ExamLen.xml`); `web.xml` define cuatro filtros en orden (cabeceras de seguridad →
disponibilidad de BD → CSRF → autenticación), sesión de 30 minutos, cookie `Secure`+`HttpOnly`, y bloqueo
del acceso directo a `/html/*` (las plantillas solo las sirve el motor).

---

## 4. Modelo de datos y migraciones

28 tablas que replican el diccionario de la ERS. Las 13 migraciones documentan la evolución completa:

| # | Migración | Qué hace |
|---|---|---|
| 001 | `usuarios` | Base inicial de usuarios y roles |
| 002 | `cursos_banco_preguntas` | Cursos, grupos, bancos, preguntas |
| 003 | `alineacion_diagrama_v3` | Alineación al diagrama de clases v3 |
| 004 | `evaluacion_pregunta_orden_puntaje` | Orden/puntaje en la selección de la evaluación |
| 005 | `intento_unico` | Un intento por estudiante y evaluación virtual |
| 006 | `nombres_ers` | Renombre de columnas a los nombres de la ERS |
| 007 | `herencia_usuario` | Herencia tabla-por-subclase (`usuario` + 3 hijas) |
| 008 | `institucion_diccionario` | `usuario.institucion` a VARCHAR(120) NULL |
| 009 | `codigos_version` | Códigos de versión V… → A, B, C… |
| 010 | `respuesta_practica_sin_calificar` | `respuesta_practica.es_correcta` pasa a NULL-able (abiertas sin calificar) |
| 011 | `titulo_material_150` | `material_practica.titulo` a VARCHAR(150) según ERS |
| 012 | `consentimiento_datos` | `estudiante.aceptacion_tratamiento_datos DATETIME NULL` |
| 013 | `foraneas_restrict` | 11 FK de CASCADE a RESTRICT (RNF-14) |

**Reglas de borrado vigentes (verificadas en `information_schema`)**:

- **CASCADE (5)**: `administrador→usuario`, `docente→usuario`, `estudiante→usuario` (herencia, correcto),
  `opcion_respuesta→pregunta` (la ERS lo pide expresamente) y `token_recuperacion→usuario` (decisión del
  autor: un token no es historial, caduca en 30 minutos).
- **RESTRICT/NO ACTION (42)**: protegen historial. En particular, borrar una evaluación, versión o material
  referenciado se rechaza; el borrado de una versión aplicada con preguntas se hizo transaccional en el DAO
  (`VersionEvaluacionDAO.eliminarVersion` borra opciones→preguntas→versión).

---

## 5. Cobertura funcional por módulo

> La matriz detallada requisito↔código está en `docs/TRAZABILIDAD.md`; aquí se resume el estado real.
> Entre paréntesis, los RF que se intervinieron o verificaron de forma expresa en las fases de trabajo.

**Usuarios, acceso y administración (RF-01–RF-11, RNF-02, RNF-04)**
Registro público de estudiantes (con consentimiento, RF/RNF-13), inicio de sesión por correo o identificación
con bloqueo por intentos fallidos, cambio de contraseña con política (8–128, mayúscula, minúscula, número y
especial), recuperación por correo con token aleatorio de 128 bits almacenado con hash, caducidad 30 min,
uso único, respuesta genérica y límite de 3 solicitudes/hora (RF-11). Administración de docentes, estudiantes
y administradores con creación, edición, activación/desactivación, restablecimiento (respaldo de RF-11) y
eliminación con auto-protecciones (RF-06/08/09/10). Acciones delicadas con pantalla de confirmación genérica
(`confirmar.html`). Control de acceso por rol y propiedad en el servidor (403 para accesos indebidos).

**Cursos, grupos y bancos (RF-12–RF-24 aprox.)**
Cursos con nivel, activación/desactivación; grupos con código de acceso aleatorio de 8 caracteres,
cierre/reapertura, regeneración de código y gestión de inscritos (quitar/reincorporar). Bancos y preguntas
con cuatro tipos (selección múltiple, verdadero/falso, respuesta corta, abierta), estados
BORRADOR/APROBADA/ARCHIVADA y reglas de aprobación (opciones fijas y única correcta, etc.).

**Evaluaciones y versiones (RF-31–RF-38)**
Creación, configuración (preguntas por versión, versiones 1–10, duración), selección por grupos de
equivalencia (competencia/dificultad) con resumen, marcado como LISTA, archivado. Generación de versiones:
orden aleatorio de preguntas y opciones **congelado** (`version_pregunta.orden`, `version_opcion.orden`),
códigos A…Z y AA… tras Z, conservación de versiones al regenerar (RF-35/36), cuadro comparativo con
distribuciones y punto de referencia, resaltado solo con 3+ versiones y diferencia ≥ umbral constante
(`UMBRAL_DIFERENCIA_COMPARATIVA = 2`) (RF-37). Edición de la copia de la versión sin tocar el banco, con
bloqueo si la versión está aplicada a un parcial abierto o cerrado o tiene intentos (RF-38); borrado de
versión con confirmación y protegido por FK.

**Aplicación virtual e intento (RF-41–RF-51, RNF-03, RNF-05)**
Programación por fechas, apertura/cierre/cancelación manual (con finalización de intentos en curso),
reparto equilibrado de versiones, intento único por estudiante y evaluación, autoguardado por
`fetch`/`sendBeacon` (temporizador, autoguardado y supervisión son el único JavaScript permitido),
advertencia de preguntas sin responder con sus números antes de enviar (RF-48), finalización automática por
tiempo o fecha de fin, calificación automática de preguntas objetivas al finalizar (RF-51), comparación de
respuesta corta **normalizando** (recorte, espacios colapsados, sin distinguir mayúsculas). La evaluación
**cancelada** tiene estado propio: el estudiante ve “Cancelada”, el aviso de que no produce calificación y el
detalle de lo respondido sin nota ni puntajes; en la lista solo aparece si presentó.

**Calificación y resultados (RF-52–RF-56)**
Calificación manual de abiertas y **recalificación** de cualquier respuesta, incluida una automática, con
tope = puntaje de la pregunta en la versión (RF-52). La calificación del intento es la suma y pasa a
CALIFICADO cuando todas las respuestas tienen puntaje. Lista de intentos del docente con versión, inicio,
fin, tiempo empleado, estado, nota e indicadores (RF-54). Resultado del estudiante con **detalle por
pregunta** (respuesta dada, puntaje, comentario) y **sin la respuesta correcta**, deliberadamente, porque el
parcial puede seguir abierto o aplicarse después a otro grupo (RF-56); mientras la virtual esté ABIERTA o el
intento no esté calificado, muestra “Pendiente de calificación” (CU-31), incluso si ya tenía nota
(cancelación y cierre mandan).

**Supervisión (RF-49, RF-50, RF-55, RNF-18)** — ver sección 7.

**Práctica (RF-57–RF-62)** — ver sección 8.

**IA (RF-25–RF-30)** — ver sección 9.

---

## 6. Seguridad y privacidad (estado real y hallazgos)

| Control | Estado |
|---|---|
| Hash de contraseñas | **PBKDF2-SHA256 con 600 000 iteraciones** (`logica/HashContrasena.java`). La ERS pide BCrypt costo ≥10 o Argon2id. **Decisión pendiente del autor del documento; no se tocó** (por indicación expresa). |
| CSRF | Filtro que exige token de sesión en todo POST; token inyectado por el motor de plantillas y por formularios generados. |
| Sesión | 30 min de inactividad, cookie `HttpOnly`, `Secure`, `SameSite=Strict`, tracking solo por cookie, sesión nueva al autenticar (evita fijación). |
| HTTPS/cabeceras | HSTS `max-age=31536000` en todas las respuestas; CSP `default-src 'none'; script-src 'self'; connect-src 'self'; …`; `X-Content-Type-Options`, `X-Frame-Options: DENY`. |
| Bloqueo por fuerza bruta | Intentos fallidos y `bloqueado_hasta` en `usuario`; códigos de grupo con límite por sesión. |
| Token de recuperación | 128 bits, hash en BD, 30 min, un solo uso, respuestas idénticas exista o no el correo, límite de 3/hora. |
| Acceso | Filtro por rol (`/app/estudiante/*` solo Estudiante, etc.); propiedad de recursos validada en cada servlet; pruebas de acceso ajeno devuelven 403/404. |
| Plantillas | `/html/*` bloqueado por `security-constraint`: solo el motor las sirve. |
| Datos personales (RNF-13) | Registro con casilla obligatoria + fecha en `estudiante.aceptacion_tratamiento_datos`; página `/politica-datos` (estática) con datos recogidos, finalidad, responsable (campo en blanco para la institución), conservación y derechos. **La política es un borrador pendiente de revisión legal**; la marca está en README e informes, no en la página. |
| Integridad histórica (RNF-14) | 11 FK a RESTRICT + borrado transaccional de versiones; borrados de usuarios con historial rechazados. |
| Secretos | Las claves de IA viven fuera del repositorio (`{Catalina Base}/conf/ia.properties`; hay ejemplo en `config/`). **`web/META-INF/context.xml` contiene la contraseña de la base de datos en el repositorio** (usuario dedicado de desarrollo); el README indica moverla a `catalina.properties` en producción. Riesgo a considerar por la auditoría. |

---

## 7. Supervisión del examen en detalle (RF-49/50/55, RNF-18)

- **Registro de eventos**: endpoints del navegador (cambio de pestaña, pérdida de foco, salida de página,
  desconexión, reconexión) con fecha/hora del servidor; solo se registran si el intento está EN_CURSO y la
  virtual tiene supervisión activa.
- **Análisis al cerrar** (RF-50), no por intento suelto:
  - **Tiempo atípico**: un intento es atípico si dura **menos del 25 % de la mediana del grupo**; un evento
    por intento con la mediana en la descripción.
  - **Similitud**: comparación de respuestas **abiertas y cortas** entre pares de intentos, por pregunta
    original (funciona entre versiones distintas). Medida **explicable**: coeficiente de **Jaccard sobre
    trigramas de caracteres**; umbral **0,85**.
  - **Reglas anti falso positivo**: mínimo 40 caracteres normalizados; se descarta el par si cualquiera de
    las dos respuestas **coincide con la respuesta esperada** (normalización igual a la de práctica) — dos
    estudiantes que contestaron bien la misma pregunta corta no son un indicio.
  - Un solo evento por par, replicado en ambos intentos, citando al otro estudiante, la pregunta y el texto.
  - Solo al **cerrar** (no al cancelar), solo si la virtual venía de ABIERTA y **idempotente** (si ya hay
    eventos de esos tipos, no se repiten). Las virtuales canceladas no dejan indicios.
  - **Umbrales configurables** (ERS: “umbrales configurables”): `{Catalina Base}/conf/supervision.properties`
    con `supervision.similitud.umbral=0.85` y `supervision.tiempo.fraccion.mediana=0.25` (o variables
    `EXAMENL_SUPERVISION_SIMILITUD`/`EXAMENL_SUPERVISION_TIEMPO`); si faltan o son inválidos, se usan los
    valores del ERS. El mínimo de 40 caracteres es una **constante documentada** a propósito (protección
    contra falsos positivos, no configurable).
- **Vista del docente (RF-55)**: indicadores agrupados por tipo con conteo, línea de tiempo cronológica y,
  **siempre**, la nota literal: “Los indicadores no determinan por sí solos una conducta indebida.”
- **Aviso previo (RNF-18/CU-27)**: pantalla “Antes de comenzar” con instrucciones, duración, preguntas, lo
  que se registra (los cinco tipos) y una nota informativa de que son indicadores, no una acusación.
- **Privacidad**: los eventos y sus descripciones (que citan a otros estudiantes) solo son visibles en las
  pantallas del docente; se verificó que ninguna pantalla del estudiante los muestra.

---

## 8. Módulo de práctica (RF-57–RF-62)

- Materiales con **nivel** (obligatorio) y filtro por nivel en la vista del estudiante (RF-57/59).
- Resolución con **retroalimentación real** (RF-60): columnas “Respuesta correcta” (opción correcta o
  respuesta esperada) y “Retroalimentación” que solo se rellena en las **incorrectas** con “Repasa: tema
  (COMPETENCIA)”; en las abiertas invita a comparar con la esperada; las correctas dejan la celda vacía.
- **Sin autocalificación de abiertas**: `respuesta_practica.es_correcta` es NULL para abiertas (y para
  cortas sin respuesta esperada) — migración 010, alineada con el ERS. El porcentaje se calcula **sobre las
  calificables** (“Resultado sobre N de M preguntas calificables”); si ninguna es calificable, muestra “Sin
  calificación automática”.
- Escritura del intento y todas sus respuestas **en una transacción**.
- **Historial (RF-62)**: intentos con fecha, material y resultado, más dos tablas de desempeño
  (competencia y tema) con Respondidas / Correctas / % de acierto, excluyendo lo no calificado; enlace en el
  menú del estudiante y desde Práctica.
- La comparación de respuestas cortas se normaliza (recorte, espacios colapsados, sin distinguir mayúsculas).

---

## 9. Integración con IA (RF-25–RF-30)

- Proveedor encapsulado tras `servicio/ProveedorIA` con `ClienteGemini` y `ClienteGroq` (cambio de proveedor
  sin tocar el resto; RNF-12). Claves fuera del repositorio.
- El formulario de generación tiene **tema y competencia** como campos explícitos: obligatorios para
  PREGUNTAS y MATERIAL_PRACTICA, opcionales para VARIANTES (que conservan la competencia de las preguntas
  base). Las preguntas generadas se crean con esos valores, no con los que decida el modelo (RF-26).
- El **modelo efectivo** se persiste en la solicitud (RF-29): si el docente deja el modelo vacío, se guarda
  el predeterminado del proveedor, incluso cuando la generación falla. Sin clave de IA, la solicitud queda
  FALLIDA con un mensaje explícito de cómo configurarla (decisión: sin generación no hay modelo).
- Todo el contenido generado queda en BORRADOR hasta revisión del docente (RF-30).
- **Hallazgo**: la generación se ejecuta **dentro de la petición** (síncrona). RNF-08 pide que la generación
  con IA sea “asíncrona sin bloquear la interfaz”. No implementado; se reporta.

---

## 10. Documentos imprimibles (RNF-17)

- Ambos PDF (examen/clave y material/clave) usan **tamaño carta**, márgenes de 2 cm, **encabezado repetido
  en cada página** y numeración “Página N”; cada pregunta es un bloque que no se parte entre páginas.
- Examen: encabezado con **título, versión y docente** (sin curso: una versión puede aplicarse a varios
  grupos después; decisión del autor). Material: encabezado con **título y cursos asignados**.
- Verificado extrayendo el texto de un PDF de 50 preguntas: 4 páginas, encabezado y pie en todas, ninguna
  pregunta partida (evidencia en `docs/pruebas/pdf-50-preguntas-*.pdf`).

---

## 11. Hallazgos, brechas conocidas y decisiones

### 11.1 Hallazgo de configuración detectado en esta auditoría (requiere acción)
El descriptor de despliegue `conf/Catalina/localhost/ExamLen.xml` tiene las credenciales correctas del
usuario `examlen_app`, pero **el URL de conexión antiguo**: usa `serverTimezone=America/Bogota` y **no**
`connectionTimeZone=America/Bogota` + `sessionVariables=time_zone='-05:00'` que sí están en
`web/META-INF/context.xml`. Consecuencia: en el despliegue actual la sesión de MySQL queda en `SYSTEM`
(UTC-5 en esta máquina, así que funcionalmente coincide **hoy**, pero RNF-15 no queda forzado de forma
explícita). Acción sugerida: sincronizar el descriptor con el URL del proyecto.
Nota relacionada: `mysql.time_zone_name` está vacío (0 zonas cargadas); por eso se usa el desplazamiento
fijo `-05:00` (Colombia sin DST desde 1993). El README documenta cómo cargar las tablas horarias.

### 11.2 Requisitos no implementados o parciales
- **RF-42 (cierre automático por fechas)**: no hay programador ni cierre automático; el docente debe cerrar.
  Consecuencia: el análisis de supervisión (RF-50) y la visibilidad de resultados dependen del cierre manual.
  Estaba planificado como tarea aparte.
- **Apertura automática por fechas**: pendiente por la misma razón (la disponibilidad se evalúa por fecha,
  pero el estado no cambia solo).
- **RNF-08 (IA asíncrona)**: la generación bloquea la petición (sección 9).
- **RNF-03 (sesión de 30 min “salvo durante un intento”)**: no hay extensión explícita de sesión durante un
  intento en curso; en la práctica el autoguardado mantiene la sesión activa mientras el examen esté abierto.

### 11.3 Decisiones tomadas y documentadas (no son defectos ocultos)
- **Hash PBKDF2-SHA256 600 000** en lugar de BCrypt/Argon2id: decisión pendiente del autor del documento.
- **`supervision_activa` siempre verdadero** al programar (decisión de la Entrega 3); no hay casilla en el
  formulario del docente.
- **Escala de puntaje 0–1000 por pregunta**: la ERS define la nota del intento 0,0–5,0 pero no la escala por
  pregunta; queda pendiente de definición (anotado desde la fase de versiones).
- **`respuesta_practica.retroalimentacion` VARCHAR(1000)** frente a TEXT del diccionario: discrepancia
  preexistente no tocada (los textos compuestos son cortos).
- **`estudiante.aceptacion_tratamiento_datos DATETIME NULL`**: columna **sin respaldo en el diccionario**
  (hueco del documento). El NULL es para cuentas creadas antes de la regla; no se inventaron fechas.
- **Política de datos**: borrador pendiente de revisión legal; responsable en blanco a propósito.
- **Token de recuperación en CASCADE**: excepción deliberada a RNF-14 (no es historial).
- **Resultado del examen sin la respuesta correcta** (RF-56) y **ocultamiento durante la virtual abierta**
  (CU-31): decisiones explícitas del autor por integridad del grupo.
- **PDF del examen sin curso**: decisión del autor por la misma razón (versión aplicable a varios grupos).
- **Cancelación con estado propio** (norma no prevista en la ERS, documentada): sin nota, sin puntajes, con
  el detalle de lo respondido; la cancelación manda sobre una calificación previa.
- **Filtro de disponibilidad de BD** ejecuta `isValid(2)` en cada petición (excepto CSS): decisión de
  diseño; medido en 3–60 ms por petición y descartado como cuello de botella.

### 11.4 Observaciones menores
- `ExtractorTexto` tiene una expresión regular para DOCX que puede dar falsos positivos (anotado, no tocado).
- El “Word” de práctica es un HTML con extensión `.doc` (el ERS solo exige PDF en RF-63).
- `web/META-INF/context.xml` versiona una contraseña de BD real (dev) — ver sección 6 (secretos).
- No existe suite de pruebas automatizadas (JUnit); la verificación es por script HTTP + aserciones en BD y
  extracción de texto de PDF.

---

## 12. Pruebas realizadas (evidencia)

Todas las fases se probaron sobre HTTP con **datos temporales creados y borrados** (usuarios/curso/grupo/
banco/evaluación/material con prefijos de prueba), verificando efectos en la base de datos y en las
plantillas, y restaurando al final la cookie `Secure` y deteniendo Tomcat. Resumen por área:

| Área | Qué se verificó |
|---|---|
| Renombres y herencia (fases tempranas) | Migraciones 006/007 aplicadas; 24 DAOs y `sql/crear_administrador.sql` ajustados; conteos 20 = 1+10+9 |
| Recuperación de contraseña (RF-11) | Token creado/caducado/usado; respuesta genérica; límite de solicitudes; cambio de contraseña y cierre de sesiones |
| Sin HTML en Java | 47 pantallas comparadas byte a byte antes/después de extraer el marcado a plantillas |
| Reorganización CSS | Capturas públicas idénticas; verificador de cascada con 0 diferencias inesperadas; `docs/INFORME-REPARTO-CSS.md` |
| Seguridad | CSP/HSTS/`secure=true`; humo de autoguardado y eventos |
| Administración (RF-06/08/09/10) | Auto-protecciones, búsqueda escapada, confirmaciones, restablecer/activar/desactivar/eliminar |
| Versiones (RF-35–38) | Aleatoriedad sin repetición dentro del grupo, conservación y códigos siguientes, comparativa con umbral, edición sin tocar el banco, bloqueos |
| Resultado/calificación (RF-48/52/56) | Advertencia con números, recalificación con tope, detalle sin respuesta correcta |
| Supervisión (RF-49/50/55, RNF-18) | Evento por par espejado, exclusión de respuestas correctas (caso pangrama), tiempo atípico contra mediana, idempotencia, aviso previo, nota ética, invisibilidad para el estudiante |
| Práctica (RF-57–62) | Nivel y filtro, retroalimentación solo en incorrectas, abiertas sin calificar, % sobre calificables, historial y resúmenes |
| IA (RF-26/29) | Campos tema/competencia aplicados a las preguntas; modelo efectivo persistido con y sin éxito de la API |
| PDF (RNF-17) | Encabezado/pie por página y preguntas sin partir (extracción de texto) |
| Rendimiento (RNF-07/08) | Ver `docs/INFORME-RENDIMIENTO.md`: p95 de guardado **100,4 ms / 95,3 ms** en dos corridas válidas; páginas 19,6/45,9 ms; PDF ≤ 21 ms; con diagnóstico del artefacto del cliente |

---

## 13. Cómo verificar (para la IA auditora)

1. **Base de datos**: aplicar en orden `sql/migraciones/001…013` (ya aplicadas aquí). Verificar que las 28
   tablas y 47 FK coinciden con la ERS (`information_schema`).
2. **Compilar y desplegar**: `build.xml` de NetBeans o, en este equipo, el script
   `%TEMP%\opencode\recompilar.ps1` (javac + copia a `build/web` + reinicio de Tomcat por WMI). Requiere
   `mysql-connector-j` en `lib/` de Tomcat y las credenciales en el descriptor.
3. **Probar a mano**: arrancar Tomcat y usar la aplicación por HTTPS; para HTTP local hay que poner
   temporalmente `<secure>false</secure>` (documentado en el README con el síntoma exacto) y **volver a
   `true`** después.
4. **Rendimiento**: `powershell -ExecutionPolicy Bypass -File docs\pruebas\rendimiento-carga.ps1`
   (crea y limpia sus propios datos; se niega a correr si hay restos suyos).
5. **Leer la trazabilidad**: `docs/TRAZABILIDAD.md` (matriz RF↔código), `docs/INFORME-RENDIMIENTO.md`,
   `docs/INFORME-REPARTO-CSS.md`, y los ejemplos de configuración en `config/`.

---

## 14. Anexo — índice de documentos y evidencia

| Ruta | Contenido |
|---|---|
| `docs/ERS-resumen.md` | Fuente de verdad (RF, RNF, CU, modelo de datos) |
| `docs/TRAZABILIDAD.md` | Matriz requisito ↔ implementación |
| `docs/PLAN.md`, `docs/PROMPTS.md` | Plan por fases e historial de tareas |
| `docs/INFORME-RENDIMIENTO.md` | RNF-07/08 con entorno, método, diagnóstico y corridas |
| `docs/INFORME-REPARTO-CSS.md` | Reorganización CSS y verificación |
| `docs/pruebas/rendimiento-carga.ps1` | Script de carga reproducible |
| `docs/pruebas/resultados-*.json` | Resultados crudos de las corridas |
| `docs/pruebas/pdf-50-preguntas-*.pdf` | Evidencia de RNF-17 |
| `config/*.ejemplo` | Ejemplos de `ia.properties` y `supervision.properties` |
| `README.md` | Puesta en marcha, HTTPS, zona horaria, umbrales, licencias de datos |

**Fin del informe.**
