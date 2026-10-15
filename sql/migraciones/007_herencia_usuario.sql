-- =====================================================================
-- ExamLen - Migración 007: herencia de usuario por subclase + tokens
-- Crea administrador, docente, estudiante y token_recuperacion (4.1).
-- Copia los datos desde usuario según su columna tipo.
-- Ejecutar como root DESPUÉS de 006. No pierde filas.
-- =====================================================================

USE examlen;

-- ---------------------------------------------------------------------
-- 1. TABLAS DE ROL (herencia de tabla por subclase, PK = FK)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS administrador (
  id_usuario       INT UNSIGNED NOT NULL,
  fecha_nacimiento DATE NULL,
  PRIMARY KEY (id_usuario),
  CONSTRAINT fk_administrador_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS docente (
  id_usuario              INT UNSIGNED NOT NULL,
  debe_cambiar_contrasena TINYINT(1) NOT NULL DEFAULT 0,
  id_administrador        INT UNSIGNED NOT NULL,
  PRIMARY KEY (id_usuario),
  KEY idx_docente_administrador (id_administrador),
  CONSTRAINT fk_docente_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario) ON DELETE CASCADE,
  CONSTRAINT fk_docente_administrador FOREIGN KEY (id_administrador)
    REFERENCES administrador (id_usuario) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS estudiante (
  id_usuario   INT UNSIGNED NOT NULL,
  nivel_ingles VARCHAR(2) NOT NULL,
  PRIMARY KEY (id_usuario),
  CONSTRAINT chk_estudiante_nivel CHECK (nivel_ingles IN ('A1','A2','B1','B2','C1','C2')),
  CONSTRAINT fk_estudiante_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 2. TOKENS DE RECUPERACIÓN (RF-11; la lógica va en la tarea siguiente)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS token_recuperacion (
  id_token_recuperacion INT UNSIGNED NOT NULL AUTO_INCREMENT,
  token_hash            VARCHAR(255) NOT NULL,
  fecha_creacion        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_expiracion      DATETIME NOT NULL,
  usado                 TINYINT(1) NOT NULL DEFAULT 0,
  id_usuario            INT UNSIGNED NOT NULL,
  PRIMARY KEY (id_token_recuperacion),
  UNIQUE KEY uk_token_recuperacion_hash (token_hash),
  KEY idx_token_recuperacion_usuario (id_usuario),
  CONSTRAINT fk_token_recuperacion_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. COPIA DE DATOS desde usuario según tipo
--    (estudiantes sin nivel declarado quedan en 'A1', el más bajo del MCER)
-- ---------------------------------------------------------------------
INSERT INTO administrador (id_usuario, fecha_nacimiento)
SELECT id_usuario, fecha_nacimiento_administrador
FROM usuario
WHERE tipo = 'ADMINISTRADOR';

INSERT INTO docente (id_usuario, debe_cambiar_contrasena, id_administrador)
SELECT id_usuario, debe_cambiar_contrasena_docente, registrado_por
FROM usuario
WHERE tipo = 'DOCENTE';

INSERT INTO estudiante (id_usuario, nivel_ingles)
SELECT id_usuario, COALESCE(nivel_ingles_estudiante, 'A1')
FROM usuario
WHERE tipo = 'ESTUDIANTE';

-- ---------------------------------------------------------------------
-- 4. VERIFICACIÓN: total de usuario contra la suma de las tres hijas
--    (deben ser iguales; si no, no continúes)
-- ---------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM usuario) AS total_usuario,
       (SELECT COUNT(*) FROM administrador) + (SELECT COUNT(*) FROM docente)
         + (SELECT COUNT(*) FROM estudiante) AS suma_hijas;

-- =====================================================================
-- NOTA: los siguientes ALTER se ejecutan SOLO cuando la aplicación ya
-- funcione con el esquema nuevo (el rol se deduce de las tablas hijas).
-- Se dejan comentados a propósito.
--
-- Primero la clave foránea de registrado_por (si no, MySQL no deja
-- borrar la columna):
-- ALTER TABLE usuario DROP FOREIGN KEY fk_usuario_registrado_por;
--
-- Luego las columnas que el diccionario no define en usuario:
-- ALTER TABLE usuario
--   DROP COLUMN tipo,
--   DROP COLUMN nivel_ingles_estudiante,
--   DROP COLUMN fecha_nacimiento_administrador,
--   DROP COLUMN debe_cambiar_contrasena_docente,
--   DROP COLUMN registrado_por;
-- (el índice idx_usuario_tipo se elimina solo al borrar tipo)
-- =====================================================================

FLUSH PRIVILEGES;
