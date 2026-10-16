-- =====================================================================
-- ExamLen - Migracion 015: evaluacion.titulo y descripcion segun el ERS
-- El ERS define evaluacion.titulo VARCHAR(150) NOT NULL y
-- evaluacion.descripcion TEXT NULL; la base tenia VARCHAR(200) NOT NULL
-- y VARCHAR(500) NOT NULL. Antes de aplicarla se verifico que ningun
-- titulo existente supera los 150 caracteres (maximo: 11) y que la
-- descripcion mas larga mide 13 caracteres (VARCHAR -> TEXT no pierde
-- datos). No se trunca nada.
-- Idempotente: si las columnas ya tienen el tipo y la nulabilidad del
-- ERS, no hace nada.
-- Ejecutar despues de 014.
-- =====================================================================

USE examlen;

SET @tipoTitulo := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'evaluacion' AND COLUMN_NAME = 'titulo');
SET @nuloTitulo := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'evaluacion' AND COLUMN_NAME = 'titulo');

SET @sql := IF(@tipoTitulo = 'varchar(150)' AND @nuloTitulo = 'NO',
    'SELECT ''evaluacion.titulo ya es VARCHAR(150) NOT NULL'' AS resultado',
    'ALTER TABLE evaluacion MODIFY titulo VARCHAR(150) NOT NULL');
PREPARE paso1 FROM @sql;
EXECUTE paso1;
DEALLOCATE PREPARE paso1;

SET @tipoDescripcion := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'evaluacion' AND COLUMN_NAME = 'descripcion');
SET @nuloDescripcion := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'evaluacion' AND COLUMN_NAME = 'descripcion');

SET @sql := IF(@tipoDescripcion = 'text' AND @nuloDescripcion = 'YES',
    'SELECT ''evaluacion.descripcion ya es TEXT NULL'' AS resultado',
    'ALTER TABLE evaluacion MODIFY descripcion TEXT NULL');
PREPARE paso2 FROM @sql;
EXECUTE paso2;
DEALLOCATE PREPARE paso2;

-- Verificacion
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'evaluacion'
  AND COLUMN_NAME IN ('titulo', 'descripcion')
ORDER BY COLUMN_NAME;
