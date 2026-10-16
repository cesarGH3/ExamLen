-- Fase 10: cierre de usuarios, acceso, seguridad y CU residuales.
-- Cambios:
--   1) usuario.debe_cambiar_contrasena: marcador generico (cualquier rol) de cambio obligatorio
--      tras un restablecimiento administrativo (RF-10/CU-09). Explicito y limpio: no se esconde
--      en la cadena del hash. La tabla ya tuvo una columna con ese nombre en 001; 003 la renombro
--      a debe_cambiar_contrasena_docente y 007 creo docente.debe_cambiar_contrasena. El codigo lee
--      ahora este marcador generico; la columna docente se mantiene sincronizada por compatibilidad.
--   2) solicitud_generacion: conteos exactos de la generacion IA (RF-27/CU-19 5b): recibidas,
--      validas, invalidas y no devueltas por el proveedor. Nullable: las solicitudes historicas no
--      los tienen y no se inventan.
--   3) solicitud_generacion: contexto minimo para el reintento con los mismos datos (RF-29):
--      proveedor, tema, competencia, evaluacion de referencia e ids de preguntas base.
-- Idempotente: se puede aplicar mas de una vez sin error (comprueba information_schema).
-- No crea tablas nuevas ni toca datos existentes.

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario'
    AND COLUMN_NAME = 'debe_cambiar_contrasena');
SET @sql := IF(@existe = 0,
    'ALTER TABLE usuario ADD COLUMN debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'cantidad_recibida');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN cantidad_recibida INT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'cantidad_valida');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN cantidad_valida INT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'cantidad_invalida');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN cantidad_invalida INT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'cantidad_no_devuelta');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN cantidad_no_devuelta INT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'proveedor');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN proveedor VARCHAR(30) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'tema');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN tema VARCHAR(120) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'competencia');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN competencia VARCHAR(40) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'id_evaluacion_referencia');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN id_evaluacion_referencia INT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud_generacion'
    AND COLUMN_NAME = 'id_preguntas_base');
SET @sql := IF(@existe = 0,
    'ALTER TABLE solicitud_generacion ADD COLUMN id_preguntas_base VARCHAR(255) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill minimo y legitimo: proveedor deducible del modelo ya persistido (no se inventan datos).
UPDATE solicitud_generacion SET proveedor = 'GEMINI'
    WHERE proveedor IS NULL AND modelo LIKE 'gemini%';
UPDATE solicitud_generacion SET proveedor = 'GROQ'
    WHERE proveedor IS NULL AND modelo LIKE 'llama%';
