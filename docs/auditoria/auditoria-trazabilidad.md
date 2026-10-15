# Auditoría de trazabilidad (Fase 4.1 — insumo previo a la Fase 1)

Fecha: 2026-10-15 · Rama: `auditoria-limpieza` · Método: verificación automática (ida y vuelta) contra `docs/TRAZABILIDAD.md` + revisión manual de las matrices §4 (§RF), §5 (§RNF) y §6 (§CU).

## 1. Resultado global

| Verificación | Resultado |
|---|---|
| **Ida — referencias `archivo:línea` citadas** | **137/137 existen** y la línea está dentro del rango real del archivo (0 fuera de rango) |
| **Ida — referencias `Clase.miembro` citadas** | **74/74 verificadas** (0 miembros inexistentes) |
| **Ida — archivos no hallados** | 4, todos esperados: `catalina.properties` y `conf/Catalina/localhost/ExamLen.xml` (config de Tomcat, externa al repo), `ia.properties` (config externa por diseño, RNF-06) y `word/document.xml` (ruta interna de un DOCX citada en la evidencia de RF-25, no es un archivo del proyecto) |
| **Vuelta — rutas `/app/*` en formularios/enlaces HTML sin servlet** | **0** (24 rutas distintas verificadas contra `@WebServlet`) |
| **Vuelta — llamadas `DAO.metodo()` sin definición** | **0** (285 llamadas distintas resueltas por variable→tipo; el resto son literales de texto en `CatalogoCasosUso.java:99` y miembros estáticos/nested válidos) |
| **Vuelta — servlets/rutas/acciones sin trazar a un CU** | 2 casos reales: `InicioServlet` (`/app/inicio`) y `AdminRendimientoServlet` (`/app/admin/rendimiento`); el resto de la lista son infraestructura o acciones descritas en prosa (ver §3) |
| **Filas obsoletas detectadas en §4** | 2 (RF-12 y RF-16 contradicen a CU-11 y CU-14, ya actualizados) — corregir en Fase 4.3 |

## 2. Matriz CU ↔ código (34/34)

Columnas: **CU** · **Vista** · **Servlet (ruta)** · **Lógica** · **DAO + método** · **Tabla BD** · **Estado** · **Evidencia**. Todo lo citado fue verificado en la ida (archivo:línea y método existen). El detalle funcional completo por CU está en `docs/TRAZABILIDAD.md` §6.

| CU | Vista | Servlet (ruta) | Lógica | DAO + método | Tabla BD | Estado | Evidencia |
|---|---|---|---|---|---|---|---|
| CU-01 Iniciar sesión | login.html | LoginServlet (`/login`) | Login por correo o identificación, bloqueo 5/15, rehash | UsuarioDAO.buscarPorCorreoOIdentificacion, rehashSiLegacy | usuario | OK | TRAZABILIDAD §6:275; `LoginServlet.java` |
| CU-02 Cambiar contraseña | cambiar-contrasena.html | CambiarContrasenaServlet (`/app/perfil/contrasena`) | PoliticaContrasena ERS exacta | UsuarioDAO (cambio autenticado) | usuario | OK | §6:276 |
| CU-03 Actualizar datos | perfil.html | PerfilServlet (`/app/perfil`) | Edición nombre/correo único | UsuarioDAO (líneas 108-123) | usuario | OK | §6:277 |
| CU-04 Registrarse | registrarse.html | RegistroServlet (`/registro`) | Consentimiento + transacción | EstudianteDAO.crear | usuario, estudiante | OK | §6:278 |
| CU-05 Gestionar administradores | admin-administradores.html | AdminAdministradoresServlet (`/app/admin/administradores`) | CRUD, último activo, auto-protección | AdministradorDAO (21-109) | usuario, administrador | OK | §6:279 |
| CU-06 Registrar docente | admin-docentes.html | AdminDocentesServlet (`/app/admin/docentes`) | Temporal 16 chars visible una vez | DocenteDAO (20-54) | usuario, docente | OK | §6:280 |
| CU-07 Gestionar estudiantes | admin-estudiantes.html | AdminEstudiantesServlet (`/app/admin/estudiantes`) | Nivel editable; borrado con intentos | EstudianteDAO (60-100) | usuario, estudiante | OK | §6:281 |
| CU-08 Activar/desactivar | admin-*.html | AdminEstadoDocenteServlet (`/app/admin/docentes/estado`) + 3 admin | Confirmación (confirmarDesactivar), revalida confirmado=1 | DocenteDAO/EstudianteDAO/AdministradorDAO estado | usuario, docente, estudiante, administrador | OK | §6:282 |
| CU-09 Restablecer contraseña | admin-*.html (confirmar.html) | 3 servlets admin | Marcador genérico (migración 017), limpia bloqueo, cierra sesiones | UsuarioDAO.restablecerContrasenaUsuario; TokenRecuperacionDAO.invalidarTokensDeUsuario | usuario, token_recuperacion | OK | §6:283 |
| CU-10 Recuperar contraseña | recuperar.html, restablecer.html | RecuperarContrasenaServlet (`/recuperar`), RestablecerContrasenaServlet (`/restablecer`) | Token 256 bits, 30 min, 3/h, compensación SMTP | TokenRecuperacionDAO (crearToken, consumirTokenYRestablecer, contarSolicitudesRecientes) | token_recuperacion | ⚠️ SMTP real no verificado (código y runtime con proveedor simulado OK) | §6:284 |
| CU-11 Gestionar cursos | docente-cursos.html, docente-curso.html | DocenteCursosServlet (`/app/docente/cursos`), DocenteCursoServlet (`/app/docente/curso`) | 5a: desactivar advierte (no bloquea) | CursoDAO.desactivarCurso (sin bloqueo) | curso | OK | §6:285; `DocenteCursoServlet.java:31-47,123-133` |
| CU-12 Gestionar grupos | docente-grupo.html (ficha en docente-curso.html) | DocenteGrupoServlet (`/app/docente/grupo`), DocenteCursoServlet (abrirGrupo) | Abrir/código/regenerar/cerrar/reabrir | GrupoDAO (abrirGrupo, generarCodigoAccesoGrupo, cerrar, reabrir) | grupo | OK | §6:286 |
| CU-13 Gestionar inscripciones | docente-grupo.html | DocenteGrupoServlet (`/app/docente/grupo`) | Listar/retirar/reincorporar/eliminar con fallback | InscripcionDAO (eliminar, retirar, reincorporar, listarPorGrupo) | inscripcion | OK | §6:287 |
| CU-14 Inscribirse | estudiante-grupos.html, estudiante-confirmar-grupo.html | EstudianteGruposServlet (`/app/estudiante/grupos`) | unirse → confirmarUnirse (revalida todo) | InscripcionDAO.crearInscripcion, buscarPorGrupoYEstudiante; GrupoDAO.buscarPorCodigo | inscripcion, grupo | OK | §6:288 |
| CU-15 Gestionar bancos | docente-bancos.html, docente-banco.html | DocenteBancosServlet (`/app/docente/bancos`), DocenteBancoServlet (`/app/docente/banco`) | Cantidad por estado en una consulta | BancoPreguntasDAO.contarPorEstadoDeDocente | banco_preguntas, pregunta | OK | §6:289 |
| CU-16 Gestionar preguntas | docente-pregunta.html | DocentePreguntaServlet (`/app/docente/pregunta`) | Opciones agregar/editar/eliminar/marcar; borrado controlado | PreguntaDAO.eliminarSiNoUsada; OpcionRespuestaDAO | pregunta, opcion_respuesta | OK | §6:290 |
| CU-17 Revisar y aprobar | docente-pregunta.html, docente-preguntas.html | DocentePreguntaServlet, DocentePreguntasServlet (`/app/docente/preguntas`) | faltantesPregunta por tipo; origen GENERADO_IA | PreguntaDAO.faltantesPregunta/aprobarPregunta | pregunta, opcion_respuesta | OK | §6:291 |
| CU-18 Cargar documento | docente-ia.html | DocenteIAServlet (`/app/docente/ia`) | Validación de contenido real anti zip-bomb | DocumentoFuenteDAO.eliminarSiNoReferenciado, cargarDocumentoFuente | documento_fuente | OK | §6:292 |
| CU-19 Generar con IA | docente-ia.html | DocenteIAServlet (`/app/docente/ia`) | Asíncrono, desglose exacto de conteos | ServicioGeneracionIA.registrarConteosSeguro; SolicitudGeneracionDAO.registrarConteos | solicitud_generacion, pregunta, material_practica | OK | §6:293 |
| CU-20 Gestionar evaluaciones | docente-evaluaciones.html, docente-evaluacion.html | DocenteEvaluacionesServlet (`/app/docente/evaluaciones`), DocenteEvaluacionServlet (`/app/docente/evaluacion`) | Alta con primera pregunta; filtros; LISTA | EvaluacionDAO.crearEvaluacionConPregunta | evaluacion, evaluacion_pregunta | OK | §6:294 |
| CU-21 Generar versiones | docente-evaluacion.html | DocenteEvaluacionServlet | exigirLista + perfil canónico | VersionEvaluacionDAO.exigirLista, generarVersionEvaluacion | version_evaluacion, version_pregunta, version_opcion | OK | §6:295 |
| CU-22 Editar versión | docente-version-pregunta.html | DocenteVersionPreguntaServlet (`/app/docente/version-pregunta`) | Solo lectura con intentos/ABIERTA/CERRADA | VersionPreguntaDAO, VersionOpcionDAO | version_pregunta, version_opcion | OK | §6:296 |
| CU-23 Descargar evaluación PDF | docente-evaluacion.html | DocenteVersionPdfServlet (`/app/docente/version/pdf`) | PDF con curso de contexto; ZIP | VersionEvaluacionDAO.construirPDF | version_* | OK | §6:297 |
| CU-24 Descargar clave | docente-evaluacion.html | DocenteVersionPdfServlet | Solo propietario; ZIP de claves | VersionEvaluacionDAO.generarClaveRespuestasPDFVersionEvaluacion | version_* | OK | §6:298 |
| CU-25 Programar virtual | docente-grupo.html | DocenteVirtualServlet (`/app/docente/virtual`) | Versiones de esa evaluación; supervisión | EvaluacionVirtualDAO.programarEvaluacionVirtual | evaluacion_virtual, evaluacion_virtual_version | OK | §6:299 |
| CU-26 Controlar virtual | docente-virtual.html | DocenteVirtualServlet | Manual + scheduler; CANCELADA terminal | EvaluacionVirtualDAO.reclamarApertura/Cierre/Cancelacion | evaluacion_virtual, intento | OK | §6:300 |
| CU-27 Presentar evaluación | estudiante-evaluaciones.html, estudiante-intento-inicio.html, estudiante-intento.html | EstudianteEvaluacionesServlet (`/app/estudiante/evaluaciones`), EstudianteInicioIntentoServlet (`/app/estudiante/intento/inicio`), EstudianteIntentoServlet (`/app/estudiante/intento`) | Cola offline respuestas; 409 vencimiento | IntentoDAO, RespuestaDAO.guardarRespuestaVigente | intento, respuesta | OK | §6:301 |
| CU-28 Registrar eventos | estudiante-intento.html | EstudianteEventoServlet (`/app/estudiante/intento/evento`) | Cola local + RECONEXION + dedupe 3 s | EventoSupervisionDAO.registrarEventoSupervision, existeSimilarReciente | evento_supervision | OK | §6:302 |
| CU-29 Calificar intentos | docente-virtual.html, docente-intento.html | DocenteIntentoServlet (`/app/docente/intento`) | Recalificar; CALIFICADO | RespuestaDAO.calificarManualmenteRespuesta; IntentoDAO.recalcularCalificacion | respuesta, intento | OK | §6:303 |
| CU-30 Revisar indicadores | docente-intento.html | DocenteIntentoServlet | Conteo, línea de tiempo, nota | EventoSupervisionDAO.listarPorIntento | evento_supervision | OK | §6:304 |
| CU-31 Consultar resultados | estudiante-resultado.html | EstudianteIntentoServlet | Visible con CALIFICADO+CERRADA | RespuestaDAO.listarConPregunta | respuesta, intento | OK | §6:305 |
| CU-32 Gestionar material | docente-practicas.html, docente-practica.html | DocentePracticaServlet (`/app/docente/practica`) | Publicar exige ≥1 y todas APROBADAS | MaterialPracticaDAO.publicarMaterialPractica | material_practica, material_pregunta, curso_material | OK | §6:306 |
| CU-33 Descargar práctica PDF | docente-practica.html, estudiante-practica.html | DocentePracticaPdfServlet (`/app/docente/practica/pdf`), EstudiantePracticaPdfServlet (`/app/estudiante/practica/pdf`) | Estudiante solo PUBLICADOS de sus cursos | MaterialPracticaDAO.construirPDF, generarPDFMaterialPracticaConRespuestas | material_practica, material_pregunta | OK | §6:307 |
| CU-34 Resolver práctica | estudiante-practicas.html, estudiante-practica.html, estudiante-practica-resultado.html, estudiante-historial-practica.html | EstudiantePracticaServlet (`/app/estudiante/practica`), EstudianteHistorialPracticaServlet (`/app/estudiante/practica/historial`) | Feedback inmediato; orden barajado estable | IntentoPracticaDAO, RespuestaPracticaDAO (evaluar, resumen) | intento_practica, respuesta_practica | OK | §6:308 |

**Resumen CU**: 33/34 OK verificados estáticamente; CU-10 en ⚠️ solo por entrega SMTP real (código y runtime con simulación OK). La verificación en ejecución por CU se completa en la Fase 2 (recorrido funcional).

## 3. Vuelta — funcionalidad del código sin trazar a un CU

| Elemento | Ruta | Estado | Comentario / disposición propuesta |
|---|---|---|---|
| `InicioServlet` | `/app/inicio` | **Sin documentar en TRAZABILIDAD.md** (0 menciones) | Panel de inicio por rol + acceso a la herramienta de rendimiento (agregado después de la última actualización del documento). Propuesta Fase 4.3: agregarlo a §8 (Arquitectura) y mencionarlo en CU-01 (post-login). |
| `AdminRendimientoServlet` + `servicio/RendimientoEscenarios`, `CatalogoCasosUso`, `GeneradorExcelRendimiento`, `ArchivoRendimiento` | `/app/admin/rendimiento` | Documentado **fuera** de TRAZABILIDAD (en `docs/INFORME-RENDIMIENTO.md`; es herramienta temporal de medición, no un CU del ERS) | Propuesta Fase 4.3: nota en §8 indicando su naturaleza temporal y su informe. |
| `LogoutServlet`, `PoliticaDatosServlet`, `ErrorServlet` | `/app/salir`, `/politica-datos`, `/error` | Clases no citadas por nombre, pero funcionalidad descrita en prosa (cierre de sesión en CU-01; política en RNF-13; errores en §8/§9) | Sin acción (opcional: citar clase en Fase 4.3). |
| Infraestructura: `InicializadorAplicacion`, `TokenCsrf`, `ClavesSesion`, `MensajeFlash`, `PaginasError`, `DisponibilidadBDFiltro` | — | No citadas por nombre; comportamiento descrito en prosa (§8, §9) | Sin acción (opcional). |
| Acciones `accion=...` manejadas y no nombradas en el documento (p. ej. `subirGrupo`, `bajarGrupo`, `puntajeGrupo`, `moverPreguntaVersion`, `agregarOpcion`, `marcarCorrecta`, `agregarVersion`, `quitarVersion`, `prepararReintento`, `confirmarRestablecer`, `actualizarNivel`) | — | Funcionalidad SÍ descrita por requisito (CU-16, CU-17, CU-20, CU-21, CU-25, RF-10, RF-29, CU-07) | Sin acción: el documento traza por requisito, no por nombre de acción. |
| Enums y excepciones de `logica` (NivelIngles, EstadoX, TipoX, excepciones) | — | No citadas por nombre | Sin acción (clases de apoyo). |

**Rutas verificadas**: las 39 rutas `@WebServlet` existen y las 24 rutas distintas usadas en `action`/`href` de las plantillas apuntan todas a servlets registrados → **0 enlaces o formularios rotos**.

## 4. Filas obsoletas en `docs/TRAZABILIDAD.md` (corregir en Fase 4.3)

| # | Fila | Dice (obsoleto) | Realidad verificada | Evidencia |
|---|---|---|---|---|
| 1 | §4 RF-12 (línea 195) | "CU-11 5a: el sistema **impide** desactivar con grupos abiertos en vez de advertir" | El código **advierte y confirma** sin bloquear (cambio de Fase 10): `DocenteCursoServlet.java:31-47` (pantalla `confirmarDesactivar` con conteo de grupos abiertos) y `:123-133` (mensaje "los grupos abiertos siguen funcionando"); `CursoDAO.desactivarCurso:69-75` sin bloqueo. La fila CU-11 (§6:285) ya está actualizada: la de RF-12 quedó atrás. |
| 2 | §4 RF-16 (línea 199) | Falta: "CU-14 paso 4: sin pantalla de confirmación con curso/grupo/periodo/docente" | La pantalla existe y está implementada: `EstudianteGruposServlet.java:39-41,130-147` + `estudiante-confirmar-grupo.html`; la fila CU-14 (§6:288) ya lo documenta: la de RF-16 quedó atrás. |

## 5. Dudas

- La verificación de línea comprueba que el número de línea existe, no que el contenido de esa línea siga siendo el citado; se hizo comprobación semántica puntual en las filas modificadas recientemente (RF-12, RF-16) y en las evidencias usadas por esta auditoría. Una verificación semántica del 100 % de las 137 referencias requeriría revisarlas una a una contra el texto citado.
- `PoliticaDatosServlet` y las clases de infraestructura no están en la matriz por decisión de estilo del documento (traza por requisito). No se proponen cambios más allá de la nota opcional.
- La página `politica-datos.html` dice "borrador con responsable en blanco" (§4 RNF-13): es una decisión documentada del autor, no una desviación de código.
- CU-10/RF-11 (SMTP) y RNF-01/RNF-03/RNF-11 se mantienen ⚠️ tal como documenta TRAZABILIDAD; no se re-miden aquí (sin servidor SMTP ni TLS ni Firefox en este entorno).
