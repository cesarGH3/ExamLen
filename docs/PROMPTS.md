# Prompts para el agente (OpenCode + DeepSeek)

Cada bloque entre ``` es un prompt completo. Cópialo tal cual, **uno por conversación**. El orden es el de `PLAN.md` y no conviene alterarlo.

En la carpeta `docs/` del proyecto deben estar estos cuatro archivos, porque los prompts los citan:

```
docs/ERS-resumen.md     lo que el documento especifica (la fuente de verdad)
docs/TRAZABILIDAD.md    qué hay, qué falta y dónde está cada cosa
docs/PLAN.md            el orden de trabajo
docs/PROMPTS.md         este archivo
```

---

## Reglas permanentes

Este bloque va **al principio de cada prompt**, pegado antes del texto de la fase. Son las reglas que el agente no puede romper en ninguna tarea.

```
REGLAS QUE NO PUEDES ROMPER EN NINGUNA TAREA

1. La especificación manda. `docs/ERS-resumen.md` define los nombres de tablas,
   columnas, clases, estados y flujos. Si el código dice otra cosa, el que está mal
   es el código. Si crees que la especificación se equivoca, NO la cambies: dilo en
   tu informe final y sigue con lo demás.

2. Nada de interfaz en Java. Ningún archivo .java puede contener etiquetas HTML,
   atributos style, ni cadenas que construyan marcado. El HTML vive en web/html/ y
   se rellena con el motor de plantillas servlet/Vistas.java, que ya existe.

3. Un HTML y un CSS por pantalla. Cada pantalla tiene su propio archivo en
   web/html/ y su propia hoja en web/css/paginas/. Los fragmentos reutilizables
   van en web/html/_*.html y los estilos compartidos en web/css/componentes/.

4. Solo HTML y CSS en la interfaz. Sin frameworks de frontend (sin React, sin
   Bootstrap, sin Tailwind). Las librerías del servidor que ya usa el proyecto
   (OpenPDF, PDFBox, Gson, el conector de MySQL) se mantienen y se pueden añadir
   otras del lado del servidor si la tarea lo pide.

5. El estilo visual actual se conserva. Colores, tipografía, espaciados y forma de
   las tarjetas y tablas se mantienen como están. Esta es una reorganización, no un
   rediseño.

6. JavaScript, el mínimo. Solo donde el documento lo exige: temporizador,
   autoguardado y eventos de supervisión del examen. Nada de JS para pintar la
   página.

7. Todas las consultas con PreparedStatement. Toda operación que toque dos o más
   tablas, dentro de una transacción.

8. No toques nada fuera del alcance de la tarea. Si te encuentras otro defecto,
   anótalo en el informe en vez de arreglarlo.

AL TERMINAR, entrégame un informe con:
- Archivos creados, modificados y eliminados.
- Qué probaste y cómo.
- Pasos exactos para que yo lo pruebe a mano.
- Dudas que tuviste, en lugar de decisiones que tomaste por tu cuenta.
```

---

## Prompt 1 — Renombrar columnas y tablas al nombre del documento

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: alinear los nombres de la base de datos con el diccionario de datos de la
especificación.

CONTEXTO DEL PROBLEMA
La migración sql/migraciones/003_alineacion_diagrama_v3.sql renombró todas las
columnas al patrón <atributo>_<clase>: nombre_curso, titulo_evaluacion,
contenido_respuesta, etc. El diccionario de datos usa el nombre corto: nombre,
titulo, contenido. Hay que deshacer ese patrón.

QUÉ HACER

1. Lee docs/TRAZABILIDAD.md, sección 4.3. Ahí está la lista completa de columnas
   a renombrar, tabla por tabla, con el nombre actual y el nombre correcto. Es la
   lista definitiva: no añadas ni quites nada.

2. Lee también la sección 4.2: hay dos tablas que se renombran.
   curso_material_practica → curso_material
   material_practica_pregunta → material_pregunta

3. Escribe sql/migraciones/006_nombres_ers.sql con todos los RENAME COLUMN y
   RENAME TABLE. Que se pueda ejecutar sobre la base de datos actual sin perder
   datos. Al final del script, deja una consulta de verificación que liste las
   columnas de cada tabla.

4. Actualiza el código Java. Busca cada nombre viejo en todo src/java/ y
   cámbialo. Presta atención especial a modelo/Mapeos.java, que concentra los
   nombres de columna, y a los 23 DAO.

5. Compila y corrige hasta que compile limpio.

DOS DETALLES QUE NO SON SOLO NOMBRE

- Las columnas fecha_creacion, fecha_actualizacion, intentos_fallidos y
  bloqueado_hasta no están en el diccionario de datos pero son necesarias.
  Déjalas como están, no las toques.
- En solicitud_generacion, la columna id_banco_preguntas se llama en el documento
  id_banco_destino, porque es el banco al que van a parar las preguntas
  generadas. Al renombrarla, revisa que el DAO la esté usando con ese sentido.

CÓMO SÉ QUE TERMINASTE
SHOW COLUMNS de cada tabla coincide exactamente con el diccionario de datos, y la
aplicación arranca y deja entrar con los tres roles.
```

---

## Prompt 2 — Herencia de usuario y tabla de tokens

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: partir la tabla usuario en cuatro tablas por herencia y crear la tabla de
tokens de recuperación.

Ejecuta esto DESPUÉS del prompt 1, nunca antes.

CONTEXTO DEL PROBLEMA
Hoy administradores, docentes y estudiantes viven en una sola tabla usuario, con
una columna tipo y columnas que solo aplican a un rol. El diagrama de clases y el
modelo relacional definen herencia de tabla por subclase: usuario guarda lo común
y cada rol tiene su tabla, unida por la misma clave primaria.

ESTADO OBJETIVO
Las cuatro tablas están definidas con sus columnas exactas en docs/TRAZABILIDAD.md,
sección 4.1. Respétalas al pie de la letra.

En resumen:
- usuario: lo común, sin columna tipo.
- administrador: id_usuario (PK y FK), fecha_nacimiento.
- docente: id_usuario (PK y FK), debe_cambiar_contrasena, id_administrador.
- estudiante: id_usuario (PK y FK), nivel_ingles.
- token_recuperacion: id_token_recuperacion, token_hash, fecha_creacion,
  fecha_expiracion, usado, id_usuario.

El rol se deduce de en cuál tabla hija existe la fila. La columna registrado_por
de usuario pasa a ser docente.id_administrador.

QUÉ HACER

1. Escribe sql/migraciones/007_herencia_usuario.sql con:
   - CREATE TABLE de administrador, docente, estudiante y token_recuperacion.
   - INSERT ... SELECT que copie a cada tabla hija las filas de usuario según su
     columna tipo actual.
   - Una consulta que compare el total de usuario contra la suma de las tres
     tablas hijas.
   - Al final y COMENTADOS, los ALTER TABLE que eliminan de usuario las columnas
     tipo, nivel_ingles, fecha_nacimiento, debe_cambiar_contrasena y
     registrado_por. Con una nota: se ejecutan solo cuando la aplicación ya
     funcione con el esquema nuevo.

2. Ajusta modelo/Mapeos.java: el mapeo de usuario ahora hace JOIN con la tabla
   hija. Añade un método que, dado un id_usuario, devuelva el rol consultando en
   cuál tabla hija existe la fila.

3. Ajusta los DAO:
   - UsuarioDAO: login, perfil, activar/desactivar, intentos fallidos y bloqueo
     trabajan solo sobre usuario. Al autenticar, resuelve el rol con las tablas
     hijas.
   - AdministradorDAO, DocenteDAO, EstudianteDAO: crear inserta primero en
     usuario y luego en la tabla hija, en UNA sola transacción. Eliminar borra de
     usuario y deja que la clave foránea en cascada haga el resto.
   - Revisa TODOS los DAO buscando consultas que usen la columna tipo o alguna de
     las columnas que se van a eliminar.

4. Ajusta lo que dependa del rol en la capa web: servlet/AutenticacionFiltro.java,
   servlet/ServletBase.java, InicioServlet y cualquier sitio donde se lea el tipo
   de usuario desde la sesión.

5. Compila y corrige.

NO implementes todavía la lógica de recuperación de contraseña: en esta tarea solo
se crea la tabla token_recuperacion. La lógica va en el prompt 3.

CÓMO SÉ QUE TERMINASTE
Entro como administrador, como docente y como estudiante; el perfil, el cambio de
contraseña y los listados siguen funcionando igual que antes.
```

---

## Prompt 3 — Recuperación de contraseña por correo (RF-11, CU-10, RNF-04)

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: implementar la recuperación de contraseña por correo. Es el único
requerimiento funcional de la especificación que hoy no existe en absoluto.

Lee en docs/ERS-resumen.md el RF-11, el CU-10 y el RNF-04 antes de empezar.

LO QUE PIDE LA ESPECIFICACIÓN

1. En la pantalla de ingreso, un enlace «¿Olvidaste tu contraseña?».
2. El usuario escribe su correo. El sistema SIEMPRE responde el mismo mensaje,
   exista o no la cuenta, para no revelar qué correos están registrados.
3. Si la cuenta existe y está activa, se genera un token aleatorio, se guarda su
   HASH en token_recuperacion (nunca el token en claro) y se envía por correo un
   enlace que lo contiene.
4. El token caduca a los 30 minutos y solo sirve una vez: al usarlo se marca
   usado = true.
5. El enlace lleva a una pantalla donde el usuario escribe la contraseña nueva
   dos veces. Se valida con logica/PoliticaContrasena.java, que ya existe.
6. Al cambiarla con éxito, se invalidan los demás tokens de ese usuario y se
   cierra cualquier sesión abierta suya.
7. Límite de solicitudes por hora y por correo, para que nadie use esto para
   inundar de correos a otra persona.

QUÉ CREAR

- Un servlet para pedir la recuperación y otro para fijar la contraseña nueva.
  Sigue el patrón de los servlets que ya existen (extienden ServletBase, usan
  PRG y mensajes flash).
- Un DAO para token_recuperacion: crear, buscar por hash, marcar usado, invalidar
  los del usuario, contar solicitudes recientes.
- Una entidad en logica/ que se llame TokenRecuperacion, igual que en el diagrama
  de clases.
- Un servicio de correo en servicio/, con SMTP configurable desde el mismo
  archivo de propiedades del servidor donde hoy se leen las claves de IA
  (servicio/ConfiguracionIA.java te muestra el patrón). Si falta la configuración,
  que falle con un mensaje claro en el log, no con un error 500 en la cara del
  usuario.
- Dos pantallas nuevas en web/html/ con sus dos hojas en web/css/paginas/.
- Un enlace en web/html/login.html.

RECUERDA
El restablecimiento de contraseña por parte del administrador (RF-10) SIGUE
EXISTIENDO: la especificación lo conserva como respaldo para quien no pueda usar
el correo. No lo quites ni lo sustituyas.

CÓMO SÉ QUE TERMINASTE
Pido recuperación, me llega el correo, el enlace funciona una vez; si lo abro otra
vez dice que ya se usó; a los 31 minutos dice que caducó; con un correo que no
existe sale el mismo mensaje que con uno que sí.
```

---

## Prompt 4 — Sacar el HTML de los archivos Java

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: ningún archivo .java puede contener marcado de interfaz. Hoy hay 23 que sí.

La lista completa, con una nota de qué genera cada uno, está en
docs/TRAZABILIDAD.md, sección 5.1. Empieza por los tres primeros, que son los que
más arrastran:

- servlet/ServletBase.java, método construirNavegacion
- servlet/Html.java
- servlet/Formulario.java

Y el más grave, porque el marcado está en la capa de datos y no en la web:
- modelo/MaterialPracticaDAO.java, que emite CSS en línea
  (<p style="margin-left:24px">) al exportar a Word.

CÓMO HACERLO
El proyecto ya tiene un motor de plantillas que funciona: servlet/Vistas.java,
con {{escape}} y {{{raw}}}. Y ya tiene 38 fragmentos _*.html en web/html/. El
patrón, entonces, ya está en el proyecto: solo hay que aplicarlo donde no se
aplicó.

Para cada bloque de HTML que encuentres en un .java:
1. Míralo y decide si le corresponde un fragmento _*.html que ya existe o si hay
   que crear uno nuevo.
2. Crea el fragmento con los marcadores del motor de plantillas.
3. Cambia el método Java para que solo prepare los datos y llame al motor.
4. Comprueba que la pantalla se ve exactamente igual que antes.

CASO ESPECIAL: MaterialPracticaDAO exporta a Word, que no es una pantalla. Ahí el
marcado tampoco puede estar en el DAO: saca esa generación a una clase propia en
servicio/, que lea su plantilla desde un archivo. El DAO solo le pasa los datos.

REGLA DE ORDEN
Un archivo a la vez, compilando entre uno y otro. No los toques todos de golpe.

CÓMO SÉ QUE TERMINASTE
Este comando no devuelve nada:
  grep -rln "<div\|<table\|<form\|<span\|style=\"" src/java/
Y todas las pantallas se ven igual que antes del cambio.
```

---

## Prompt 5 — Un CSS por pantalla

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: reorganizar las hojas de estilo. Hoy hay 27 pantallas compartiendo 9
hojas; ninguna pantalla tiene la suya.

El reparto actual está en docs/TRAZABILIDAD.md, sección 5.2.

ESTRUCTURA A LA QUE HAY QUE LLEGAR

web/css/
├── base.css                  variables de color, tipografía, reset, utilidades
├── componentes/
│   ├── navegacion.css
│   ├── tarjeta.css
│   ├── tabla.css
│   ├── boton.css
│   ├── formulario.css
│   ├── alerta.css
│   ├── insignia.css
│   └── paginacion.css
└── paginas/
    ├── login.css
    ├── registrarse.css
    ├── inicio.css
    ├── perfil.css
    ├── cambiar-contrasena.css
    ├── error.css
    ├── admin-docentes.css
    ├── docente-*.css          (una por cada pantalla de docente: 13)
    └── estudiante-*.css       (una por cada pantalla de estudiante: 7)

CÓMO HACERLO

1. Lee global.css, docente.css y estudiante.css enteros antes de mover nada.
2. Lo que es común a todo (colores, tipografía, reset, utilidades) va a base.css,
   con los colores como variables CSS en :root.
3. Lo que es un componente repetido (la tarjeta, la tabla, el botón, el
   formulario, la alerta, la navegación) va a su archivo en componentes/.
4. Lo que solo afecta a una pantalla va a su archivo en paginas/.
5. Cada pantalla carga, en este orden: base.css, los componentes que use, y su
   propia hoja.
6. Si una pantalla se queda sin reglas propias, crea igual su archivo con un
   comentario que diga que está reservado. La regla es una hoja por pantalla.

ESTO ES UNA REORGANIZACIÓN, NO UN REDISEÑO
No cambies ni un color, ni un tamaño de letra, ni un espaciado. El resultado tiene
que verse idéntico. Compara pantalla por pantalla antes y después.

DE PASO
Cinco hojas no tienen ninguna consulta @media y por eso esas pantallas no se ven
bien en móvil (RNF-09). Al repartir, añade en base.css y en los componentes los
puntos de corte que ya usan login.css y registro.css, para que el mismo criterio
cubra todas las pantallas.

CÓMO SÉ QUE TERMINASTE
web/css/paginas/ tiene una hoja por cada pantalla de web/html/ que no empiece por
guion bajo, y las 27 pantallas se ven igual que antes en escritorio y mejor en
móvil.
```

---

## Prompt 6 — Arreglar la cabecera CSP y la cookie

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: corregir dos defectos de seguridad. El primero está rompiendo el examen
virtual ahora mismo.

DEFECTO 1 — La CSP bloquea todo el JavaScript

En src/java/servlet/HeadersSeguridadFiltro.java:

  private static final String CSP = "default-src 'none'; style-src 'self'; "
      + "img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

No hay script-src, así que default-src 'none' bloquea web/js/examen.js en
cualquier navegador que respete la cabecera. Eso deja sin funcionar, durante el
examen virtual, el autoguardado (RF-45), el temporizador (RF-47) y los eventos de
supervisión (RF-49).

Añade script-src 'self' y connect-src 'self'. No uses 'unsafe-inline' ni
'unsafe-eval': si hay algún script en línea en los HTML, sácalo a un archivo.

Después comprueba en el navegador, con la consola abierta, que examen.js carga sin
errores de CSP, que el temporizador corre, que el autoguardado envía y que los
eventos de supervisión se registran.

DEFECTO 2 — La cookie de sesión no es Secure

En web/WEB-INF/web.xml:

  <cookie-config>
      <http-only>true</http-only>
      <!-- En produccion con HTTPS cambiar a true -->
      <secure>false</secure>
  </cookie-config>

El RNF-03 exige HTTPS con cookie Secure. Deja <secure>true</secure> como valor y
documenta en README.md que para desarrollo en local sin HTTPS hay que ponerlo en
false. Añade también la cabecera Strict-Transport-Security en el filtro.

NO TOQUES nada más del filtro de seguridad.
```

---

## Prompt 7 — Pantallas de administración que faltan (RF-06, RF-08, RF-09, RF-10)

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: cuatro requerimientos donde el DAO está escrito y probado pero nadie lo
llama desde la interfaz. Hay que darles servlet, pantalla y enlace.

RF-06 — Gestionar administradores
  modelo/AdministradorDAO.java tiene crear, actualizar y eliminar, pero la clase
  ni siquiera se instancia en servlet/InicializadorAplicacion.java. Regístrala
  ahí y crea la pantalla para que un administrador pueda gestionar a los otros.
  Un administrador no puede eliminarse a sí mismo.

RF-08 — Gestionar estudiantes
  modelo/EstudianteDAO.java tiene actualizarEstudiante y eliminarEstudiante sin
  usar. Falta además listar estudiantes. Crea la pantalla de listado con su
  servlet, y desde ahí las acciones de editar y eliminar.

RF-09 — Activar y desactivar cuentas
  Hoy solo se puede con docentes, desde admin-docentes.html. La especificación
  dice que aplica a cualquier cuenta. Extiéndelo a estudiantes y administradores,
  reutilizando UsuarioDAO.activarUsuario y desactivarUsuario, que ya existen.

RF-10 — Restablecer contraseña de un usuario
  modelo/UsuarioDAO.java tiene restablecerContrasenaUsuario y nadie lo llama.
  Añade la acción y el botón. Genera la contraseña temporal con
  logica/GeneradorContrasenaTemporal.java y obliga a cambiarla en el primer
  ingreso, igual que ya se hace al registrar un docente.

  La especificación conserva esto como RESPALDO de la recuperación por correo
  (RF-11), para quien no pueda usar el método normal. Que la pantalla lo diga con
  esas palabras.

ADEMÁS, EN TODAS ESTAS PANTALLAS
Hoy no existe ninguna confirmación antes de eliminar o archivar, en ningún sitio
del sistema. Añade un paso de confirmación antes de cada acción destructiva de
estas cuatro pantallas: una pantalla intermedia que diga qué se va a eliminar y
pida confirmar. Sin JavaScript: una pantalla y un formulario bastan.

Cada pantalla nueva: su archivo en web/html/ y su hoja en web/css/paginas/,
siguiendo la estructura del prompt 5.
```

---

## Prompt 8 — Generación de versiones del parcial (RF-35 a RF-38)

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: corregir la generación de versiones. Es el corazón del sistema y es donde
más se aleja de la especificación.

Lee en docs/ERS-resumen.md los RF-35, RF-36, RF-37 y RF-38, y el caso de uso
asociado, antes de tocar nada.

RF-35 — No aleatoriza y los códigos están mal
  modelo/VersionEvaluacionDAO.java:generarVersionEvaluacion selecciona de forma
  determinística y no aleatoriza nada. La especificación pide:
  - orden de preguntas aleatorio en cada versión;
  - orden de opciones aleatorio dentro de cada pregunta;
  - códigos de versión A, B, C... y no V1, V2.
  El orden aleatorio se congela al crear la versión: cada versión guarda su propio
  orden en version_pregunta.orden y version_opcion.orden, para que el PDF y el
  examen virtual muestren siempre lo mismo.

RF-36 — Regenerar BORRA las versiones anteriores
  Hoy generarVersionEvaluacion hace DELETE de las versiones previas. La
  especificación dice conservarlas. Cámbialo para que las versiones anteriores se
  conserven y las nuevas se añadan, continuando los códigos (si ya hay A y B, las
  siguientes son C y D). Si el docente quiere quitar una versión, que sea una
  acción explícita suya, con confirmación.

RF-37 — Falta el cuadro comparativo (no existe nada)
  La especificación pide una tabla que compare las versiones generadas mostrando,
  para cada una, la distribución por tema, por competencia y por dificultad, y el
  puntaje total. Sirve para que el docente vea de un vistazo que las versiones son
  equivalentes. Va en la pantalla de la evaluación.

RF-38 — Solo deja reordenar
  Hoy solo se puede mover una pregunta de sitio. La especificación pide poder
  editar, dentro de una versión concreta y sin afectar al banco: el enunciado, la
  respuesta esperada, el puntaje y las opciones.
  modelo/VersionOpcionDAO.java ya tiene crear, actualizar y eliminar sin usar:
  úsalos.

CUIDADO
Una versión ya aplicada en un parcial virtual con intentos registrados NO se puede
editar ni borrar: sería cambiar el examen debajo de los estudiantes. Comprueba eso
antes de permitir cualquier cambio y, si lo hay, explica por qué no se puede.
```

---

## Prompt 9 — Calificación, resultados y práctica

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: un grupo de defectos de la parte final del flujo. Lee cada RF en
docs/ERS-resumen.md antes de tocarlo.

RF-62 — Historial de práctica del estudiante (NO EXISTE)
  Es lo más grande de este prompt. La especificación pide una pantalla donde el
  estudiante vea su historial de intentos de práctica con el análisis de su
  desempeño por competencia (GRAMMAR, VOCABULARY, READING, WRITING).
  modelo/IntentoPracticaDAO.java ya tiene listarPorEstudiante y nadie lo llama.
  Falta el servlet, la pantalla, su hoja de estilos y el cálculo por competencia.

RF-60 — La retroalimentación de práctica son dos frases fijas
  Hoy guarda «¡Correcto!» o «Revisa esta respuesta». La especificación pide
  mostrar la respuesta correcta y una explicación. Para las preguntas de opción,
  la explicación sale de los datos que ya hay. Para las abiertas, muestra la
  respuesta esperada y NO las califiques automáticamente: hoy se autocalifican y
  no debería ser así.

RF-56 — El resultado del examen no muestra el detalle
  Hoy estudiante-resultado.html muestra la nota y el total. La especificación pide
  el detalle por pregunta y los comentarios que el docente haya escrito al
  calificar.

RF-48 — Enviar sin advertencia
  El botón de finalizar envía sin avisar. La especificación pide contar las
  preguntas sin responder y advertir antes de enviar.

RF-52 — No se puede corregir una calificación
  servlet/DocenteIntentoServlet.java solo califica respuestas pendientes y rechaza
  reeditar una calificación ya puesta. El docente tiene que poder ajustar una
  calificación automática o corregir la suya.

RF-54 — Faltan columnas en la tabla de intentos
  La tabla de intentos del docente no muestra la versión asignada, la hora de
  inicio, la hora de fin ni el tiempo empleado. Añádelas.

RF-57 y RF-59 — El nivel del material de práctica
  Al crear un material, el formulario solo pide título y descripción: el nivel
  nunca se asigna, aunque la columna existe. Añádelo al formulario y a
  crearMaterialPractica. Y en la pantalla del estudiante, añade el filtro por
  nivel, que hoy se muestra como columna pero no se puede filtrar.

RF-26 y RF-29 — Dos detalles de la generación con IA
  El formulario de generación no tiene campos para tema ni competencia: van
  sueltos dentro del texto de instrucciones y los acaba decidiendo el modelo.
  Hazlos campos del formulario.
  Y cuando el docente deja el modelo vacío, se guarda NULL en la solicitud: guarda
  el modelo que realmente se usó, que ProveedorIA ya sabe cuál es.
```

---

## Prompt 10 — Supervisión: similitud y tiempo atípico (RF-50, RF-55)

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: completar la supervisión del examen virtual. Es lo más delicado del
sistema: un falso positivo aquí acusa a un estudiante de algo que no hizo.

Lee el RF-50, el RF-55 y el RNF-18 en docs/ERS-resumen.md antes de empezar.

RF-50 — Dos problemas

  a) Similitud entre respuestas: el valor SIMILITUD_RESPUESTAS existe en
     logica/TipoEventoSupervision.java pero nunca se calcula ni se registra.
     Impleméntalo: al cerrar un parcial virtual, compara las respuestas abiertas
     de los estudiantes del mismo grupo y registra un evento cuando dos superen
     el umbral. Usa una medida de similitud de texto sencilla y explicable
     (por ejemplo, coeficiente de Jaccard sobre n-gramas), NO una librería opaca:
     el docente tiene que poder entender por qué saltó la alerta.

  b) Tiempo atípico: hoy se evalúa por intento suelto. La especificación dice que
     se calcula al CERRAR el parcial virtual, comparando cada intento contra el
     conjunto del grupo. Corrígelo.

RF-55 — La lista de eventos es plana
  servlet/DocenteIntentoServlet.java lista los eventos uno detrás de otro. La
  especificación pide agruparlos por tipo con su conteo y presentarlos como una
  línea de tiempo del intento.

RNF-18 — El aviso previo (NO EXISTE)
  Antes de empezar un parcial supervisado, el estudiante tiene que ver qué se va
  a registrar durante su examen. Y en la pantalla del docente, donde se ven los
  eventos, tiene que aparecer esta frase, tal cual:

    «Los indicadores no determinan por sí solos una conducta indebida.»

  No es decorativa: es lo que impide que el sistema se use como prueba
  automática contra un estudiante.

UMBRALES
Todo umbral (similitud, tiempo atípico) va en una constante con nombre y un
comentario que explique de dónde sale. Nada de números sueltos en medio del
código.
```

---

## Prompt 11 — No funcionales pendientes

```
[PEGA AQUÍ LAS REGLAS PERMANENTES]

TAREA: cerrar los requerimientos no funcionales que quedaron fuera de las otras
fases. Lee cada uno en docs/ERS-resumen.md.

RNF-13 — Consentimiento de datos (NO EXISTE)
  web/html/registrarse.html no pide aceptar la política de tratamiento de datos.
  Añade la casilla, obligatoria, y una columna que registre la aceptación con su
  fecha. Sin aceptar, no hay registro.

RNF-14 — Claves foráneas en cascada
  Varias claves foráneas usan ON DELETE CASCADE donde la especificación pide
  RESTRICT. Hoy eso permite que borrar un registro arrastre intentos y respuestas
  de estudiantes. Revisa cada clave foránea contra la especificación y escribe una
  migración que corrija las que estén mal.
  Excepción: las de herencia de usuario (administrador, docente, estudiante) SÍ
  van en cascada; ahí es lo correcto.

RNF-15 — Zona horaria
  El código usa new Date() sin zona explícita y solo la URL de conexión declara
  serverTimezone. La especificación fija America/Bogota. Que todas las fechas del
  código usen esa zona de forma explícita.

RNF-17 — Los PDF
  Ni el PDF del examen ni el del material de práctica llevan numeración de
  páginas ni encabezado repetido, y las preguntas se parten entre páginas. El del
  material de práctica, además, no lleva el curso. Corrígelo en
  modelo/VersionEvaluacionDAO.java y en modelo/MaterialPracticaDAO.java.

RNF-07 y RNF-08 — Rendimiento
  No hay evidencia de pruebas. Escribe un script sencillo que simule 40 usuarios
  concurrentes rindiendo un examen y deja el resultado en docs/. Si no llega al
  objetivo de la especificación, dilo en el informe en vez de ajustar el número.

RNF-01 — NO LO TOQUES EN ESTE PROMPT
  La especificación pide BCrypt o Argon2id y logica/HashContrasena.java usa
  PBKDF2-SHA256 con 600 000 iteraciones. Es una decisión pendiente del autor del
  documento. Déjalo como está y menciónalo en tu informe.
```

---

## Después de cada prompt

Pásame el informe que te devuelva el agente y, si puedes, el `git diff`. Yo lo reviso contra el documento antes de que pases a la fase siguiente.

Si el agente te propone «una forma mejor» de hacer algo que el documento ya define, no le hagas caso y consúltame. El documento es el contrato con el docente evaluador.
