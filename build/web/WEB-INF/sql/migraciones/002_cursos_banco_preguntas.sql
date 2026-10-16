-- =====================================================================
-- ExamLen - Migración 002: cursos, grupos, inscripciones y banco de preguntas
-- Entrega 1. Ejecutar como root DESPUÉS de 001_usuarios.sql.
-- No modifica ni borra datos existentes.
-- =====================================================================

USE examlen;

-- ---------------------------------------------------------------------
-- CURSO: lo que se enseña. El docente lo gestiona.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS curso (
  id_curso             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_docente           INT UNSIGNED  NOT NULL,
  nombre               VARCHAR(100)  NOT NULL,
  descripcion          VARCHAR(500)  NOT NULL DEFAULT '',
  nivel                ENUM('A1','A2','B1','B2','C1','C2') NOT NULL,
  activo               BOOLEAN       NOT NULL DEFAULT TRUE,
  fecha_creacion       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_curso),
  UNIQUE KEY uk_curso_docente_nombre (id_docente, nombre),
  CONSTRAINT fk_curso_docente FOREIGN KEY (id_docente) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- GRUPO: cuándo y a quién se dicta un curso (composición con curso).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grupo (
  id_grupo             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_curso             INT UNSIGNED  NOT NULL,
  nombre               VARCHAR(50)   NOT NULL,
  periodo              VARCHAR(10)   NOT NULL,
  codigo_acceso        CHAR(8)       NOT NULL,
  estado               ENUM('ABIERTO','CERRADO') NOT NULL DEFAULT 'ABIERTO',
  fecha_creacion       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_grupo),
  UNIQUE KEY uk_grupo_codigo (codigo_acceso),
  UNIQUE KEY uk_grupo_curso_periodo_nombre (id_curso, periodo, nombre),
  KEY idx_grupo_estado (estado),
  CONSTRAINT fk_grupo_curso FOREIGN KEY (id_curso) REFERENCES curso (id_curso)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- INSCRIPCION: vínculo estudiante - grupo (reemplaza CursoSolicitud y CursoEstudiante).
-- A partir de la Entrega 3, los intentos la referencian: por eso una inscripción
-- con actividad no se puede borrar y queda RETIRADA.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inscripcion (
  id_inscripcion       INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_grupo             INT UNSIGNED  NOT NULL,
  id_estudiante        INT UNSIGNED  NOT NULL,
  fecha_inscripcion    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  estado               ENUM('ACTIVA','RETIRADA') NOT NULL DEFAULT 'ACTIVA',
  PRIMARY KEY (id_inscripcion),
  UNIQUE KEY uk_inscripcion_grupo_estudiante (id_grupo, id_estudiante),
  KEY idx_inscripcion_estudiante (id_estudiante, estado),
  CONSTRAINT fk_inscripcion_grupo FOREIGN KEY (id_grupo) REFERENCES grupo (id_grupo),
  CONSTRAINT fk_inscripcion_estudiante FOREIGN KEY (id_estudiante) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- BANCO_PREGUNTAS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS banco_preguntas (
  id_banco             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_docente           INT UNSIGNED  NOT NULL,
  nombre               VARCHAR(100)  NOT NULL,
  descripcion          VARCHAR(500)  NOT NULL DEFAULT '',
  fecha_creacion       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_banco),
  UNIQUE KEY uk_banco_docente_nombre (id_docente, nombre),
  CONSTRAINT fk_banco_docente FOREIGN KEY (id_docente) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- PREGUNTA (composición con banco)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pregunta (
  id_pregunta          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_banco             INT UNSIGNED  NOT NULL,
  enunciado            TEXT          NOT NULL,
  tipo                 ENUM('SELECCION_MULTIPLE','VERDADERO_FALSO','RESPUESTA_CORTA','RESPUESTA_ABIERTA') NOT NULL,
  nivel                ENUM('A1','A2','B1','B2','C1','C2') NOT NULL,
  dificultad           ENUM('BAJA','MEDIA','ALTA') NOT NULL,
  competencia          ENUM('GRAMMAR','VOCABULARY','READING','WRITING') NOT NULL,
  tema                 VARCHAR(100)  NOT NULL,
  respuesta_esperada   VARCHAR(500)  NULL,
  estado               ENUM('BORRADOR','APROBADA','ARCHIVADA') NOT NULL DEFAULT 'BORRADOR',
  origen               ENUM('MANUAL','GENERADO_IA') NOT NULL DEFAULT 'MANUAL',
  fecha_creacion       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_pregunta),
  KEY idx_pregunta_banco_estado (id_banco, estado),
  KEY idx_pregunta_seleccion (nivel, competencia, dificultad, estado),
  CONSTRAINT fk_pregunta_banco FOREIGN KEY (id_banco) REFERENCES banco_preguntas (id_banco)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- OPCION_RESPUESTA (composición con pregunta: se borra con ella)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS opcion_respuesta (
  id_opcion            INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_pregunta          INT UNSIGNED  NOT NULL,
  texto                VARCHAR(500)  NOT NULL,
  es_correcta          BOOLEAN       NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id_opcion),
  KEY idx_opcion_pregunta (id_pregunta),
  CONSTRAINT fk_opcion_pregunta FOREIGN KEY (id_pregunta) REFERENCES pregunta (id_pregunta) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Permisos: DELETE solo en las tablas donde el dominio permite eliminar.
-- ---------------------------------------------------------------------
GRANT DELETE ON examlen.inscripcion      TO 'examlen_app'@'localhost';
GRANT DELETE ON examlen.pregunta         TO 'examlen_app'@'localhost';
GRANT DELETE ON examlen.opcion_respuesta TO 'examlen_app'@'localhost';
FLUSH PRIVILEGES;
