-- =====================================================================
-- ExamLen - Migracion 016: pregunta.tema segun el ERS (RF-19/24)
-- El ERS define pregunta.tema VARCHAR(120) NOT NULL; la base tenia
-- VARCHAR(100). Antes de aplicarla se verifico que ningun tema existente
-- supera los 120 caracteres (maximo: 24). No se trunca nada.
-- Idempotente: si la columna ya es varchar(120), no hace nada.
-- Ejecutar despues de 015.
-- =====================================================================

USE examlen;

SET @tipoTema := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'pregunta' AND COLUMN_NAME = 'tema');
SET @nuloTema := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'pregunta' AND COLUMN_NAME = 'tema');

SET @sql := IF(@tipoTema = 'varchar(120)' AND @nuloTema = 'NO',
    'SELECT ''pregunta.tema ya es VARCHAR(120) NOT NULL'' AS resultado',
    'ALTER TABLE pregunta MODIFY tema VARCHAR(120) NOT NULL');
PREPARE paso1 FROM @sql;
EXECUTE paso1;
DEALLOCATE PREPARE paso1;

-- Verificacion
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'examlen' AND TABLE_NAME = 'pregunta' AND COLUMN_NAME = 'tema';
