-- =====================================================================
-- ExamLen - Migración 003: alineación con el diagrama de clases v3
-- Ejecutar como root DESPUÉS de 001 y 002.
-- 1) Renombra columnas de los módulos 1-3 al nombre del atributo del diagrama
-- 2) Agrega las columnas nuevas de Usuario/Administrador/Docente/Estudiante
-- 3) Crea las tablas de los módulos 4-7 (clases + relaciones M:N)
-- 4) Agrega la relación SolicitudGeneracion -> Pregunta
-- =====================================================================

USE examlen;

-- ---------------------------------------------------------------------
-- 1. RENAMES (usuarios)
-- ---------------------------------------------------------------------
ALTER TABLE usuario
  RENAME COLUMN identificacion TO identificacion_usuario,
  RENAME COLUMN nombre TO nombre_usuario,
  RENAME COLUMN correo TO correo_usuario,
  RENAME COLUMN contrasena_hash TO contrasena_hash_usuario,
  RENAME COLUMN activo TO activo_usuario,
  RENAME COLUMN debe_cambiar_contrasena TO debe_cambiar_contrasena_docente;

-- ---------------------------------------------------------------------
-- 1. RENAMES (cursos)
-- ---------------------------------------------------------------------
ALTER TABLE curso
  RENAME COLUMN nombre TO nombre_curso,
  RENAME COLUMN descripcion TO descripcion_curso,
  RENAME COLUMN nivel TO nivel_curso,
  RENAME COLUMN activo TO activo_curso;

ALTER TABLE grupo
  RENAME COLUMN nombre TO nombre_grupo,
  RENAME COLUMN periodo TO periodo_grupo,
  RENAME COLUMN codigo_acceso TO codigo_acceso_grupo,
  RENAME COLUMN estado TO estado_grupo;

ALTER TABLE inscripcion
  RENAME COLUMN estado TO estado_inscripcion;

-- ---------------------------------------------------------------------
-- 1. RENAMES (banco de preguntas)
-- ---------------------------------------------------------------------
ALTER TABLE banco_preguntas
  RENAME COLUMN id_banco TO id_banco_preguntas,
  RENAME COLUMN nombre TO nombre_banco_preguntas,
  RENAME COLUMN descripcion TO descripcion_banco_preguntas;

ALTER TABLE pregunta
  RENAME COLUMN id_banco TO id_banco_preguntas,
  RENAME COLUMN enunciado TO enunciado_pregunta,
  RENAME COLUMN tipo TO tipo_pregunta,
  RENAME COLUMN nivel TO nivel_pregunta,
  RENAME COLUMN dificultad TO dificultad_pregunta,
  RENAME COLUMN competencia TO competencia_pregunta,
  RENAME COLUMN tema TO tema_pregunta,
  RENAME COLUMN respuesta_esperada TO respuesta_esperada_pregunta,
  RENAME COLUMN estado TO estado_pregunta,
  RENAME COLUMN origen TO origen_pregunta;

ALTER TABLE opcion_respuesta
  RENAME COLUMN id_opcion TO id_opcion_respuesta,
  RENAME COLUMN texto TO texto_opcion_respuesta,
  RENAME COLUMN es_correcta TO es_correcta_opcion_respuesta;

-- ---------------------------------------------------------------------
-- 2. COLUMNAS NUEVAS (atributos del diagrama v3)
-- ---------------------------------------------------------------------
ALTER TABLE usuario
  ADD COLUMN institucion_usuario VARCHAR(150) NOT NULL DEFAULT '' AFTER correo_usuario,
  ADD COLUMN nivel_ingles_estudiante ENUM('A1','A2','B1','B2','C1','C2') NULL AFTER institucion_usuario,
  ADD COLUMN fecha_nacimiento_administrador DATE NULL AFTER nivel_ingles_estudiante;

-- ---------------------------------------------------------------------
-- 3. MÓDULO 4: EVALUACIONES Y VERSIONES
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS evaluacion (
  id_evaluacion                  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_docente                     INT UNSIGNED NOT NULL,
  titulo_evaluacion              VARCHAR(200) NOT NULL,
  descripcion_evaluacion         VARCHAR(500) NOT NULL DEFAULT '',
  numero_preguntas_evaluacion    INT NOT NULL DEFAULT 0,
  numero_versiones_evaluacion    INT NOT NULL DEFAULT 0,
  duracion_minutos_evaluacion    INT NOT NULL DEFAULT 0,
  estado_evaluacion              ENUM('BORRADOR','LISTA','ARCHIVADA') NOT NULL DEFAULT 'BORRADOR',
  fecha_creacion                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_evaluacion),
  KEY idx_evaluacion_docente (id_docente),
  CONSTRAINT fk_evaluacion_docente FOREIGN KEY (id_docente) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS version_evaluacion (
  id_version_evaluacion               INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_evaluacion                       INT UNSIGNED NOT NULL,
  codigo_version_evaluacion           VARCHAR(20) NOT NULL,
  fecha_generacion_version_evaluacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_creacion                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_version_evaluacion),
  UNIQUE KEY uk_version_codigo (id_evaluacion, codigo_version_evaluacion),
  CONSTRAINT fk_version_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES evaluacion (id_evaluacion)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS version_pregunta (
  id_version_pregunta                   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_version_evaluacion                 INT UNSIGNED NOT NULL,
  id_pregunta                           INT UNSIGNED NOT NULL,
  enunciado_version_pregunta            TEXT NOT NULL,
  respuesta_esperada_version_pregunta   VARCHAR(500) NULL,
  orden_version_pregunta                INT NOT NULL DEFAULT 0,
  puntaje_version_pregunta              DOUBLE NOT NULL DEFAULT 0,
  PRIMARY KEY (id_version_pregunta),
  KEY idx_version_pregunta_version (id_version_evaluacion),
  KEY idx_version_pregunta_origen (id_pregunta),
  CONSTRAINT fk_version_pregunta_version FOREIGN KEY (id_version_evaluacion) REFERENCES version_evaluacion (id_version_evaluacion) ON DELETE CASCADE,
  CONSTRAINT fk_version_pregunta_origen FOREIGN KEY (id_pregunta) REFERENCES pregunta (id_pregunta)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS version_opcion (
  id_version_opcion           INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_version_pregunta         INT UNSIGNED NOT NULL,
  texto_version_opcion        VARCHAR(500) NOT NULL,
  es_correcta_version_opcion  BOOLEAN NOT NULL DEFAULT FALSE,
  orden_version_opcion        INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id_version_opcion),
  KEY idx_version_opcion_pregunta (id_version_pregunta),
  CONSTRAINT fk_version_opcion_pregunta FOREIGN KEY (id_version_pregunta) REFERENCES version_pregunta (id_version_pregunta) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Relación Evaluacion 0..* -- 1..* Pregunta (selecciona)
CREATE TABLE IF NOT EXISTS evaluacion_pregunta (
  id_evaluacion INT UNSIGNED NOT NULL,
  id_pregunta   INT UNSIGNED NOT NULL,
  PRIMARY KEY (id_evaluacion, id_pregunta),
  CONSTRAINT fk_evaluacion_pregunta_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES evaluacion (id_evaluacion) ON DELETE CASCADE,
  CONSTRAINT fk_evaluacion_pregunta_pregunta FOREIGN KEY (id_pregunta) REFERENCES pregunta (id_pregunta)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. MÓDULO 5: APLICACIÓN VIRTUAL Y SUPERVISIÓN
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS evaluacion_virtual (
  id_evaluacion_virtual                    INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_grupo                                 INT UNSIGNED NOT NULL,
  id_evaluacion                            INT UNSIGNED NOT NULL,
  fecha_inicio_evaluacion_virtual          DATETIME NOT NULL,
  fecha_fin_evaluacion_virtual             DATETIME NOT NULL,
  supervision_activa_evaluacion_virtual    BOOLEAN NOT NULL DEFAULT FALSE,
  estado_evaluacion_virtual                ENUM('PROGRAMADA','ABIERTA','CERRADA','CANCELADA') NOT NULL DEFAULT 'PROGRAMADA',
  fecha_creacion                           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_evaluacion_virtual),
  KEY idx_evaluacion_virtual_grupo (id_grupo),
  KEY idx_evaluacion_virtual_evaluacion (id_evaluacion),
  CONSTRAINT fk_evaluacion_virtual_grupo FOREIGN KEY (id_grupo) REFERENCES grupo (id_grupo),
  CONSTRAINT fk_evaluacion_virtual_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES evaluacion (id_evaluacion)
) ENGINE = InnoDB;

-- Relación EvaluacionVirtual 0..* -- 1..* VersionEvaluacion (utiliza)
CREATE TABLE IF NOT EXISTS evaluacion_virtual_version (
  id_evaluacion_virtual  INT UNSIGNED NOT NULL,
  id_version_evaluacion  INT UNSIGNED NOT NULL,
  PRIMARY KEY (id_evaluacion_virtual, id_version_evaluacion),
  CONSTRAINT fk_ev_version_virtual FOREIGN KEY (id_evaluacion_virtual) REFERENCES evaluacion_virtual (id_evaluacion_virtual) ON DELETE CASCADE,
  CONSTRAINT fk_ev_version_version FOREIGN KEY (id_version_evaluacion) REFERENCES version_evaluacion (id_version_evaluacion)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS intento (
  id_intento               INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_evaluacion_virtual    INT UNSIGNED NOT NULL,
  id_inscripcion           INT UNSIGNED NOT NULL,
  id_version_evaluacion    INT UNSIGNED NOT NULL,
  fecha_inicio_intento     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_fin_intento        DATETIME NULL,
  calificacion_intento     DOUBLE NULL,
  estado_intento           ENUM('EN_CURSO','FINALIZADO','CALIFICADO') NOT NULL DEFAULT 'EN_CURSO',
  fecha_creacion           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_intento),
  KEY idx_intento_virtual (id_evaluacion_virtual),
  KEY idx_intento_inscripcion (id_inscripcion),
  KEY idx_intento_version (id_version_evaluacion),
  CONSTRAINT fk_intento_virtual FOREIGN KEY (id_evaluacion_virtual) REFERENCES evaluacion_virtual (id_evaluacion_virtual) ON DELETE CASCADE,
  CONSTRAINT fk_intento_inscripcion FOREIGN KEY (id_inscripcion) REFERENCES inscripcion (id_inscripcion),
  CONSTRAINT fk_intento_version FOREIGN KEY (id_version_evaluacion) REFERENCES version_evaluacion (id_version_evaluacion)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS respuesta (
  id_respuesta                 INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_intento                   INT UNSIGNED NOT NULL,
  id_version_pregunta          INT UNSIGNED NOT NULL,
  id_version_opcion            INT UNSIGNED NULL,
  contenido_respuesta          TEXT NULL,
  fecha_respuesta              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  puntaje_respuesta            DOUBLE NULL,
  comentario_docente_respuesta VARCHAR(1000) NULL,
  PRIMARY KEY (id_respuesta),
  KEY idx_respuesta_intento (id_intento),
  KEY idx_respuesta_pregunta (id_version_pregunta),
  KEY idx_respuesta_opcion (id_version_opcion),
  CONSTRAINT fk_respuesta_intento FOREIGN KEY (id_intento) REFERENCES intento (id_intento) ON DELETE CASCADE,
  CONSTRAINT fk_respuesta_pregunta FOREIGN KEY (id_version_pregunta) REFERENCES version_pregunta (id_version_pregunta),
  CONSTRAINT fk_respuesta_opcion FOREIGN KEY (id_version_opcion) REFERENCES version_opcion (id_version_opcion)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS evento_supervision (
  id_evento_supervision             INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_intento                        INT UNSIGNED NOT NULL,
  tipo_evento_supervision           ENUM('CAMBIO_PESTANA','PERDIDA_FOCO','SALIDA_PAGINA','DESCONEXION','RECONEXION','TIEMPO_ATIPICO','SIMILITUD_RESPUESTAS') NOT NULL,
  descripcion_evento_supervision    VARCHAR(500) NOT NULL DEFAULT '',
  fecha_hora_evento_supervision     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_evento_supervision),
  KEY idx_evento_intento (id_intento),
  CONSTRAINT fk_evento_intento FOREIGN KEY (id_intento) REFERENCES intento (id_intento) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. MÓDULO 7: IA (documento y solicitud, antes de material de práctica)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS documento_fuente (
  id_documento_fuente                     INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_docente                              INT UNSIGNED NOT NULL,
  nombre_archivo_documento_fuente         VARCHAR(255) NOT NULL,
  tipo_archivo_documento_fuente           VARCHAR(100) NOT NULL,
  ruta_almacenamiento_documento_fuente    VARCHAR(500) NOT NULL,
  fecha_carga_documento_fuente            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_documento_fuente),
  KEY idx_documento_docente (id_docente),
  CONSTRAINT fk_documento_docente FOREIGN KEY (id_docente) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS solicitud_generacion (
  id_solicitud_generacion               INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_docente                            INT UNSIGNED NOT NULL,
  id_documento_fuente                   INT UNSIGNED NULL,
  id_banco_preguntas                    INT UNSIGNED NULL,
  tipo_solicitud_generacion             ENUM('PREGUNTAS','VARIANTES','MATERIAL_PRACTICA') NOT NULL,
  instrucciones_solicitud_generacion    TEXT NULL,
  cantidad_solicitud_generacion         INT NOT NULL DEFAULT 0,
  nivel_solicitud_generacion            ENUM('A1','A2','B1','B2','C1','C2') NULL,
  modelo_solicitud_generacion           VARCHAR(100) NULL,
  estado_solicitud_generacion           ENUM('PENDIENTE','EN_PROCESO','COMPLETADA','FALLIDA') NOT NULL DEFAULT 'PENDIENTE',
  fecha_solicitud_generacion            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  mensaje_error_solicitud_generacion    VARCHAR(1000) NULL,
  fecha_creacion                        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_solicitud_generacion),
  KEY idx_solicitud_docente (id_docente),
  KEY idx_solicitud_documento (id_documento_fuente),
  KEY idx_solicitud_banco (id_banco_preguntas),
  CONSTRAINT fk_solicitud_docente FOREIGN KEY (id_docente) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_solicitud_documento FOREIGN KEY (id_documento_fuente) REFERENCES documento_fuente (id_documento_fuente),
  CONSTRAINT fk_solicitud_banco FOREIGN KEY (id_banco_preguntas) REFERENCES banco_preguntas (id_banco_preguntas)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. MÓDULO 6: PRÁCTICA
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS material_practica (
  id_material_practica          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_docente                    INT UNSIGNED NOT NULL,
  id_solicitud_generacion       INT UNSIGNED NULL,
  titulo_material_practica      VARCHAR(200) NOT NULL,
  descripcion_material_practica VARCHAR(500) NOT NULL DEFAULT '',
  nivel_material_practica       ENUM('A1','A2','B1','B2','C1','C2') NULL,
  estado_material_practica      ENUM('BORRADOR','PUBLICADO','ARCHIVADO') NOT NULL DEFAULT 'BORRADOR',
  origen_material_practica      ENUM('MANUAL','GENERADO_IA') NOT NULL DEFAULT 'MANUAL',
  fecha_creacion                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_material_practica),
  KEY idx_material_docente (id_docente),
  KEY idx_material_solicitud (id_solicitud_generacion),
  CONSTRAINT fk_material_docente FOREIGN KEY (id_docente) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_material_solicitud FOREIGN KEY (id_solicitud_generacion) REFERENCES solicitud_generacion (id_solicitud_generacion)
) ENGINE = InnoDB;

-- Relación Curso 0..* -- 0..* MaterialPractica (ofrece)
CREATE TABLE IF NOT EXISTS curso_material_practica (
  id_curso              INT UNSIGNED NOT NULL,
  id_material_practica  INT UNSIGNED NOT NULL,
  PRIMARY KEY (id_curso, id_material_practica),
  CONSTRAINT fk_cmp_curso FOREIGN KEY (id_curso) REFERENCES curso (id_curso) ON DELETE CASCADE,
  CONSTRAINT fk_cmp_material FOREIGN KEY (id_material_practica) REFERENCES material_practica (id_material_practica) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Relación MaterialPractica 0..* -- 1..* Pregunta (incluye)
CREATE TABLE IF NOT EXISTS material_practica_pregunta (
  id_material_practica  INT UNSIGNED NOT NULL,
  id_pregunta           INT UNSIGNED NOT NULL,
  PRIMARY KEY (id_material_practica, id_pregunta),
  CONSTRAINT fk_mpp_material FOREIGN KEY (id_material_practica) REFERENCES material_practica (id_material_practica) ON DELETE CASCADE,
  CONSTRAINT fk_mpp_pregunta FOREIGN KEY (id_pregunta) REFERENCES pregunta (id_pregunta)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS intento_practica (
  id_intento_practica             INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_material_practica            INT UNSIGNED NOT NULL,
  id_estudiante                   INT UNSIGNED NOT NULL,
  fecha_inicio_intento_practica   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_fin_intento_practica      DATETIME NULL,
  calificacion_intento_practica   DOUBLE NULL,
  fecha_creacion                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_intento_practica),
  KEY idx_intento_practica_material (id_material_practica),
  KEY idx_intento_practica_estudiante (id_estudiante),
  CONSTRAINT fk_intento_practica_material FOREIGN KEY (id_material_practica) REFERENCES material_practica (id_material_practica),
  CONSTRAINT fk_intento_practica_estudiante FOREIGN KEY (id_estudiante) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS respuesta_practica (
  id_respuesta_practica                   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_intento_practica                     INT UNSIGNED NOT NULL,
  id_pregunta                             INT UNSIGNED NOT NULL,
  id_opcion_respuesta                     INT UNSIGNED NULL,
  contenido_respuesta_practica            TEXT NULL,
  es_correcta_respuesta_practica          BOOLEAN NOT NULL DEFAULT FALSE,
  retroalimentacion_respuesta_practica    VARCHAR(1000) NULL,
  PRIMARY KEY (id_respuesta_practica),
  KEY idx_respuesta_practica_intento (id_intento_practica),
  KEY idx_respuesta_practica_pregunta (id_pregunta),
  KEY idx_respuesta_practica_opcion (id_opcion_respuesta),
  CONSTRAINT fk_respuesta_practica_intento FOREIGN KEY (id_intento_practica) REFERENCES intento_practica (id_intento_practica) ON DELETE CASCADE,
  CONSTRAINT fk_respuesta_practica_pregunta FOREIGN KEY (id_pregunta) REFERENCES pregunta (id_pregunta),
  CONSTRAINT fk_respuesta_practica_opcion FOREIGN KEY (id_opcion_respuesta) REFERENCES opcion_respuesta (id_opcion_respuesta)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 4. SolicitudGeneracion 0..1 -- 0..* Pregunta (genera)
-- ---------------------------------------------------------------------
ALTER TABLE pregunta
  ADD COLUMN id_solicitud_generacion INT UNSIGNED NULL AFTER id_banco_preguntas,
  ADD KEY idx_pregunta_solicitud (id_solicitud_generacion),
  ADD CONSTRAINT fk_pregunta_solicitud FOREIGN KEY (id_solicitud_generacion) REFERENCES solicitud_generacion (id_solicitud_generacion);

FLUSH PRIVILEGES;
