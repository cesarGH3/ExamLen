-- =====================================================================
-- ExamLen - Migración 006: nombres de BD según el diccionario del ERS
-- Revierte el patrón <atributo>_<clase> de la migración 003.
-- Lista definitiva: docs/TRAZABILIDAD.md secciones 4.2 y 4.3.
-- Ejecutar como root DESPUÉS de 005. No pierde datos (RENAME conserva todo).
-- =====================================================================

USE examlen;

-- ---------------------------------------------------------------------
-- 4.2 TABLAS QUE SE RENOMBRAN
-- ---------------------------------------------------------------------
RENAME TABLE curso_material_practica TO curso_material;
RENAME TABLE material_practica_pregunta TO material_pregunta;

-- ---------------------------------------------------------------------
-- 4.3 COLUMNAS QUE SE RENOMBRAN
-- ---------------------------------------------------------------------
ALTER TABLE usuario
  RENAME COLUMN identificacion_usuario TO identificacion,
  RENAME COLUMN nombre_usuario TO nombre,
  RENAME COLUMN correo_usuario TO correo,
  RENAME COLUMN institucion_usuario TO institucion,
  RENAME COLUMN contrasena_hash_usuario TO contrasena_hash,
  RENAME COLUMN activo_usuario TO activo;

ALTER TABLE curso
  RENAME COLUMN nombre_curso TO nombre,
  RENAME COLUMN descripcion_curso TO descripcion,
  RENAME COLUMN nivel_curso TO nivel,
  RENAME COLUMN activo_curso TO activo;

ALTER TABLE grupo
  RENAME COLUMN nombre_grupo TO nombre,
  RENAME COLUMN periodo_grupo TO periodo,
  RENAME COLUMN codigo_acceso_grupo TO codigo_acceso,
  RENAME COLUMN estado_grupo TO estado;

ALTER TABLE inscripcion
  RENAME COLUMN fecha_inscripcion TO fecha,
  RENAME COLUMN estado_inscripcion TO estado;

ALTER TABLE banco_preguntas
  RENAME COLUMN nombre_banco_preguntas TO nombre,
  RENAME COLUMN descripcion_banco_preguntas TO descripcion;

ALTER TABLE pregunta
  RENAME COLUMN enunciado_pregunta TO enunciado,
  RENAME COLUMN tipo_pregunta TO tipo,
  RENAME COLUMN nivel_pregunta TO nivel,
  RENAME COLUMN dificultad_pregunta TO dificultad,
  RENAME COLUMN competencia_pregunta TO competencia,
  RENAME COLUMN tema_pregunta TO tema,
  RENAME COLUMN respuesta_esperada_pregunta TO respuesta_esperada,
  RENAME COLUMN estado_pregunta TO estado,
  RENAME COLUMN origen_pregunta TO origen;

ALTER TABLE opcion_respuesta
  RENAME COLUMN texto_opcion_respuesta TO texto,
  RENAME COLUMN es_correcta_opcion_respuesta TO es_correcta;

ALTER TABLE evaluacion
  RENAME COLUMN titulo_evaluacion TO titulo,
  RENAME COLUMN descripcion_evaluacion TO descripcion,
  RENAME COLUMN numero_preguntas_evaluacion TO numero_preguntas,
  RENAME COLUMN numero_versiones_evaluacion TO numero_versiones,
  RENAME COLUMN duracion_minutos_evaluacion TO duracion_minutos,
  RENAME COLUMN estado_evaluacion TO estado;

ALTER TABLE version_evaluacion
  RENAME COLUMN codigo_version_evaluacion TO codigo,
  RENAME COLUMN fecha_generacion_version_evaluacion TO fecha_generacion;

ALTER TABLE version_pregunta
  RENAME COLUMN enunciado_version_pregunta TO enunciado,
  RENAME COLUMN respuesta_esperada_version_pregunta TO respuesta_esperada,
  RENAME COLUMN orden_version_pregunta TO orden,
  RENAME COLUMN puntaje_version_pregunta TO puntaje;

ALTER TABLE version_opcion
  RENAME COLUMN texto_version_opcion TO texto,
  RENAME COLUMN es_correcta_version_opcion TO es_correcta,
  RENAME COLUMN orden_version_opcion TO orden;

ALTER TABLE evaluacion_virtual
  RENAME COLUMN fecha_inicio_evaluacion_virtual TO fecha_inicio,
  RENAME COLUMN fecha_fin_evaluacion_virtual TO fecha_fin,
  RENAME COLUMN supervision_activa_evaluacion_virtual TO supervision_activa,
  RENAME COLUMN estado_evaluacion_virtual TO estado;

ALTER TABLE intento
  RENAME COLUMN fecha_inicio_intento TO fecha_inicio,
  RENAME COLUMN fecha_fin_intento TO fecha_fin,
  RENAME COLUMN calificacion_intento TO calificacion,
  RENAME COLUMN estado_intento TO estado;

ALTER TABLE respuesta
  RENAME COLUMN contenido_respuesta TO contenido,
  RENAME COLUMN fecha_respuesta TO fecha,
  RENAME COLUMN puntaje_respuesta TO puntaje,
  RENAME COLUMN comentario_docente_respuesta TO comentario_docente;

ALTER TABLE evento_supervision
  RENAME COLUMN tipo_evento_supervision TO tipo,
  RENAME COLUMN descripcion_evento_supervision TO descripcion,
  RENAME COLUMN fecha_hora_evento_supervision TO fecha_hora;

ALTER TABLE material_practica
  RENAME COLUMN titulo_material_practica TO titulo,
  RENAME COLUMN descripcion_material_practica TO descripcion,
  RENAME COLUMN nivel_material_practica TO nivel,
  RENAME COLUMN estado_material_practica TO estado,
  RENAME COLUMN origen_material_practica TO origen;

ALTER TABLE intento_practica
  RENAME COLUMN fecha_inicio_intento_practica TO fecha_inicio,
  RENAME COLUMN fecha_fin_intento_practica TO fecha_fin,
  RENAME COLUMN calificacion_intento_practica TO calificacion;

ALTER TABLE respuesta_practica
  RENAME COLUMN contenido_respuesta_practica TO contenido,
  RENAME COLUMN es_correcta_respuesta_practica TO es_correcta,
  RENAME COLUMN retroalimentacion_respuesta_practica TO retroalimentacion;

ALTER TABLE documento_fuente
  RENAME COLUMN nombre_archivo_documento_fuente TO nombre_archivo,
  RENAME COLUMN tipo_archivo_documento_fuente TO tipo_archivo,
  RENAME COLUMN ruta_almacenamiento_documento_fuente TO ruta_almacenamiento,
  RENAME COLUMN fecha_carga_documento_fuente TO fecha_carga;

ALTER TABLE solicitud_generacion
  RENAME COLUMN tipo_solicitud_generacion TO tipo,
  RENAME COLUMN instrucciones_solicitud_generacion TO instrucciones,
  RENAME COLUMN cantidad_solicitud_generacion TO cantidad,
  RENAME COLUMN nivel_solicitud_generacion TO nivel,
  RENAME COLUMN modelo_solicitud_generacion TO modelo,
  RENAME COLUMN estado_solicitud_generacion TO estado,
  RENAME COLUMN fecha_solicitud_generacion TO fecha,
  RENAME COLUMN mensaje_error_solicitud_generacion TO mensaje_error,
  RENAME COLUMN id_banco_preguntas TO id_banco_destino;

-- ---------------------------------------------------------------------
-- VERIFICACIÓN: lista las columnas de cada tabla tras la migración
-- ---------------------------------------------------------------------
SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;

FLUSH PRIVILEGES;
