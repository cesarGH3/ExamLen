-- Auditoria 2026-11-19: una sola representacion autoritativa de los roles.
-- El rol se deduce de la fila existente en administrador, docente o estudiante.
-- Preflight: aborta antes del primer DDL si hay usuarios sin rol, con mas de un
-- rol o con usuario.tipo incoherente. No borra usuarios ni datos historicos.
CREATE TEMPORARY TABLE auditoria_023_preflight (
  inconsistencias BIGINT NOT NULL,
  CONSTRAINT ck_auditoria_023_sin_inconsistencias CHECK (inconsistencias = 0)
);
INSERT INTO auditoria_023_preflight
SELECT COUNT(*) FROM (
  SELECT u.id_usuario AS id
  FROM usuario u
  LEFT JOIN administrador a ON a.id_usuario = u.id_usuario
  LEFT JOIN docente d ON d.id_usuario = u.id_usuario
  LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario
  WHERE (a.id_usuario IS NOT NULL) + (d.id_usuario IS NOT NULL) + (e.id_usuario IS NOT NULL) <> 1
  UNION ALL
  SELECT u.id_usuario
  FROM usuario u
  LEFT JOIN administrador a ON a.id_usuario = u.id_usuario
  LEFT JOIN docente d ON d.id_usuario = u.id_usuario
  LEFT JOIN estudiante e ON e.id_usuario = u.id_usuario
  WHERE u.tipo <> CASE
    WHEN a.id_usuario IS NOT NULL THEN 'ADMINISTRADOR'
    WHEN d.id_usuario IS NOT NULL THEN 'DOCENTE'
    WHEN e.id_usuario IS NOT NULL THEN 'ESTUDIANTE'
    ELSE 'SIN_ROL'
  END
) AS invalidos;
DROP TEMPORARY TABLE auditoria_023_preflight;

-- Un unico ALTER atomico: elimina el discriminador redundante y su indice.
-- Las columnas historicas de 003/007 se conservan; ya no representan el rol.
ALTER TABLE usuario DROP COLUMN tipo;
