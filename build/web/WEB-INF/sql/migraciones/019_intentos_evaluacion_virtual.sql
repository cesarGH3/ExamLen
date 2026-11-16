-- =====================================================================
-- ExamLen - Migracion 019: uno o dos intentos por evaluacion virtual
-- Fase 3. Conserva todos los registros historicos como intento 1 y
-- reemplaza la unicidad por estudiante por unicidad por numero de intento.
-- Aplicar con escrituras detenidas y respaldo verificado.
-- =====================================================================

USE examlen;

ALTER TABLE evaluacion_virtual
  ADD COLUMN intentos_permitidos TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER supervision_activa,
  ADD CONSTRAINT ck_evaluacion_virtual_intentos CHECK (intentos_permitidos IN (1, 2));

ALTER TABLE intento
  ADD COLUMN numero_intento TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER id_inscripcion,
  ADD CONSTRAINT ck_intento_numero CHECK (numero_intento IN (1, 2));

UPDATE evaluacion_virtual
SET intentos_permitidos = 1
WHERE intentos_permitidos IS NULL OR intentos_permitidos NOT IN (1, 2);

UPDATE intento
SET numero_intento = 1
WHERE numero_intento IS NULL OR numero_intento NOT IN (1, 2);

ALTER TABLE intento
  DROP INDEX uk_intento_virtual_inscripcion,
  ADD UNIQUE KEY uk_intento_virtual_inscripcion_numero
    (id_evaluacion_virtual, id_inscripcion, numero_intento);
