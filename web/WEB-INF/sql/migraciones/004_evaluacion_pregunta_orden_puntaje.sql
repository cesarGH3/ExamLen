-- =====================================================================
-- ExamLen - Migración 004: orden y puntaje de las preguntas seleccionadas
-- Entrega 2. Ejecutar como root DESPUÉS de 003.
-- El orden es por grupo de equivalencia (los miembros del grupo comparten
-- el valor) y el puntaje también (así todas las versiones suman igual).
-- =====================================================================

USE examlen;

ALTER TABLE evaluacion_pregunta
  ADD COLUMN orden_evaluacion_pregunta   INT    NOT NULL DEFAULT 0 AFTER id_pregunta,
  ADD COLUMN puntaje_evaluacion_pregunta DOUBLE NOT NULL DEFAULT 0 AFTER orden_evaluacion_pregunta;

FLUSH PRIVILEGES;
