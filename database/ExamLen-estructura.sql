-- =====================================================================
-- ExamLen - Estructura de la base de datos (solo esquema, sin datos)
-- Generado desde una BD de pruebas con las migraciones 001 a 026.
--
-- Importacion segura:
--   * No contiene sentencias destructivas de tablas, bases ni registros.
--   * Conserva indices, claves foraneas, CHECK y triggers.
--   * Los triggers no llevan DEFINER: se crean con la cuenta que importa.
--   * No contiene usuarios, contrasenas ni datos personales.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `examlen` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `examlen`;

SET FOREIGN_KEY_CHECKS = 1;
SET UNIQUE_CHECKS = 1;

-- Tabla esquema_migracion
CREATE TABLE `esquema_migracion` (
  `nombre` varchar(150) NOT NULL,
  `fecha` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla esquema_migracion_paso
CREATE TABLE `esquema_migracion_paso` (
  `nombre` varchar(150) NOT NULL,
  `huella` char(64) NOT NULL,
  `pasos_completados` int unsigned NOT NULL,
  `fecha` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla usuario
CREATE TABLE `usuario` (
  `id_usuario` int unsigned NOT NULL AUTO_INCREMENT,
  `identificacion` varchar(20) NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `correo` varchar(254) NOT NULL,
  `institucion` varchar(120) DEFAULT NULL,
  `nivel_ingles_estudiante` enum('A1','A2','B1','B2','C1','C2') DEFAULT NULL,
  `fecha_nacimiento_administrador` date DEFAULT NULL,
  `contrasena_hash` varchar(255) NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `debe_cambiar_contrasena_docente` tinyint(1) NOT NULL DEFAULT '0',
  `intentos_fallidos` tinyint unsigned NOT NULL DEFAULT '0',
  `bloqueado_hasta` datetime DEFAULT NULL,
  `registrado_por` int unsigned DEFAULT NULL,
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `debe_cambiar_contrasena` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `uk_usuario_identificacion` (`identificacion`),
  UNIQUE KEY `uk_usuario_correo` (`correo`),
  KEY `fk_usuario_registrado_por` (`registrado_por`),
  CONSTRAINT `fk_usuario_registrado_por` FOREIGN KEY (`registrado_por`) REFERENCES `usuario` (`id_usuario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla administrador
CREATE TABLE `administrador` (
  `id_usuario` int unsigned NOT NULL,
  `fecha_nacimiento` date DEFAULT NULL,
  PRIMARY KEY (`id_usuario`),
  CONSTRAINT `fk_administrador_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla documento_fuente
CREATE TABLE `documento_fuente` (
  `id_documento_fuente` int unsigned NOT NULL AUTO_INCREMENT,
  `id_docente` int unsigned NOT NULL,
  `nombre_archivo` varchar(255) NOT NULL,
  `tipo_archivo` varchar(100) NOT NULL,
  `ruta_almacenamiento` varchar(500) NOT NULL,
  `fecha_carga` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_documento_fuente`),
  KEY `idx_documento_docente` (`id_docente`),
  CONSTRAINT `fk_documento_docente` FOREIGN KEY (`id_docente`) REFERENCES `usuario` (`id_usuario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla estudiante
CREATE TABLE `estudiante` (
  `id_usuario` int unsigned NOT NULL,
  `nivel_ingles` varchar(2) NOT NULL,
  `aceptacion_tratamiento_datos` datetime DEFAULT NULL,
  PRIMARY KEY (`id_usuario`),
  CONSTRAINT `fk_estudiante_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `chk_estudiante_nivel` CHECK ((`nivel_ingles` in (_utf8mb4'A1',_utf8mb4'A2',_utf8mb4'B1',_utf8mb4'B2',_utf8mb4'C1',_utf8mb4'C2')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla token_api
CREATE TABLE `token_api` (
  `id_token` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `id_usuario` int unsigned NOT NULL,
  `expira_en` bigint NOT NULL,
  `revocado` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_token`),
  KEY `idx_token_api_usuario` (`id_usuario`),
  KEY `idx_token_api_expira` (`expira_en`),
  CONSTRAINT `fk_token_api_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE,
  CONSTRAINT `ck_token_api_expira` CHECK ((`expira_en` > 0)),
  CONSTRAINT `ck_token_api_revocado` CHECK ((`revocado` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla token_recuperacion
CREATE TABLE `token_recuperacion` (
  `id_token_recuperacion` int unsigned NOT NULL AUTO_INCREMENT,
  `token_hash` varchar(255) NOT NULL,
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_expiracion` datetime NOT NULL,
  `usado` tinyint(1) NOT NULL DEFAULT '0',
  `id_usuario` int unsigned NOT NULL,
  PRIMARY KEY (`id_token_recuperacion`),
  UNIQUE KEY `uk_token_recuperacion_hash` (`token_hash`),
  KEY `idx_token_recuperacion_usuario` (`id_usuario`),
  CONSTRAINT `fk_token_recuperacion_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla docente
CREATE TABLE `docente` (
  `id_usuario` int unsigned NOT NULL,
  `debe_cambiar_contrasena` tinyint(1) NOT NULL DEFAULT '0',
  `id_administrador` int unsigned NOT NULL,
  PRIMARY KEY (`id_usuario`),
  KEY `idx_docente_administrador` (`id_administrador`),
  CONSTRAINT `fk_docente_administrador` FOREIGN KEY (`id_administrador`) REFERENCES `administrador` (`id_usuario`) ON DELETE RESTRICT,
  CONSTRAINT `fk_docente_usuario` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla banco_preguntas
CREATE TABLE `banco_preguntas` (
  `id_banco_preguntas` int unsigned NOT NULL AUTO_INCREMENT,
  `id_docente` int unsigned NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `descripcion` varchar(500) NOT NULL DEFAULT '',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_banco_preguntas`),
  UNIQUE KEY `uk_banco_docente_nombre` (`id_docente`,`nombre`),
  UNIQUE KEY `uk_banco_id_docente` (`id_banco_preguntas`,`id_docente`),
  CONSTRAINT `fk_banco_docente` FOREIGN KEY (`id_docente`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `fk_banco_rol_docente` FOREIGN KEY (`id_docente`) REFERENCES `docente` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla clave_ia_docente
CREATE TABLE `clave_ia_docente` (
  `id_docente` int unsigned NOT NULL,
  `proveedor` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `clave_cifrada` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_docente`,`proveedor`),
  KEY `idx_clave_ia_docente` (`id_docente`),
  CONSTRAINT `fk_clave_ia_docente` FOREIGN KEY (`id_docente`) REFERENCES `docente` (`id_usuario`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla curso
CREATE TABLE `curso` (
  `id_curso` int unsigned NOT NULL AUTO_INCREMENT,
  `id_docente` int unsigned NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `descripcion` varchar(500) NOT NULL DEFAULT '',
  `nivel` enum('A1','A2','B1','B2','C1','C2') NOT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_curso`),
  UNIQUE KEY `uk_curso_docente_nombre` (`id_docente`,`nombre`),
  CONSTRAINT `fk_curso_docente` FOREIGN KEY (`id_docente`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `fk_curso_rol_docente` FOREIGN KEY (`id_docente`) REFERENCES `docente` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla evaluacion
CREATE TABLE `evaluacion` (
  `id_evaluacion` int unsigned NOT NULL AUTO_INCREMENT,
  `id_docente` int unsigned NOT NULL,
  `titulo` varchar(150) NOT NULL,
  `descripcion` text,
  `numero_preguntas` int NOT NULL DEFAULT '0',
  `numero_versiones` int NOT NULL DEFAULT '0',
  `duracion_minutos` int NOT NULL DEFAULT '0',
  `estado` enum('BORRADOR','LISTA','ARCHIVADA') NOT NULL DEFAULT 'BORRADOR',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_evaluacion`),
  KEY `idx_evaluacion_docente` (`id_docente`),
  CONSTRAINT `fk_evaluacion_docente` FOREIGN KEY (`id_docente`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `fk_evaluacion_rol_docente` FOREIGN KEY (`id_docente`) REFERENCES `docente` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_evaluacion_duracion` CHECK ((`duracion_minutos` >= 0)),
  CONSTRAINT `ck_evaluacion_numero_preguntas` CHECK ((`numero_preguntas` >= 0)),
  CONSTRAINT `ck_evaluacion_numero_versiones` CHECK ((`numero_versiones` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla grupo
CREATE TABLE `grupo` (
  `id_grupo` int unsigned NOT NULL AUTO_INCREMENT,
  `id_curso` int unsigned NOT NULL,
  `nombre` varchar(50) NOT NULL,
  `periodo` varchar(10) NOT NULL,
  `codigo_acceso` char(8) NOT NULL,
  `estado` enum('ABIERTO','CERRADO') NOT NULL DEFAULT 'ABIERTO',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_grupo`),
  UNIQUE KEY `uk_grupo_codigo` (`codigo_acceso`),
  UNIQUE KEY `uk_grupo_curso_periodo_nombre` (`id_curso`,`periodo`,`nombre`),
  KEY `idx_grupo_estado` (`estado`),
  CONSTRAINT `fk_grupo_curso` FOREIGN KEY (`id_curso`) REFERENCES `curso` (`id_curso`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla solicitud_generacion
CREATE TABLE `solicitud_generacion` (
  `id_solicitud_generacion` int unsigned NOT NULL AUTO_INCREMENT,
  `id_docente` int unsigned NOT NULL,
  `id_documento_fuente` int unsigned DEFAULT NULL,
  `id_banco_destino` int unsigned DEFAULT NULL,
  `tipo` enum('PREGUNTAS','VARIANTES','MATERIAL_PRACTICA') NOT NULL,
  `instrucciones` text,
  `cantidad` int NOT NULL DEFAULT '0',
  `nivel` enum('A1','A2','B1','B2','C1','C2') DEFAULT NULL,
  `modelo` varchar(100) DEFAULT NULL,
  `estado` enum('PENDIENTE','EN_PROCESO','COMPLETADA','FALLIDA') NOT NULL DEFAULT 'PENDIENTE',
  `fecha` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `mensaje_error` varchar(1000) DEFAULT NULL,
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `cantidad_recibida` int DEFAULT NULL,
  `cantidad_valida` int DEFAULT NULL,
  `cantidad_invalida` int DEFAULT NULL,
  `cantidad_no_devuelta` int DEFAULT NULL,
  `proveedor` varchar(30) DEFAULT NULL,
  `tema` varchar(120) DEFAULT NULL,
  `competencia` varchar(40) DEFAULT NULL,
  `id_evaluacion_referencia` int unsigned DEFAULT NULL,
  `id_preguntas_base` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_solicitud_generacion`),
  KEY `idx_solicitud_docente` (`id_docente`),
  KEY `idx_solicitud_documento` (`id_documento_fuente`),
  KEY `idx_solicitud_banco` (`id_banco_destino`),
  KEY `idx_solicitud_evaluacion_referencia` (`id_evaluacion_referencia`),
  CONSTRAINT `fk_solicitud_banco` FOREIGN KEY (`id_banco_destino`) REFERENCES `banco_preguntas` (`id_banco_preguntas`),
  CONSTRAINT `fk_solicitud_docente` FOREIGN KEY (`id_docente`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `fk_solicitud_documento` FOREIGN KEY (`id_documento_fuente`) REFERENCES `documento_fuente` (`id_documento_fuente`),
  CONSTRAINT `fk_solicitud_evaluacion_referencia` FOREIGN KEY (`id_evaluacion_referencia`) REFERENCES `evaluacion` (`id_evaluacion`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_solicitud_rol_docente` FOREIGN KEY (`id_docente`) REFERENCES `docente` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_solicitud_cantidad` CHECK ((`cantidad` >= 0)),
  CONSTRAINT `ck_solicitud_cantidades_resultado` CHECK ((((`cantidad_recibida` is null) or (`cantidad_recibida` >= 0)) and ((`cantidad_valida` is null) or (`cantidad_valida` >= 0)) and ((`cantidad_invalida` is null) or (`cantidad_invalida` >= 0)) and ((`cantidad_no_devuelta` is null) or (`cantidad_no_devuelta` >= 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla version_evaluacion
CREATE TABLE `version_evaluacion` (
  `id_version_evaluacion` int unsigned NOT NULL AUTO_INCREMENT,
  `id_evaluacion` int unsigned NOT NULL,
  `codigo` varchar(20) NOT NULL,
  `fecha_generacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_version_evaluacion`),
  UNIQUE KEY `uk_version_codigo` (`id_evaluacion`,`codigo`),
  CONSTRAINT `fk_version_evaluacion` FOREIGN KEY (`id_evaluacion`) REFERENCES `evaluacion` (`id_evaluacion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla evaluacion_virtual
CREATE TABLE `evaluacion_virtual` (
  `id_evaluacion_virtual` int unsigned NOT NULL AUTO_INCREMENT,
  `id_grupo` int unsigned NOT NULL,
  `id_evaluacion` int unsigned NOT NULL,
  `fecha_inicio` datetime NOT NULL,
  `fecha_fin` datetime NOT NULL,
  `supervision_activa` tinyint(1) NOT NULL DEFAULT '0',
  `intentos_permitidos` tinyint unsigned NOT NULL DEFAULT '1',
  `exigir_respuestas_completas` tinyint(1) NOT NULL DEFAULT '0',
  `estado` enum('PROGRAMADA','ABIERTA','CERRADA','CANCELADA') NOT NULL DEFAULT 'PROGRAMADA',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_evaluacion_virtual`),
  KEY `idx_evaluacion_virtual_grupo` (`id_grupo`),
  KEY `idx_evaluacion_virtual_evaluacion` (`id_evaluacion`),
  CONSTRAINT `fk_evaluacion_virtual_evaluacion` FOREIGN KEY (`id_evaluacion`) REFERENCES `evaluacion` (`id_evaluacion`),
  CONSTRAINT `fk_evaluacion_virtual_grupo` FOREIGN KEY (`id_grupo`) REFERENCES `grupo` (`id_grupo`),
  CONSTRAINT `ck_evaluacion_virtual_fechas` CHECK ((`fecha_fin` > `fecha_inicio`)),
  CONSTRAINT `ck_evaluacion_virtual_intentos` CHECK ((`intentos_permitidos` in (1,2)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla inscripcion
CREATE TABLE `inscripcion` (
  `id_inscripcion` int unsigned NOT NULL AUTO_INCREMENT,
  `id_grupo` int unsigned NOT NULL,
  `id_estudiante` int unsigned NOT NULL,
  `fecha` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `estado` enum('ACTIVA','RETIRADA') NOT NULL DEFAULT 'ACTIVA',
  PRIMARY KEY (`id_inscripcion`),
  UNIQUE KEY `uk_inscripcion_grupo_estudiante` (`id_grupo`,`id_estudiante`),
  KEY `idx_inscripcion_estudiante` (`id_estudiante`,`estado`),
  CONSTRAINT `fk_inscripcion_estudiante` FOREIGN KEY (`id_estudiante`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `fk_inscripcion_grupo` FOREIGN KEY (`id_grupo`) REFERENCES `grupo` (`id_grupo`),
  CONSTRAINT `fk_inscripcion_rol_estudiante` FOREIGN KEY (`id_estudiante`) REFERENCES `estudiante` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla material_practica
CREATE TABLE `material_practica` (
  `id_material_practica` int unsigned NOT NULL AUTO_INCREMENT,
  `id_docente` int unsigned NOT NULL,
  `id_solicitud_generacion` int unsigned DEFAULT NULL,
  `titulo` varchar(150) NOT NULL,
  `descripcion` varchar(500) NOT NULL DEFAULT '',
  `nivel` enum('A1','A2','B1','B2','C1','C2') DEFAULT NULL,
  `estado` enum('BORRADOR','PUBLICADO','ARCHIVADO') NOT NULL DEFAULT 'BORRADOR',
  `origen` enum('MANUAL','GENERADO_IA') NOT NULL DEFAULT 'MANUAL',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_material_practica`),
  KEY `idx_material_docente` (`id_docente`),
  KEY `idx_material_solicitud` (`id_solicitud_generacion`),
  CONSTRAINT `fk_material_docente` FOREIGN KEY (`id_docente`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `fk_material_rol_docente` FOREIGN KEY (`id_docente`) REFERENCES `docente` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_material_solicitud` FOREIGN KEY (`id_solicitud_generacion`) REFERENCES `solicitud_generacion` (`id_solicitud_generacion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla pregunta
CREATE TABLE `pregunta` (
  `id_pregunta` int unsigned NOT NULL AUTO_INCREMENT,
  `id_docente` int unsigned NOT NULL,
  `id_banco_preguntas` int unsigned DEFAULT NULL,
  `id_solicitud_generacion` int unsigned DEFAULT NULL,
  `enunciado` text NOT NULL,
  `tipo` enum('SELECCION_MULTIPLE','VERDADERO_FALSO','RESPUESTA_CORTA','RESPUESTA_ABIERTA') NOT NULL,
  `nivel` enum('A1','A2','B1','B2','C1','C2') NOT NULL,
  `dificultad` enum('BAJA','MEDIA','ALTA') NOT NULL,
  `competencia` enum('GRAMMAR','VOCABULARY','READING','WRITING') NOT NULL,
  `tema` varchar(120) NOT NULL,
  `respuesta_esperada` varchar(500) DEFAULT NULL,
  `estado` enum('BORRADOR','APROBADA','ARCHIVADA') NOT NULL DEFAULT 'BORRADOR',
  `origen` enum('MANUAL','GENERADO_IA') NOT NULL DEFAULT 'MANUAL',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_pregunta`),
  KEY `idx_pregunta_banco_estado` (`id_banco_preguntas`,`estado`),
  KEY `idx_pregunta_seleccion` (`nivel`,`competencia`,`dificultad`,`estado`),
  KEY `idx_pregunta_solicitud` (`id_solicitud_generacion`),
  KEY `idx_pregunta_docente_estado` (`id_docente`,`estado`,`id_pregunta`),
  KEY `idx_pregunta_banco_docente` (`id_banco_preguntas`,`id_docente`),
  CONSTRAINT `fk_pregunta_banco_docente` FOREIGN KEY (`id_banco_preguntas`, `id_docente`) REFERENCES `banco_preguntas` (`id_banco_preguntas`, `id_docente`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_pregunta_docente` FOREIGN KEY (`id_docente`) REFERENCES `docente` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_pregunta_solicitud` FOREIGN KEY (`id_solicitud_generacion`) REFERENCES `solicitud_generacion` (`id_solicitud_generacion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla curso_material
CREATE TABLE `curso_material` (
  `id_curso` int unsigned NOT NULL,
  `id_material_practica` int unsigned NOT NULL,
  PRIMARY KEY (`id_curso`,`id_material_practica`),
  KEY `fk_cmp_material` (`id_material_practica`),
  CONSTRAINT `fk_cmp_curso` FOREIGN KEY (`id_curso`) REFERENCES `curso` (`id_curso`) ON DELETE RESTRICT,
  CONSTRAINT `fk_cmp_material` FOREIGN KEY (`id_material_practica`) REFERENCES `material_practica` (`id_material_practica`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla evaluacion_pregunta
CREATE TABLE `evaluacion_pregunta` (
  `id_evaluacion` int unsigned NOT NULL,
  `id_pregunta` int unsigned NOT NULL,
  `orden_evaluacion_pregunta` int NOT NULL DEFAULT '0',
  `puntaje_evaluacion_pregunta` double NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_evaluacion`,`id_pregunta`),
  KEY `fk_evaluacion_pregunta_pregunta` (`id_pregunta`),
  CONSTRAINT `fk_evaluacion_pregunta_evaluacion` FOREIGN KEY (`id_evaluacion`) REFERENCES `evaluacion` (`id_evaluacion`) ON DELETE RESTRICT,
  CONSTRAINT `fk_evaluacion_pregunta_pregunta` FOREIGN KEY (`id_pregunta`) REFERENCES `pregunta` (`id_pregunta`),
  CONSTRAINT `ck_evaluacion_pregunta_orden` CHECK ((`orden_evaluacion_pregunta` >= 0)),
  CONSTRAINT `ck_evaluacion_pregunta_puntaje` CHECK ((`puntaje_evaluacion_pregunta` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla evaluacion_virtual_version
CREATE TABLE `evaluacion_virtual_version` (
  `id_evaluacion_virtual` int unsigned NOT NULL,
  `id_version_evaluacion` int unsigned NOT NULL,
  PRIMARY KEY (`id_evaluacion_virtual`,`id_version_evaluacion`),
  KEY `fk_ev_version_version` (`id_version_evaluacion`),
  CONSTRAINT `fk_ev_version_version` FOREIGN KEY (`id_version_evaluacion`) REFERENCES `version_evaluacion` (`id_version_evaluacion`),
  CONSTRAINT `fk_ev_version_virtual` FOREIGN KEY (`id_evaluacion_virtual`) REFERENCES `evaluacion_virtual` (`id_evaluacion_virtual`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla intento_practica
CREATE TABLE `intento_practica` (
  `id_intento_practica` int unsigned NOT NULL AUTO_INCREMENT,
  `id_material_practica` int unsigned NOT NULL,
  `id_estudiante` int unsigned NOT NULL,
  `fecha_inicio` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_fin` datetime DEFAULT NULL,
  `calificacion` double DEFAULT NULL,
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_intento_practica`),
  UNIQUE KEY `uk_ip_id_material` (`id_intento_practica`,`id_material_practica`),
  KEY `idx_intento_practica_material` (`id_material_practica`),
  KEY `idx_intento_practica_estudiante` (`id_estudiante`),
  CONSTRAINT `fk_intento_practica_estudiante` FOREIGN KEY (`id_estudiante`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `fk_intento_practica_material` FOREIGN KEY (`id_material_practica`) REFERENCES `material_practica` (`id_material_practica`),
  CONSTRAINT `fk_practica_rol_estudiante` FOREIGN KEY (`id_estudiante`) REFERENCES `estudiante` (`id_usuario`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_ip_calificacion_rango` CHECK (((`calificacion` is null) or (`calificacion` between 0 and 100)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla material_pregunta
CREATE TABLE `material_pregunta` (
  `id_material_practica` int unsigned NOT NULL,
  `id_pregunta` int unsigned NOT NULL,
  PRIMARY KEY (`id_material_practica`,`id_pregunta`),
  KEY `fk_mpp_pregunta` (`id_pregunta`),
  CONSTRAINT `fk_mpp_material` FOREIGN KEY (`id_material_practica`) REFERENCES `material_practica` (`id_material_practica`) ON DELETE RESTRICT,
  CONSTRAINT `fk_mpp_pregunta` FOREIGN KEY (`id_pregunta`) REFERENCES `pregunta` (`id_pregunta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla opcion_respuesta
CREATE TABLE `opcion_respuesta` (
  `id_opcion_respuesta` int unsigned NOT NULL AUTO_INCREMENT,
  `id_pregunta` int unsigned NOT NULL,
  `texto` varchar(500) NOT NULL,
  `es_correcta` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_opcion_respuesta`),
  UNIQUE KEY `uk_opcion_id_pregunta` (`id_opcion_respuesta`,`id_pregunta`),
  KEY `idx_opcion_pregunta` (`id_pregunta`),
  CONSTRAINT `fk_opcion_pregunta` FOREIGN KEY (`id_pregunta`) REFERENCES `pregunta` (`id_pregunta`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla version_pregunta
CREATE TABLE `version_pregunta` (
  `id_version_pregunta` int unsigned NOT NULL AUTO_INCREMENT,
  `id_version_evaluacion` int unsigned NOT NULL,
  `id_pregunta` int unsigned NOT NULL,
  `enunciado` text NOT NULL,
  `respuesta_esperada` varchar(500) DEFAULT NULL,
  `orden` int NOT NULL DEFAULT '0',
  `puntaje` double NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_version_pregunta`),
  UNIQUE KEY `uk_vp_id_version` (`id_version_pregunta`,`id_version_evaluacion`),
  UNIQUE KEY `uk_version_pregunta_orden` (`id_version_evaluacion`,`orden`),
  KEY `idx_version_pregunta_version` (`id_version_evaluacion`),
  KEY `idx_version_pregunta_origen` (`id_pregunta`),
  CONSTRAINT `fk_version_pregunta_origen` FOREIGN KEY (`id_pregunta`) REFERENCES `pregunta` (`id_pregunta`),
  CONSTRAINT `fk_version_pregunta_version` FOREIGN KEY (`id_version_evaluacion`) REFERENCES `version_evaluacion` (`id_version_evaluacion`) ON DELETE RESTRICT,
  CONSTRAINT `ck_version_pregunta_orden` CHECK ((`orden` >= 0)),
  CONSTRAINT `ck_vp_puntaje_no_negativo` CHECK ((`puntaje` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla intento
CREATE TABLE `intento` (
  `id_intento` int unsigned NOT NULL AUTO_INCREMENT,
  `id_evaluacion_virtual` int unsigned NOT NULL,
  `id_inscripcion` int unsigned NOT NULL,
  `numero_intento` tinyint unsigned NOT NULL DEFAULT '1',
  `id_version_evaluacion` int unsigned NOT NULL,
  `fecha_inicio` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_fin` datetime DEFAULT NULL,
  `calificacion` decimal(3,1) DEFAULT NULL,
  `estado` enum('EN_CURSO','FINALIZADO','CALIFICADO') NOT NULL DEFAULT 'EN_CURSO',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `fecha_actualizacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_intento`),
  UNIQUE KEY `uk_intento_virtual_inscripcion_numero` (`id_evaluacion_virtual`,`id_inscripcion`,`numero_intento`),
  UNIQUE KEY `uk_intento_id_version` (`id_intento`,`id_version_evaluacion`),
  KEY `idx_intento_virtual` (`id_evaluacion_virtual`),
  KEY `idx_intento_inscripcion` (`id_inscripcion`),
  KEY `idx_intento_version` (`id_version_evaluacion`),
  KEY `fk_intento_virtual_version` (`id_evaluacion_virtual`,`id_version_evaluacion`),
  CONSTRAINT `fk_intento_inscripcion` FOREIGN KEY (`id_inscripcion`) REFERENCES `inscripcion` (`id_inscripcion`),
  CONSTRAINT `fk_intento_version` FOREIGN KEY (`id_version_evaluacion`) REFERENCES `version_evaluacion` (`id_version_evaluacion`),
  CONSTRAINT `fk_intento_virtual` FOREIGN KEY (`id_evaluacion_virtual`) REFERENCES `evaluacion_virtual` (`id_evaluacion_virtual`) ON DELETE RESTRICT,
  CONSTRAINT `fk_intento_virtual_version` FOREIGN KEY (`id_evaluacion_virtual`, `id_version_evaluacion`) REFERENCES `evaluacion_virtual_version` (`id_evaluacion_virtual`, `id_version_evaluacion`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_intento_calificacion_rango` CHECK (((`calificacion` is null) or (`calificacion` between 0 and 5))),
  CONSTRAINT `ck_intento_numero` CHECK ((`numero_intento` in (1,2)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla respuesta_practica
CREATE TABLE `respuesta_practica` (
  `id_respuesta_practica` int unsigned NOT NULL AUTO_INCREMENT,
  `id_intento_practica` int unsigned NOT NULL,
  `id_pregunta` int unsigned NOT NULL,
  `id_opcion_respuesta` int unsigned DEFAULT NULL,
  `contenido` text,
  `es_correcta` tinyint(1) DEFAULT NULL,
  `retroalimentacion` varchar(1000) DEFAULT NULL,
  `id_material_practica` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_respuesta_practica`),
  UNIQUE KEY `uk_rp_intento_pregunta` (`id_intento_practica`,`id_pregunta`),
  KEY `idx_respuesta_practica_intento` (`id_intento_practica`),
  KEY `idx_respuesta_practica_pregunta` (`id_pregunta`),
  KEY `idx_respuesta_practica_opcion` (`id_opcion_respuesta`),
  KEY `fk_rp_intento_material` (`id_intento_practica`,`id_material_practica`),
  KEY `fk_rp_material_pregunta` (`id_material_practica`,`id_pregunta`),
  KEY `fk_rp_opcion_pregunta` (`id_opcion_respuesta`,`id_pregunta`),
  CONSTRAINT `fk_respuesta_practica_intento` FOREIGN KEY (`id_intento_practica`) REFERENCES `intento_practica` (`id_intento_practica`) ON DELETE RESTRICT,
  CONSTRAINT `fk_respuesta_practica_opcion` FOREIGN KEY (`id_opcion_respuesta`) REFERENCES `opcion_respuesta` (`id_opcion_respuesta`),
  CONSTRAINT `fk_respuesta_practica_pregunta` FOREIGN KEY (`id_pregunta`) REFERENCES `pregunta` (`id_pregunta`),
  CONSTRAINT `fk_rp_intento_material` FOREIGN KEY (`id_intento_practica`, `id_material_practica`) REFERENCES `intento_practica` (`id_intento_practica`, `id_material_practica`),
  CONSTRAINT `fk_rp_material_pregunta` FOREIGN KEY (`id_material_practica`, `id_pregunta`) REFERENCES `material_pregunta` (`id_material_practica`, `id_pregunta`),
  CONSTRAINT `fk_rp_opcion_pregunta` FOREIGN KEY (`id_opcion_respuesta`, `id_pregunta`) REFERENCES `opcion_respuesta` (`id_opcion_respuesta`, `id_pregunta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla version_opcion
CREATE TABLE `version_opcion` (
  `id_version_opcion` int unsigned NOT NULL AUTO_INCREMENT,
  `id_version_pregunta` int unsigned NOT NULL,
  `texto` varchar(500) NOT NULL,
  `es_correcta` tinyint(1) NOT NULL DEFAULT '0',
  `orden` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_version_opcion`),
  UNIQUE KEY `uk_vo_id_pregunta` (`id_version_opcion`,`id_version_pregunta`),
  UNIQUE KEY `uk_version_opcion_orden` (`id_version_pregunta`,`orden`),
  KEY `idx_version_opcion_pregunta` (`id_version_pregunta`),
  CONSTRAINT `fk_version_opcion_pregunta` FOREIGN KEY (`id_version_pregunta`) REFERENCES `version_pregunta` (`id_version_pregunta`) ON DELETE RESTRICT,
  CONSTRAINT `ck_version_opcion_orden` CHECK ((`orden` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla evento_supervision
CREATE TABLE `evento_supervision` (
  `id_evento_supervision` int unsigned NOT NULL AUTO_INCREMENT,
  `id_intento` int unsigned NOT NULL,
  `tipo` enum('CAMBIO_PESTANA','PERDIDA_FOCO','SALIDA_PAGINA','DESCONEXION','RECONEXION','TIEMPO_ATIPICO','SIMILITUD_RESPUESTAS') NOT NULL,
  `descripcion` varchar(500) NOT NULL DEFAULT '',
  `fecha_hora` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_evento_supervision`),
  KEY `idx_evento_intento` (`id_intento`),
  CONSTRAINT `fk_evento_intento` FOREIGN KEY (`id_intento`) REFERENCES `intento` (`id_intento`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Tabla respuesta
CREATE TABLE `respuesta` (
  `id_respuesta` int unsigned NOT NULL AUTO_INCREMENT,
  `id_intento` int unsigned NOT NULL,
  `id_version_pregunta` int unsigned NOT NULL,
  `id_version_opcion` int unsigned DEFAULT NULL,
  `contenido` text,
  `fecha` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `puntaje` double DEFAULT NULL,
  `comentario_docente` varchar(1000) DEFAULT NULL,
  `id_version_evaluacion` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id_respuesta`),
  UNIQUE KEY `uk_respuesta_intento_pregunta` (`id_intento`,`id_version_pregunta`),
  KEY `idx_respuesta_intento` (`id_intento`),
  KEY `idx_respuesta_pregunta` (`id_version_pregunta`),
  KEY `idx_respuesta_opcion` (`id_version_opcion`),
  KEY `fk_respuesta_intento_version` (`id_intento`,`id_version_evaluacion`),
  KEY `fk_respuesta_pregunta_version` (`id_version_pregunta`,`id_version_evaluacion`),
  KEY `fk_respuesta_opcion_pregunta` (`id_version_opcion`,`id_version_pregunta`),
  CONSTRAINT `fk_respuesta_intento` FOREIGN KEY (`id_intento`) REFERENCES `intento` (`id_intento`) ON DELETE RESTRICT,
  CONSTRAINT `fk_respuesta_intento_version` FOREIGN KEY (`id_intento`, `id_version_evaluacion`) REFERENCES `intento` (`id_intento`, `id_version_evaluacion`),
  CONSTRAINT `fk_respuesta_opcion` FOREIGN KEY (`id_version_opcion`) REFERENCES `version_opcion` (`id_version_opcion`),
  CONSTRAINT `fk_respuesta_opcion_pregunta` FOREIGN KEY (`id_version_opcion`, `id_version_pregunta`) REFERENCES `version_opcion` (`id_version_opcion`, `id_version_pregunta`),
  CONSTRAINT `fk_respuesta_pregunta` FOREIGN KEY (`id_version_pregunta`) REFERENCES `version_pregunta` (`id_version_pregunta`),
  CONSTRAINT `fk_respuesta_pregunta_version` FOREIGN KEY (`id_version_pregunta`, `id_version_evaluacion`) REFERENCES `version_pregunta` (`id_version_pregunta`, `id_version_evaluacion`),
  CONSTRAINT `ck_respuesta_puntaje` CHECK (((`puntaje` is null) or (`puntaje` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Triggers (sin DEFINER para importacion reproducible)
DELIMITER $$
CREATE TRIGGER `trg_administrador_rol_ai` AFTER INSERT ON `administrador` FOR EACH ROW
UPDATE usuario SET activo = TRUE WHERE id_usuario = NEW.id_usuario$$
CREATE TRIGGER `trg_administrador_rol_bd` BEFORE DELETE ON `administrador` FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El rol no se borra por separado: elimine el usuario.'$$
CREATE TRIGGER `trg_administrador_rol_bi` BEFORE INSERT ON `administrador` FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = NEW.id_usuario FOR SHARE),
  NULL, NEW.id_usuario)$$
CREATE TRIGGER `trg_administrador_rol_bu` BEFORE UPDATE ON `administrador` FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario = OLD.id_usuario, NEW.id_usuario, NULL)$$
CREATE TRIGGER `trg_docente_rol_ai` AFTER INSERT ON `docente` FOR EACH ROW
UPDATE usuario SET activo = TRUE WHERE id_usuario = NEW.id_usuario$$
CREATE TRIGGER `trg_docente_rol_bd` BEFORE DELETE ON `docente` FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El rol no se borra por separado: elimine el usuario.'$$
CREATE TRIGGER `trg_docente_rol_bi` BEFORE INSERT ON `docente` FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = NEW.id_usuario FOR SHARE),
  NULL, NEW.id_usuario)$$
CREATE TRIGGER `trg_docente_rol_bu` BEFORE UPDATE ON `docente` FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario = OLD.id_usuario, NEW.id_usuario, NULL)$$
CREATE TRIGGER `trg_estudiante_rol_ai` AFTER INSERT ON `estudiante` FOR EACH ROW
UPDATE usuario SET activo = TRUE WHERE id_usuario = NEW.id_usuario$$
CREATE TRIGGER `trg_estudiante_rol_bd` BEFORE DELETE ON `estudiante` FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El rol no se borra por separado: elimine el usuario.'$$
CREATE TRIGGER `trg_estudiante_rol_bi` BEFORE INSERT ON `estudiante` FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = NEW.id_usuario FOR SHARE)
  OR EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = NEW.id_usuario FOR SHARE),
  NULL, NEW.id_usuario)$$
CREATE TRIGGER `trg_estudiante_rol_bu` BEFORE UPDATE ON `estudiante` FOR EACH ROW
SET NEW.id_usuario = IF(NEW.id_usuario = OLD.id_usuario, NEW.id_usuario, NULL)$$
CREATE TRIGGER `trg_evaluacion_virtual_bu` BEFORE UPDATE ON `evaluacion_virtual` FOR EACH ROW
SET NEW.id_grupo = IF(NEW.id_grupo = OLD.id_grupo OR NOT EXISTS (
      SELECT id_intento FROM intento WHERE id_evaluacion_virtual = OLD.id_evaluacion_virtual FOR SHARE
    ), NEW.id_grupo, NULL),
    NEW.id_evaluacion = IF(NEW.id_evaluacion = OLD.id_evaluacion OR NOT EXISTS (
      SELECT id_version_evaluacion FROM evaluacion_virtual_version
      WHERE id_evaluacion_virtual = OLD.id_evaluacion_virtual FOR SHARE
    ), NEW.id_evaluacion, NULL)$$
CREATE TRIGGER `trg_evaluacion_virtual_version_bi` BEFORE INSERT ON `evaluacion_virtual_version` FOR EACH ROW
SET NEW.id_version_evaluacion = (
  SELECT v.id_version_evaluacion FROM evaluacion_virtual ev
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
)$$
CREATE TRIGGER `trg_evaluacion_virtual_version_bu` BEFORE UPDATE ON `evaluacion_virtual_version` FOR EACH ROW
SET NEW.id_version_evaluacion = (
  SELECT v.id_version_evaluacion FROM evaluacion_virtual ev
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
)$$
CREATE TRIGGER `trg_inscripcion_bi` BEFORE INSERT ON `inscripcion` FOR EACH ROW
SET NEW.id_grupo = (SELECT id_grupo FROM grupo WHERE id_grupo = NEW.id_grupo AND estado = 'ABIERTO' FOR SHARE)$$
CREATE TRIGGER `trg_inscripcion_bu` BEFORE UPDATE ON `inscripcion` FOR EACH ROW
SET NEW.id_grupo = IF((NEW.id_grupo = OLD.id_grupo AND NEW.id_estudiante = OLD.id_estudiante) OR NOT EXISTS (
      SELECT id_intento FROM intento WHERE id_inscripcion = OLD.id_inscripcion FOR SHARE
    ), NEW.id_grupo, NULL)$$
CREATE TRIGGER `trg_intento_bi` BEFORE INSERT ON `intento` FOR EACH ROW
SET NEW.id_inscripcion = (
  SELECT n.id_inscripcion FROM inscripcion n
  JOIN evaluacion_virtual ev ON ev.id_grupo = n.id_grupo
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE n.id_inscripcion = NEW.id_inscripcion
    AND ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
)$$
CREATE TRIGGER `trg_intento_bu` BEFORE UPDATE ON `intento` FOR EACH ROW
SET NEW.id_inscripcion = (
  SELECT n.id_inscripcion FROM inscripcion n
  JOIN evaluacion_virtual ev ON ev.id_grupo = n.id_grupo
  JOIN version_evaluacion v ON v.id_evaluacion = ev.id_evaluacion
  WHERE n.id_inscripcion = NEW.id_inscripcion
    AND ev.id_evaluacion_virtual = NEW.id_evaluacion_virtual
    AND v.id_version_evaluacion = NEW.id_version_evaluacion FOR SHARE
)$$
CREATE TRIGGER `trg_respuesta_contexto_bi` BEFORE INSERT ON `respuesta` FOR EACH ROW
SET NEW.id_version_evaluacion = (

  SELECT i.id_version_evaluacion FROM intento i

  JOIN version_pregunta vp ON vp.id_version_evaluacion = i.id_version_evaluacion

  WHERE i.id_intento = NEW.id_intento AND vp.id_version_pregunta = NEW.id_version_pregunta

    AND (NEW.puntaje IS NULL OR NEW.puntaje BETWEEN 0 AND vp.puntaje) FOR SHARE

)$$
CREATE TRIGGER `trg_respuesta_contexto_bu` BEFORE UPDATE ON `respuesta` FOR EACH ROW
SET NEW.id_version_evaluacion = (

  SELECT i.id_version_evaluacion FROM intento i

  JOIN version_pregunta vp ON vp.id_version_evaluacion = i.id_version_evaluacion

  WHERE i.id_intento = NEW.id_intento AND vp.id_version_pregunta = NEW.id_version_pregunta

    AND (NEW.puntaje IS NULL OR NEW.puntaje BETWEEN 0 AND vp.puntaje) FOR SHARE

)$$
CREATE TRIGGER `trg_rp_contexto_bi` BEFORE INSERT ON `respuesta_practica` FOR EACH ROW
SET NEW.id_material_practica = (SELECT id_material_practica FROM intento_practica WHERE id_intento_practica = NEW.id_intento_practica FOR SHARE)$$
CREATE TRIGGER `trg_rp_contexto_bu` BEFORE UPDATE ON `respuesta_practica` FOR EACH ROW
SET NEW.id_material_practica = (SELECT id_material_practica FROM intento_practica WHERE id_intento_practica = NEW.id_intento_practica FOR SHARE)$$
CREATE TRIGGER `trg_usuario_alta_bi` BEFORE INSERT ON `usuario` FOR EACH ROW
SET NEW.activo = FALSE$$
CREATE TRIGGER `trg_usuario_rol_bu` BEFORE UPDATE ON `usuario` FOR EACH ROW
SET NEW.id_usuario = IF(
  EXISTS (SELECT id_usuario FROM administrador WHERE id_usuario = OLD.id_usuario)
  OR EXISTS (SELECT id_usuario FROM docente WHERE id_usuario = OLD.id_usuario)
  OR EXISTS (SELECT id_usuario FROM estudiante WHERE id_usuario = OLD.id_usuario),
  NEW.id_usuario, NULL)$$
CREATE TRIGGER `trg_version_evaluacion_bu` BEFORE UPDATE ON `version_evaluacion` FOR EACH ROW
SET NEW.id_evaluacion = IF(NEW.id_evaluacion = OLD.id_evaluacion OR NOT EXISTS (
  SELECT id_evaluacion_virtual FROM evaluacion_virtual_version
  WHERE id_version_evaluacion = OLD.id_version_evaluacion FOR SHARE
), NEW.id_evaluacion, NULL)$$
CREATE TRIGGER `trg_vp_puntaje_bu` BEFORE UPDATE ON `version_pregunta` FOR EACH ROW
SET NEW.puntaje = IF(NEW.puntaje = OLD.puntaje OR NOT EXISTS (

  SELECT id_respuesta FROM respuesta WHERE id_version_pregunta = OLD.id_version_pregunta FOR SHARE

), NEW.puntaje, NULL)$$
DELIMITER ;

-- Migraciones aplicadas (tabla tecnica, sin datos de negocio)
INSERT INTO `esquema_migracion` (nombre) VALUES ('001_usuarios.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('002_cursos_banco_preguntas.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('003_alineacion_diagrama_v3.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('004_evaluacion_pregunta_orden_puntaje.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('005_intento_unico.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('006_nombres_ers.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('007_herencia_usuario.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('008_institucion_diccionario.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('009_codigos_version.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('010_respuesta_practica_sin_calificar.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('011_titulo_material_150.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('012_consentimiento_datos.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('013_foraneas_restrict.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('014_intento_calificacion_escala.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('015_evaluacion_titulo_descripcion_ers.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('016_pregunta_tema_120.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('017_fase10_usuarios_ia.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('018_pregunta_propietario_banco_opcional.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('019_intentos_evaluacion_virtual.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('020_politica_respuestas_completas.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('021_clave_ia_docente.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('022_integridad_respuestas_tokens.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('023_roles_sin_columna_tipo.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('024_restricciones_integridad.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('025_historial_roles_dominios.sql');
INSERT INTO `esquema_migracion` (nombre) VALUES ('026_referencias_orden_alta_usuarios.sql');

