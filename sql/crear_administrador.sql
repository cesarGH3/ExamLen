-- =====================================================================
-- Crea el primer administrador. Ejecutar como root.
--
-- 1. En NetBeans abre src/java/logica/GenerarHashContrasena.java
-- 2. Ejecutalo con Run File (Shift + F6), escribe la contrasena del
--    administrador y copia el hash que imprime.
-- 3. Reemplaza los valores de abajo y ejecuta este script.
--
-- No existe ninguna pantalla que cree administradores: es intencional.
-- =====================================================================
USE examlen;

INSERT INTO usuario (tipo, identificacion, nombre, correo, contrasena_hash,
                     activo, debe_cambiar_contrasena_docente)
VALUES ('ADMINISTRADOR', '1000000000', 'Administrador ExamLen', 'admin@examlen.local', 'pbkdf2_sha256$600000$CfqOyLZM8h4bBPGx4K9jfw==$Tv+IGIDc87vM1bFxOR6oYSgC4wI8sQGIgonosFqygdY=', TRUE, FALSE);
