-- Auditoria 5 (2026-11-19): SQL-001..SQL-003.
-- Requiere MySQL 8.4 con sql_mode estricto. Detener escrituras y respaldar antes de aplicar.
-- El inicializador ejecuta este script paso a paso con bitacora y lo reanuda tras una
-- interrupcion (esquema_migracion_paso).

-- SQL-001: id_evaluacion_referencia pasa a ser una clave foranea real hacia evaluacion.
-- Normalizacion previa: una referencia a una evaluacion inexistente se deja en NULL, que es
-- exactamente lo que ON DELETE SET NULL habria hecho si la FK hubiera existido. Es idempotente.
UPDATE solicitud_generacion s
LEFT JOIN evaluacion e ON e.id_evaluacion = s.id_evaluacion_referencia
SET s.id_evaluacion_referencia = NULL
WHERE s.id_evaluacion_referencia IS NOT NULL AND e.id_evaluacion IS NULL;
-- Mismo tipo que evaluacion.id_evaluacion (INT UNSIGNED), requisito de la FK. El CHECK de 025
-- (> 0) se retira: MySQL no admite CHECK sobre una columna con accion ON DELETE SET NULL y la FK
-- ya lo implica (todo valor no nulo debe existir en evaluacion, cuyos ids empiezan en 1).
ALTER TABLE solicitud_generacion
  DROP CHECK ck_solicitud_evaluacion_referencia,
  MODIFY id_evaluacion_referencia INT UNSIGNED NULL,
  ADD KEY idx_solicitud_evaluacion_referencia (id_evaluacion_referencia),
  ADD CONSTRAINT fk_solicitud_evaluacion_referencia FOREIGN KEY (id_evaluacion_referencia)
    REFERENCES evaluacion (id_evaluacion) ON DELETE SET NULL ON UPDATE RESTRICT;

-- SQL-003: posicion unica de cada pregunta en su version y de cada opcion en su pregunta.
-- Normalizacion previa: se renumera 1..n por (orden, id). Conserva el orden visible (las
-- consultas ordenan por orden y los empates se resolvian por id) y es idempotente.
UPDATE version_pregunta vp
JOIN (SELECT id_version_pregunta AS id,
             ROW_NUMBER() OVER (PARTITION BY id_version_evaluacion ORDER BY orden, id_version_pregunta) AS n
      FROM version_pregunta) x ON x.id = vp.id_version_pregunta
SET vp.orden = x.n
WHERE vp.orden <> x.n;
UPDATE version_opcion vo
JOIN (SELECT id_version_opcion AS id,
             ROW_NUMBER() OVER (PARTITION BY id_version_pregunta ORDER BY orden, id_version_opcion) AS n
      FROM version_opcion) x ON x.id = vo.id_version_opcion
SET vo.orden = x.n
WHERE vo.orden <> x.n;
ALTER TABLE version_pregunta ADD UNIQUE KEY uk_version_pregunta_orden (id_version_evaluacion, orden);
ALTER TABLE version_opcion ADD UNIQUE KEY uk_version_opcion_orden (id_version_pregunta, orden);
-- evaluacion_pregunta.orden_evaluacion_pregunta NO es unico por diseno: es la posicion del
-- grupo de equivalencia (nivel, competencia, dificultad) dentro de la evaluacion, y todas las
-- preguntas de un mismo grupo comparten ese valor (PreguntaDAO.asignarOrdenGrupoEnEvaluacion).
-- La posicion de cada pregunta impresa vive en version_pregunta, que si es unica.

-- SQL-002: un usuario nace inactivo y solo se activa al recibir su fila de rol. Un INSERT
-- directo en usuario sin rol deja una cuenta inactiva (no inicia sesion) y, por
-- trg_usuario_rol_bu, inmodificable hasta que se le asigne un rol.
CREATE TRIGGER trg_usuario_alta_bi BEFORE INSERT ON usuario FOR EACH ROW
SET NEW.activo = FALSE;
CREATE TRIGGER trg_administrador_rol_ai AFTER INSERT ON administrador FOR EACH ROW
UPDATE usuario SET activo = TRUE WHERE id_usuario = NEW.id_usuario;
CREATE TRIGGER trg_docente_rol_ai AFTER INSERT ON docente FOR EACH ROW
UPDATE usuario SET activo = TRUE WHERE id_usuario = NEW.id_usuario;
CREATE TRIGGER trg_estudiante_rol_ai AFTER INSERT ON estudiante FOR EACH ROW
UPDATE usuario SET activo = TRUE WHERE id_usuario = NEW.id_usuario;

-- Verificacion final: 1 FK, 2 UNIQUE y 4 triggers de esta migracion.
CREATE TEMPORARY TABLE auditoria_026_verificacion (
  numero INT NOT NULL CHECK (numero = 7)
);
INSERT INTO auditoria_026_verificacion
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN (
     'fk_solicitud_evaluacion_referencia','uk_version_pregunta_orden','uk_version_opcion_orden'))
  + (SELECT COUNT(*) FROM information_schema.TRIGGERS
     WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME IN (
       'trg_usuario_alta_bi','trg_administrador_rol_ai','trg_docente_rol_ai','trg_estudiante_rol_ai'));
DROP TEMPORARY TABLE auditoria_026_verificacion;
