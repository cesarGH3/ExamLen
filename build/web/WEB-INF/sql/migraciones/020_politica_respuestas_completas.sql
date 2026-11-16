-- =====================================================================
-- ExamLen - Migracion 020: politica de respuestas completas
-- Fase 4. El valor historico es FALSE para no bloquear envios que antes
-- estaban permitidos. No modifica respuestas, intentos ni notas.
-- =====================================================================

USE examlen;

ALTER TABLE evaluacion_virtual
  ADD COLUMN exigir_respuestas_completas BOOLEAN NOT NULL DEFAULT FALSE AFTER intentos_permitidos;

UPDATE evaluacion_virtual
SET exigir_respuestas_completas = FALSE
WHERE exigir_respuestas_completas IS NULL;
