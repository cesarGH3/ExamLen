# ExamLen - entrega y operación

Este documento describe el estado entregado tras la auditoría del **19 de noviembre de 2026**.
No contiene credenciales ni secretos reales.

## Requisitos

- JDK 17 o posterior.
- Apache Tomcat 10 (Servlet 5.0).
- MySQL 8.4.
- Las bibliotecas de `web/WEB-INF/lib` (se incluyen en el WAR).

## Base de datos

La aplicación no incluye credenciales dentro del WAR. Configura la conexión de una de estas formas:

1. En `{catalina.base}/conf/catalina.properties` (recomendado en producción):
   ```properties
   examlen.db.url=jdbc:mysql://db.ejemplo.com:3306/examlen
   examlen.db.user=USUARIO
   examlen.db.password=CONTRASENA
   examlen.db.sslMode=VERIFY_IDENTITY
   examlen.db.trustStore=file:/ruta/truststore.p12
   examlen.db.trustStorePassword=CLAVE_TRUSTSTORE
   examlen.db.trustStoreType=PKCS12
   ```
2. Con variables `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
   `DB_SSL_MODE`, `DB_TRUSTSTORE`, `DB_TRUSTSTORE_PASSWORD`, `DB_TRUSTSTORE_TYPE`.
3. En un `WEB-INF/classes/examlen.properties` externo a Git, solo para desarrollo.
   El repositorio incluye `config/examlen.properties.ejemplo` y `.gitignore` impide
   versionar el archivo real.

### Privilegios de la cuenta

La cuenta configurada aplica y verifica las migraciones al arrancar. Necesita, sobre la base:

```sql
GRANT CREATE, ALTER, INDEX, REFERENCES, TRIGGER, SELECT, INSERT, UPDATE, DELETE
  ON `examlen`.* TO 'USUARIO'@'host';
```

`TRIGGER` es obligatorio: sin él MySQL oculta los disparadores en `information_schema` y el
inicializador no puede demostrar que existen, por lo que deja la aplicación en modo 503.

### TLS

- En producción el valor por defecto es `sslMode=VERIFY_IDENTITY`: exige TLS, valida la CA
  y comprueba que el host coincida con el SAN del certificado.
- `allowPublicKeyRetrieval` no se utiliza. `allowMultiQueries` solo se activa en la conexión
  interna del inicializador, nunca en la aplicación.
- Si la CA es privada, genera un truststore PKCS12:
  ```bash
  keytool -importcert -alias mysql-ca -file ca.pem \
    -keystore truststore.p12 -storetype PKCS12 -storepass CAMBIAR
  ```
- Desarrollo local en loopback: se puede usar explícitamente
  `examlen.db.sslMode=REQUIRED` y `examlen.db.devMode=true`. `REQUIRED` cifra pero **no**
  valida la identidad; no debe usarse en producción.
- `PREFERRED` y `DISABLED` se rechazan.

## Primer administrador (bootstrap)

Sin credencial externa, `/primer-administrador` responde 404 y no se puede crear la primera
cuenta. Para la instalación inicial:

1. Genera un secreto largo (32+ caracteres), por ejemplo con `openssl rand -base64 48`.
2. Defínelo en el entorno del servidor:
   ```bash
   export EXAMLEN_BOOTSTRAP_SECRET='...'
   ```
   También se admite `examlen.bootstrap.secret` o la clave `bootstrap.secret` en
   `{catalina.base}/conf/examlen-secrets.properties`.
3. Abre `/primer-administrador`, introduce la credencial en el formulario y crea la cuenta.
4. Retira `EXAMLEN_BOOTSTRAP_SECRET` y reinicia Tomcat. La ruta vuelve a 404 en cuanto
   existe un administrador.

La credencial se envía por POST, nunca por URL, y no se registra en logs. La creación usa un
bloqueo `GET_LOCK` de MySQL para impedir dos administradores por peticiones concurrentes.

## Secretos persistentes

Variables canónicas:

| Uso | Variable | Propiedad | Clave en archivo |
|---|---|---|---|
| Tokens API | `EXAMLEN_API_TOKEN_SECRET` | `examlen.api.token.secret` | `api.token.secret` |
| Orden de prácticas | `EXAMLEN_ORDER_SECRET` | `examlen.order.secret` | `order.secret` |
| Bootstrap | `EXAMLEN_BOOTSTRAP_SECRET` | `examlen.bootstrap.secret` | `bootstrap.secret` |
| Cifrado de claves IA | `EXAMLEN_IA_CRYPTO_KEY` | `examlen.ia.crypto.key` | `ia.crypto.key` |

- Se aceptan los alias históricos `EXAMENL_*` con un aviso; migra a `EXAMLEN_*`.
- Archivo externo: `{catalina.base}/conf/examlen-secrets.properties`,
  `EXAMLEN_SECRETS_FILE` o `-Dexamlen.secrets.file`.
- `examlen.env=produccion` es el valor por defecto. Si falta `EXAMLEN_API_TOKEN_SECRET`,
  la aplicación no arranca. Para desarrollo explícito:
  ```bash
  export EXAMLEN_ENV=desarrollo
  export EXAMLEN_ALLOW_EPHEMERAL_SECRETS=true
  ```
  Una clave efímera solo vale para ese proceso; no es una configuración de producción.
- **Conserva `EXAMLEN_IA_CRYPTO_KEY`**: cambiarla sin migrar los valores cifrados impide
  recuperar las claves de IA guardadas.
- Nunca guardes secretos en Git, en el WAR ni en mensajes de error.

## Migraciones

El índice está en `web/WEB-INF/sql/migraciones/indice.txt`.

- `001` a `021`: esquema académico y de seguridad previo.
- `022`: integridad de respuestas, claves foráneas, CHECK y tokens revocables.
- `023`: elimina `usuario.tipo`; el rol queda en `administrador`, `docente` o `estudiante`.
  Incluye un preflight que aborta sin borrar usuarios si encuentra inconsistencias.
- `024`: restricciones de integridad que antes solo figuraban en el script SQL:
  `fk_intento_virtual_version`, `uk_respuesta_intento_pregunta`, `fk_clave_ia_docente`,
  `ck_evaluacion_virtual_fechas`, `ck_evaluacion_pregunta_puntaje`, `ck_respuesta_puntaje`;
  los 7 disparadores de coherencia de intentos (`trg_intento_bi/bu`, `trg_inscripcion_bu`,
  `trg_evaluacion_virtual_bu`, `trg_version_evaluacion_bu`, `trg_evaluacion_virtual_version_bi/bu`)
  y 6 disparadores de exclusividad de rol (un usuario solo puede estar en una de
  `administrador`, `docente` o `estudiante`). Con preflight y verificación final.

Al arrancar, `InicializadorEsquema` aplica las migraciones pendientes una sola vez, registra
cada una en `esquema_migracion` y verifica el esquema. En una instalación nueva aplica
`001` a `024`; en una base en `021` aplica `022`, `023` y `024`; en una base en `023` aplica `024`.
El esquema solo se marca disponible si existen los 18 disparadores (022 y 024) y todas las
restricciones de `024` con sus tablas y columnas; el historial de migraciones por sí solo no basta.

Antes de aplicar en una base con datos: detén escrituras, haz un respaldo verificado y
revisa el preflight. Las migraciones no borran usuarios ni respuestas.

## Compilar y probar

```bash
export SERVLET_API_JAR=/ruta/tomcat/lib/servlet-api.jar
export EXAMLEN_TEST_SERVER=jdbc:mysql://127.0.0.1:3310/
export EXAMLEN_TEST_USER=usuario_pruebas
export EXAMLEN_TEST_PASSWORD=clave_pruebas
python pruebas/seguridad/ejecutar.py
```

El ejecutor compila todas las fuentes desde cero en un directorio temporal y ejecuta:

- `api.SeguridadRegression`: regresión de seguridad, roles y preflight.
- `api.Auditoria20261119`: bootstrap concurrente, secretos, TLS y Gemini.

Para TLS con validación de identidad real, define también
`EXAMLEN_TEST_TRUSTSTORE` con un PKCS12 que contenga la CA del MySQL de pruebas.

Smoke test HTTP/HTTPS sobre Tomcat:

```bash
export EXAMLEN_HTTP_CA=/ruta/ca.pem
export EXAMLEN_HTTP_BOOTSTRAP='...credencial de prueba...'
python pruebas/verificar_http.py
```

## Exportar la estructura SQL

Con una base de pruebas que tenga aplicadas `001` a `024` y **sin datos de negocio**:

```bash
export EXAMLEN_EXPORT_HOST=127.0.0.1
export EXAMLEN_EXPORT_PORT=3310
export EXAMLEN_EXPORT_USER=usuario
export EXAMLEN_EXPORT_PASSWORD=clave
export EXAMLEN_EXPORT_DB=examlen
python pruebas/exportar_estructura.py database/ExamLen-estructura.sql
python pruebas/verificar_estructura.py database/ExamLen-estructura.sql
```

El exportador conserva índices, FK, CHECK y triggers, elimina `DEFINER`, no incluye datos
personales ni credenciales y ordena las tablas por dependencias. El verificador importa el
SQL en otra base vacía y compara tablas, columnas, índices, FK, CHECK y triggers.

## Despliegue

1. Configura la base, TLS, secretos y bootstrap fuera del WAR.
2. Compila el WAR con las fuentes de `src/java` y el contenido de `web` (sin
   `web/WEB-INF/classes/examlen.properties`).
3. Despliega en Tomcat 10 y revisa el arranque: el inicializador debe informar
   `Esquema verificado y disponible`.
4. Crea el primer administrador y retira la credencial de bootstrap.
5. Verifica HTTPS, login, registro de estudiantes y `/api/v1/auth/validar`.

No se versionan `build/`, `dist/`, WAR, ZIP, parches descargados ni archivos con credenciales.

## Referencias

- `AUDITORIA-2026-11-19.md`: hallazgos corregidos, evidencias y operaciones pendientes.
- `SEGURIDAD-AUDITORIA-2.md`: informe histórico de la ronda anterior.
- `pruebas/seguridad/RESULTADOS.md`: resultados reproducibles de las pruebas.
