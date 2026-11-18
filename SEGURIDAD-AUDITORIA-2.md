# Correcciones de la auditoría académica 2

Base de trabajo: `a26d093` (Evalaucion 2). Alcance: código y migraciones del repositorio. El dump `ExamLen-estructura(1).sql` citado por el auditor no fue entregado; los cambios SQL parten del esquema reconstruido con las migraciones 001–021. No se han aplicado sobre la BD real ni sobre el equipo del usuario.

| Hallazgo | Cambio y evidencia |
|---|---|
| JAVA-001 | `RespuestaDAO.guardarRespuestaVigente` limpia `id_version_opcion` al reemplazar una respuesta sin opción. Conserva transacción y bloqueo por vigencia. |
| JAVA-002 | Tokens v2 con UUID aleatorio y registro persistente `token_api`. El filtro exige firma, vencimiento, registro no revocado y cuenta/rol vigentes. `POST /api/v1/auth/revoke` revoca solo el Bearer presentado. |
| JAVA-003 | HTTPS sigue siendo obligatorio por defecto. Valores booleanos inválidos impiden inicializar el filtro en vez de desactivar HTTPS. Pruebas de HTTP directo, TLS, proxy autorizado y cabecera falsificada. La configuración del servidor real requiere comprobación externa. |
| JAVA-004 | JSON malformado o campos de tipos incorrectos devuelven 400. Fallos internos devuelven 500 con mensaje público genérico y registro sin credenciales ni tokens. |
| SQL-001 | FK compuestas respuesta–intento–versión, pregunta–versión y opción–pregunta. Los disparadores rellenan el contexto y validan el puntaje, con lecturas bloqueantes. |
| SQL-002 | FK compuestas respuesta de práctica–intento–material, material–pregunta y opción–pregunta. También protegen cambios y borrados posteriores de los padres. |
| SQL-003 | `UNIQUE (id_intento_practica, id_pregunta)`. El preflight rechaza duplicados históricos sin eliminarlos. |
| SQL-004 | CHECK en notas de evaluación 0–5, prácticas 0–100 y máximos no negativos. Puntajes de respuestas entre 0 y el máximo de su pregunta. Se conserva NULL para respuestas sin calificar. Una pregunta de versión con respuestas no permite cambiar su máximo histórico. |
| SQL-005 | FK adicionales hacia `docente` y `estudiante` en cursos, bancos, evaluaciones, materiales, solicitudes, inscripciones e intentos de práctica. |

## Aplicación de la migración

Requiere MySQL 8.4. No se promete compatibilidad con MySQL 5.6: las migraciones anteriores ya usan sintaxis de MySQL 8 y 5.6 no aplica CHECK.

1. Detener las escrituras y realizar un respaldo verificado. La migración 022 valida las inconsistencias conocidas antes de su primer DDL persistente. Si falla el preflight, revisar las filas y resolverlas explícitamente; no se inventan notas ni se borran respuestas.
2. Aplicar `web/WEB-INF/sql/migraciones/022_integridad_respuestas_tokens.sql` con una cuenta administradora de mantenimiento, seleccionando primero la BD correcta. Ejecutar en modo que se detenga en el primer error; no usar `--force`. DDL hace commit implícito: ante una aplicación parcial, restaurar o reparar antes de reintentar.
3. Solo tras la ejecución completa, registrar la migración si se aplicó manualmente:

```sql
INSERT INTO esquema_migracion (nombre)
VALUES ('022_integridad_respuestas_tokens.sql');
```

El inicializador también incluye la migración en su índice; si tiene permisos suficientes la aplica automáticamente. Con binlog activo, MySQL puede exigir privilegios administrativos adicionales para crear triggers. No conceder SUPER a la cuenta habitual de la aplicación para resolverlo: usar la cuenta de mantenimiento en el paso anterior. El inicializador rechaza migraciones parciales y mantiene la aplicación en 503 si no verifica la estructura.

4. La cuenta de ejecución necesita SELECT, INSERT y UPDATE sobre `token_api`; el grant existente sobre `examlen.*` ya los cubre. Con grants por tabla, otorgarlos explícitamente. La migración verifica sus cinco triggers con la cuenta de mantenimiento. La cuenta de ejecución no necesita TRIGGER para arrancar; el inicializador comprueba las FK, los CHECK activos y la unicidad añadidos. Los triggers deben conservar una cuenta definidora válida al exportar e importar. El arranque de un esquema ya migrado no exige CREATE sobre `esquema_migracion`.
5. Ejecutar **Clean and Build** en NetBeans y desplegar el WAR recién generado. No reutilizar `dist/ExamLen.war` ni las clases compiladas antiguas que ya estaban versionadas en el repositorio. Reiniciar y comprobar el mensaje `Esquema verificado y disponible`.

## API y transporte

Los tokens v1 anteriores dejan de aceptarse; los clientes deben autenticarse de nuevo en `POST /api/v1/auth/token`. Los tokens v2 conservan la duración configurable anterior y el mismo JSON de respuesta. Configurar `EXAMLEN_API_TOKEN_SECRET` o `examlen.api.token.secret` con un secreto externo y compartido entre las instancias. El registro guarda el UUID y metadatos, nunca el Bearer ni contraseñas.

Para revocar un token, enviar `POST /api/v1/auth/revoke` con `Authorization: Bearer <token>`. Responde 204; las siguientes peticiones con ese token reciben 401. Los demás tokens permanecen vigentes. La revocación afecta a las nuevas verificaciones; no cancela peticiones que ya habían superado el filtro. Los registros vencidos pueden depurarse mediante mantenimiento programado con permisos separados.

En producción mantener `examlen.https.required=true`. Si hay proxy TLS, configurar `examlen.behind.proxy=true` y `examlen.trusted.proxies` con las IP/CIDR reales; impedir acceso público al conector interno. El proxy debe sustituir las cabeceras Forwarded recibidas del cliente. Verificar desde fuera que HTTP no permite enviar credenciales ni operar y que HTTPS entrega HSTS. Las pruebas locales del filtro no certifican certificados, TLS ni la configuración real de Tomcat/proxy.

## Pruebas reproducibles

Requisitos: Java 17 o posterior, Python 3, bibliotecas del repositorio y Servlet API 5 de Tomcat 10. Definir `SERVLET_API_JAR` con su ruta absoluta. Opcionalmente `ECJ_JAR` permite compilar mediante Eclipse ECJ en vez de `javac`.

```bash
python pruebas/seguridad/ejecutar.py
```

Para incluir MySQL, definir `EXAMLEN_TEST_SERVER=jdbc:mysql://127.0.0.1:3306/`, `EXAMLEN_TEST_USER` y `EXAMLEN_TEST_PASSWORD` con una **instancia local exclusiva de pruebas** y una cuenta administradora. El programa crea una BD y un usuario con nombres aleatorios y los elimina en `finally`; nunca usa `examlen` como nombre de BD. Reconstruye 001–021 sin ejecutar sus grants/creación de cuentas originales, aplica 022 como DBA y comprueba el arranque posterior con una cuenta con únicamente SELECT, INSERT, UPDATE y DELETE sobre la BD de pruebas.

Se verifican eliminación de selección anterior, relaciones cruzadas, duplicados, límites y valores frontera, cambios en registros padres, roles, preflight sin DDL persistente, firma y expiración, revocación persistente e independiente, endpoint de revocación, errores HTTP y HTTPS/proxies.

Resultados de esta revisión: ver `pruebas/seguridad/RESULTADOS.md`. No constituye una reevaluación del auditor ni una prueba de penetración del despliegue real.
