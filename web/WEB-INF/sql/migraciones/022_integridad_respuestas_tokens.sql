-- Auditoria 2: SQL-001..005 y JAVA-002. Requiere MySQL 8.4.
-- Detener escrituras y respaldar antes de aplicar: DDL hace commit implicito.
-- El preflight aborta antes del primer cambio persistente si hay datos invalidos.
-- No reejecutar una migracion parcial; restaurar o reparar explicitamente.
CREATE TEMPORARY TABLE auditoria_022_preflight (
  inconsistencias BIGINT NOT NULL,
  CONSTRAINT ck_auditoria_022_sin_inconsistencias CHECK (inconsistencias = 0)
);
INSERT INTO auditoria_022_preflight
SELECT COUNT(*) FROM (
  SELECT r.id_respuesta AS id FROM respuesta r
  JOIN intento i ON i.id_intento = r.id_intento
  JOIN version_pregunta vp ON vp.id_version_pregunta = r.id_version_pregunta
  LEFT JOIN version_opcion vo ON vo.id_version_opcion = r.id_version_opcion
  WHERE i.id_version_evaluacion <> vp.id_version_evaluacion
     OR (r.id_version_opcion IS NOT NULL AND (vo.id_version_opcion IS NULL OR vo.id_version_pregunta <> r.id_version_pregunta))
     OR r.puntaje < 0 OR r.puntaje > vp.puntaje
  UNION ALL
  SELECT rp.id_respuesta_practica FROM respuesta_practica rp
  JOIN intento_practica ip ON ip.id_intento_practica = rp.id_intento_practica
  LEFT JOIN material_pregunta mp ON mp.id_material_practica = ip.id_material_practica AND mp.id_pregunta = rp.id_pregunta
  LEFT JOIN opcion_respuesta o ON o.id_opcion_respuesta = rp.id_opcion_respuesta
  WHERE mp.id_pregunta IS NULL OR (rp.id_opcion_respuesta IS NOT NULL AND (o.id_opcion_respuesta IS NULL OR o.id_pregunta <> rp.id_pregunta))
  UNION ALL
  SELECT MIN(id_respuesta_practica) FROM respuesta_practica GROUP BY id_intento_practica, id_pregunta HAVING COUNT(*) > 1
  UNION ALL
  SELECT id_intento FROM intento WHERE calificacion < 0 OR calificacion > 5
  UNION ALL
  SELECT id_intento_practica FROM intento_practica WHERE calificacion < 0 OR calificacion > 100
  UNION ALL
  SELECT id_version_pregunta FROM version_pregunta WHERE puntaje < 0
  UNION ALL
  SELECT t.id_curso FROM curso t LEFT JOIN docente u ON u.id_usuario = t.id_docente WHERE u.id_usuario IS NULL
  UNION ALL
  SELECT t.id_banco_preguntas FROM banco_preguntas t LEFT JOIN docente u ON u.id_usuario = t.id_docente WHERE u.id_usuario IS NULL
  UNION ALL
  SELECT t.id_evaluacion FROM evaluacion t LEFT JOIN docente u ON u.id_usuario = t.id_docente WHERE u.id_usuario IS NULL
  UNION ALL
  SELECT t.id_material_practica FROM material_practica t LEFT JOIN docente u ON u.id_usuario = t.id_docente WHERE u.id_usuario IS NULL
  UNION ALL
  SELECT t.id_solicitud_generacion FROM solicitud_generacion t LEFT JOIN docente u ON u.id_usuario = t.id_docente WHERE u.id_usuario IS NULL
  UNION ALL
  SELECT t.id_inscripcion FROM inscripcion t LEFT JOIN estudiante u ON u.id_usuario = t.id_estudiante WHERE u.id_usuario IS NULL
  UNION ALL
  SELECT t.id_intento_practica FROM intento_practica t LEFT JOIN estudiante u ON u.id_usuario = t.id_estudiante WHERE u.id_usuario IS NULL
 ) AS invalidos;
DROP TEMPORARY TABLE auditoria_022_preflight;

-- Contexto redundante controlado por FK compuestas: tambien protege cambios en los padres.
ALTER TABLE intento ADD UNIQUE KEY uk_intento_id_version (id_intento, id_version_evaluacion);
ALTER TABLE version_pregunta ADD UNIQUE KEY uk_vp_id_version (id_version_pregunta, id_version_evaluacion);
ALTER TABLE version_opcion ADD UNIQUE KEY uk_vo_id_pregunta (id_version_opcion, id_version_pregunta);
ALTER TABLE respuesta ADD COLUMN id_version_evaluacion INT UNSIGNED NULL;
UPDATE respuesta r JOIN intento i ON i.id_intento = r.id_intento SET r.id_version_evaluacion = i.id_version_evaluacion;
ALTER TABLE respuesta
  MODIFY id_version_evaluacion INT UNSIGNED NOT NULL DEFAULT 0,
  ADD CONSTRAINT fk_respuesta_intento_version FOREIGN KEY (id_intento, id_version_evaluacion) REFERENCES intento (id_intento, id_version_evaluacion),
  ADD CONSTRAINT fk_respuesta_pregunta_version FOREIGN KEY (id_version_pregunta, id_version_evaluacion) REFERENCES version_pregunta (id_version_pregunta, id_version_evaluacion),
  ADD CONSTRAINT fk_respuesta_opcion_pregunta FOREIGN KEY (id_version_opcion, id_version_pregunta) REFERENCES version_opcion (id_version_opcion, id_version_pregunta);

ALTER TABLE intento_practica ADD UNIQUE KEY uk_ip_id_material (id_intento_practica, id_material_practica);
ALTER TABLE opcion_respuesta ADD UNIQUE KEY uk_opcion_id_pregunta (id_opcion_respuesta, id_pregunta);
ALTER TABLE respuesta_practica ADD COLUMN id_material_practica INT UNSIGNED NULL;
UPDATE respuesta_practica rp JOIN intento_practica ip ON ip.id_intento_practica = rp.id_intento_practica SET rp.id_material_practica = ip.id_material_practica;
ALTER TABLE respuesta_practica
  MODIFY id_material_practica INT UNSIGNED NOT NULL DEFAULT 0,
  ADD UNIQUE KEY uk_rp_intento_pregunta (id_intento_practica, id_pregunta),
  ADD CONSTRAINT fk_rp_intento_material FOREIGN KEY (id_intento_practica, id_material_practica) REFERENCES intento_practica (id_intento_practica, id_material_practica),
  ADD CONSTRAINT fk_rp_material_pregunta FOREIGN KEY (id_material_practica, id_pregunta) REFERENCES material_pregunta (id_material_practica, id_pregunta),
  ADD CONSTRAINT fk_rp_opcion_pregunta FOREIGN KEY (id_opcion_respuesta, id_pregunta) REFERENCES opcion_respuesta (id_opcion_respuesta, id_pregunta);

ALTER TABLE intento ADD CONSTRAINT ck_intento_calificacion_rango CHECK (calificacion IS NULL OR calificacion BETWEEN 0 AND 5);
ALTER TABLE intento_practica ADD CONSTRAINT ck_ip_calificacion_rango CHECK (calificacion IS NULL OR calificacion BETWEEN 0 AND 100);
ALTER TABLE version_pregunta ADD CONSTRAINT ck_vp_puntaje_no_negativo CHECK (puntaje >= 0);
ALTER TABLE curso ADD CONSTRAINT fk_curso_rol_docente FOREIGN KEY (id_docente) REFERENCES docente (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE banco_preguntas ADD CONSTRAINT fk_banco_rol_docente FOREIGN KEY (id_docente) REFERENCES docente (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE evaluacion ADD CONSTRAINT fk_evaluacion_rol_docente FOREIGN KEY (id_docente) REFERENCES docente (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE material_practica ADD CONSTRAINT fk_material_rol_docente FOREIGN KEY (id_docente) REFERENCES docente (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE solicitud_generacion ADD CONSTRAINT fk_solicitud_rol_docente FOREIGN KEY (id_docente) REFERENCES docente (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE inscripcion ADD CONSTRAINT fk_inscripcion_rol_estudiante FOREIGN KEY (id_estudiante) REFERENCES estudiante (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE intento_practica ADD CONSTRAINT fk_practica_rol_estudiante FOREIGN KEY (id_estudiante) REFERENCES estudiante (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- Triggers de una sentencia: compatibles con JDBC allowMultiQueries y cliente mysql (sin DELIMITER).
-- Las lecturas bloqueantes serializan la validacion con cambios del intento/puntaje.
CREATE TRIGGER trg_respuesta_contexto_bi BEFORE INSERT ON respuesta FOR EACH ROW
SET NEW.id_version_evaluacion = (
  SELECT i.id_version_evaluacion FROM intento i
  JOIN version_pregunta vp ON vp.id_version_evaluacion = i.id_version_evaluacion
  WHERE i.id_intento = NEW.id_intento AND vp.id_version_pregunta = NEW.id_version_pregunta
    AND (NEW.puntaje IS NULL OR NEW.puntaje BETWEEN 0 AND vp.puntaje) FOR SHARE
);
CREATE TRIGGER trg_rp_contexto_bi BEFORE INSERT ON respuesta_practica FOR EACH ROW
SET NEW.id_material_practica = (SELECT id_material_practica FROM intento_practica WHERE id_intento_practica = NEW.id_intento_practica FOR SHARE);
CREATE TRIGGER trg_respuesta_contexto_bu BEFORE UPDATE ON respuesta FOR EACH ROW
SET NEW.id_version_evaluacion = (
  SELECT i.id_version_evaluacion FROM intento i
  JOIN version_pregunta vp ON vp.id_version_evaluacion = i.id_version_evaluacion
  WHERE i.id_intento = NEW.id_intento AND vp.id_version_pregunta = NEW.id_version_pregunta
    AND (NEW.puntaje IS NULL OR NEW.puntaje BETWEEN 0 AND vp.puntaje) FOR SHARE
);
CREATE TRIGGER trg_rp_contexto_bu BEFORE UPDATE ON respuesta_practica FOR EACH ROW
SET NEW.id_material_practica = (SELECT id_material_practica FROM intento_practica WHERE id_intento_practica = NEW.id_intento_practica FOR SHARE);
-- Una version con respuestas conserva su maximo; no se invalida una calificacion historica.
CREATE TRIGGER trg_vp_puntaje_bu BEFORE UPDATE ON version_pregunta FOR EACH ROW
SET NEW.puntaje = IF(NEW.puntaje = OLD.puntaje OR NOT EXISTS (
  SELECT id_respuesta FROM respuesta WHERE id_version_pregunta = OLD.id_version_pregunta FOR SHARE
), NEW.puntaje, NULL);

CREATE TABLE token_api (
  id_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  id_usuario INT UNSIGNED NOT NULL,
  expira_en BIGINT NOT NULL,
  revocado BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id_token),
  KEY idx_token_api_usuario (id_usuario),
  KEY idx_token_api_expira (expira_en),
  CONSTRAINT fk_token_api_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE CASCADE,
  CONSTRAINT ck_token_api_expira CHECK (expira_en > 0),
  CONSTRAINT ck_token_api_revocado CHECK (revocado IN (0, 1))
) ENGINE=InnoDB;

-- Comprobar los disparadores con la cuenta de mantenimiento que los creo.
CREATE TEMPORARY TABLE auditoria_022_verificacion (
  numero INT NOT NULL CHECK (numero = 5)
);
INSERT INTO auditoria_022_verificacion
SELECT COUNT(*) FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME IN (
  'trg_respuesta_contexto_bi','trg_respuesta_contexto_bu',
  'trg_rp_contexto_bi','trg_rp_contexto_bu','trg_vp_puntaje_bu'
);
DROP TEMPORARY TABLE auditoria_022_verificacion;
