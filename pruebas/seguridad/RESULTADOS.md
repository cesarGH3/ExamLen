# Resultado de validación — 18 de noviembre de 2026

Base revisada: `a26d093`. Compilación de todas las fuentes de `src/java` y de `SeguridadRegression.java` mediante Eclipse ECJ 3.40.0 con nivel Java 17, ejecutando en OpenJDK 21 y Servlet API 5.0.0. Se utilizaron las bibliotecas JAR del repositorio; no se reutilizaron las clases de `build/`.

**Resultado final: 62 verificaciones correctas.**

- MySQL Community Server **8.4.13**, instancia aislada en loopback. BD y cuenta de prueba aleatorias, eliminadas al terminar.
- Reconstrucción de migraciones 001–021; preflight con duplicados rechazado antes del primer cambio de estructura persistente.
- Aplicación completa de 022 mediante cuenta administradora, comprobación de los cinco disparadores y registro de migración.
- Dos arranques verificados con una cuenta con SELECT, INSERT, UPDATE y DELETE, sin CREATE, ALTER, TRIGGER ni SUPER. No se repite la migración.
- Selección de respuesta eliminada correctamente cuando se guarda sin opción.
- Inserciones y modificaciones con preguntas/versiones/materiales/opciones cruzados rechazadas.
- Duplicados de práctica rechazados; relaciones protegidas también ante cambios y borrados en registros padres.
- Notas válidas de evaluación 0 y 5, porcentaje de práctica 100 y puntaje máximo aceptados; negativos y valores superiores a los máximos rechazados.
- Asignaciones de docentes/estudiantes a relaciones de otro rol rechazadas.
- Firma, adulteración, expiración exacta, tokens antiguos y tamaño máximo comprobados.
- Registro persistente y revocación individual; otro token del mismo usuario sigue vigente; otro usuario no puede revocar el token.
- Endpoint de revocación autenticado devuelve 204; una nueva petición con el mismo Bearer recibe 401.
- JSON malformado y tipos incorrectos devuelven 400; un fallo interno de BD devuelve 500.
- HTTP directo y cabecera de proxy falsificada rechazados; HTTPS directo y proxy autorizado aceptados con HSTS; configuración booleana inválida rechazada.

Comando reproducible: `python pruebas/seguridad/ejecutar.py`, con las variables descritas en `SEGURIDAD-AUDITORIA-2.md`. Sin `EXAMLEN_TEST_SERVER`, el ejecutor omite las pruebas de BD y lo informa explícitamente.

No se probó la base de datos real, no se inspeccionó el dump mencionado por el auditor y no se desplegó un WAR en el Tomcat del usuario. HTTPS/TLS real y el esquema exportado actualizado deben comprobarse durante el despliegue. No es una certificación de seguridad ni una garantía de calificación.
