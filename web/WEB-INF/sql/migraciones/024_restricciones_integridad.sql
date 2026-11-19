-- Auditoria 3 (2026-11-19): SQL-001..SQL-007.
-- Lleva al historial de migraciones las restricciones que la entrega 2 declaraba solo
-- en el script SQL, y agrega la exclusividad de roles. Requiere MySQL 8.4 con
-- sql_mode estricto (STRICT_TRANS_TABLES, valor por defecto).
-- Detener escrituras y respaldar antes de aplicar: DDL hace commit implicito.
-- El preflight aborta antes del primer cambio persistente si hay datos invalidos.
-- No reejecutar una migracion parcial; restaurar o reparar explicitamente.
CREATE TEMPORARY TABLE auditoria_024_preflight (
  inconsistencias BIGINT NOT NULL,
  CONSTRAINT ck_auditoria_024_sin_inconsistencias CHECK (inconsistencias = 0)
);
INSERT INTO auditoria_024_preflight
SELECT COUNT(*) FROM (
  -- SQL-001: intento coherente con inscripcion, grupo, virtual y version.
  SELECT i.id_intento AS id FROM intento i
  JOIN inscripcion n ON n.id_inscripcion = i.id_inscripcion
  JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = i.id_evaluacion_virtual
  JOIN version_evaluacion v ON v.id_version_evaluacion = i.id_version_evaluacion
  LEFT JOIN evaluacion_virtual_version evv
    ON evv.id_evaluacion_virtual = i.id_evaluacion_virtual
   AND evv.id_version_evaluacion = i.id_version_evaluacion
  WHERE n.id_grupo <> ev.id_grupo OR v.id_evaluacion <> ev.id_evaluacion
     OR evv.id_evaluacion_virtual IS NULL
  UNION ALL
  SELECT evv.id_evaluacion_virtual FROM evaluacion_virtual_version evv
  JOIN evaluacion_virtual ev ON ev.id_evaluacion_virtual = evv.id_evaluacion_virtual
  JOIN version_evaluacion v ON v.id_version_evaluacion = evv.id_version_evaluacion
  WHERE v.id_evaluacion <> ev.id_evaluacion
  UNION ALL
  -- SQL-002: una respuesta por intento y pregunta.
  SELECT MIN(id_respuesta) FROM respuesta GROUP BY id_intento, id_version_pregunta HAVING COUNT(*) > 1
  UNION ALL
  -- SQL-003 y SQL-005: fechas y puntajes.
  SELECT id_evaluacion_virtual FROM evaluacion_virtual WHERE fecha_fin <= fecha_inicio
  UNION ALL
  SELECT id_evaluacion FROM evaluacion_pregunta WHERE puntaje_evaluacion_pregunta < 0
  UNION ALL
  SELECT id_respuesta FROM respuesta WHERE puntaje < 0
  UNION ALL
  -- SQL-004: claves de IA de un docente existente.
  SELECT k.id_docente FROM clave_ia_docente k LEFT JOIN docente d ON d.id_usuario = k.id_docente
  WHERE d.id_usuario IS NULL
  UNION ALL
  -- SQL-007: ningun usuario en mas de una tabla de rol.
  SELECT u.id_usuario FROM usuario u
  LEFT JOIN administrador a ON a.id_usuario = u.id_usuario
  LEFT JOIN docente d ON d.id_usuario = u.id_usuario
  LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario
  WHERE (a.id_usuario IS NOT NULL) + (d.id_usuario IS NOT NULL) + (e.id_usuario IS NOT NULL) > 1
) AS invalidos;
DROP TEMPORARY TABLE auditoria_024_preflight;

-- SQL-001: el par (virtual, version) del intento debe estar asociado en evaluacion_virtual_version.
-- ON UPDATE/DELETE RESTRICT impide quitar o cambiar una asociacion usada por un intento.
ALTER TABLE intento
  ADD CONSTRAINT fk_intento_virtual_version
    FOREIGN KEY (id_evaluacion_virtual, id_version_evaluacion)
    REFERENCES evaluacion_virtual_version (id_evaluacion_virtual, id_version_evaluacion)
    ON DELETE RESTRICT ON UPDATE RESTRICT;

-- SQL-002 y SQL-005: unicidad y puntaje no negativo de respuestas.
ALTER TABLE respuesta
  ADD UNIQUE KEY uk_respuesta_intento_pregunta (id_intento, id_version_pregunta),
  ADD CONSTRAINT ck_respuesta_puntaje CHECK (puntaje IS NULL OR puntaje >= 0);

-- SQL-003: la ventana de la evaluacion virtual debe ser valida.
ALTER TABLE evaluacion_virtual
  ADD CONSTRAINT ck_evaluacion_virtual_fechas CHECK (fecha_fin > fecha_inicio);

-- SQL-005: puntaje de la pregunta dentro de la evaluacion.
ALTER TABLE evaluacion_pregunta
  ADD CONSTRAINT ck_evaluacion_pregunta_puntaje CHECK (puntaje_evaluacion_pregunta >= 0);

-- SQL-004: la clave cifrada pertenece a un docente; se elimina con el docente.
ALTER TABLE clave_ia_docente
  ADD CONSTRAINT fk_clave_ia_docente FOREIGN KEY (id_docente)
    REFERENCES docente (id_usuario) ON DELETE CASCADE ON UPDATE RESTRICT;

-- Triggers de una sentencia (sin DELIMITER), igual que en 022. Un dato incoherente
-- produce NULL en una columna NOT NULL y MySQL rechaza la fila (error 1048) en modo
-- estricto. Las lecturas FOR SHARE serializan la validacion con cambios concurrentes.

-- SQL-001: coherencia de intentos (inscripcion del grupo de la virtual, version de su evaluacion).
CREATE TRIGGER trg_intento_bi BEFORE INSERT ON intento FOR EACH ROW
SET NEW.id_inscripcion = (
  SELECT n.id_inscripcion FROM inscripcion n
  JOIN evaluacion_virtual ev ON ev.id_grupo = n.id_grupo
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE n.id_inscripcion = NEW.id_inscripcion
    AND ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
);
CREATE TRIGGER trg_intento_bu BEFORE UPDATE ON intento FOR EACH ROW
SET NEW.id_inscripcion = (
  SELECT n.id_inscripcion FROM inscripcion n
  JOIN evaluacion_virtual ev ON ev.id_grupo = n.id_grupo
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE n.id_inscripcion = NEW.id_inscripcion
    AND ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
);
-- Una inscripcion con intentos no cambia de grupo.
CREATE TRIGGER trg_inscripcion_bu BEFORE UPDATE ON inscripcion FOR EACH ROW
SET NEW.id_grupo = IF(NEW.id_grupo = OLD.id_grupo OR NOT EXISTS (
  SELECT id_intento FROM intento WHERE id_inscripcion = OLD.id_inscripcion FOR SHARE
), NEW.id_grupo, NULL);
-- Una virtual con intentos no cambia de grupo; con intentos o versiones no cambia de evaluacion.
CREATE TRIGGER trg_evaluacion_virtual_bu BEFORE UPDATE ON evaluacion_virtual FOR EACH ROW
SET NEW.id_grupo = IF(NEW.id_grupo = OLD.id_grupo OR NOT EXISTS (
      SELECT id_intento FROM intento WHERE id_evaluacion_virtual = OLD.id_evaluacion_virtual FOR SHARE
    ), NEW.id_grupo, NULL),
    NEW.id_evaluacion = IF(NEW.id_evaluacion = OLD.id_evaluacion OR NOT EXISTS (
      SELECT id_version_evaluacion FROM evaluacion_virtual_version
      WHERE id_evaluacion_virtual = OLD.id_evaluacion_virtual FOR SHARE
    ), NEW.id_evaluacion, NULL);
-- Una version asociada a una virtual no cambia de evaluacion.
CREATE TRIGGER trg_version_evaluacion_bu BEFORE UPDATE ON version_evaluacion FOR EACH ROW
SET NEW.id_evaluacion = IF(NEW.id_evaluacion = OLD.id_evaluacion OR NOT EXISTS (
  SELECT id_evaluacion_virtual FROM evaluacion_virtual_version
  WHERE id_version_evaluacion = OLD.id_version_evaluacion FOR SHARE
), NEW.id_evaluacion, NULL);
-- Solo se asocian a una virtual versiones de su misma evaluacion.
CREATE TRIGGER trg_evaluacion_virtual_version_bi BEFORE INSERT ON evaluacion_virtual_version FOR EACH ROW
SET NEW.id_version_evaluacion = (
  SELECT v.id_version_evaluacion FROM evaluacion_virtual ev
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
);
CREATE TRIGGER trg_evaluacion_virtual_version_bu BEFORE UPDATE ON evaluacion_virtual_version FOR EACH ROW
SET NEW.id_version_evaluacion = (
  SELECT v.id_version_evaluacion FROM evaluacion_virtual ev
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
);

-- SQL-007: un usuario pertenece a una sola tabla de rol (administrador, docente o estudiante).
CREATE TRIGGER trg_administrador_rol_bi BEFORE INSERT ON administrador FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = NEW.id_usuario FOR SHARE),
  NULL, NEW.id_usuario);
CREATE TRIGGER trg_administrador_rol_bu BEFORE UPDATE ON administrador FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario <> OLD.id_usuario AND (
  EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = NEW.id_usuario FOR SHARE)),
  NULL, NEW.id_usuario);
CREATE TRIGGER trg_docente_rol_bi BEFORE INSERT ON docente FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = NEW.id_usuario FOR SHARE),
  NULL, NEW.id_usuario);
CREATE TRIGGER trg_docente_rol_bu BEFORE UPDATE ON docente FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario <> OLD.id_usuario AND (
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = NEW.id_usuario FOR SHARE)),
  NULL, NEW.id_usuario);
CREATE TRIGGER trg_estudiante_rol_bi BEFORE INSERT ON estudiante FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = NEW.id_usuario FOR SHARE),
  NULL, NEW.id_usuario);
CREATE TRIGGER trg_estudiante_rol_bu BEFORE UPDATE ON estudiante FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario <> OLD.id_usuario AND (
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = NEW.id_usuario FOR SHARE)),
  NULL, NEW.id_usuario);

-- Verificacion final: 13 triggers y 6 restricciones de esta migracion presentes.
CREATE TEMPORARY TABLE auditoria_024_verificacion (
  numero INT NOT NULL CHECK (numero = 19)
);
INSERT INTO auditoria_024_verificacion
SELECT
  (SELECT COUNT(*) FROM information_schema.TRIGGERS
   WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME IN (
     'trg_intento_bi','trg_intento_bu','trg_inscripcion_bu','trg_evaluacion_virtual_bu',
     'trg_version_evaluacion_bu','trg_evaluacion_virtual_version_bi','trg_evaluacion_virtual_version_bu',
     'trg_administrador_rol_bi','trg_administrador_rol_bu','trg_docente_rol_bi','trg_docente_rol_bu',
     'trg_estudiante_rol_bi','trg_estudiante_rol_bu'))
  + (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN (
       'fk_intento_virtual_version','uk_respuesta_intento_pregunta','ck_respuesta_puntaje',
       'ck_evaluacion_virtual_fechas','ck_evaluacion_pregunta_puntaje','fk_clave_ia_docente'));
DROP TEMPORARY TABLE auditoria_024_verificacion;
