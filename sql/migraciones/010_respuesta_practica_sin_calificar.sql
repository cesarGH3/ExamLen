-- =====================================================================
-- ExamLen - Migración 010: respuestas de práctica sin calificar (RF-60)
-- La ERS define respuesta_practica.es_correcta como nullable: las
-- preguntas abiertas de práctica muestran la respuesta esperada como
-- referencia y no se califican automáticamente, así que quedan en NULL.
-- Ejecutar como root DESPUÉS de 009.
-- =====================================================================

USE examlen;

ALTER TABLE respuesta_practica MODIFY es_correcta TINYINT(1) NULL DEFAULT NULL;

-- Verificación: la columna debe admitir nulos
SHOW COLUMNS FROM respuesta_practica;
