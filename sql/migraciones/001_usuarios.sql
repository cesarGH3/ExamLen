-- =====================================================================
-- ExamLen - Migración 001: usuarios (MySQL 8.4)
-- Ejecutar como root una sola vez.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS examlen
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE examlen;

-- ---------------------------------------------------------------------
-- USUARIO
-- Herencia Usuario <- Administrador / Docente / Estudiante mapeada en
-- una sola tabla. La columna "tipo" es el discriminador de persistencia,
-- no un atributo del modelo de dominio.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario (
  id_usuario               INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  tipo                     ENUM('ADMINISTRADOR','DOCENTE','ESTUDIANTE') NOT NULL,
  identificacion           VARCHAR(20)      NOT NULL,
  nombre                   VARCHAR(100)     NOT NULL,
  correo                   VARCHAR(254)     NOT NULL,
  contrasena_hash          VARCHAR(255)     NOT NULL,
  activo                   BOOLEAN          NOT NULL DEFAULT TRUE,
  debe_cambiar_contrasena  BOOLEAN          NOT NULL DEFAULT FALSE,
  intentos_fallidos        TINYINT UNSIGNED NOT NULL DEFAULT 0,
  bloqueado_hasta          DATETIME         NULL,
  registrado_por           INT UNSIGNED     NULL,
  fecha_creacion           DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uk_usuario_identificacion (identificacion),
  UNIQUE KEY uk_usuario_correo (correo),
  KEY idx_usuario_tipo (tipo),
  CONSTRAINT fk_usuario_registrado_por
    FOREIGN KEY (registrado_por) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- Usuario de aplicacion con minimo privilegio (sin DELETE, sin DDL).
-- CAMBIA la contrasena antes de ejecutar y usa la misma en Tomcat.
-- ---------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'examlen_app'@'localhost' IDENTIFIED BY 'CAMBIA_ESTA_CONTRASENA';
GRANT SELECT, INSERT, UPDATE ON examlen.* TO 'examlen_app'@'localhost';
FLUSH PRIVILEGES;
