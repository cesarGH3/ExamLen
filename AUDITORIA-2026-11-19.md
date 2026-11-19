# Auditoría ExamLen - 19 de noviembre de 2026

Estado de la entrega: correcciones implementadas y probadas en una instancia MySQL 8.4.9
aislada y en Tomcat 10.0.23. La base real no fue migrada durante esta ronda.

## Hallazgos corregidos

| Hallazgo | Corrección | Archivos principales |
|---|---|---|
| Los DAO insertaban en `usuario` sin `tipo`, pero la columna era `NOT NULL` | Migración 023: preflight de consistencia y eliminación atómica de `usuario.tipo`; el rol queda en las tablas hijas | `web/WEB-INF/sql/migraciones/023_roles_sin_columna_tipo.sql`, `InicializadorEsquema.java` |
| TLS con `PREFERRED` y `allowPublicKeyRetrieval=true` | `VERIFY_IDENTITY` por defecto, truststore PKCS12, `REQUIRED` solo para loopback en desarrollo y `allowMultiQueries` solo en el inicializador | `ConfiguracionBD.java`, `Conexion.java`, `InicializadorEsquema.java` |
| Cualquiera podía crear el primer administrador | Credencial externa `EXAMLEN_BOOTSTRAP_SECRET`, 404 sin configuración, 403 con credencial incorrecta y bloqueo `GET_LOCK` | `Instalacion.java`, `PrimerAdministradorServlet.java`, `InicioSesionServlet.java`, `AdministradorDAO.java` |
| Secretos efímeros por proceso y variables `EXAMLEN`/`EXAMENL` inconsistentes | Secretos persistentes por entorno, propiedad o archivo externo; producción falla sin secreto de API; alias históricos aceptados | `Secretos.java`, `CifradoClaves.java`, `ConfiguracionIA.java`, `ConfiguracionScheduler.java`, `ConfiguracionSupervision.java` |
| Clave Gemini en la URL | `x-goog-api-key` en `ProbadorClaveIA` y `ClienteGemini` | `GeminiApi.java`, `ProbadorClaveIA.java`, `ClienteGemini.java` |
| SQL entregado desactualizado respecto de 022 | Exportación desde una base con 001-023, sin datos, con FK/CHECK/triggers y sin `DEFINER` | `database/ExamLen-estructura.sql`, `pruebas/exportar_estructura.py` |

## Migraciones

- **022** se conserva sin reescribir. Añade coherencia respuesta/intento/versión,
  opción/pregunta, práctica/material/pregunta, unicidad de práctica, rangos de notas y
  puntajes, claves foráneas por rol, tokens revocables y triggers.
- **023** es nueva: elimina `usuario.tipo` después de comprobar que cada usuario tiene
  exactamente una fila de rol y que `tipo` coincide. No borra usuarios ni datos.

El preflight de 022 y 023 sobre los datos de la base real fue verificado en modo lectura:
todas las comprobaciones devolvieron cero inconsistencias.

## Pruebas ejecutadas

- MySQL 8.4.9 aislado en `127.0.0.1:3310`, datos temporales y usuario de prueba; nunca se
  usó el servicio MySQL del usuario para las pruebas.
- JDK 17, compilación desde cero sin reutilizar `build/`.
- `api.SeguridadRegression`: **73 verificaciones**.
- `api.Auditoria20261119`: **40 verificaciones**, incluidas:
  - ocho peticiones concurrentes al primer administrador y exactamente una creada;
  - instalación limpia 001-023 y segundo arranque sin repetir migraciones;
  - actualización desde 021 aplicando 022 y 023;
  - continuidad de tokens entre dos procesos con el mismo secreto y rechazo con otro;
  - TLS real con `REQUIRED` y con `VERIFY_IDENTITY` usando CA temporal y certificado con
    SAN `localhost`/`127.0.0.1`; una CA no confiable fue rechazada;
  - Gemini contra un servidor HTTP local: cabecera `x-goog-api-key`, sin `?key=` y sin
    exposición en errores.
- Tomcat 10.0.23 sobre HTTPS con la misma CA: **24 verificaciones HTTP** (200/302/403/404/
  400/401/405/204), CSRF, bootstrap, login de administrador y estudiante, autorización por
  rol, emisión y revocación de Bearer.
- Exportación e importación en una base vacía: **31 tablas, 215 columnas, 130 índices,
  69 claves foráneas, 8 CHECK y 5 triggers**; 23 migraciones registradas y cero filas de
  negocio.

Resultados completos: `pruebas/seguridad/RESULTADOS.md`.

## Incidencia durante las pruebas

Al iniciar por primera vez un Tomcat de pruebas, el archivo copiado
`conf/catalina.properties` conservaba la conexión local al MySQL de desarrollo en el puerto
3306. Tomcat carga esos valores como propiedades del sistema y anuló los `-D` del arranque,
por lo que el inicializador intentó aplicar 022 sobre esa base local. El intento se detuvo
al crear los triggers por falta de privilegio `SUPER`, después de algunos `ALTER TABLE`.

Se comprobó que las tablas afectadas (`respuesta`, `respuesta_practica`) tenían **cero
filas** y se revirtió exactamente el DDL parcial de 022: se eliminaron las columnas, claves,
CHECK y FK añadidas. La base local volvió a `021_clave_ia_docente.sql`, sin pérdida de
usuarios ni datos. Después se repitió el smoke test con un `catalina.properties` propio del
entorno aislado en 3310. La base real remota no se modificó.

## Operaciones pendientes en la instalación real

1. **Autorizar** la aplicación de `022` y `023` a la base real, con respaldo verificado y
   cuenta de mantenimiento. El preflight ya está validado en lectura.
2. **Rotar la contraseña** del usuario MySQL de la aplicación: el archivo
   `web/WEB-INF/classes/examlen.properties` estuvo versionado y sigue apareciendo en el
   historial de Git. Retirarlo del índice no borra el historial. No se muestra el valor.
3. Configurar TLS de producción: CA/truststore PKCS12 y certificado del servidor con SAN
   correcto; usar `VERIFY_IDENTITY`.
4. Definir `EXAMLEN_API_TOKEN_SECRET`, `EXAMLEN_ORDER_SECRET` y, si se usan claves de IA,
   conservar la `EXAMLEN_IA_CRYPTO_KEY` existente. Configurar `EXAMLEN_BOOTSTRAP_SECRET`
   solo durante la creación del primer administrador.
5. Desplegar el WAR y verificar el arranque, el login, el registro y la API.

## Limitaciones

- No se llamó a la API real de Gemini ni se usaron claves reales.
- La prueba `VERIFY_IDENTITY` usó una CA y un certificado temporales; no sustituye la
  validación del certificado real del proveedor de base de datos.
- No se migró la base real ni se desplegó en el Tomcat real del usuario.
- La contraseña expuesta en el historial debe rotarse; no es suficiente con eliminarla del
  árbol de trabajo.
