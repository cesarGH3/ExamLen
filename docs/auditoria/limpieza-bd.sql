-- ============================================================================
-- ExamLen - Limpieza de la base de datos (Fase 3 de la auditoria, 2026-10-15)
--
-- OBJETIVO: eliminar unicamente DATOS DE PRUEBA/DESARROLLO y conservar
-- el esquema completo y el administrador funcional (admin@examlen.local, id 1).
--
-- SALVAGUARDAS APLICADAS:
--   * Ejecutar con Tomcat DETENIDO (no hay escrituras de la aplicacion).
--   * Respaldo completo previo: %TEMP%\opencode\backup-examlen-pre-limpieza.sql
--   * Conteos del inventario revalidados inmediatamente antes de este script.
--   * UNA sola transaccion; ante cualquier error, el cliente mysql aborta y la
--     conexion se cierra sin COMMIT (rollback implicito).
--   * Sin SET FOREIGN_KEY_CHECKS=0, sin TRUNCATE, sin DROP, sin cambios de
--     estructura. Se conservan: 29 tablas, 47 FK, indices y restricciones.
--   * Identificadores CONCRETOS verificados en el inventario:
--       - usuarios demo (19): 2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21
--       - documento_fuente huérfanos (2): 1,2
--       - solicitud_generacion demo (5): 2,3,4,5,6
--   * esquema_migracion NO se toca (17 registros de control tecnico).
--   * administrador (usuario id 1 + fila en administrador) NO se toca.
-- ============================================================================

START TRANSACTION;

-- 1. Hijas sin datos actuales (por completitud y orden FK; ancladas a ids concretos)
DELETE es FROM evento_supervision es
  JOIN intento i ON i.id_intento = es.id_intento
  JOIN evaluacion_virtual v ON v.id_evaluacion_virtual = i.id_evaluacion_virtual
  JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE rp FROM respuesta_practica rp
  JOIN intento_practica ip ON ip.id_intento_practica = rp.id_intento_practica
  JOIN material_practica m ON m.id_material_practica = ip.id_material_practica
  WHERE m.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE ip FROM intento_practica ip
  JOIN material_practica m ON m.id_material_practica = ip.id_material_practica
  WHERE m.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE r FROM respuesta r
  JOIN intento i ON i.id_intento = r.id_intento
  JOIN evaluacion_virtual v ON v.id_evaluacion_virtual = i.id_evaluacion_virtual
  JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE i FROM intento i
  JOIN evaluacion_virtual v ON v.id_evaluacion_virtual = i.id_evaluacion_virtual
  JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE evv FROM evaluacion_virtual_version evv
  JOIN evaluacion_virtual v ON v.id_evaluacion_virtual = evv.id_evaluacion_virtual
  JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE v FROM evaluacion_virtual v
  JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE ins FROM inscripcion ins
  JOIN grupo g ON g.id_grupo = ins.id_grupo
  JOIN curso c ON c.id_curso = g.id_curso
  WHERE c.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE FROM token_recuperacion
  WHERE id_usuario IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

-- 2. Datos demo directos anclados a identificadores concretos
DELETE FROM solicitud_generacion WHERE id_solicitud_generacion IN (2,3,4,5,6);
DELETE FROM documento_fuente WHERE id_documento_fuente IN (1,2);

-- 3. Material de practica
DELETE mp FROM material_pregunta mp
  JOIN material_practica m ON m.id_material_practica = mp.id_material_practica
  WHERE m.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE cm FROM curso_material cm
  JOIN material_practica m ON m.id_material_practica = cm.id_material_practica
  WHERE m.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE FROM material_practica
  WHERE id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

-- 4. Evaluaciones y versiones
DELETE vo FROM version_opcion vo
  JOIN version_pregunta vp ON vp.id_version_pregunta = vo.id_version_pregunta
  JOIN version_evaluacion ve ON ve.id_version_evaluacion = vp.id_version_evaluacion
  JOIN evaluacion e ON e.id_evaluacion = ve.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE vp FROM version_pregunta vp
  JOIN version_evaluacion ve ON ve.id_version_evaluacion = vp.id_version_evaluacion
  JOIN evaluacion e ON e.id_evaluacion = ve.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE ve FROM version_evaluacion ve
  JOIN evaluacion e ON e.id_evaluacion = ve.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE ep FROM evaluacion_pregunta ep
  JOIN evaluacion e ON e.id_evaluacion = ep.id_evaluacion
  WHERE e.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE FROM evaluacion
  WHERE id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

-- 5. Bancos y preguntas
DELETE o FROM opcion_respuesta o
  JOIN pregunta p ON p.id_pregunta = o.id_pregunta
  JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas
  WHERE b.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE p FROM pregunta p
  JOIN banco_preguntas b ON b.id_banco_preguntas = p.id_banco_preguntas
  WHERE b.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE FROM banco_preguntas
  WHERE id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

-- 6. Grupos y cursos
DELETE g FROM grupo g
  JOIN curso c ON c.id_curso = g.id_curso
  WHERE c.id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

DELETE FROM curso
  WHERE id_docente IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

-- 7. Usuarios demo (las filas docente/estudiante caen por FK CASCADE;
--    NO se incluye el id 1 = administrador funcional)
DELETE FROM usuario
  WHERE id_usuario IN (2,3,4,5,6,8,9,10,11,12,13,14,15,16,17,18,19,20,21);

COMMIT;

-- ============================================================================
-- Verificacion posterior (todo debe coincidir con el estado esperado)
-- ============================================================================
SELECT 'usuario' t, COUNT(*) n FROM usuario
UNION ALL SELECT 'administrador', COUNT(*) FROM administrador
UNION ALL SELECT 'docente', COUNT(*) FROM docente
UNION ALL SELECT 'estudiante', COUNT(*) FROM estudiante
UNION ALL SELECT 'curso', COUNT(*) FROM curso
UNION ALL SELECT 'grupo', COUNT(*) FROM grupo
UNION ALL SELECT 'banco_preguntas', COUNT(*) FROM banco_preguntas
UNION ALL SELECT 'pregunta', COUNT(*) FROM pregunta
UNION ALL SELECT 'opcion_respuesta', COUNT(*) FROM opcion_respuesta
UNION ALL SELECT 'evaluacion', COUNT(*) FROM evaluacion
UNION ALL SELECT 'evaluacion_pregunta', COUNT(*) FROM evaluacion_pregunta
UNION ALL SELECT 'version_evaluacion', COUNT(*) FROM version_evaluacion
UNION ALL SELECT 'version_pregunta', COUNT(*) FROM version_pregunta
UNION ALL SELECT 'version_opcion', COUNT(*) FROM version_opcion
UNION ALL SELECT 'material_practica', COUNT(*) FROM material_practica
UNION ALL SELECT 'material_pregunta', COUNT(*) FROM material_pregunta
UNION ALL SELECT 'curso_material', COUNT(*) FROM curso_material
UNION ALL SELECT 'solicitud_generacion', COUNT(*) FROM solicitud_generacion
UNION ALL SELECT 'documento_fuente', COUNT(*) FROM documento_fuente
UNION ALL SELECT 'esquema_migracion', COUNT(*) FROM esquema_migracion;

SELECT identificacion, correo, activo FROM usuario;

SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='examlen') tablas,
       (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE constraint_schema='examlen') fk;
