# Resultados de validación - 19 de noviembre de 2026

Base revisada: rama `main`, con las correcciones de la auditoría del 19 de noviembre.
La compilación se hizo desde cero con JDK 17; no se reutilizaron clases de `build/`.

## Entorno

- MySQL Community Server **8.4.9** en una instancia aislada en `127.0.0.1:3310`, con
  directorio de datos temporal y usuarios desechables.
- Certificado de servidor temporal emitido por una CA de prueba, con SAN
  `DNS:localhost` e `IP:127.0.0.1`.
- Tomcat 10.0.23 sobre HTTPS con esa CA.
- Servlet API 5.0 de Tomcat.

## Resultados

### Regresión de seguridad (`api.SeguridadRegression`)

**73 verificaciones correctas**, entre ellas:

- preflight de 022 y 023, sin DDL persistente cuando hay datos inválidos;
- roles reales creados por `AdministradorDAO`, `DocenteDAO` y `EstudianteDAO`;
- rollback de estudiante con nivel inválido y de docente con administrador inexistente;
- arranque con 022/023 aplicadas y reinicio sin repetir migraciones;
- las comprobaciones anteriores de respuestas, tokens, triggers, autorización y errores.

### Auditoría 19/11 (`api.Auditoria20261119`)

**40 verificaciones correctas**, entre ellas:

- producción sin secreto de API falla al arrancar y no expone valores;
- clave efímera solo con `EXAMLEN_ENV=desarrollo` y opt-in explícito;
- `CifradoClaves` ida y vuelta y rechazo con otra clave;
- `VERIFY_IDENTITY` por defecto, sin `allowPublicKeyRetrieval` ni `allowMultiQueries` en la
  aplicación;
- ocho peticiones concurrentes al primer administrador: exactamente una creada;
- bootstrap deshabilitado sin credencial y segundo intento rechazado;
- instalación limpia 001-023 y segundo arranque sin repetir migraciones;
- actualización desde 021 con aplicación automática de 022 y 023;
- continuidad de tokens entre procesos con el mismo secreto y rechazo con otro;
- Gemini con `x-goog-api-key`, sin `?key=` y sin fuga en errores;
- conexión TLS real con `REQUIRED` y con `VERIFY_IDENTITY`; CA no confiable rechazada.

### Smoke test Tomcat (`pruebas/verificar_http.py`)

**24 verificaciones HTTP/HTTPS correctas**:

- bootstrap 200, CSRF 403, credencial incorrecta 403, alta 302 y ruta 404 después;
- login de administrador y estudiante, con el rol efectivo en la sesión;
- estudiante rechazado en `/app/admin/*` con 403;
- API: 405 en GET de token, 400 en cuerpo vacío/JSON malformado, 401 con credenciales
  incorrectas, 200 al emitir Bearer, 401 sin token, 200 con token, 403 por rol en catálogos,
  204 al revocar y 401 después de revocar.

### Exportación e importación SQL

`pruebas/exportar_estructura.py` generó `database/ExamLen-estructura.sql` desde una base
con 001-023 y sin datos. `pruebas/verificar_estructura.py` lo importó en otra base vacía y
comparó:

- 31 tablas;
- 215 columnas;
- 130 índices;
- 69 claves foráneas;
- 8 CHECK;
- 5 triggers;
- 23 migraciones registradas;
- cero filas de negocio.

El SQL no contiene `DEFINER`, usuarios, `GRANT`, contraseñas ni `INSERT` de datos de
negocio.

## Comando reproducible

```bash
export SERVLET_API_JAR=/ruta/tomcat/lib/servlet-api.jar
export EXAMLEN_TEST_SERVER=jdbc:mysql://127.0.0.1:3310/
export EXAMLEN_TEST_USER=usuario_pruebas
export EXAMLEN_TEST_PASSWORD=clave_pruebas
export EXAMLEN_TEST_TRUSTSTORE=/ruta/truststore.p12
export EXAMLEN_TEST_TRUSTSTORE_PASSWORD=changeit
python pruebas/seguridad/ejecutar.py
```

## Limitaciones

No se llamó a la API real de Gemini ni se usaron claves reales. No se migró la base real ni
se desplegó en el Tomcat real del usuario. La contraseña de MySQL que estuvo versionada
debe rotarse; eliminarla del índice no la borra del historial. La incidencia y la reversión
del intento accidental sobre la base local de desarrollo se documentan en
`AUDITORIA-2026-11-19.md`.
