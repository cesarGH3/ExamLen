# ExamLen + ExamBridge — entrega

Aplicación web de evaluación (Java 17 · Jakarta Servlets · Tomcat 10 · MySQL 8 · NetBeans · Ant).

## Requisitos

- JDK 17
- NetBeans
- Apache Tomcat 10 (compatible con Jakarta EE 9+)
- MySQL 8

## Pasos

1. Inicia MySQL.
2. Configura las credenciales de conexión (una sola vez): copia las tres líneas
   de `ExamLen/config/catalina.properties.ejemplo` al final de
   `{Tomcat}/conf/catalina.properties` y ajusta el usuario y la contraseña.
   (También funcionan las variables de entorno `DB_HOST`, `DB_PORT`, `DB_NAME`,
   `DB_USER`, `DB_PASSWORD`.)
3. Abre **ExamLen** en NetBeans (`File → Open Project`).
4. Selecciona el servidor Tomcat si NetBeans lo solicita.
5. `Clean and Build`.
6. `Run`. En el primer arranque, ExamLen crea la base de datos y su esquema
   **automáticamente** y muestra la pantalla para crear el **primer administrador**.
   Después podrás crear docentes, estudiantes, cursos, bancos, evaluaciones y todo
   lo demás desde el propio sistema.
7. Abre **ExamBridge** en NetBeans (`File → Open Project`).
8. `Clean and Build`.
9. `Run`.

## URLs

- ExamLen: http://localhost:8080/ExamLen/
- ExamBridge: http://localhost:8080/ExamBridge/

## Notas

- No hay que ejecutar ningún script SQL ni migración a mano: ExamLen inicializa
  la base y aplica las migraciones pendientes en cada arranque (idempotente).
- Permisos mínimos del usuario MySQL configurado (se otorgan **una sola vez** como
  administrador de MySQL):
  ```sql
  GRANT CREATE, ALTER, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE ON `examlen`.* TO 'usuario'@'localhost';
  ```
  Si tu usuario ya tiene esos permisos (o equivalentes), no hace falta nada más.
- ExamBridge no usa base de datos propia: consume ExamLen por HTTP (Basic);
  por defecto apunta a `http://localhost:8080/ExamLen`.
