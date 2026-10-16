-- =====================================================================
-- ExamLen - Migracion 014: intento.calificacion en escala 0,0-5,0 (RF-53)
-- El ERS define intento.calificacion DECIMAL(3,1); la columna estaba en
-- DOUBLE y guardaba la suma bruta de puntos. La Fase 3 normaliza la nota
-- final a 0,0-5,0 y alinea el tipo con el diccionario.
-- Idempotente: si la columna ya es DECIMAL(3,1), no hace nada.
-- Ejecutar como root DESPUES de 013. Antes de aplicarla se verifico que
-- no existen intentos con calificacion fuera de 0-5 (0 intentos).
-- =====================================================================

USE examlen;

SET @tipo := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'intento' AND COLUMN_NAME = 'calificacion');

SET @sql := IF(@tipo LIKE 'decimal(3,1)%',
    'SELECT ''intento.calificacion ya es DECIMAL(3,1)'' AS resultado',
    'ALTER TABLE intento MODIFY calificacion DECIMAL(3,1) NULL');

PREPARE migracion FROM @sql;
EXECUTE migracion;
DEALLOCATE PREPARE migracion;

-- Verificacion
SELECT COLUMN_TYPE AS tipo_final FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'intento' AND COLUMN_NAME = 'calificacion';
