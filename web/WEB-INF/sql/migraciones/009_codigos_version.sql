-- =====================================================================
-- ExamLen - Migración 009: códigos de versión A, B, C… (RF-35)
-- Renombra las versiones existentes con códigos V1, V2, V3… a A, B, C…
-- por evaluación, en orden de creación. Ejecutar como root DESPUÉS de 008.
-- (Si alguna evaluación tuviera más de 26 versiones con código V, las
--  posteriores a la 26 se dejan como están y se reportan.)
-- =====================================================================

USE examlen;

UPDATE version_evaluacion v
JOIN (
    SELECT id_version_evaluacion,
           ROW_NUMBER() OVER (PARTITION BY id_evaluacion ORDER BY id_version_evaluacion) AS n
    FROM version_evaluacion
    WHERE codigo LIKE 'V%'
) r ON r.id_version_evaluacion = v.id_version_evaluacion
SET v.codigo = CHAR(64 + r.n)
WHERE r.n <= 26;

-- Verificación: versiones por evaluación con su código
SELECT v.id_evaluacion, e.titulo, v.id_version_evaluacion, v.codigo
FROM version_evaluacion v
JOIN evaluacion e ON e.id_evaluacion = v.id_evaluacion
ORDER BY v.id_evaluacion, v.id_version_evaluacion;

-- Si esta consulta devuelve filas, quedaron versiones sin renombrar
SELECT id_version_evaluacion, id_evaluacion, codigo
FROM version_evaluacion WHERE codigo LIKE 'V%';

FLUSH PRIVILEGES;
