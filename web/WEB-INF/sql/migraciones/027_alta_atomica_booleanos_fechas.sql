-- Auditoria 6 (2026-11-19): SQL-001..SQL-003.
-- Requiere MySQL 8.4 con sql_mode estricto. Detener escrituras y respaldar antes de aplicar.
-- El preflight aborta antes del primer cambio persistente si hay datos invalidos.
CREATE TEMPORARY TABLE auditoria_027_preflight (
  inconsistencias BIGINT NOT NULL,
  CONSTRAINT ck_auditoria_027_sin_inconsistencias CHECK (inconsistencias = 0)
);
INSERT INTO auditoria_027_preflight
SELECT COUNT(*) FROM (
  -- SQL-001: ningun usuario sin rol (ni con mas de uno).
  SELECT u.id_usuario AS id FROM usuario u
  LEFT JOIN administrador a ON a.id_usuario = u.id_usuario
  LEFT JOIN docente d ON d.id_usuario = u.id_usuario
  LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario
  WHERE (a.id_usuario IS NOT NULL) + (d.id_usuario IS NOT NULL) + (e.id_usuario IS NOT NULL) <> 1
  UNION ALL
  -- SQL-002: indicadores logicos en {0, 1}.
  SELECT id_usuario FROM usuario
  WHERE activo NOT IN (0, 1) OR debe_cambiar_contrasena NOT IN (0, 1) OR debe_cambiar_contrasena_docente NOT IN (0, 1)
  UNION ALL
  SELECT id_usuario FROM docente WHERE debe_cambiar_contrasena NOT IN (0, 1)
  UNION ALL
  SELECT id_usuario FROM token_recuperacion WHERE usado NOT IN (0, 1)
  UNION ALL
  SELECT id_curso FROM curso WHERE activo NOT IN (0, 1)
  UNION ALL
  SELECT id_evaluacion_virtual FROM evaluacion_virtual
  WHERE supervision_activa NOT IN (0, 1) OR exigir_respuestas_completas NOT IN (0, 1)
  UNION ALL
  SELECT id_opcion_respuesta FROM opcion_respuesta WHERE es_correcta NOT IN (0, 1)
  UNION ALL
  SELECT id_respuesta_practica FROM respuesta_practica WHERE es_correcta NOT IN (0, 1)
  UNION ALL
  SELECT id_version_opcion FROM version_opcion WHERE es_correcta NOT IN (0, 1)
  UNION ALL
  -- SQL-003: un intento no termina antes de empezar.
  SELECT id_intento FROM intento WHERE fecha_fin < fecha_inicio
  UNION ALL
  SELECT id_intento_practica FROM intento_practica WHERE fecha_fin < fecha_inicio
) AS invalidos;
DROP TEMPORARY TABLE auditoria_027_preflight;

-- SQL-002: dominio {0, 1} de los indicadores logicos (token_api.revocado ya lo tenia en 022).
ALTER TABLE usuario
  ADD CONSTRAINT ck_usuario_activo_booleano CHECK (activo IN (0, 1)),
  ADD CONSTRAINT ck_usuario_debe_cambiar_booleano CHECK (debe_cambiar_contrasena IN (0, 1)),
  ADD CONSTRAINT ck_usuario_debe_cambiar_docente_booleano CHECK (debe_cambiar_contrasena_docente IN (0, 1));
ALTER TABLE docente
  ADD CONSTRAINT ck_docente_debe_cambiar_booleano CHECK (debe_cambiar_contrasena IN (0, 1));
ALTER TABLE token_recuperacion
  ADD CONSTRAINT ck_token_recuperacion_usado_booleano CHECK (usado IN (0, 1));
ALTER TABLE curso
  ADD CONSTRAINT ck_curso_activo_booleano CHECK (activo IN (0, 1));
ALTER TABLE evaluacion_virtual
  ADD CONSTRAINT ck_ev_supervision_booleano CHECK (supervision_activa IN (0, 1)),
  ADD CONSTRAINT ck_ev_respuestas_completas_booleano CHECK (exigir_respuestas_completas IN (0, 1));
ALTER TABLE opcion_respuesta
  ADD CONSTRAINT ck_opcion_respuesta_correcta_booleano CHECK (es_correcta IN (0, 1));
ALTER TABLE respuesta_practica
  ADD CONSTRAINT ck_respuesta_practica_correcta_booleano CHECK (es_correcta IS NULL OR es_correcta IN (0, 1));
ALTER TABLE version_opcion
  ADD CONSTRAINT ck_version_opcion_correcta_booleano CHECK (es_correcta IN (0, 1));

-- SQL-003: cronologia de intentos.
ALTER TABLE intento
  ADD CONSTRAINT ck_intento_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio);
ALTER TABLE intento_practica
  ADD CONSTRAINT ck_intento_practica_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio);

-- SQL-001: alta atomica de usuario y rol. El INSERT en usuario declara el rol en variables de
-- sesion (@examlen_alta_*) y los triggers AFTER INSERT crean la fila de rol dentro de la MISMA
-- sentencia: si falta el rol, o sus datos son invalidos, se deshace el usuario completo. No puede
-- existir un usuario sin rol, ni siquiera inactivo. Las altas de los DAO fijan y limpian estas
-- variables; un INSERT directo que no las declare se rechaza.
-- Sustituye el mecanismo de 026 (alta inactiva + activacion desde la tabla de rol): un trigger
-- de la tabla de rol no puede modificar usuario durante el INSERT que lo origina.
DROP TRIGGER trg_administrador_rol_ai;
DROP TRIGGER trg_docente_rol_ai;
DROP TRIGGER trg_estudiante_rol_ai;
DROP TRIGGER trg_usuario_alta_bi;
CREATE TRIGGER trg_usuario_alta_bi BEFORE INSERT ON usuario FOR EACH ROW
SET NEW.activo = IF(@examlen_alta_rol IN ('ADMINISTRADOR', 'DOCENTE', 'ESTUDIANTE'), NEW.activo, NULL);
CREATE TRIGGER trg_usuario_alta_administrador_ai AFTER INSERT ON usuario FOR EACH ROW
INSERT INTO administrador (id_usuario, fecha_nacimiento)
SELECT NEW.id_usuario, @examlen_alta_fecha_nacimiento FROM DUAL WHERE @examlen_alta_rol = 'ADMINISTRADOR';
CREATE TRIGGER trg_usuario_alta_docente_ai AFTER INSERT ON usuario FOR EACH ROW
INSERT INTO docente (id_usuario, debe_cambiar_contrasena, id_administrador)
SELECT NEW.id_usuario, @examlen_alta_debe_cambiar, @examlen_alta_id_administrador FROM DUAL WHERE @examlen_alta_rol = 'DOCENTE';
CREATE TRIGGER trg_usuario_alta_estudiante_ai AFTER INSERT ON usuario FOR EACH ROW
INSERT INTO estudiante (id_usuario, nivel_ingles, aceptacion_tratamiento_datos)
SELECT NEW.id_usuario, @examlen_alta_nivel, @examlen_alta_aceptacion FROM DUAL WHERE @examlen_alta_rol = 'ESTUDIANTE';

-- Verificacion final: 13 CHECK y 4 triggers de esta migracion.
CREATE TEMPORARY TABLE auditoria_027_verificacion (
  numero INT NOT NULL CHECK (numero = 17)
);
INSERT INTO auditoria_027_verificacion
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK' AND ENFORCED = 'YES'
     AND CONSTRAINT_NAME IN (
       'ck_usuario_activo_booleano','ck_usuario_debe_cambiar_booleano','ck_usuario_debe_cambiar_docente_booleano',
       'ck_docente_debe_cambiar_booleano','ck_token_recuperacion_usado_booleano','ck_curso_activo_booleano',
       'ck_ev_supervision_booleano','ck_ev_respuestas_completas_booleano','ck_opcion_respuesta_correcta_booleano',
       'ck_respuesta_practica_correcta_booleano','ck_version_opcion_correcta_booleano',
       'ck_intento_fechas','ck_intento_practica_fechas'))
  + (SELECT COUNT(*) FROM information_schema.TRIGGERS
     WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME IN (
       'trg_usuario_alta_bi','trg_usuario_alta_administrador_ai','trg_usuario_alta_docente_ai',
       'trg_usuario_alta_estudiante_ai'));
DROP TEMPORARY TABLE auditoria_027_verificacion;
