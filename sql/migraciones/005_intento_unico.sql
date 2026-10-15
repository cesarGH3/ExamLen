-- =====================================================================
-- ExamLen - Migración 005: un intento por estudiante por evaluación virtual
-- Entrega 3. Ejecutar como root DESPUÉS de 004.
-- =====================================================================

USE examlen;

ALTER TABLE intento
  ADD UNIQUE KEY uk_intento_virtual_inscripcion (id_evaluacion_virtual, id_inscripcion);

FLUSH PRIVILEGES;
