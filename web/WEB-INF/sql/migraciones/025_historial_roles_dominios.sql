-- Auditoria 4 (2026-11-19): SQL-001..SQL-003 y JAVA-001 (defensa en la base).
-- Requiere MySQL 8.4 con sql_mode estricto (STRICT_TRANS_TABLES, valor por defecto).
-- Detener escrituras y respaldar antes de aplicar. El inicializador ejecuta este script
-- paso a paso con bitacora (esquema_migracion_paso) y lo reanuda tras una interrupcion.
-- El preflight aborta antes del primer cambio persistente si hay datos invalidos.
CREATE TEMPORARY TABLE auditoria_025_preflight (
  inconsistencias BIGINT NOT NULL,
  CONSTRAINT ck_auditoria_025_sin_inconsistencias CHECK (inconsistencias = 0)
);
INSERT INTO auditoria_025_preflight
SELECT COUNT(*) FROM (
  SELECT id_evaluacion AS id FROM evaluacion
  WHERE numero_preguntas < 0 OR numero_versiones < 0 OR duracion_minutos < 0
  UNION ALL
  SELECT id_solicitud_generacion FROM solicitud_generacion
  WHERE cantidad < 0 OR cantidad_recibida < 0 OR cantidad_valida < 0
     OR cantidad_invalida < 0 OR cantidad_no_devuelta < 0 OR id_evaluacion_referencia <= 0
  UNION ALL
  SELECT id_evaluacion FROM evaluacion_pregunta WHERE orden_evaluacion_pregunta < 0
  UNION ALL
  SELECT id_version_pregunta FROM version_pregunta WHERE orden < 0
  UNION ALL
  SELECT id_version_opcion FROM version_opcion WHERE orden < 0
  UNION ALL
  -- SQL-002: todo usuario existente debe tener exactamente un rol.
  SELECT u.id_usuario FROM usuario u
  LEFT JOIN administrador a ON a.id_usuario = u.id_usuario
  LEFT JOIN docente d ON d.id_usuario = u.id_usuario
  LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario
  WHERE (a.id_usuario IS NOT NULL) + (d.id_usuario IS NOT NULL) + (e.id_usuario IS NOT NULL) <> 1
) AS invalidos;
DROP TEMPORARY TABLE auditoria_025_preflight;

-- SQL-003: dominios de cantidades, duraciones y ordenes.
ALTER TABLE evaluacion
  ADD CONSTRAINT ck_evaluacion_numero_preguntas CHECK (numero_preguntas >= 0),
  ADD CONSTRAINT ck_evaluacion_numero_versiones CHECK (numero_versiones >= 0),
  ADD CONSTRAINT ck_evaluacion_duracion CHECK (duracion_minutos >= 0);
ALTER TABLE solicitud_generacion
  ADD CONSTRAINT ck_solicitud_cantidad CHECK (cantidad >= 0),
  ADD CONSTRAINT ck_solicitud_cantidades_resultado CHECK (
    (cantidad_recibida IS NULL OR cantidad_recibida >= 0)
    AND (cantidad_valida IS NULL OR cantidad_valida >= 0)
    AND (cantidad_invalida IS NULL OR cantidad_invalida >= 0)
    AND (cantidad_no_devuelta IS NULL OR cantidad_no_devuelta >= 0)),
  ADD CONSTRAINT ck_solicitud_evaluacion_referencia CHECK (id_evaluacion_referencia IS NULL OR id_evaluacion_referencia > 0);
ALTER TABLE evaluacion_pregunta
  ADD CONSTRAINT ck_evaluacion_pregunta_orden CHECK (orden_evaluacion_pregunta >= 0);
ALTER TABLE version_pregunta
  ADD CONSTRAINT ck_version_pregunta_orden CHECK (orden >= 0);
ALTER TABLE version_opcion
  ADD CONSTRAINT ck_version_opcion_orden CHECK (orden >= 0);

-- SQL-001: una inscripcion con intentos conserva grupo y estudiante (identidad historica).
DROP TRIGGER trg_inscripcion_bu;
CREATE TRIGGER trg_inscripcion_bu BEFORE UPDATE ON inscripcion FOR EACH ROW
SET NEW.id_grupo = IF((NEW.id_grupo = OLD.id_grupo AND NEW.id_estudiante = OLD.id_estudiante) OR NOT EXISTS (
      SELECT id_intento FROM intento WHERE id_inscripcion = OLD.id_inscripcion FOR SHARE
    ), NEW.id_grupo, NULL);

-- JAVA-001 en la base: solo se inscribe en grupos ABIERTO; la lectura FOR SHARE del grupo
-- se serializa con el UPDATE que lo cierra.
CREATE TRIGGER trg_inscripcion_bi BEFORE INSERT ON inscripcion FOR EACH ROW
SET NEW.id_grupo = (SELECT id_grupo FROM grupo WHERE id_grupo = NEW.id_grupo AND estado = 'ABIERTO' FOR SHARE);

-- SQL-002: el rol de un usuario existente no se puede quitar ni mover.
-- Las acciones en cascada de las FK no disparan triggers en MySQL, de modo que borrar el
-- usuario sigue eliminando su fila de rol; lo que se rechaza es borrar solo la fila de rol.
CREATE TRIGGER trg_administrador_rol_bd BEFORE DELETE ON administrador FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El rol no se borra por separado: elimine el usuario.';
CREATE TRIGGER trg_docente_rol_bd BEFORE DELETE ON docente FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El rol no se borra por separado: elimine el usuario.';
CREATE TRIGGER trg_estudiante_rol_bd BEFORE DELETE ON estudiante FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El rol no se borra por separado: elimine el usuario.';
DROP TRIGGER trg_administrador_rol_bu;
CREATE TRIGGER trg_administrador_rol_bu BEFORE UPDATE ON administrador FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario = OLD.id_usuario, NEW.id_usuario, NULL);
DROP TRIGGER trg_docente_rol_bu;
CREATE TRIGGER trg_docente_rol_bu BEFORE UPDATE ON docente FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario = OLD.id_usuario, NEW.id_usuario, NULL);
DROP TRIGGER trg_estudiante_rol_bu;
CREATE TRIGGER trg_estudiante_rol_bu BEFORE UPDATE ON estudiante FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario = OLD.id_usuario, NEW.id_usuario, NULL);
-- Un usuario sin rol (solo posible si se inserta fuera de la transaccion de los DAO) queda
-- inutilizable: no se puede activar, cambiar credenciales ni datos hasta asignarle un rol.
CREATE TRIGGER trg_usuario_rol_bu BEFORE UPDATE ON usuario FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = OLD.id_usuario)
  OR EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = OLD.id_usuario)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = OLD.id_usuario),
  NEW.id_usuario, NULL);

-- Verificacion final: 9 CHECK y 9 triggers de esta migracion.
CREATE TEMPORARY TABLE auditoria_025_verificacion (
  numero INT NOT NULL CHECK (numero = 18)
);
INSERT INTO auditoria_025_verificacion
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK' AND ENFORCED = 'YES'
     AND CONSTRAINT_NAME IN (
       'ck_evaluacion_numero_preguntas','ck_evaluacion_numero_versiones','ck_evaluacion_duracion',
       'ck_solicitud_cantidad','ck_solicitud_cantidades_resultado','ck_solicitud_evaluacion_referencia',
       'ck_evaluacion_pregunta_orden','ck_version_pregunta_orden','ck_version_opcion_orden'))
  + (SELECT COUNT(*) FROM information_schema.TRIGGERS
     WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME IN (
       'trg_inscripcion_bu','trg_inscripcion_bi','trg_administrador_rol_bd','trg_docente_rol_bd',
       'trg_estudiante_rol_bd','trg_administrador_rol_bu','trg_docente_rol_bu','trg_estudiante_rol_bu',
       'trg_usuario_rol_bu'));
DROP TEMPORARY TABLE auditoria_025_verificacion;
