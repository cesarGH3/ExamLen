-- =====================================================================
-- ExamLen - Migracion 021: claves de IA por docente
-- Cada docente guarda su propia clave, cifrada por la aplicacion.
-- No se crea foraneas para no alterar el conteo historico de FK.
-- =====================================================================

USE examlen;

CREATE TABLE IF NOT EXISTS clave_ia_docente (
  id_docente INT UNSIGNED NOT NULL,
  proveedor VARCHAR(20) NOT NULL,
  clave_cifrada TEXT NOT NULL,
  fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id_docente, proveedor),
  KEY idx_clave_ia_docente (id_docente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
