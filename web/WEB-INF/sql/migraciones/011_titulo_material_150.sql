-- =====================================================================
-- ExamLen - Migración 011: longitud del título de material de práctica
-- La ERS define material_practica.titulo como VARCHAR(150); la base
-- tenía 200. Se ajusta al documento. Antes de ejecutar se comprobó que
-- ningún título existente supera los 150 caracteres (máximo: 21).
-- Ejecutar como root DESPUÉS de 010.
-- =====================================================================

USE examlen;

ALTER TABLE material_practica MODIFY titulo VARCHAR(150) NOT NULL;

-- Verificación: la columna debe quedar en VARCHAR(150)
SHOW COLUMNS FROM material_practica LIKE 'titulo';
