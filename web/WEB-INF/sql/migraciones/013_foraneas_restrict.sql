-- =====================================================================
-- ExamLen - Migración 013: claves foráneas a RESTRICT (RNF-14)
-- La ERS solo da ON DELETE CASCADE a opcion_respuesta.id_pregunta; el
-- resto de foráneas no lo especifican, y las que arrastraban intentos,
-- respuestas o eventos permitían destruir historial en silencio.
-- Se conservan en CASCADE:
--   · opcion_respuesta → pregunta           (la ERS lo dice expresamente)
--   · administrador/docente/estudiante → usuario (herencia de usuario)
--   · token_recuperacion → usuario          (no es historial: caduca en 30 min)
-- Las 11 restantes pasan a ON DELETE RESTRICT.
-- Ejecutar como root DESPUÉS de 012.
-- =====================================================================

USE examlen;

ALTER TABLE curso_material DROP FOREIGN KEY fk_cmp_curso;
ALTER TABLE curso_material ADD CONSTRAINT fk_cmp_curso FOREIGN KEY (id_curso)
    REFERENCES curso (id_curso) ON DELETE RESTRICT;

ALTER TABLE curso_material DROP FOREIGN KEY fk_cmp_material;
ALTER TABLE curso_material ADD CONSTRAINT fk_cmp_material FOREIGN KEY (id_material_practica)
    REFERENCES material_practica (id_material_practica) ON DELETE RESTRICT;

ALTER TABLE evaluacion_pregunta DROP FOREIGN KEY fk_evaluacion_pregunta_evaluacion;
ALTER TABLE evaluacion_pregunta ADD CONSTRAINT fk_evaluacion_pregunta_evaluacion FOREIGN KEY (id_evaluacion)
    REFERENCES evaluacion (id_evaluacion) ON DELETE RESTRICT;

ALTER TABLE evaluacion_virtual_version DROP FOREIGN KEY fk_ev_version_virtual;
ALTER TABLE evaluacion_virtual_version ADD CONSTRAINT fk_ev_version_virtual FOREIGN KEY (id_evaluacion_virtual)
    REFERENCES evaluacion_virtual (id_evaluacion_virtual) ON DELETE RESTRICT;

ALTER TABLE intento DROP FOREIGN KEY fk_intento_virtual;
ALTER TABLE intento ADD CONSTRAINT fk_intento_virtual FOREIGN KEY (id_evaluacion_virtual)
    REFERENCES evaluacion_virtual (id_evaluacion_virtual) ON DELETE RESTRICT;

ALTER TABLE evento_supervision DROP FOREIGN KEY fk_evento_intento;
ALTER TABLE evento_supervision ADD CONSTRAINT fk_evento_intento FOREIGN KEY (id_intento)
    REFERENCES intento (id_intento) ON DELETE RESTRICT;

ALTER TABLE respuesta DROP FOREIGN KEY fk_respuesta_intento;
ALTER TABLE respuesta ADD CONSTRAINT fk_respuesta_intento FOREIGN KEY (id_intento)
    REFERENCES intento (id_intento) ON DELETE RESTRICT;

ALTER TABLE respuesta_practica DROP FOREIGN KEY fk_respuesta_practica_intento;
ALTER TABLE respuesta_practica ADD CONSTRAINT fk_respuesta_practica_intento FOREIGN KEY (id_intento_practica)
    REFERENCES intento_practica (id_intento_practica) ON DELETE RESTRICT;

ALTER TABLE material_pregunta DROP FOREIGN KEY fk_mpp_material;
ALTER TABLE material_pregunta ADD CONSTRAINT fk_mpp_material FOREIGN KEY (id_material_practica)
    REFERENCES material_practica (id_material_practica) ON DELETE RESTRICT;

ALTER TABLE version_pregunta DROP FOREIGN KEY fk_version_pregunta_version;
ALTER TABLE version_pregunta ADD CONSTRAINT fk_version_pregunta_version FOREIGN KEY (id_version_evaluacion)
    REFERENCES version_evaluacion (id_version_evaluacion) ON DELETE RESTRICT;

ALTER TABLE version_opcion DROP FOREIGN KEY fk_version_opcion_pregunta;
ALTER TABLE version_opcion ADD CONSTRAINT fk_version_opcion_pregunta FOREIGN KEY (id_version_pregunta)
    REFERENCES version_pregunta (id_version_pregunta) ON DELETE RESTRICT;

-- Verificación: todas las reglas de borrado de la base
SELECT rc.TABLE_NAME, rc.CONSTRAINT_NAME, rc.REFERENCED_TABLE_NAME, rc.DELETE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS rc
WHERE rc.CONSTRAINT_SCHEMA = 'examlen'
ORDER BY rc.DELETE_RULE DESC, rc.REFERENCED_TABLE_NAME, rc.TABLE_NAME;
