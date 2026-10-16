-- =====================================================================
-- ExamLen - Migración 012: consentimiento de tratamiento de datos (RNF-13)
-- La ERS (RNF-13) exige registrar la aceptación de la política de datos en
-- el registro de estudiantes, pero el diccionario de datos no definió dónde.
-- Columna nueva, SIN respaldo del diccionario: hueco del documento que se
-- deja destacado en el informe para agregarla al diccionario.
-- Tipo exacto: DATETIME NULL.
-- Las cuentas creadas antes de esta regla quedan en NULL: no se inventa una
-- fecha de aceptación que nunca existió.
-- Ejecutar como root DESPUÉS de 011.
-- =====================================================================

USE examlen;

ALTER TABLE estudiante ADD COLUMN aceptacion_tratamiento_datos DATETIME NULL AFTER nivel_ingles;

-- Verificación
SHOW COLUMNS FROM estudiante LIKE 'aceptacion_tratamiento_datos';
