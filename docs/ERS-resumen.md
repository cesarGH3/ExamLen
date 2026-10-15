# ExamLen — Resumen técnico de la ERS (fuente de verdad)

Este documento resume la Especificación de Requisitos de Software aprobada. **El software debe ajustarse a este documento**, no al revés: nombres de tablas, columnas, clases, estados y flujos deben coincidir con lo que aquí se define.

## Restricciones técnicas obligatorias

- Frontend: HTML y CSS, sin frameworks. JavaScript solo donde es indispensable (temporizador del intento, guardado automático de respuestas, eventos de supervisión).
- Ningún archivo .java contiene HTML ni CSS. Las vistas viven en archivos .html y se rellenan con el motor de plantillas del proyecto (servlet/Vistas.java).
- Un archivo .html y un .css por pantalla; los componentes reutilizables van en archivos propios.
- Backend: Java con Servlets (sin JSP, sin Spring). Capas: presentación (Servlets) → lógica → acceso a datos (DAO/JDBC).
- Base de datos: MySQL 8, InnoDB, utf8mb4. La herencia de Usuario se implementa como tabla por subclase: usuario + administrador + docente + estudiante, unidas por la misma clave primaria.
- IA: API gratuita de Google Gemini en la fase inicial, con Groq como alternativa, encapsulada tras una interfaz para cambiar de proveedor.
- Correo: servicio SMTP para los enlaces de recuperación de contraseña.
- PDF: generación del lado del servidor.
- Contraseñas: hash con sal mediante algoritmo adaptativo. Nunca en texto plano.

## Modelo de datos (MySQL) — 28 tablas

Los nombres de tabla y de columna de esta sección son obligatorios y deben usarse tal cual en la base de datos.

### usuario  — clase Usuario (abstracta)
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_usuario | INT AUTO_INCREMENT | PK | No |
| identificacion | VARCHAR(20) | UQ | No |
| nombre | VARCHAR(120) | — | No |
| correo | VARCHAR(120) | UQ | No |
| institucion | VARCHAR(120) | — | Sí |
| contrasena_hash | VARCHAR(255) | — | No |
| activo | BOOLEAN | — | No |

### administrador  — clase Administrador
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_usuario | INT | PK, FK → usuario | No |
| fecha_nacimiento | DATE | — | Sí |

### docente  — clase Docente
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_usuario | INT | PK, FK → usuario | No |
| debe_cambiar_contrasena | BOOLEAN | — | No |
| id_administrador | INT | FK → administrador | No |

### estudiante  — clase Estudiante
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_usuario | INT | PK, FK → usuario | No |
| nivel_ingles | VARCHAR(2) | CHECK (A1–C2) | No |

### token_recuperacion  — clase TokenRecuperacion
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_token_recuperacion | INT AUTO_INCREMENT | PK | No |
| token_hash | VARCHAR(255) | UQ | No |
| fecha_creacion | DATETIME | — | No |
| fecha_expiracion | DATETIME | — | No |
| usado | BOOLEAN | — | No |
| id_usuario | INT | FK → usuario | No |

### curso  — clase Curso
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_curso | INT AUTO_INCREMENT | PK | No |
| nombre | VARCHAR(120) | — | No |
| descripcion | TEXT | — | Sí |
| nivel | ENUM('A1','A2','B1','B2','C1','C2') | — | No |
| activo | BOOLEAN | — | No |
| id_docente | INT | FK → docente | No |

### grupo  — clase Grupo
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_grupo | INT AUTO_INCREMENT | PK | No |
| nombre | VARCHAR(60) | — | No |
| periodo | VARCHAR(10) | — | No |
| codigo_acceso | VARCHAR(8) | UQ | No |
| estado | ENUM('ABIERTO','CERRADO') | — | No |
| id_curso | INT | FK → curso | No |

### inscripcion  — clase Inscripcion
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_inscripcion | INT AUTO_INCREMENT | PK | No |
| fecha | DATETIME | — | No |
| estado | ENUM('ACTIVA','RETIRADA') | — | No |
| id_grupo | INT | FK → grupo; UQ(id_grupo, id_estudiante) | No |
| id_estudiante | INT | FK → estudiante | No |

### banco_preguntas  — clase BancoPreguntas
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_banco_preguntas | INT AUTO_INCREMENT | PK | No |
| nombre | VARCHAR(120) | — | No |
| descripcion | TEXT | — | Sí |
| id_docente | INT | FK → docente | No |

### pregunta  — clase Pregunta
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_pregunta | INT AUTO_INCREMENT | PK | No |
| enunciado | TEXT | — | No |
| tipo | ENUM('SELECCION_MULTIPLE','VERDADERO_FALSO','RESPUESTA_CORTA','RESPUESTA_ABIERTA') | — | No |
| nivel | ENUM('A1','A2','B1','B2','C1','C2') | — | No |
| dificultad | ENUM('BAJA','MEDIA','ALTA') | — | No |
| competencia | ENUM('GRAMMAR','VOCABULARY','READING','WRITING') | — | No |
| tema | VARCHAR(120) | — | No |
| respuesta_esperada | TEXT | — | Sí |
| estado | ENUM('BORRADOR','APROBADA','ARCHIVADA') | — | No |
| origen | ENUM('MANUAL','GENERADO_IA') | — | No |
| id_banco_preguntas | INT | FK → banco_preguntas | No |
| id_solicitud_generacion | INT | FK → solicitud_generacion | Sí |

### opcion_respuesta  — clase OpcionRespuesta
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_opcion_respuesta | INT AUTO_INCREMENT | PK | No |
| texto | VARCHAR(500) | — | No |
| es_correcta | BOOLEAN | — | No |
| id_pregunta | INT | FK → pregunta (ON DELETE CASCADE) | No |

### evaluacion  — clase Evaluacion
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_evaluacion | INT AUTO_INCREMENT | PK | No |
| titulo | VARCHAR(150) | — | No |
| descripcion | TEXT | — | Sí |
| numero_preguntas | INT | — | No |
| numero_versiones | INT | — | No |
| duracion_minutos | INT | — | No |
| estado | ENUM('BORRADOR','LISTA','ARCHIVADA') | — | No |
| id_docente | INT | FK → docente | No |

### evaluacion_pregunta  — clase Asociación Evaluacion–Pregunta (N:M)
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_evaluacion | INT | PK, FK → evaluacion | No |
| id_pregunta | INT | PK, FK → pregunta | No |

### version_evaluacion  — clase VersionEvaluacion
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_version_evaluacion | INT AUTO_INCREMENT | PK | No |
| codigo | VARCHAR(5) | UQ(id_evaluacion, codigo) | No |
| fecha_generacion | DATETIME | — | No |
| id_evaluacion | INT | FK → evaluacion | No |

### version_pregunta  — clase VersionPregunta
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_version_pregunta | INT AUTO_INCREMENT | PK | No |
| enunciado | TEXT | — | No |
| respuesta_esperada | TEXT | — | Sí |
| orden | INT | — | No |
| puntaje | DECIMAL(5,2) | — | No |
| id_version_evaluacion | INT | FK → version_evaluacion | No |
| id_pregunta | INT | FK → pregunta | No |

### version_opcion  — clase VersionOpcion
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_version_opcion | INT AUTO_INCREMENT | PK | No |
| texto | VARCHAR(500) | — | No |
| es_correcta | BOOLEAN | — | No |
| orden | INT | — | No |
| id_version_pregunta | INT | FK → version_pregunta | No |

### evaluacion_virtual  — clase EvaluacionVirtual
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_evaluacion_virtual | INT AUTO_INCREMENT | PK | No |
| fecha_inicio | DATETIME | — | No |
| fecha_fin | DATETIME | — | No |
| supervision_activa | BOOLEAN | — | No |
| estado | ENUM('PROGRAMADA','ABIERTA','CERRADA','CANCELADA') | — | No |
| id_grupo | INT | FK → grupo | No |
| id_evaluacion | INT | FK → evaluacion | No |

### evaluacion_virtual_version  — clase Asociación EvaluacionVirtual–VersionEvaluacion (N:M)
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_evaluacion_virtual | INT | PK, FK → evaluacion_virtual | No |
| id_version_evaluacion | INT | PK, FK → version_evaluacion | No |

### intento  — clase Intento
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_intento | INT AUTO_INCREMENT | PK | No |
| fecha_inicio | DATETIME | — | No |
| fecha_fin | DATETIME | — | Sí |
| calificacion | DECIMAL(3,1) | — | Sí |
| estado | ENUM('EN_CURSO','FINALIZADO','CALIFICADO') | — | No |
| id_evaluacion_virtual | INT | FK → evaluacion_virtual; UQ(id_evaluacion_virtual, id_inscripcion) | No |
| id_inscripcion | INT | FK → inscripcion | No |
| id_version_evaluacion | INT | FK → version_evaluacion | No |

### respuesta  — clase Respuesta
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_respuesta | INT AUTO_INCREMENT | PK | No |
| contenido | TEXT | — | Sí |
| fecha | DATETIME | — | No |
| puntaje | DECIMAL(5,2) | — | Sí |
| comentario_docente | TEXT | — | Sí |
| id_intento | INT | FK → intento; UQ(id_intento, id_version_pregunta) | No |
| id_version_pregunta | INT | FK → version_pregunta | No |
| id_version_opcion | INT | FK → version_opcion | Sí |

### evento_supervision  — clase EventoSupervision
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_evento_supervision | INT AUTO_INCREMENT | PK | No |
| tipo | ENUM('CAMBIO_PESTANA','PERDIDA_FOCO','SALIDA_PAGINA','DESCONEXION','RECONEXION','TIEMPO_ATIPICO','SIMILITUD_RESPUESTAS') | — | No |
| descripcion | VARCHAR(500) | — | Sí |
| fecha_hora | DATETIME | — | No |
| id_intento | INT | FK → intento | No |

### material_practica  — clase MaterialPractica
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_material_practica | INT AUTO_INCREMENT | PK | No |
| titulo | VARCHAR(150) | — | No |
| descripcion | TEXT | — | Sí |
| nivel | ENUM('A1','A2','B1','B2','C1','C2') | — | No |
| estado | ENUM('BORRADOR','PUBLICADO','ARCHIVADO') | — | No |
| origen | ENUM('MANUAL','GENERADO_IA') | — | No |
| id_docente | INT | FK → docente | No |
| id_solicitud_generacion | INT | FK → solicitud_generacion | Sí |

### curso_material  — clase Asociación Curso–MaterialPractica (N:M)
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_curso | INT | PK, FK → curso | No |
| id_material_practica | INT | PK, FK → material_practica | No |

### material_pregunta  — clase Asociación MaterialPractica–Pregunta (N:M)
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_material_practica | INT | PK, FK → material_practica | No |
| id_pregunta | INT | PK, FK → pregunta | No |

### intento_practica  — clase IntentoPractica
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_intento_practica | INT AUTO_INCREMENT | PK | No |
| fecha_inicio | DATETIME | — | No |
| fecha_fin | DATETIME | — | Sí |
| calificacion | DECIMAL(5,2) | — | Sí |
| id_material_practica | INT | FK → material_practica | No |
| id_estudiante | INT | FK → estudiante | No |

### respuesta_practica  — clase RespuestaPractica
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_respuesta_practica | INT AUTO_INCREMENT | PK | No |
| contenido | TEXT | — | Sí |
| es_correcta | BOOLEAN | — | Sí |
| retroalimentacion | TEXT | — | Sí |
| id_intento_practica | INT | FK → intento_practica | No |
| id_pregunta | INT | FK → pregunta | No |
| id_opcion_respuesta | INT | FK → opcion_respuesta | Sí |

### documento_fuente  — clase DocumentoFuente
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_documento_fuente | INT AUTO_INCREMENT | PK | No |
| nombre_archivo | VARCHAR(255) | — | No |
| tipo_archivo | VARCHAR(10) | — | No |
| ruta_almacenamiento | VARCHAR(500) | — | No |
| fecha_carga | DATETIME | — | No |
| id_docente | INT | FK → docente | No |

### solicitud_generacion  — clase SolicitudGeneracion
| Columna | Tipo | Restricción | Nulo |
|---|---|---|---|
| id_solicitud_generacion | INT AUTO_INCREMENT | PK | No |
| tipo | ENUM('PREGUNTAS','VARIANTES','MATERIAL_PRACTICA') | — | No |
| instrucciones | TEXT | — | No |
| cantidad | INT | — | No |
| nivel | ENUM('A1','A2','B1','B2','C1','C2') | — | No |
| modelo | VARCHAR(80) | — | Sí |
| estado | ENUM('PENDIENTE','EN_PROCESO','COMPLETADA','FALLIDA') | — | No |
| fecha | DATETIME | — | No |
| mensaje_error | TEXT | — | Sí |
| id_docente | INT | FK → docente | No |
| id_documento_fuente | INT | FK → documento_fuente | Sí |
| id_banco_destino | INT | FK → banco_preguntas | Sí |

## Requisitos funcionales

### Módulo de Usuarios y Acceso
- **RF-01 Autenticar usuario** (prioridad Alta, actor: Administrador, Docente, Estudiante, caso de uso: CU-01)
  - Descripción: El sistema debe permitir que un usuario registrado inicie sesión con su correo o número de identificación y su contraseña, y cerrarla cuando lo desee.
  - Entradas: Correo o identificación; contraseña.
  - Salidas: Sesión iniciada y acceso al menú correspondiente al tipo de usuario, o mensaje de error.
  - Reglas: La contraseña se compara contra el hash almacenado. Una cuenta con activoUsuario = falso no puede iniciar sesión.
  - Clases: Usuario
- **RF-02 Cambiar contraseña** (prioridad Alta, actor: Administrador, Docente, Estudiante, caso de uso: CU-02)
  - Descripción: El sistema debe permitir que el usuario cambie su contraseña indicando la contraseña actual y la nueva.
  - Entradas: Contraseña actual; nueva contraseña; confirmación.
  - Salidas: Contraseña actualizada.
  - Reglas: La nueva contraseña debe tener mínimo 8 caracteres, con letras y números, y ser distinta de la actual.
  - Clases: Usuario
- **RF-03 Exigir cambio de contraseña inicial del docente** (prioridad Alta, actor: Docente, caso de uso: CU-02)
  - Descripción: El sistema debe obligar al docente a cambiar la contraseña temporal asignada por el administrador en su primer ingreso, antes de permitirle usar cualquier otra función.
  - Entradas: Contraseña temporal; nueva contraseña.
  - Salidas: Contraseña definitiva registrada; debeCambiarContrasenaDocente = falso.
  - Reglas: Mientras debeCambiarContrasenaDocente sea verdadero, el sistema solo muestra el formulario de cambio de contraseña.
  - Clases: Docente, Usuario
- **RF-04 Actualizar datos personales** (prioridad Media, actor: Administrador, Docente, Estudiante, caso de uso: CU-03)
  - Descripción: El sistema debe permitir que el usuario actualice su nombre y su correo electrónico.
  - Entradas: Nombre; correo.
  - Salidas: Datos actualizados.
  - Reglas: El correo debe ser único en el sistema.
  - Clases: Usuario
- **RF-05 Registrar estudiante** (prioridad Alta, actor: Estudiante, caso de uso: CU-04)
  - Descripción: El sistema debe permitir que un estudiante cree su cuenta registrando identificación, nombre, correo, institución, nivel de inglés y contraseña.
  - Entradas: Identificación; nombre; correo; institución; nivel de inglés (A1–C2); contraseña.
  - Salidas: Cuenta de estudiante activa.
  - Reglas: La identificación y el correo no pueden estar registrados previamente.
  - Clases: Estudiante, Usuario
- **RF-06 Gestionar administradores** (prioridad Media, actor: Administrador, caso de uso: CU-05)
  - Descripción: El sistema debe permitir al administrador crear, actualizar y eliminar cuentas de administrador.
  - Entradas: Identificación; nombre; correo; fecha de nacimiento.
  - Salidas: Cuenta de administrador creada, actualizada o eliminada.
  - Reglas: El sistema no permite eliminar al último administrador activo ni que un administrador se elimine a sí mismo.
  - Clases: Administrador
- **RF-07 Registrar docente** (prioridad Alta, actor: Administrador, caso de uso: CU-06)
  - Descripción: El sistema debe permitir al administrador registrar docentes de inglés, asignándoles una contraseña temporal.
  - Entradas: Identificación; nombre; correo; institución.
  - Salidas: Cuenta de docente creada con debeCambiarContrasenaDocente = verdadero y contraseña temporal visible una sola vez para el administrador.
  - Reglas: Solo el administrador puede crear cuentas de docente; no existe autorregistro de docentes.
  - Clases: Administrador, Docente
- **RF-08 Gestionar estudiantes** (prioridad Media, actor: Administrador, caso de uso: CU-07)
  - Descripción: El sistema debe permitir al administrador consultar, actualizar el nivel de inglés y eliminar cuentas de estudiante.
  - Entradas: Criterios de búsqueda; nivel de inglés.
  - Salidas: Listado de estudiantes; datos actualizados o cuenta eliminada.
  - Reglas: Un estudiante con intentos registrados no se elimina; se desactiva (RF-09).
  - Clases: Estudiante
- **RF-09 Activar y desactivar cuentas** (prioridad Alta, actor: Administrador, caso de uso: CU-08)
  - Descripción: El sistema debe permitir al administrador activar o desactivar la cuenta de cualquier usuario.
  - Entradas: Usuario seleccionado; acción.
  - Salidas: Estado de la cuenta actualizado.
  - Reglas: Desactivar una cuenta no elimina su información histórica.
  - Clases: Usuario
- **RF-10 Restablecer contraseña de un usuario** (prioridad Alta, actor: Administrador, caso de uso: CU-09)
  - Descripción: El sistema debe permitir al administrador asignar una contraseña temporal a un usuario que olvidó la suya. Es el mecanismo de respaldo para los casos en que el usuario no puede usar la recuperación por correo (RF-11), por ejemplo si perdió el acceso a su cuenta de correo o no recibe el mensaje.
  - Entradas: Usuario seleccionado.
  - Salidas: Contraseña temporal generada; el usuario debe cambiarla en su siguiente ingreso.
  - Reglas: La contraseña temporal se muestra una sola vez al administrador. Este procedimiento se usa solo como respaldo de RF-11, previa solicitud del usuario por un canal institucional.
  - Clases: Usuario
- **RF-11 Recuperar la contraseña olvidada** (prioridad Alta, actor: Administrador, Docente, Estudiante, caso de uso: CU-10)
  - Descripción: El sistema debe permitir que un usuario que olvidó su contraseña la restablezca por sí mismo: solicita la recuperación con su correo, recibe un enlace con un token de un solo uso y define una contraseña nueva.
  - Entradas: Correo electrónico registrado; token recibido por correo; nueva contraseña y confirmación.
  - Salidas: Correo con el enlace de recuperación; contraseña actualizada y token marcado como usado.
  - Reglas: El token es de un solo uso y caduca a los 30 minutos. Solicitar un token nuevo invalida los anteriores del mismo usuario. Por seguridad, el sistema muestra siempre el mismo mensaje de confirmación, exista o no el correo (RNF-04). La nueva contraseña cumple las reglas de RF-02.
  - Clases: Usuario, TokenRecuperacion

### Módulo de Cursos y Grupos
- **RF-12 Gestionar cursos** (prioridad Alta, actor: Docente, caso de uso: CU-11)
  - Descripción: El sistema debe permitir al docente crear cursos indicando nombre, descripción y nivel MCER, así como actualizarlos, activarlos y desactivarlos.
  - Entradas: Nombre; descripción; nivel (A1–C2).
  - Salidas: Curso registrado o actualizado.
  - Reglas: Un curso desactivado no permite abrir grupos nuevos.
  - Clases: Curso, Docente
- **RF-13 Abrir grupos** (prioridad Alta, actor: Docente, caso de uso: CU-12)
  - Descripción: El sistema debe permitir al docente abrir grupos de un curso para un periodo académico.
  - Entradas: Curso; nombre del grupo; periodo (p. ej. 2026-2).
  - Salidas: Grupo en estado ABIERTO con código de acceso.
  - Reglas: El nombre del grupo no se repite dentro del mismo curso y periodo.
  - Clases: Grupo, Curso
- **RF-14 Generar código de acceso del grupo** (prioridad Alta, actor: Docente, caso de uso: CU-12)
  - Descripción: El sistema debe generar un código de acceso único para cada grupo y permitir al docente regenerarlo.
  - Entradas: Grupo.
  - Salidas: Código alfanumérico de 6 a 8 caracteres.
  - Reglas: Al regenerarlo, el código anterior deja de ser válido.
  - Clases: Grupo
- **RF-15 Cerrar y reabrir grupos** (prioridad Media, actor: Docente, caso de uso: CU-12)
  - Descripción: El sistema debe permitir al docente cerrar un grupo al terminar el periodo y reabrirlo si es necesario.
  - Entradas: Grupo; acción.
  - Salidas: Grupo en estado CERRADO o ABIERTO.
  - Reglas: Un grupo cerrado no admite nuevas inscripciones ni nuevas evaluaciones virtuales.
  - Clases: Grupo
- **RF-16 Inscribirse en un grupo** (prioridad Alta, actor: Estudiante, caso de uso: CU-14)
  - Descripción: El sistema debe permitir al estudiante inscribirse en un grupo ingresando su código de acceso.
  - Entradas: Código de acceso.
  - Salidas: Inscripción en estado ACTIVA.
  - Reglas: El grupo debe estar ABIERTO. Un estudiante no puede tener dos inscripciones en el mismo grupo.
  - Clases: Inscripcion, Grupo, Estudiante
- **RF-17 Gestionar inscripciones** (prioridad Media, actor: Docente, caso de uso: CU-13)
  - Descripción: El sistema debe permitir al docente consultar los estudiantes inscritos en un grupo y retirar, reincorporar o eliminar inscripciones.
  - Entradas: Grupo; inscripción; acción.
  - Salidas: Listado de inscritos; estado de la inscripción actualizado.
  - Reglas: Una inscripción RETIRADA no permite presentar evaluaciones. Una inscripción con intentos no se elimina, se retira.
  - Clases: Inscripcion

### Módulo de Banco de Preguntas
- **RF-18 Gestionar bancos de preguntas** (prioridad Alta, actor: Docente, caso de uso: CU-15)
  - Descripción: El sistema debe permitir al docente crear, renombrar y describir bancos de preguntas para organizar su contenido.
  - Entradas: Nombre; descripción.
  - Salidas: Banco de preguntas registrado o actualizado.
  - Reglas: Cada banco pertenece a un único docente.
  - Clases: BancoPreguntas, Docente
- **RF-19 Crear preguntas** (prioridad Alta, actor: Docente, caso de uso: CU-16)
  - Descripción: El sistema debe permitir al docente crear preguntas manualmente en un banco, indicando enunciado, tipo, nivel, dificultad, competencia, tema y respuesta esperada.
  - Entradas: Enunciado; tipo (selección múltiple, verdadero/falso, respuesta corta, respuesta abierta); nivel; dificultad (baja, media, alta); competencia (grammar, vocabulary, reading, writing); tema; respuesta esperada.
  - Salidas: Pregunta en estado BORRADOR con origen MANUAL.
  - Reglas: La respuesta esperada es obligatoria para respuesta corta y opcional (guía de corrección) para respuesta abierta.
  - Clases: Pregunta, BancoPreguntas
- **RF-20 Gestionar opciones de respuesta** (prioridad Alta, actor: Docente, caso de uso: CU-16)
  - Descripción: El sistema debe permitir al docente agregar, editar, eliminar y marcar como correcta las opciones de respuesta de una pregunta.
  - Entradas: Texto de la opción; indicador de opción correcta.
  - Salidas: Opciones registradas en la pregunta.
  - Reglas: Selección múltiple: mínimo 2 opciones y exactamente una correcta. Verdadero/falso: exactamente 2 opciones.
  - Clases: OpcionRespuesta, Pregunta
- **RF-21 Actualizar y eliminar preguntas** (prioridad Alta, actor: Docente, caso de uso: CU-16)
  - Descripción: El sistema debe permitir al docente modificar o eliminar preguntas de sus bancos.
  - Entradas: Pregunta; nuevos datos.
  - Salidas: Pregunta actualizada o eliminada.
  - Reglas: Una pregunta usada en alguna versión de evaluación o material de práctica no se elimina; solo se archiva. Modificar una pregunta del banco no altera las versiones ya generadas.
  - Clases: Pregunta
- **RF-22 Validar y aprobar preguntas** (prioridad Alta, actor: Docente, caso de uso: CU-17)
  - Descripción: El sistema debe validar que una pregunta esté completa y permitir al docente aprobarla para su uso en evaluaciones y prácticas.
  - Entradas: Pregunta en BORRADOR.
  - Salidas: Pregunta en estado APROBADA, o lista de errores de validación.
  - Reglas: Solo las preguntas APROBADAS pueden seleccionarse para evaluaciones o asociarse a materiales de práctica.
  - Clases: Pregunta
- **RF-23 Archivar preguntas** (prioridad Media, actor: Docente, caso de uso: CU-17)
  - Descripción: El sistema debe permitir al docente archivar preguntas que ya no desea usar sin perder su historial.
  - Entradas: Pregunta.
  - Salidas: Pregunta en estado ARCHIVADA.
  - Reglas: Una pregunta archivada no aparece en la selección de preguntas, pero se conserva en versiones e intentos existentes.
  - Clases: Pregunta
- **RF-24 Buscar y filtrar preguntas** (prioridad Alta, actor: Docente, caso de uso: CU-15, CU-16, CU-20)
  - Descripción: El sistema debe permitir al docente buscar preguntas por texto y filtrarlas por banco, tipo, nivel, dificultad, competencia, tema, estado y origen.
  - Entradas: Texto y filtros.
  - Salidas: Listado de preguntas que cumplen los criterios.
  - Reglas: El docente solo ve las preguntas de sus propios bancos.
  - Clases: Pregunta, BancoPreguntas

### Módulo de Inteligencia Artificial
- **RF-25 Cargar documento fuente** (prioridad Alta, actor: Docente, caso de uso: CU-18)
  - Descripción: El sistema debe permitir al docente cargar una evaluación o material que ya posee para usarlo como base de la generación de contenido.
  - Entradas: Archivo PDF, DOCX o TXT.
  - Salidas: Documento fuente almacenado con nombre, tipo, ruta y fecha de carga.
  - Reglas: Tamaño máximo de 10 MB por archivo. Solo el docente que lo cargó puede usarlo.
  - Clases: DocumentoFuente, Docente
- **RF-26 Generar preguntas con IA** (prioridad Alta, actor: Docente, Servicio de IA, caso de uso: CU-19)
  - Descripción: El sistema debe permitir al docente solicitar la generación de preguntas nuevas a partir de un documento fuente o de instrucciones (tema, nivel, competencia, cantidad), guardándolas en un banco destino.
  - Entradas: Instrucciones; cantidad; nivel; documento fuente (opcional); banco destino.
  - Salidas: Preguntas en estado BORRADOR con origen GENERADO_IA en el banco destino.
  - Reglas: Cantidad máxima por solicitud: 30 preguntas.
  - Clases: SolicitudGeneracion, Pregunta, BancoPreguntas
- **RF-27 Generar variantes de preguntas con IA** (prioridad Alta, actor: Docente, Servicio de IA, caso de uso: CU-19)
  - Descripción: El sistema debe permitir al docente solicitar variantes de preguntas existentes que conserven el contenido, la competencia, el tipo y el nivel de dificultad.
  - Entradas: Preguntas base; cantidad de variantes; banco destino.
  - Salidas: Nuevas preguntas en BORRADOR con origen GENERADO_IA.
  - Reglas: Las variantes no reemplazan a las preguntas base.
  - Clases: SolicitudGeneracion, Pregunta
- **RF-28 Generar material de práctica con IA** (prioridad Alta, actor: Docente, Servicio de IA, caso de uso: CU-19)
  - Descripción: El sistema debe permitir al docente generar material de práctica a partir de los contenidos de una evaluación o de un tema, nivel y competencia elegidos, sin repetir las preguntas de la evaluación.
  - Entradas: Evaluación de referencia o tema/nivel/competencia; cantidad de ejercicios; instrucciones.
  - Salidas: Material de práctica en BORRADOR con origen GENERADO_IA y sus preguntas asociadas.
  - Reglas: El material generado no incluye preguntas idénticas a las de la evaluación de referencia.
  - Clases: SolicitudGeneracion, MaterialPractica, Pregunta
- **RF-29 Seguir el estado de las solicitudes de generación** (prioridad Media, actor: Docente, caso de uso: CU-19)
  - Descripción: El sistema debe registrar y mostrar el estado de cada solicitud de generación (PENDIENTE, EN_PROCESO, COMPLETADA, FALLIDA), el modelo usado y el mensaje de error si lo hay.
  - Entradas: Solicitud de generación.
  - Salidas: Estado, fecha, modelo y mensaje de error.
  - Reglas: Una solicitud fallida puede reintentarse creando una nueva solicitud con los mismos datos.
  - Clases: SolicitudGeneracion
- **RF-30 Revisión obligatoria del contenido generado** (prioridad Alta, actor: Docente, caso de uso: CU-17, CU-32)
  - Descripción: El sistema debe mantener todo contenido generado por IA en estado BORRADOR hasta que el docente lo revise, edite o descarte.
  - Entradas: Contenido generado.
  - Salidas: Contenido aprobado, editado o eliminado.
  - Reglas: Ningún contenido GENERADO_IA puede llegar a estudiantes sin aprobación del docente.
  - Clases: Pregunta, MaterialPractica

### Módulo de Evaluaciones y Versiones
- **RF-31 Crear evaluación** (prioridad Alta, actor: Docente, caso de uso: CU-20)
  - Descripción: El sistema debe permitir al docente crear una evaluación con título y descripción.
  - Entradas: Título; descripción.
  - Salidas: Evaluación en estado BORRADOR.
  - Reglas: Una evaluación debe tener al menos una pregunta seleccionada (multiplicidad 1..* entre Evaluacion y Pregunta); se guarda al seleccionar su primera pregunta (RF-33).
  - Clases: Evaluacion, Docente
- **RF-32 Configurar evaluación** (prioridad Alta, actor: Docente, caso de uso: CU-20)
  - Descripción: El sistema debe permitir al docente definir el número de preguntas por versión, el número de versiones y la duración en minutos.
  - Entradas: Número de preguntas; número de versiones (1 a 10); duración en minutos.
  - Salidas: Evaluación configurada.
  - Reglas: El número de preguntas seleccionadas debe ser mayor o igual al número de preguntas por versión.
  - Clases: Evaluacion
- **RF-33 Seleccionar preguntas para la evaluación** (prioridad Alta, actor: Docente, caso de uso: CU-20)
  - Descripción: El sistema debe permitir al docente seleccionar y quitar preguntas aprobadas de sus bancos para conformar la evaluación, incluidas preguntas usadas en evaluaciones anteriores.
  - Entradas: Preguntas aprobadas.
  - Salidas: Conjunto de preguntas de la evaluación con resumen por tema, competencia y dificultad.
  - Reglas: Solo se pueden seleccionar preguntas APROBADAS.
  - Clases: Evaluacion, Pregunta
- **RF-34 Marcar evaluación como lista y archivarla** (prioridad Alta, actor: Docente, caso de uso: CU-20)
  - Descripción: El sistema debe permitir al docente marcar la evaluación como LISTA cuando cumple la configuración, y archivarla cuando ya no la use.
  - Entradas: Evaluación.
  - Salidas: Evaluación en estado LISTA o ARCHIVADA.
  - Reglas: Solo una evaluación LISTA puede generar versiones definitivas y programarse virtualmente.
  - Clases: Evaluacion
- **RF-35 Generar versiones de la evaluación** (prioridad Alta, actor: Docente, caso de uso: CU-21)
  - Descripción: El sistema debe generar las versiones configuradas (A, B, C…) seleccionando preguntas y ordenando aleatoriamente preguntas y opciones, de forma que todas cubran los mismos temas y competencias con dificultad comparable.
  - Entradas: Evaluación LISTA.
  - Salidas: Versiones con código, fecha de generación, preguntas, opciones, orden y puntaje.
  - Reglas: Cada versión conserva la misma distribución de competencias y dificultad del conjunto seleccionado. El puntaje total de todas las versiones es igual.
  - Clases: VersionEvaluacion, VersionPregunta, VersionOpcion
- **RF-36 Reutilizar una evaluación para nuevas versiones** (prioridad Alta, actor: Docente, caso de uso: CU-21)
  - Descripción: El sistema debe permitir generar nuevas versiones de una evaluación existente en cualquier momento, conservando las versiones anteriores.
  - Entradas: Evaluación existente.
  - Salidas: Nuevas versiones con códigos no repetidos.
  - Reglas: Las versiones anteriores y sus intentos no se modifican.
  - Clases: Evaluacion, VersionEvaluacion
- **RF-37 Comparar versiones** (prioridad Alta, actor: Docente, caso de uso: CU-21)
  - Descripción: El sistema debe mostrar un cuadro comparativo de las versiones con cantidad de preguntas, distribución por tema, competencia y dificultad, y puntaje total.
  - Entradas: Evaluación con versiones.
  - Salidas: Cuadro comparativo de versiones.
  - Reglas: Las diferencias de distribución entre versiones se resaltan.
  - Clases: VersionEvaluacion, VersionPregunta, Pregunta
- **RF-38 Editar versión** (prioridad Alta, actor: Docente, caso de uso: CU-22)
  - Descripción: El sistema debe permitir al docente editar el enunciado, la respuesta esperada, el orden y el puntaje de las preguntas de una versión, así como sus opciones.
  - Entradas: Versión; cambios.
  - Salidas: Versión actualizada.
  - Reglas: Las versiones guardan una copia independiente de las preguntas: editarlas no modifica el banco. No se edita una versión que ya tiene intentos.
  - Clases: VersionPregunta, VersionOpcion
- **RF-39 Descargar versión en PDF** (prioridad Alta, actor: Docente, caso de uso: CU-23)
  - Descripción: El sistema debe generar un PDF imprimible de cada versión de la evaluación.
  - Entradas: Versión.
  - Salidas: Archivo PDF (p. ej. Evaluacion_B1_Unit3_A.pdf).
  - Reglas: El PDF incluye encabezado con curso, título, versión y espacio para datos del estudiante; no incluye respuestas.
  - Clases: VersionEvaluacion
- **RF-40 Descargar clave de respuestas** (prioridad Alta, actor: Docente, caso de uso: CU-24)
  - Descripción: El sistema debe generar un PDF separado con las respuestas correctas y el puntaje de cada pregunta de una versión.
  - Entradas: Versión.
  - Salidas: Archivo PDF de clave de respuestas.
  - Reglas: Solo el docente propietario puede descargarlo.
  - Clases: VersionEvaluacion

### Módulo de Aplicación Virtual y Supervisión
- **RF-41 Programar evaluación virtual** (prioridad Alta, actor: Docente, caso de uso: CU-25)
  - Descripción: El sistema debe permitir al docente programar una evaluación LISTA para un grupo, indicando fecha y hora de inicio y fin, las versiones a usar y si la supervisión está activa.
  - Entradas: Grupo; evaluación; versiones; fecha/hora de inicio y fin; supervisión (sí/no).
  - Salidas: Evaluación virtual en estado PROGRAMADA.
  - Reglas: La fecha de fin debe ser posterior a la de inicio. El grupo debe estar ABIERTO.
  - Clases: EvaluacionVirtual, Grupo, VersionEvaluacion
- **RF-42 Abrir, cerrar y cancelar evaluación virtual** (prioridad Alta, actor: Docente, caso de uso: CU-26)
  - Descripción: El sistema debe abrir y cerrar la evaluación virtual automáticamente según sus fechas, y permitir al docente abrirla, cerrarla o cancelarla manualmente.
  - Entradas: Evaluación virtual; acción.
  - Salidas: Estado ABIERTA, CERRADA o CANCELADA.
  - Reglas: Al cerrarse, todos los intentos EN_CURSO se finalizan con las respuestas guardadas. Una evaluación CANCELADA no se puede reabrir.
  - Clases: EvaluacionVirtual, Intento
- **RF-43 Consultar evaluaciones disponibles** (prioridad Alta, actor: Estudiante, caso de uso: CU-27)
  - Descripción: El sistema debe mostrar al estudiante las evaluaciones virtuales de sus grupos con inscripción activa, indicando su estado y fechas.
  - Entradas: Sesión del estudiante.
  - Salidas: Listado de evaluaciones programadas, abiertas y cerradas.
  - Reglas: Solo se puede iniciar una evaluación en estado ABIERTA.
  - Clases: EvaluacionVirtual, Inscripcion
- **RF-44 Iniciar intento y asignar versión** (prioridad Alta, actor: Estudiante, caso de uso: CU-27)
  - Descripción: El sistema debe crear un intento al iniciar la evaluación y asignar al estudiante una de las versiones habilitadas, distribuyéndolas de forma equilibrada.
  - Entradas: Evaluación virtual abierta.
  - Salidas: Intento EN_CURSO con versión asignada y hora de inicio.
  - Reglas: Cada inscripción tiene un único intento por evaluación virtual.
  - Clases: Intento, VersionEvaluacion, Inscripcion
- **RF-45 Responder y guardar automáticamente** (prioridad Alta, actor: Estudiante, caso de uso: CU-27)
  - Descripción: El sistema debe guardar cada respuesta en el servidor en el momento en que el estudiante la registra, y permitirle modificarla mientras el intento esté en curso.
  - Entradas: Pregunta de la versión; contenido de la respuesta u opción seleccionada.
  - Salidas: Respuesta guardada con fecha y hora; indicador visual de guardado.
  - Reglas: Si se pierde la conexión, al reconectarse el estudiante continúa el mismo intento con sus respuestas guardadas.
  - Clases: Respuesta, Intento
- **RF-46 Ocultar retroalimentación durante la evaluación** (prioridad Alta, actor: Estudiante, caso de uso: CU-27, CU-31)
  - Descripción: El sistema no debe mostrar respuestas correctas, puntajes ni retroalimentación mientras el intento esté en curso.
  - Reglas: La calificación solo es visible cuando el intento está CALIFICADO y la evaluación virtual está CERRADA.
  - Clases: Intento, Respuesta
- **RF-47 Controlar el tiempo del intento** (prioridad Alta, actor: Estudiante, caso de uso: CU-27)
  - Descripción: El sistema debe mostrar el tiempo restante y finalizar el intento automáticamente al vencer la duración de la evaluación o la fecha de fin, lo que ocurra primero.
  - Entradas: Duración; fecha de fin.
  - Salidas: Intento FINALIZADO con hora de finalización.
  - Reglas: El tiempo se calcula con la hora del servidor.
  - Clases: Intento, Evaluacion, EvaluacionVirtual
- **RF-48 Finalizar intento** (prioridad Alta, actor: Estudiante, caso de uso: CU-27)
  - Descripción: El sistema debe permitir al estudiante enviar su evaluación antes del tiempo límite, advirtiendo si hay preguntas sin responder.
  - Entradas: Confirmación del estudiante.
  - Salidas: Intento FINALIZADO.
  - Reglas: Un intento finalizado no se puede reabrir.
  - Clases: Intento
- **RF-49 Registrar eventos de supervisión** (prioridad Alta, actor: Sistema, caso de uso: CU-28)
  - Descripción: Cuando la supervisión está activa, el sistema debe registrar con fecha y hora los cambios de pestaña, pérdidas de foco, salidas de la página, desconexiones y reconexiones durante el intento.
  - Entradas: Eventos del navegador.
  - Salidas: Eventos de supervisión asociados al intento.
  - Reglas: El estudiante es informado, antes de iniciar, de que la evaluación está supervisada y qué se registra.
  - Clases: EventoSupervision, Intento
- **RF-50 Detectar tiempos atípicos y similitud de respuestas** (prioridad Media, actor: Sistema, caso de uso: CU-28, CU-30)
  - Descripción: Al cerrarse una evaluación virtual supervisada, el sistema debe registrar como eventos los intentos con tiempos de respuesta atípicos y los pares de intentos con respuestas abiertas o cortas excesivamente similares.
  - Entradas: Intentos y respuestas de la evaluación virtual.
  - Salidas: Eventos TIEMPO_ATIPICO y SIMILITUD_RESPUESTAS.
  - Reglas: Tiempo atípico: duración menor al 25 % de la mediana del grupo. Similitud: coeficiente de similitud de texto mayor o igual a 0,85 (umbrales configurables).
  - Clases: EventoSupervision, Respuesta
- **RF-51 Calificar automáticamente preguntas objetivas** (prioridad Alta, actor: Sistema, caso de uso: CU-29)
  - Descripción: El sistema debe calificar automáticamente las preguntas de selección múltiple, verdadero/falso y respuesta corta al finalizar el intento.
  - Entradas: Respuestas del intento.
  - Salidas: Puntaje por respuesta objetiva.
  - Reglas: La respuesta corta se compara con la respuesta esperada ignorando mayúsculas, tildes y espacios sobrantes.
  - Clases: Respuesta, VersionPregunta
- **RF-52 Calificar manualmente respuestas abiertas** (prioridad Alta, actor: Docente, caso de uso: CU-29)
  - Descripción: El sistema debe permitir al docente asignar puntaje y comentario a las respuestas abiertas, y ajustar la calificación automática si lo considera necesario.
  - Entradas: Respuesta; puntaje; comentario.
  - Salidas: Respuesta calificada.
  - Reglas: El puntaje no puede superar el puntaje de la pregunta en la versión.
  - Clases: Respuesta
- **RF-53 Calcular calificación del intento** (prioridad Alta, actor: Sistema, caso de uso: CU-29)
  - Descripción: El sistema debe calcular la calificación del intento como la suma de puntajes y marcarlo CALIFICADO cuando todas sus respuestas tengan puntaje.
  - Entradas: Puntajes de las respuestas.
  - Salidas: Calificación del intento en escala 0,0 a 5,0.
  - Reglas: Las preguntas sin responder obtienen 0.
  - Clases: Intento
- **RF-54 Consultar intentos de una evaluación virtual** (prioridad Alta, actor: Docente, caso de uso: CU-29, CU-30)
  - Descripción: El sistema debe mostrar al docente los intentos de una evaluación virtual con estudiante, versión, hora de inicio, hora de fin, tiempo empleado, estado, calificación y cantidad de indicadores.
  - Entradas: Evaluación virtual.
  - Salidas: Listado de intentos.
  - Reglas: Se incluyen los estudiantes inscritos que no presentaron la evaluación.
  - Clases: Intento, EvaluacionVirtual
- **RF-55 Revisar indicadores de supervisión** (prioridad Alta, actor: Docente, caso de uso: CU-30)
  - Descripción: El sistema debe presentar al docente, por intento, los eventos de supervisión registrados, agrupados por tipo y con línea de tiempo.
  - Entradas: Intento.
  - Salidas: Resumen de indicadores (p. ej. «4 cambios de pestaña; 2 periodos fuera de la página»).
  - Reglas: Los indicadores se muestran como información para revisión y nunca como una conclusión de fraude.
  - Clases: EventoSupervision, Intento
- **RF-56 Consultar resultados propios** (prioridad Media, actor: Estudiante, caso de uso: CU-31)
  - Descripción: El sistema debe permitir al estudiante consultar la calificación de sus intentos calificados y los comentarios del docente.
  - Entradas: Sesión del estudiante.
  - Salidas: Listado de evaluaciones presentadas con calificación y comentarios.
  - Reglas: El estudiante no ve los eventos de supervisión ni las respuestas de otros estudiantes.
  - Clases: Intento, Respuesta

### Módulo de Práctica
- **RF-57 Crear material de práctica** (prioridad Alta, actor: Docente, caso de uso: CU-32)
  - Descripción: El sistema debe permitir al docente crear material de práctica con título, descripción y nivel, y asociarle o quitarle preguntas aprobadas.
  - Entradas: Título; descripción; nivel; preguntas.
  - Salidas: Material de práctica en BORRADOR con origen MANUAL.
  - Reglas: Todo material debe tener al menos una pregunta asociada (multiplicidad 1..* entre MaterialPractica y Pregunta); para publicarse, todas sus preguntas deben estar APROBADAS.
  - Clases: MaterialPractica, Pregunta
- **RF-58 Publicar y archivar material de práctica** (prioridad Alta, actor: Docente, caso de uso: CU-32)
  - Descripción: El sistema debe permitir al docente asignar un material a uno o varios de sus cursos, publicarlo y archivarlo.
  - Entradas: Material; cursos.
  - Salidas: Material PUBLICADO visible para los estudiantes de los cursos asignados, o ARCHIVADO.
  - Reglas: Un material archivado deja de ser visible, pero se conservan los intentos de práctica.
  - Clases: MaterialPractica, Curso
- **RF-59 Consultar material de práctica disponible** (prioridad Alta, actor: Estudiante, caso de uso: CU-34)
  - Descripción: El sistema debe mostrar al estudiante los materiales publicados en los cursos de los grupos donde tiene inscripción activa, filtrables por nivel.
  - Entradas: Sesión del estudiante; filtros.
  - Salidas: Listado de materiales.
  - Clases: MaterialPractica, Curso, Inscripcion
- **RF-60 Resolver práctica con retroalimentación inmediata** (prioridad Alta, actor: Estudiante, caso de uso: CU-34)
  - Descripción: El sistema debe permitir al estudiante resolver un material de práctica y mostrarle, por cada respuesta, si es correcta, la respuesta correcta y una explicación.
  - Entradas: Respuestas del estudiante.
  - Salidas: Respuestas de práctica evaluadas con retroalimentación.
  - Reglas: Las respuestas abiertas de práctica muestran la respuesta esperada como referencia, sin calificación automática.
  - Clases: IntentoPractica, RespuestaPractica
- **RF-61 Calcular resultado y repetir la práctica** (prioridad Media, actor: Estudiante, caso de uso: CU-34)
  - Descripción: El sistema debe calcular el resultado de cada intento de práctica y permitir al estudiante repetir el material las veces que quiera.
  - Entradas: Intento de práctica finalizado.
  - Salidas: Porcentaje de aciertos; nuevo intento disponible.
  - Reglas: Cada repetición presenta las preguntas en orden aleatorio.
  - Clases: IntentoPractica
- **RF-62 Consultar historial de práctica** (prioridad Media, actor: Estudiante, caso de uso: CU-34)
  - Descripción: El sistema debe permitir al estudiante consultar sus intentos de práctica anteriores con fecha y resultado, y ver en qué competencias y temas tuvo más errores.
  - Entradas: Sesión del estudiante.
  - Salidas: Historial de intentos y resumen de errores por tema y competencia.
  - Clases: IntentoPractica, RespuestaPractica, Pregunta
- **RF-63 Descargar material de práctica en PDF** (prioridad Media, actor: Docente, Estudiante, caso de uso: CU-33)
  - Descripción: El sistema debe generar un PDF imprimible del material de práctica, con una sección final opcional de respuestas.
  - Entradas: Material de práctica; incluir respuestas (sí/no).
  - Salidas: Archivo PDF.
  - Reglas: El estudiante solo puede descargar materiales PUBLICADOS de sus cursos.
  - Clases: MaterialPractica

## Requisitos no funcionales

- **RNF-01 Almacenamiento seguro de contraseñas** (Seguridad, prioridad Alta): Las contraseñas se almacenan únicamente como hash con sal mediante un algoritmo adaptativo (BCrypt con factor de costo ≥ 10 o Argon2id). Criterio de aceptación: Ninguna tabla ni registro de log contiene contraseñas en texto plano.
- **RNF-02 Control de acceso por tipo de usuario** (Seguridad, prioridad Alta): Cada operación valida en el servidor el tipo de usuario y la propiedad del recurso: un docente solo accede a sus cursos, bancos, evaluaciones y materiales; un estudiante solo a los grupos donde está inscrito. Criterio de aceptación: Las pruebas de acceso directo por URL a recursos ajenos devuelven error 403.
- **RNF-03 Comunicación cifrada y sesiones** (Seguridad, prioridad Alta): Toda la comunicación usa HTTPS. Las sesiones expiran tras 30 minutos de inactividad, salvo durante un intento de evaluación en curso. Criterio de aceptación: El servidor rechaza conexiones HTTP sin redirigir; la cookie de sesión tiene los atributos Secure y HttpOnly.
- **RNF-04 Seguridad del token de recuperación** (Seguridad, prioridad Alta): El token de recuperación de contraseña es aleatorio e impredecible (mínimo 128 bits), se almacena como hash, caduca a los 30 minutos y solo puede usarse una vez. El sistema no revela si un correo está registrado ni admite más de tres solicitudes por hora para el mismo correo. Criterio de aceptación: Un token usado o vencido devuelve el mensaje «El enlace ya no es válido»; la respuesta es igual para correos registrados y no registrados.
- **RNF-05 Tolerancia a desconexiones** (Confiabilidad, prioridad Alta): Ninguna respuesta ya confirmada por el servidor se pierde ante una caída de conexión o de energía del computador del estudiante. Criterio de aceptación: Tras cerrar el navegador a mitad de un intento y volver a ingresar, el 100 % de las respuestas confirmadas aparece guardado.
- **RNF-06 Independencia del servicio de IA** (Disponibilidad, prioridad Alta): Si el servicio de IA no responde o supera sus límites, las funciones que no dependen de él (usuarios, cursos, bancos, evaluaciones, aplicación virtual, calificación, PDF y práctica) siguen funcionando. Criterio de aceptación: Con el servicio de IA deshabilitado, todos los casos de uso excepto CU-19 se ejecutan con normalidad.
- **RNF-07 Concurrencia en evaluaciones virtuales** (Rendimiento, prioridad Alta): El sistema soporta al menos 40 estudiantes presentando simultáneamente una evaluación virtual (tamaño de una sala de cómputo). Criterio de aceptación: Con 40 usuarios concurrentes, el 95 % de las operaciones de guardar respuesta responde en menos de 2 segundos.
- **RNF-08 Tiempo de respuesta general** (Rendimiento, prioridad Media): Las pantallas de consulta cargan rápidamente y los PDF se generan en un tiempo razonable. Criterio de aceptación: Páginas de consulta: < 3 s. Generación de un PDF de evaluación de hasta 50 preguntas: < 10 s. La generación con IA se ejecuta de forma asíncrona sin bloquear la interfaz.
- **RNF-09 Diseño adaptable** (Usabilidad, prioridad Alta): La interfaz se adapta a computadores, tabletas y teléfonos móviles. Criterio de aceptación: Todas las funciones del estudiante se pueden completar en pantallas desde 360 px de ancho sin desplazamiento horizontal.
- **RNF-10 Facilidad de uso** (Usabilidad, prioridad Alta): La interfaz está en español, con el contenido de las preguntas en inglés, y un docente sin capacitación previa puede crear y publicar una evaluación. Criterio de aceptación: En prueba con usuarios, un docente crea una evaluación de 10 preguntas y genera 2 versiones en menos de 20 minutos sin ayuda.
- **RNF-11 Compatibilidad con navegadores** (Compatibilidad, prioridad Alta): El sistema funciona en las dos últimas versiones estables de Chrome, Firefox y Edge. La supervisión usa las capacidades estándar del navegador (Page Visibility API y eventos focus/blur). Criterio de aceptación: El conjunto de pruebas funcionales pasa en los tres navegadores.
- **RNF-12 Arquitectura mantenible** (Mantenibilidad, prioridad Alta): La interfaz se construye con HTML y CSS, sin frameworks, y el backend en Java (Servlets) con MySQL en una arquitectura por capas (presentación, lógica, acceso a datos). La integración con IA se encapsula detrás de una interfaz para cambiar de proveedor sin modificar el resto del sistema; esto permite alternar entre Google Gemini y Groq, o pasar a una API de pago si el cliente lo decide más adelante. Criterio de aceptación: Cambiar el proveedor de IA solo requiere una nueva implementación de la interfaz de generación y su configuración.
- **RNF-13 Protección de datos personales** (Legal / Privacidad, prioridad Alta): El tratamiento de datos personales cumple la Ley 1581 de 2012. Los eventos de supervisión solo son visibles para el docente del grupo. Criterio de aceptación: El registro de estudiantes incluye la aceptación de la política de tratamiento de datos; ningún estudiante accede a datos de otros.
- **RNF-14 Integridad histórica** (Integridad, prioridad Alta): No se eliminan físicamente preguntas, versiones, evaluaciones ni inscripciones que tengan intentos asociados; se archivan o se retiran. Criterio de aceptación: Las claves foráneas impiden borrar registros referenciados; las pruebas de eliminación sobre esos registros son rechazadas.
- **RNF-15 Trazabilidad temporal** (Auditoría, prioridad Media): Las fechas y horas de intentos, respuestas y eventos se registran con la hora del servidor en la zona horaria America/Bogota. Criterio de aceptación: Modificar el reloj del computador del estudiante no altera los tiempos registrados.
- **RNF-16 Despliegue en infraestructura convencional** (Portabilidad, prioridad Media): El sistema se despliega en un servidor o VPS de 2 a 8 GB de RAM sin GPU; la IA se consume como servicio externo. Criterio de aceptación: El sistema completo se instala y opera en un servidor de 4 GB de RAM cumpliendo RNF-07.
- **RNF-17 Formato de documentos imprimibles** (Usabilidad, prioridad Media): Los PDF usan tamaño carta, márgenes de impresión estándar, encabezado con curso, título y versión, y numeración de páginas. Criterio de aceptación: Los PDF se imprimen sin cortes de texto ni preguntas divididas entre páginas.
- **RNF-18 Transparencia de la supervisión** (Ética / Usabilidad, prioridad Alta): El sistema informa al estudiante qué se registra durante una evaluación supervisada y presenta al docente los indicadores como información para revisión, no como prueba de fraude. Criterio de aceptación: La pantalla previa al intento muestra el aviso de supervisión; la vista de indicadores incluye la nota «Los indicadores no determinan por sí solos una conducta indebida».

## Casos de uso

### CU-01 Iniciar sesión — Usuarios y Acceso (actores: Administrador, Docente, Estudiante (Usuario))
Permite a un usuario registrado autenticarse para acceder a las funciones que le corresponden, y cerrar su sesión.
- Precondiciones: El usuario tiene una cuenta registrada.
- Flujo básico:
  1. El usuario ingresa a la página de inicio de sesión.
  2. El usuario escribe su correo o identificación y su contraseña.
  3. El sistema verifica la contraseña contra el hash almacenado.
  4. El sistema verifica que la cuenta esté activa.
  5. El sistema inicia la sesión y muestra el menú según el tipo de usuario (administrador, docente o estudiante).
- Excepciones:
  - 3a. Credenciales incorrectas: el sistema muestra «Correo o contraseña incorrectos» sin indicar cuál dato falló y vuelve al paso 2.
  - 4a. Cuenta inactiva: el sistema informa que la cuenta está desactivada y que debe contactar al administrador.
  - 5a. Docente con debeCambiarContrasenaDocente = verdadero: el sistema redirige a CU-02 antes de mostrar el menú.
  - En cualquier momento, el usuario selecciona «Cerrar sesión» y el sistema invalida la sesión.
- Postcondición: El usuario tiene una sesión activa.
- Requisitos: RF-01, RF-03

### CU-02 Cambiar contraseña — Usuarios y Acceso (actores: Administrador, Docente, Estudiante (Usuario))
Permite al usuario reemplazar su contraseña. Es obligatorio para el docente en su primer ingreso y para cualquier usuario tras un restablecimiento.
- Precondiciones: El usuario tiene sesión activa.
- Flujo básico:
  1. El usuario selecciona «Cambiar contraseña».
  2. El sistema solicita contraseña actual, nueva contraseña y confirmación.
  3. El usuario ingresa los datos.
  4. El sistema valida la contraseña actual y las reglas de la nueva contraseña.
  5. El sistema guarda el nuevo hash y confirma el cambio.
- Excepciones:
  - 4a. La contraseña actual no coincide: se muestra un error y se vuelve al paso 3.
  - 4b. La nueva contraseña no cumple las reglas o no coincide con la confirmación: se indican los errores.
  - 5a. Si el usuario es docente con cambio obligatorio pendiente, el sistema marca debeCambiarContrasenaDocente = falso y muestra el menú principal.
- Postcondición: La contraseña del usuario queda actualizada.
- Requisitos: RF-02, RF-03

### CU-03 Actualizar datos personales — Usuarios y Acceso (actores: Administrador, Docente, Estudiante (Usuario))
Permite al usuario modificar su nombre y su correo electrónico.
- Precondiciones: El usuario tiene sesión activa.
- Flujo básico:
  1. El usuario abre «Mi perfil».
  2. El sistema muestra nombre y correo actuales.
  3. El usuario modifica los datos y guarda.
  4. El sistema valida que el correo no esté en uso por otra cuenta.
  5. El sistema actualiza los datos y confirma.
- Excepciones:
  - 4a. El correo ya existe: el sistema muestra el error y no guarda.
- Postcondición: Los datos del usuario quedan actualizados.
- Requisitos: RF-04

### CU-04 Registrarse como estudiante — Usuarios y Acceso (actores: Estudiante)
Permite a un estudiante crear su propia cuenta.
- Precondiciones: El estudiante no tiene cuenta en el sistema.
- Flujo básico:
  1. El estudiante selecciona «Crear cuenta».
  2. El sistema muestra el formulario de registro y la política de tratamiento de datos.
  3. El estudiante ingresa identificación, nombre, correo, institución, nivel de inglés y contraseña, y acepta la política.
  4. El sistema valida que la identificación y el correo no existan.
  5. El sistema crea la cuenta activa y muestra el inicio de sesión.
- Excepciones:
  - 4a. Identificación o correo ya registrados: el sistema informa el dato duplicado.
  - 3a. El estudiante no acepta la política de datos: el sistema no permite continuar.
- Postcondición: Existe una cuenta de estudiante activa.
- Requisitos: RF-05

### CU-05 Gestionar administradores — Usuarios y Acceso (actores: Administrador)
Permite al administrador crear, actualizar y eliminar cuentas de administrador.
- Precondiciones: El administrador tiene sesión activa.
- Flujo básico:
  1. El administrador abre «Administradores».
  2. El sistema lista los administradores registrados.
  3. El administrador elige crear, editar o eliminar.
  4. Para crear o editar, ingresa identificación, nombre, correo y fecha de nacimiento; el sistema valida y guarda.
  5. Para eliminar, el sistema solicita confirmación y elimina la cuenta.
- Excepciones:
  - 5a. Es el último administrador activo o el propio usuario: el sistema rechaza la eliminación.
  - 4a. Identificación o correo duplicados: el sistema muestra el error.
- Postcondición: La lista de administradores queda actualizada.
- Requisitos: RF-06

### CU-06 Registrar docente — Usuarios y Acceso (actores: Administrador)
Permite al administrador crear la cuenta de un docente con una contraseña temporal.
- Precondiciones: El administrador tiene sesión activa.
- Flujo básico:
  1. El administrador abre «Docentes» y selecciona «Registrar docente».
  2. El administrador ingresa identificación, nombre, correo e institución.
  3. El sistema valida que la identificación y el correo no existan.
  4. El sistema crea la cuenta, genera una contraseña temporal y marca debeCambiarContrasenaDocente = verdadero.
  5. El sistema muestra la contraseña temporal una sola vez para que el administrador la entregue al docente.
- Excepciones:
  - 3a. Datos duplicados: el sistema muestra el error y no crea la cuenta.
- Postcondición: El docente puede ingresar y debe cambiar su contraseña (CU-02).
- Requisitos: RF-07

### CU-07 Gestionar estudiantes — Usuarios y Acceso (actores: Administrador)
Permite al administrador consultar estudiantes, corregir su nivel de inglés y eliminar cuentas sin actividad.
- Precondiciones: El administrador tiene sesión activa.
- Flujo básico:
  1. El administrador abre «Estudiantes».
  2. El sistema lista los estudiantes con búsqueda por nombre, identificación o correo.
  3. El administrador selecciona un estudiante.
  4. El administrador actualiza el nivel de inglés o solicita eliminar la cuenta.
  5. El sistema guarda el cambio o, previa confirmación, elimina la cuenta.
- Excepciones:
  - 5a. El estudiante tiene intentos registrados: el sistema no lo elimina y ofrece desactivarlo (CU-08).
- Postcondición: Los datos del estudiante quedan actualizados.
- Requisitos: RF-08

### CU-08 Activar o desactivar cuenta — Usuarios y Acceso (actores: Administrador)
Permite al administrador impedir o restablecer el acceso de un usuario sin eliminar su información.
- Precondiciones: El administrador tiene sesión activa.
- Flujo básico:
  1. El administrador busca al usuario.
  2. El sistema muestra su estado actual.
  3. El administrador selecciona «Activar» o «Desactivar» y confirma.
  4. El sistema actualiza activoUsuario.
- Excepciones:
  - 3a. El administrador intenta desactivarse a sí mismo: el sistema lo impide.
- Postcondición: El estado de la cuenta queda actualizado; una cuenta inactiva no puede iniciar sesión.
- Requisitos: RF-09

### CU-09 Restablecer contraseña de usuario — Usuarios y Acceso (actores: Administrador)
Permite al administrador asignar una contraseña temporal a un usuario que olvidó la suya.
- Precondiciones: El administrador tiene sesión activa. / El usuario solicitó el restablecimiento por un canal institucional.
- Flujo básico:
  1. El administrador busca al usuario.
  2. El administrador selecciona «Restablecer contraseña» y confirma.
  3. El sistema genera una contraseña temporal, guarda su hash y exige cambio en el siguiente ingreso.
  4. El sistema muestra la contraseña temporal una sola vez.
- Postcondición: El usuario ingresa con la contraseña temporal y la cambia (CU-02).
- Requisitos: RF-10

### CU-10 Recuperar contraseña olvidada — Usuarios y Acceso (actores: Administrador, Docente, Estudiante (Usuario); Servicio de correo (secundario))
Permite al usuario que olvidó su contraseña restablecerla por sí mismo mediante un enlace con token enviado a su correo electrónico registrado.
- Precondiciones: El usuario tiene una cuenta registrada y activa. / El usuario tiene acceso al correo con el que se registró.
- Flujo básico:
  1. El usuario selecciona «¿Olvidó su contraseña?» en la pantalla de inicio de sesión.
  2. El usuario ingresa su correo electrónico.
  3. El sistema informa que, si el correo está registrado, se enviará un enlace de recuperación.
  4. El sistema genera un token de un solo uso con 30 minutos de vigencia, guarda su hash e invalida los tokens anteriores del usuario.
  5. El sistema envía al correo el enlace de recuperación a través del servicio de correo.
  6. El usuario abre el enlace; el sistema valida que el token exista, no esté usado y no haya caducado.
  7. El usuario ingresa la nueva contraseña y su confirmación.
  8. El sistema valida las reglas de la contraseña, guarda el nuevo hash, marca el token como usado y confirma el cambio.
- Excepciones:
  - 3a. El correo no está registrado: el sistema muestra el mismo mensaje y no envía ningún correo (RNF-04).
  - 5a. El servicio de correo no responde: el sistema registra el error e invita a reintentar más tarde o a solicitar el restablecimiento al administrador (CU-09).
  - 6a. El token no existe, ya fue usado o caducó: el sistema muestra «El enlace ya no es válido» y ofrece solicitar uno nuevo.
  - 7a. La cuenta está desactivada: el sistema no permite continuar e indica contactar al administrador.
  - 8a. La nueva contraseña no cumple las reglas o no coincide con la confirmación: el sistema indica los errores y vuelve al paso 7.
  - 2a. El usuario supera las tres solicitudes por hora: el sistema pide esperar antes de intentarlo de nuevo.
- Postcondición: La contraseña queda actualizada, el token usado y el usuario puede iniciar sesión (CU-01).
- Requisitos: RF-11

### CU-11 Gestionar cursos — Cursos y Grupos (actores: Docente)
Permite al docente crear y mantener los cursos de inglés que orienta.
- Precondiciones: El docente tiene sesión activa.
- Flujo básico:
  1. El docente abre «Mis cursos».
  2. El sistema lista sus cursos.
  3. El docente selecciona «Nuevo curso» e ingresa nombre, descripción y nivel MCER.
  4. El sistema registra el curso como activo.
  5. El docente puede editar nombre y descripción, o activar/desactivar el curso.
- Excepciones:
  - 5a. Al desactivar un curso con grupos abiertos, el sistema advierte que no se podrán abrir grupos nuevos, pero los existentes continúan.
- Postcondición: El curso queda registrado o actualizado.
- Requisitos: RF-12

### CU-12 Gestionar grupos — Cursos y Grupos (actores: Docente)
Permite al docente abrir grupos de un curso por periodo, obtener su código de acceso y cerrarlos o reabrirlos.
- Precondiciones: El docente tiene al menos un curso activo.
- Flujo básico:
  1. El docente selecciona un curso y elige «Abrir grupo».
  2. El docente ingresa nombre y periodo.
  3. El sistema crea el grupo en estado ABIERTO y genera un código de acceso único.
  4. El sistema muestra el código para compartirlo con los estudiantes.
  5. El docente puede regenerar el código, cerrar o reabrir el grupo.
- Excepciones:
  - 2a. Ya existe un grupo con ese nombre en el mismo curso y periodo: el sistema muestra error.
  - 5a. Al regenerar el código, el anterior deja de funcionar.
- Postcondición: El grupo está disponible para inscripciones mientras esté ABIERTO.
- Requisitos: RF-13, RF-14, RF-15

### CU-13 Gestionar inscripciones — Cursos y Grupos (actores: Docente)
Permite al docente revisar quiénes están inscritos en su grupo y retirar, reincorporar o eliminar inscripciones.
- Precondiciones: El docente tiene un grupo creado.
- Flujo básico:
  1. El docente abre el grupo.
  2. El sistema lista los inscritos con fecha y estado.
  3. El docente selecciona una inscripción y elige retirar, reincorporar o eliminar.
  4. El sistema aplica el cambio.
- Excepciones:
  - 3a. La inscripción tiene intentos: el sistema no permite eliminarla y ofrece retirarla.
- Postcondición: El estado de la inscripción queda actualizado.
- Requisitos: RF-17

### CU-14 Inscribirse en un grupo — Cursos y Grupos (actores: Estudiante)
Permite al estudiante unirse a un grupo con el código que le entrega su docente.
- Precondiciones: El estudiante tiene sesión activa.
- Flujo básico:
  1. El estudiante selecciona «Unirme a un grupo».
  2. El estudiante ingresa el código de acceso.
  3. El sistema busca el grupo y verifica que esté ABIERTO.
  4. El sistema muestra curso, grupo, periodo y docente, y solicita confirmación.
  5. El sistema crea la inscripción ACTIVA.
- Excepciones:
  - 3a. Código inexistente: el sistema informa que el código no es válido.
  - 3b. Grupo cerrado: el sistema informa que no admite inscripciones.
  - 5a. El estudiante ya está inscrito: el sistema lo informa.
- Postcondición: El estudiante accede a evaluaciones y materiales del grupo y su curso.
- Requisitos: RF-16

### CU-15 Gestionar bancos de preguntas — Banco de Preguntas e IA (actores: Docente)
Permite al docente organizar su contenido en bancos de preguntas.
- Precondiciones: El docente tiene sesión activa.
- Flujo básico:
  1. El docente abre «Bancos de preguntas».
  2. El sistema lista sus bancos con la cantidad de preguntas por estado.
  3. El docente crea un banco o edita nombre y descripción de uno existente.
  4. El docente abre un banco para ver y filtrar sus preguntas.
- Postcondición: El banco queda disponible para almacenar preguntas.
- Requisitos: RF-18, RF-24

### CU-16 Gestionar preguntas — Banco de Preguntas e IA (actores: Docente)
Permite al docente crear, editar y eliminar preguntas y sus opciones de respuesta.
- Precondiciones: El docente tiene al menos un banco de preguntas.
- Flujo básico:
  1. El docente abre un banco y selecciona «Nueva pregunta».
  2. El docente ingresa enunciado, tipo, nivel, dificultad, competencia, tema y respuesta esperada.
  3. Si el tipo es selección múltiple o verdadero/falso, el docente agrega las opciones y marca la correcta.
  4. El sistema guarda la pregunta en BORRADOR con origen MANUAL.
  5. El docente puede editar la pregunta y sus opciones, o eliminarla.
- Excepciones:
  - 5a. La pregunta está usada en una versión o material: el sistema no la elimina y ofrece archivarla (CU-17).
  - 3a. Faltan opciones obligatorias: el sistema guarda el borrador y señala lo que falta para aprobarla.
- Postcondición: La pregunta queda registrada en el banco.
- Requisitos: RF-19, RF-20, RF-21, RF-24

### CU-17 Revisar y aprobar preguntas — Banco de Preguntas e IA (actores: Docente)
Permite al docente revisar preguntas en borrador (manuales o generadas con IA), aprobarlas o archivarlas.
- Precondiciones: Existen preguntas en estado BORRADOR o APROBADA.
- Flujo básico:
  1. El docente filtra las preguntas por estado BORRADOR (y opcionalmente por origen GENERADO_IA).
  2. El docente abre una pregunta y la revisa; puede editarla (CU-16).
  3. El docente selecciona «Aprobar».
  4. El sistema valida que la pregunta esté completa según su tipo.
  5. El sistema cambia el estado a APROBADA.
- Excepciones:
  - 4a. La validación falla: el sistema lista los errores y la pregunta sigue en BORRADOR.
  - 3a. El docente selecciona «Archivar»: la pregunta pasa a ARCHIVADA.
  - 3b. El docente descarta una pregunta generada que no le sirve: se elimina.
- Postcondición: Las preguntas aprobadas pueden usarse en evaluaciones y prácticas.
- Requisitos: RF-22, RF-23, RF-30

### CU-18 Cargar documento fuente — Banco de Preguntas e IA (actores: Docente)
Permite al docente subir una evaluación o material que ya tiene para que la IA lo use como base.
- Precondiciones: El docente está en CU-19 o en «Mis documentos».
- Flujo básico:
  1. El docente selecciona «Cargar documento».
  2. El docente elige un archivo PDF, DOCX o TXT.
  3. El sistema valida tipo y tamaño.
  4. El sistema almacena el archivo y registra nombre, tipo, ruta y fecha.
- Excepciones:
  - 3a. Tipo no permitido o tamaño mayor a 10 MB: el sistema rechaza el archivo.
- Postcondición: El documento queda disponible para solicitudes de generación.
- Requisitos: RF-25

### CU-19 Generar contenido con IA — Banco de Preguntas e IA (actores: Docente; Servicio de IA (secundario))
Permite al docente solicitar al servicio de IA preguntas nuevas, variantes de preguntas existentes o material de práctica.
- Precondiciones: El docente tiene sesión activa y al menos un banco de preguntas.
- Flujo básico:
  1. El docente selecciona «Generar con IA» y elige el tipo: preguntas, variantes o material de práctica.
  2. El docente indica instrucciones, cantidad, nivel y, según el tipo, banco destino, preguntas base o evaluación de referencia.
  3. Opcionalmente, el docente selecciona o carga un documento fuente (CU-18).
  4. El sistema crea la solicitud en estado PENDIENTE y la envía al servicio de IA (EN_PROCESO).
  5. El servicio de IA devuelve el contenido.
  6. El sistema guarda las preguntas o el material en BORRADOR con origen GENERADO_IA y marca la solicitud COMPLETADA.
  7. El sistema notifica al docente para que revise el contenido (CU-17 o CU-32).
- Excepciones:
  - 5a. El servicio de IA falla o no responde: el sistema marca la solicitud FALLIDA con el mensaje de error; el docente puede reintentar.
  - 5b. La respuesta no tiene el formato esperado: el sistema descarta los ítems inválidos, guarda los válidos e informa cuántos se descartaron.
- Postcondición: Existe contenido en borrador pendiente de revisión.
- Requisitos: RF-26, RF-27, RF-28, RF-29, RF-30

### CU-20 Gestionar evaluaciones — Evaluaciones y Versiones (actores: Docente)
Permite al docente crear y configurar una evaluación, seleccionar sus preguntas y marcarla como lista o archivarla.
- Precondiciones: El docente tiene preguntas APROBADAS.
- Flujo básico:
  1. El docente selecciona «Nueva evaluación» e ingresa título y descripción.
  2. El docente configura número de preguntas por versión, número de versiones y duración.
  3. El sistema muestra las preguntas aprobadas con filtros por tema, nivel, dificultad y competencia.
  4. El docente selecciona las preguntas; el sistema guarda la evaluación en BORRADOR y muestra un resumen de su distribución.
  5. El docente selecciona «Marcar como lista».
  6. El sistema valida la configuración y cambia el estado a LISTA.
- Excepciones:
  - 6a. Hay menos preguntas seleccionadas que las requeridas por versión: el sistema indica cuántas faltan.
  - 4a. El docente quita preguntas: el resumen se actualiza; el sistema no permite quitar la última pregunta, porque una evaluación debe tener al menos una.
  - 5a. El docente archiva la evaluación: pasa a ARCHIVADA y deja de aparecer en la programación virtual.
- Postcondición: La evaluación queda LISTA para generar versiones.
- Requisitos: RF-24, RF-31, RF-32, RF-33, RF-34

### CU-21 Generar versiones de evaluación — Evaluaciones y Versiones (actores: Docente)
Permite obtener varias versiones equivalentes de una misma evaluación y compararlas.
- Precondiciones: La evaluación está en estado LISTA.
- Flujo básico:
  1. El docente abre la evaluación y selecciona «Generar versiones».
  2. El sistema selecciona preguntas para cada versión respetando la distribución de competencias y dificultad.
  3. El sistema copia enunciados, opciones y respuestas en cada versión y ordena aleatoriamente preguntas y opciones.
  4. El sistema asigna código (A, B, C…) y fecha de generación a cada versión.
  5. El sistema muestra el cuadro comparativo de versiones.
- Excepciones:
  - 1a. La evaluación ya tiene versiones: el sistema genera versiones nuevas con códigos siguientes sin modificar las anteriores.
  - 5a. El docente quiere ajustar una versión: continúa en CU-22.
- Postcondición: La evaluación tiene versiones listas para PDF o aplicación virtual.
- Requisitos: RF-35, RF-36, RF-37

### CU-22 Editar versión de evaluación — Evaluaciones y Versiones (actores: Docente)
Permite al docente ajustar una versión generada antes de usarla.
- Precondiciones: La versión existe y no tiene intentos.
- Flujo básico:
  1. El docente abre la versión.
  2. El sistema muestra sus preguntas en orden con opciones y puntajes.
  3. El docente modifica enunciados, respuestas esperadas, orden, puntajes u opciones.
  4. El sistema valida y guarda los cambios en la versión, sin tocar el banco.
- Excepciones:
  - 1a. La versión ya tiene intentos: el sistema la muestra en solo lectura.
- Postcondición: La versión queda actualizada.
- Requisitos: RF-38

### CU-23 Descargar evaluación en PDF — Evaluaciones y Versiones (actores: Docente)
Permite al docente obtener el PDF imprimible de una o todas las versiones para aplicación presencial.
- Precondiciones: La evaluación tiene versiones.
- Flujo básico:
  1. El docente abre la evaluación y selecciona «Descargar PDF».
  2. El docente elige una versión o todas.
  3. El sistema genera los PDF con encabezado y preguntas sin respuestas.
  4. El sistema entrega el archivo (o un .zip si son varias versiones).
- Excepciones:
  - 3a. Error al generar el PDF: el sistema informa el error y permite reintentar.
- Postcondición: El docente tiene los PDF de la evaluación.
- Requisitos: RF-39

### CU-24 Descargar clave de respuestas — Evaluaciones y Versiones (actores: Docente)
Permite al docente obtener el PDF con las respuestas correctas de cada versión para la corrección.
- Precondiciones: La evaluación tiene versiones.
- Flujo básico:
  1. El docente selecciona «Descargar clave de respuestas».
  2. El docente elige una versión o todas.
  3. El sistema genera el PDF con número de pregunta, respuesta correcta y puntaje.
  4. El sistema entrega el archivo.
- Postcondición: El docente tiene la clave de respuestas.
- Requisitos: RF-40

### CU-25 Programar evaluación virtual — Aplicación Virtual y Supervisión (actores: Docente)
Permite al docente dejar una evaluación disponible en la plataforma para un grupo en un rango de fechas.
- Precondiciones: La evaluación está LISTA y tiene versiones. / El grupo está ABIERTO.
- Flujo básico:
  1. El docente abre el grupo y selecciona «Programar evaluación».
  2. El docente elige la evaluación y las versiones a usar.
  3. El docente define fecha y hora de inicio y fin, y activa o no la supervisión.
  4. El sistema valida los datos.
  5. El sistema crea la evaluación virtual en estado PROGRAMADA.
- Excepciones:
  - 4a. La fecha de fin es anterior o igual a la de inicio: el sistema muestra el error.
- Postcondición: La evaluación virtual se abrirá automáticamente en la fecha de inicio.
- Requisitos: RF-41

### CU-26 Controlar evaluación virtual — Aplicación Virtual y Supervisión (actores: Docente)
Permite al docente abrir, cerrar o cancelar manualmente una evaluación virtual.
- Precondiciones: Existe una evaluación virtual PROGRAMADA o ABIERTA.
- Flujo básico:
  1. El docente abre la evaluación virtual.
  2. El sistema muestra su estado, fechas y número de intentos en curso y finalizados.
  3. El docente selecciona «Abrir ahora», «Cerrar» o «Cancelar» y confirma.
  4. El sistema cambia el estado; al cerrar, finaliza los intentos EN_CURSO.
- Excepciones:
  - 3a. Cancelar con intentos existentes: el sistema advierte que los intentos quedarán sin efecto y pide doble confirmación.
- Postcondición: La evaluación virtual queda en el nuevo estado.
- Requisitos: RF-42

### CU-27 Presentar evaluación virtual — Aplicación Virtual y Supervisión (actores: Estudiante)
Permite al estudiante responder en línea una evaluación abierta de su grupo.
- Precondiciones: El estudiante tiene inscripción ACTIVA en el grupo. / La evaluación virtual está ABIERTA. / El estudiante no tiene un intento finalizado en esta evaluación.
- Flujo básico:
  1. El estudiante abre «Mis evaluaciones» y elige una evaluación abierta.
  2. El sistema muestra instrucciones, duración y, si aplica, el aviso de supervisión.
  3. El estudiante selecciona «Iniciar».
  4. El sistema crea el intento, asigna una versión y muestra las preguntas con el tiempo restante.
  5. El estudiante responde; el sistema guarda cada respuesta al instante.
  6. El estudiante selecciona «Enviar»; el sistema advierte si hay preguntas sin responder.
  7. El estudiante confirma; el sistema finaliza el intento y califica automáticamente las preguntas objetivas.
- Excepciones:
  - 4a. El estudiante ya tiene un intento EN_CURSO: el sistema lo retoma con sus respuestas guardadas.
  - 5a. Se pierde la conexión: el sistema avisa, reintenta el guardado y, al reconectar, continúa el intento.
  - 5b. Se vence el tiempo o la fecha de fin: el sistema finaliza el intento automáticamente.
  - En todo el flujo, si la supervisión está activa, se ejecuta CU-28.
- Postcondición: El intento queda FINALIZADO con sus respuestas guardadas.
- Requisitos: RF-43, RF-44, RF-45, RF-46, RF-47, RF-48, RF-51

### CU-28 Registrar eventos de supervisión — Aplicación Virtual y Supervisión (actores: Sistema (iniciado durante CU-27))
Registra situaciones atípicas durante un intento supervisado y, al cierre, analiza tiempos y similitud de respuestas.
- Precondiciones: La evaluación virtual tiene supervisionActivaEvaluacionVirtual = verdadero. / Hay un intento EN_CURSO.
- Flujo básico:
  1. El navegador detecta un cambio de pestaña, pérdida de foco, salida de la página, desconexión o reconexión.
  2. El cliente envía el evento al servidor.
  3. El sistema registra el evento con tipo, descripción y fecha/hora del servidor.
  4. Al cerrarse la evaluación virtual, el sistema calcula tiempos atípicos y similitud de respuestas y registra los eventos correspondientes.
- Excepciones:
  - 2a. No hay conexión: el cliente conserva los eventos localmente y los envía al reconectarse, junto con el evento de reconexión.
- Postcondición: Los eventos quedan asociados al intento para revisión del docente.
- Requisitos: RF-49, RF-50

### CU-29 Calificar intentos — Aplicación Virtual y Supervisión (actores: Docente)
Permite al docente revisar los intentos, calificar las respuestas abiertas y completar la calificación.
- Precondiciones: Existen intentos FINALIZADOS.
- Flujo básico:
  1. El docente abre la evaluación virtual y consulta la lista de intentos.
  2. El docente abre un intento; el sistema muestra cada respuesta con su puntaje automático o pendiente.
  3. El docente asigna puntaje y comentario a las respuestas abiertas y, si quiere, ajusta puntajes automáticos.
  4. El sistema calcula la calificación del intento.
  5. Cuando todas las respuestas tienen puntaje, el sistema marca el intento CALIFICADO.
- Excepciones:
  - 3a. El puntaje supera el máximo de la pregunta: el sistema lo rechaza.
- Postcondición: El intento queda CALIFICADO y visible para el estudiante al cerrar la evaluación.
- Requisitos: RF-51, RF-52, RF-53, RF-54

### CU-30 Revisar indicadores de supervisión — Aplicación Virtual y Supervisión (actores: Docente)
Permite al docente consultar los eventos registrados en los intentos supervisados.
- Precondiciones: La evaluación virtual tuvo supervisión activa.
- Flujo básico:
  1. El docente abre la lista de intentos; el sistema muestra la cantidad de indicadores por intento.
  2. El docente abre un intento.
  3. El sistema muestra los eventos agrupados por tipo y en línea de tiempo, con la nota de que no constituyen prueba de fraude.
  4. Para eventos de similitud, el sistema muestra el otro intento y las preguntas comparadas.
- Excepciones:
  - 1a. Ningún intento tiene eventos: el sistema lo informa.
- Postcondición: El docente decide, por su cuenta, si los eventos son relevantes.
- Requisitos: RF-54, RF-55

### CU-31 Consultar resultados de evaluación — Aplicación Virtual y Supervisión (actores: Estudiante)
Permite al estudiante ver la calificación y los comentarios de sus evaluaciones virtuales.
- Precondiciones: El estudiante tiene intentos.
- Flujo básico:
  1. El estudiante abre «Mis resultados».
  2. El sistema lista sus evaluaciones con estado del intento.
  3. El estudiante abre una evaluación calificada.
  4. El sistema muestra la calificación y los comentarios del docente.
- Excepciones:
  - 3a. El intento aún no está calificado o la evaluación sigue abierta: el sistema muestra «Pendiente de calificación».
- Postcondición: —
- Requisitos: RF-46, RF-56

### CU-32 Gestionar material de práctica — Práctica (actores: Docente)
Permite al docente crear, revisar, publicar y archivar materiales de práctica y asignarlos a sus cursos.
- Precondiciones: El docente tiene preguntas APROBADAS o contenido generado con IA.
- Flujo básico:
  1. El docente selecciona «Nuevo material» e ingresa título, descripción y nivel.
  2. El docente asocia preguntas aprobadas (o revisa las generadas con IA).
  3. El docente elige los cursos a los que se asignará el material.
  4. El docente selecciona «Publicar».
  5. El sistema valida que tenga al menos una pregunta aprobada y cambia el estado a PUBLICADO.
- Excepciones:
  - 1a. El docente prefiere generar el material: se ejecuta CU-19 (tipo material de práctica).
  - 5a. El material tiene preguntas en BORRADOR: el sistema pide aprobarlas o quitarlas.
  - 2a. El docente intenta guardar el material sin preguntas: el sistema indica que debe asociar al menos una.
  - El docente archiva un material: deja de ser visible para los estudiantes.
- Postcondición: El material está disponible para los estudiantes de los cursos asignados.
- Requisitos: RF-30, RF-57, RF-58

### CU-33 Descargar material de práctica en PDF — Práctica (actores: Docente, Estudiante)
Permite obtener una versión imprimible del material de práctica.
- Precondiciones: El material está PUBLICADO (para el estudiante) o existe (para el docente).
- Flujo básico:
  1. El usuario abre el material y selecciona «Descargar PDF».
  2. El usuario indica si desea incluir las respuestas al final.
  3. El sistema genera y entrega el PDF.
- Postcondición: El usuario tiene el PDF del material.
- Requisitos: RF-63

### CU-34 Resolver material de práctica — Práctica (actores: Estudiante)
Permite al estudiante practicar en línea y recibir retroalimentación inmediata.
- Precondiciones: El estudiante tiene inscripción ACTIVA en un grupo de un curso donde hay material PUBLICADO.
- Flujo básico:
  1. El estudiante abre «Practicar» y el sistema lista los materiales disponibles.
  2. El estudiante elige un material; el sistema crea un intento de práctica.
  3. El estudiante responde una pregunta.
  4. El sistema indica si es correcta, muestra la respuesta correcta y la explicación.
  5. Se repiten los pasos 3 y 4 hasta terminar.
  6. El sistema calcula el resultado y lo muestra con el resumen por tema y competencia.
- Excepciones:
  - 6a. El estudiante repite el material: el sistema crea un nuevo intento con las preguntas en otro orden.
  - 2a. El estudiante consulta su historial de prácticas anteriores.
- Postcondición: El intento de práctica queda registrado en el historial.
- Requisitos: RF-59, RF-60, RF-61, RF-62
