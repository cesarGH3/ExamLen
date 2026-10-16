-- =====================================================================
-- ExamLen - Migración 008: alinear usuario.institucion con el diccionario
-- El diccionario (ERS) define: VARCHAR(120), nullable.
-- La BD tenía VARCHAR(150) NOT NULL, así que no se podía distinguir
-- "no se indicó" (NULL) de "se indicó vacía" ('').
-- Ejecutar como root DESPUÉS de 007. No pierde datos (el máximo actual es 22).
-- =====================================================================

USE examlen;

ALTER TABLE usuario
  MODIFY COLUMN institucion VARCHAR(120) NULL;

-- Verificación
SHOW COLUMNS FROM usuario LIKE 'institucion';

FLUSH PRIVILEGES;
