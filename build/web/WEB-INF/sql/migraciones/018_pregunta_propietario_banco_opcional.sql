-- Fase 1: RF-19, RF-24, RF-31, RF-33; RNF-02 y RNF-14.
-- Aplicar con escrituras detenidas y respaldo verificado. DDL no es reversible por ROLLBACK.
-- InicializadorEsquema verifica propietarios y asociaciones antes de ejecutar este archivo.
-- No reejecutar sobre un esquema parcialmente migrado: restaurar respaldo o reparar explícitamente.
ALTER TABLE pregunta ADD COLUMN id_docente INT UNSIGNED NULL AFTER id_pregunta;

UPDATE pregunta p JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas
SET p.id_docente = b.id_docente, p.fecha_actualizacion = p.fecha_actualizacion;

ALTER TABLE banco_preguntas
  ADD UNIQUE KEY uk_banco_id_docente (id_banco_preguntas, id_docente);

ALTER TABLE pregunta
  MODIFY COLUMN id_docente INT UNSIGNED NOT NULL,
  MODIFY COLUMN id_banco_preguntas INT UNSIGNED NULL DEFAULT NULL,
  ADD KEY idx_pregunta_docente_estado (id_docente, estado, id_pregunta),
  ADD KEY idx_pregunta_banco_docente (id_banco_preguntas, id_docente),
  ADD CONSTRAINT fk_pregunta_docente FOREIGN KEY (id_docente)
    REFERENCES docente (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT,
  DROP FOREIGN KEY fk_pregunta_banco,
  ADD CONSTRAINT fk_pregunta_banco_docente FOREIGN KEY (id_banco_preguntas, id_docente)
    REFERENCES banco_preguntas (id_banco_preguntas, id_docente) ON DELETE RESTRICT ON UPDATE RESTRICT;
