# Library API

API REST de usuarios y libros para una biblioteca, construida con Spring Boot.

Gestiona dos recursos: usuarios (`/library/users`, consulta por id y alta) y
libros (`/library/books`, consulta por ISBN-13 y alta). Los datos se persisten
con JPA/Hibernate sobre H2 en memoria.

---

## Requisitos

| | |
|---|---|
| **JDK** | **17.0.10 o superior** (ver [ nota importante](#nota-importante-jdk)) |
| Maven | 3.9+ (incluido: `./mvnw`) |

Configura el JDK correcto antes de construir o ejecutar:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/17.0.10-tem
```

### ⚠️ Nota importante: JDK

El proyecto **no arranca con JDK 17.0.2** sobre sistemas con cgroup v2.
El fallo no es del código: el propio JDK no logra inicializar el MBean server de
la plataforma y lanza un `NullPointerException`:

```
Cannot invoke "jdk.internal.platform.CgroupInfo.getMountPoint()" because "anyController" is null
```

Lo dispara Micrometer (a través de `TomcatMetricsBinder`) durante el arranque, así
que el contexto nunca llega a crearse. Es un defecto conocido del JDK 17.0.2;
se corrige subiendo a **17.0.10+** o a JDK 21.

---

## Puesta en marcha

```bash
# 1. Compilar y ejecutar los tests
./mvnw clean test

# 2. Levantar la aplicación
./mvnw spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080`.

| URL | Descripción |
|---|---|
| `http://localhost:8080/actuator` | Endpoints de actuator |
| `http://localhost:8080/h2-console` | Consola web de H2 |

Datos de conexión a la base de datos en memoria:

```
JDBC URL: jdbc:h2:mem:testdb
Usuario:  sa
Password: (vacío)
```

> Al ser una base en memoria, **los datos se pierden en cada reinicio**.

---

## API

Base: `http://localhost:8080`

### Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/library` | Comprobación de vida | 200 `"ok"` |
| `GET` | `/library/users/{id}` | Obtener un usuario por id | 200, 400, 404 |
| `POST` | `/library/users` | Crear un usuario | 201, 400 |
| `GET` | `/library/books/{isbn}` | Obtener un libro por ISBN-13 | 200, 400, 404 |
| `POST` | `/library/books` | Dar de alta un libro | 201, 400, 409 |

Todos los errores comparten el mismo cuerpo (`ApiError`): `timestamp`, `status`,
`error`, `message` y `fieldErrors` con el detalle campo a campo en los 400 de
validación.

### Usuarios

Base: `http://localhost:8080/library/users`

#### `GET /library/users/{id}` — Obtener un usuario

```bash
curl -i http://localhost:8080/library/users/1
```

**200 OK**

```json
{
  "id": 1,
  "name": "Ana",
  "firstLastName": "Gomez",
  "secondLastName": "Ruiz",
  "status": "ACTIVE"
}
```

**404 Not Found** — el id no existe

```json
{
  "timestamp": "2026-09-28T18:31:03.076390332Z",
  "status": 404,
  "error": "Not Found",
  "message": "No existe un usuario con id: 999",
  "fieldErrors": {}
}
```

**400 Bad Request** — el id no es positivo (`@Positive` en el path variable)

---

#### `POST /library/users` — Crear un usuario

```bash
curl -i -X POST http://localhost:8080/library/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana","firstLastName":"Gomez","secondLastName":"Ruiz"}'
```

**201 Created** (con cabecera `Location`)

```
HTTP/1.1 201
Location: /library/users/1

{
  "id": 1,
  "name": "Ana",
  "firstLastName": "Gomez",
  "secondLastName": "Ruiz",
  "status": "ACTIVE"
}
```

**400 Bad Request** — datos inválidos, con el detalle por campo

```json
{
  "timestamp": "2026-09-28T18:31:08.084912430Z",
  "status": 400,
  "error": "Bad Request",
  "message": "La peticion contiene datos invalidos",
  "fieldErrors": {
    "name": "el nombre es obligatorio",
    "firstLastName": "el primer apellido es obligatorio"
  }
}
```

#### Modelo de datos

| Campo | Tipo | Obligatorio | Notas |
|---|---|---|---|
| `id` | `Long` | — | **Solo en la respuesta.** Lo genera la base de datos; si lo envías en el request se ignora |
| `name` | `String` | Sí | Máx. 100 caracteres |
| `firstLastName` | `String` | Sí | Máx. 100 caracteres |
| `secondLastName` | `String` | No | Máx. 100 caracteres |
| `status` | `UserStatus` | No | `ACTIVE` (por defecto), `INACTIVE`, `SUSPENDED` |

---

## Libros

Base: `http://localhost:8080/library/books`

La clave del recurso es el **ISBN-13**, un `String` que aporta el cliente. A
diferencia de `User`, el id **no** lo genera la base de datos: el ISBN es la
clave natural del libro, así que la tabla `books` lo declara como `@Id` sin
`@GeneratedValue`.

Para probar los endpoints de abajo, el ISBN debe ser válido (13 dígitos con
dígito de control correcto); `9780306406157` sirve de ejemplo.

```bash
curl -i -X POST http://localhost:8080/library/books \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"9780306406157","author":"Ursula K. Le Guin","title":"The Left Hand of Darkness","publicationDate":"1997-03-03","availableCopyNumber":4}'

curl -i http://localhost:8080/library/books/9780306406157
```

### `GET /library/books/{isbn}` — Obtener un libro

```bash
curl -i http://localhost:8080/library/books/9780306406157
```

**200 OK**

```json
{
  "isbn": "9780306406157",
  "author": "Ursula K. Le Guin",
  "title": "The Left Hand of Darkness",
  "publicationDate": "1997-03-03",
  "availableCopyNumber": 4
}
```

**404 Not Found** — el ISBN no está dado de alta

```json
{
  "timestamp": "2026-09-29T16:36:53.834634505Z",
  "status": 404,
  "error": "Not Found",
  "message": "No existe un libro con isbn: 9788491050469",
  "fieldErrors": {}
}
```

**400 Bad Request** — el ISBN no son 13 dígitos. El formato se descarta en el
borde (`@Pattern`): un valor con otro formato no puede existir en la tabla, así
que responder 400 es más útil que un 404 para una consulta imposible.

### `POST /library/books` — Dar de alta un libro

```bash
curl -i -X POST http://localhost:8080/library/books \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"9780306406157","author":"Ursula K. Le Guin","title":"The Left Hand of Darkness","publicationDate":"1997-03-03","availableCopyNumber":4}'
```

**201 Created** (con cabecera `Location`)

```
HTTP/1.1 201
Location: /library/books/9780306406157

{"isbn":"9780306406157","author":"Ursula K. Le Guin","title":"The Left Hand of Darkness","publicationDate":"1997-03-03","availableCopyNumber":4}
```

**409 Conflict** — el ISBN ya está dado de alta

```json
{
  "timestamp": "2026-09-29T16:36:53.834634505Z",
  "status": 409,
  "error": "Conflict",
  "message": "Ya existe un libro con isbn: 9780306406157",
  "fieldErrors": {}
}
```

**400 Bad Request** — datos inválidos, con el detalle por campo

```json
{
  "timestamp": "2026-09-29T16:36:53.857440187Z",
  "status": 400,
  "error": "Bad Request",
  "message": "La peticion contiene datos invalidos",
  "fieldErrors": { "isbn": "el ISBN-13 introducido no es valido" }
}
```

#### Modelo de datos

| Campo | Tipo | Obligatorio | Notas |
|---|---|---|---|
| `isbn` | `String` | Sí | **Clave primaria.** 13 dígitos con dígito de control válido |
| `author` | `String` | Sí | Máx. 100 caracteres |
| `title` | `String` | Sí | Máx. 100 caracteres |
| `publicationDate` | `LocalDate` | No | ISO-8601. No puede ser futura |
| `availableCopyNumber` | `Long` | Sí | `>= 0` |

### Gestión de ejemplares (sin endpoint)

`BookService` expone dos operaciones para mover ejemplares. **No tienen endpoint
HTTP**: son casos de uso del dominio pendientes de exponer, y se prueban
directamente contra el service.

```java
void reserveCopy(String isbn);   // reserva un ejemplar
void releaseCopy(String isbn);   // devuelve un ejemplar
```

| Operación | Libro inexistente | Sin ejemplares | Éxito |
|---|---|---|---|
| `reserveCopy` | `BookNotFoundException` (404) | `NoAvailableCopiesException` (409) | — |
| `releaseCopy` | `BookNotFoundException` (404) | — | — |

`reserveCopy` no devuelve nada y no puede quedarse en negativo: o reserva, o
lanza. Antes devolvía un `boolean` cuyo `false` no distinguía entre «agotado» y
cualquier otro resultado negativo.

Las dos operaciones usan estrategias distintas, a propósito:

- **`reserveCopy`** lee con `findByIsbnForUpdate`, que aplica
  `@Lock(PESSIMISTIC_WRITE)`. Necesita el valor porque tiene que **decidir**
  si hay ejemplares. Sin el bloqueo, dos reservas concurrentes del último
  ejemplar lo consumen ambas (lost update).
- **`releaseCopy`** no lee nada: un único `UPDATE ... SET available_copy_number
  = available_copy_number + 1` es atómico por construcción, y el número de
  filas afectadas resuelve de paso si el libro existe. Por eso este no
  necesita bloqueo y hace un solo viaje a la base de datos.

---

## Estructura del proyecto

```
src/main/java/library_api/
├── LibraryApiApplication.java          # Punto de entrada
├── controller/
│   ├── LibraryController.java          # Endpoint placeholder (GET /library → "ok")
│   ├── BookController.java             # Endpoints de libro
│   └── UserController.java             # Endpoints de usuario
├── dto/
│   ├── BookRequest.java                # Entrada (alta de libro)
│   ├── BookResponse.java               # Salida (lectura de libro)
│   ├── UserRequest.java                # Entrada (alta). Sin id
│   └── UserResponse.java               # Salida (lectura). Con id
├── entity/
│   ├── Book.java                       # Entidad JPA. @Id = ISBN-13
│   ├── User.java                       # Entidad JPA
│   └── UserStatus.java                 # Enum de estados
├── exception/
│   ├── ApiError.java                   # Cuerpo uniforme de error
│   ├── BookAlreadyExistsException.java  # ISBN duplicado → 409
│   ├── BookNotFoundException.java      # ISBN inexistente → 404
│   ├── GlobalExceptionHandler.java     # Excepciones → respuestas HTTP
│   ├── NoAvailableCopiesException.java # Sin ejemplares → 409
│   └── UserNotFoundException.java      # Excepción de dominio
├── repository/
│   ├── BookRepository.java             # JpaRepository<Book, String>
│   └── UserRepository.java             # JpaRepository<User, Long>
├── service/
│   ├── BookService.java                # Contrato
│   ├── BookServiceImpl.java            # Implementación
│   ├── UserService.java                # Contrato
│   └── UserServiceImpl.java            # Implementación
└── util/mapper/
    ├── BookMapper.java                 # MapStruct (genera BookMapperImpl)
    └── UserMapper.java                 # MapStruct (genera UserMapperImpl)

src/main/resources/
├── application.properties              # Configuración base
└── application-dev.properties          # Configuración de desarrollo
```

---

## Decisiones de diseño

### DTOs separados por dirección

`UserRequest` (alta) y `UserResponse` (lectura) están separados a propósito:

- El `id` **no** forma parte del request. Lo genera la base de datos
  (`GenerationType.IDENTITY`). Si el cliente pudiera enviarlo, Spring Data
  tomaría la ruta `merge()` en lugar de `persist()`: un `SELECT` extra y un
  `UPDATE` en vez de un `INSERT`.
- El DTO de respuesta expone todos los campos de la entidad, incluido el id.

### Un único mapper

Todo el mapeo pasa por `UserMapper` y `BookMapper` (MapStruct), declarados con
`componentModel = SPRING` para que las implementaciones generadas se registren
como beans. Antes coexistían dos estrategias (el mapper y un factory estático
`UserDto.fromEntity`), que se desincronizaban y perdían campos en silencio.

`BookMapper` no ignora ningún campo a propósito: el `isbn` es la clave primaria y
llega en el request, así que `BookRequest` y `Book` tienen exactamente los mismos
campos y MapStruct los copia todos en ambos sentidos.

### Excepciones de dominio

`UserService.getUser` lanza `UserNotFoundException` cuando el id no existe, en
lugar de devolver `null`. `GlobalExceptionHandler` la traduce a **404**, junto
con el resto de errores de validación (400) y una red de seguridad para
excepciones inesperadas que no filtra detalles internos al cliente.

Los libros siguen el mismo patrón: `BookNotFoundException` → **404** y
`BookAlreadyExistsException` → **409**.

### El ISBN es un `String`, no un `long`

`BookService.getBookByIsbn` recibe un `String` porque el ISBN-13 no cabe de
forma fiel en un primitivo numérico (prefijo 978/979, cuerpo de 9 dígitos y
dígito de control) y, sobre todo, porque `Book.isbn` es la clave primaria
`String` de la entidad. Un `long` obligaría a convertir la clave al buscar y a
convertirla de vuelta al guardar, con riesgo de perder el ISBN real.

### ISBN duplicado → 409, no un 201 silencioso

Como el id lo aporta el cliente, un alta repetida haría que Spring Data tomara
la ruta `merge()` y devolviera **201 Created** sobre un libro que ya existía,
sin que el cliente se entere de que nada se creó. `addBook` comprueba
`existsById` **antes** de mapear y persistir, y lanza
`BookAlreadyExistsException`; la restricción de clave primaria de la base de
datos sigue siendo la garantía final de unicidad.

### `Long` en el DTO, `long` en la entidad

`BookRequest.availableCopyNumber` es un `Long` (wrapper) y no un `long`
primitivo, aunque la entidad lo declare primitivo. Motivo: Jackson falla con un
**500** (`Cannot map null into type long`) cuando un primitivo no aparece en el
JSON, y ese error ocurre al deserializar, **antes** de que Bean Validation pueda
actuar. Con el wrapper, el campo ausente llega como `null` y lo rechaza el
`@NotNull`, produciendo el 400 con el detalle por campo que el cliente espera.
Hay un test de regresión que fija este comportamiento
(`addBook_sinNumeroDeCopias_devuelve400`).

### Fechas con `java.time`

`Book.publicationDate` es un `java.time.LocalDate` y no un `java.util.Date`:
`Date` es mutable, no es thread-safe y su API aritmética está obsoleta desde
Java 8. Para una fecha civil `LocalDate` es el tipo correcto y Jackson lo
serializa directamente en ISO-8601 (`"1997-03-03"`).

### Entidad JPA

- `status` es un **enum** con `@Enumerated(EnumType.STRING)`, no un `String` libre.
- `equals`/`hashCode` usan el patrón recomendado para JPA: identidad por `id` y
  `hashCode` constante. Un `equals` sobre todos los campos (lo que genera
  Lombok con `@Data`) rompe las colecciones de sesión de Hibernate, porque la
  entidad cambia de estado al ser gestionada.

### Transacciones y logging

- `@Transactional(readOnly = true)` en las lecturas, `@Transactional` en la escritura.
- Logging SLF4J: `debug` al entrar, `info` al crear (con el id generado),
  `warn` cuando no se encuentra el recurso o cuando se rechaza un ISBN duplicado.

### Validación en el borde, no en la entidad

Las restricciones (`@NotBlank`, `@Size`, `@ISBN`, `@NotNull`, `@PastOrPresent`,
`@PositiveOrZero`) viven en `BookRequest`, no en `Book`. Una petición inválida se
rechaza con un 400 y el detalle por campo sin llegar a abrir una transacción
contra la base de datos. En `Book` solo quedan las restricciones de esquema
(`nullable`, `length`), que documentan la forma de la tabla.

---

## Configuración

| Archivo | Contenido |
|---|---|
| `application.properties` | Nombre de la app, datasource H2, consola H2, perfil activo |
| `application-dev.properties` | `ddl-auto`, `show-sql`, logging de Hibernate |

`application-dev.properties` está pensado **solo para desarrollo**. En un entorno
real hay que arrancar con otro perfil y configuración propia:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Además, `ddl-auto=update` no debería usarse contra una base de datos real: lo
correcto es `validate` y correr migraciones con Flyway o Liquibase.

---

## Tests

```bash
./mvnw test
```

63 tests repartidos en siete clases:

| Clase | Tipo | Qué cubre |
|---|---|---|
| `BookServiceImplTest` | Unitario (Mockito) | Libro encontrado, no encontrado (**404**), persistencia, ISBN duplicado (**409**), reserva (incluido el agotado), devolución y alternancia de ambas |
| `BookControllerTest` | Slice web (`@WebMvcTest`) | 200, 404, **409**, 400 con errores por campo, 400 por formato de ISBN, 400 por fecha futura, cabecera `Location` |
| `BookControllerIntegrationTest` | Integración (`@SpringBootTest`) | Recorrido completo controller → service → mapper → repository → H2: alta y lectura, ISBN como clave primaria, 409 sin duplicar, reserva y devolución persistidas de verdad |
| `GlobalExceptionHandlerTest` | Unitario | Que cada excepción se traduzca a su código: 404 de recurso ausente, 409 por duplicado, 409 por falta de ejemplares, 400 de validación con errores por campo, y que una excepción inesperada no filtre su mensaje al cliente |
| `UserServiceImplTest` | Unitario (Mockito) | Usuario encontrado, no encontrado, persistencia, no pérdida de campos, `status` por defecto |
| `UserControllerTest` | Slice web (`@WebMvcTest`) | 200, **404** en recurso inexistente, 400 con errores por campo, 400 por id no positivo, cabecera `Location`, id del cliente descartado |
| `LibraryApiApplicationTests` | Contexto | Carga completa de la aplicación |

Los tests de integración son la única capa que valida lo que los unitarios no
pueden ver: que MapStruct genera la implementación que Spring registra, que el
ISBN se persiste como clave primaria y que el esquema se crea con los tipos
correctos.

### Cobertura (JaCoCo)

```bash
./mvnw test
# reporte en target/site/jacoco/index.html
```

El `jacoco-maven-plugin` (0.8.15) arranca los tests con el agente y genera el
reporte HTML/CSV en cada `test`. Las clases generadas por MapStruct
(`UserMapperImpl`, `BookMapperImpl`) están **excluidas del reporte**:

```xml
<exclude>**/*MapperImpl.class</exclude>
```

La exclusión es por patrón de nombre, no por `@Generated`: MapStruct anota con
`javax.annotation.processing.Generated`, que tiene retención `SOURCE`, así que
**no llega al bytecode** y el filtro por defecto de JaCoCo (que analiza
bytecode) no la ve.

---

## Notas de compilación

Dos detalles del `pom.xml` que no son obvios y conviene no deshacer:

**1. `mapstruct-processor` como dependencia, no en `annotationProcessorPaths`**

El bloque `annotationProcessorPaths` estaba dentro de `spring-boot-maven-plugin`,
donde **solo aplica al goal `repackage`**. Durante la compilación nunca se
ejecutaba, así que MapStruct no generaba `UserMapperImpl` ni `BookMapperImpl` y la
aplicación no arrancaba.

**2. `useIncrementalCompilation=false`**

Con la compilación incremental activada, el goal `test-compile` reescribía
`target/classes/.../UserMapperImpl.class` compilando el fuente generado sin
tener `UserMapper` resoluble. La clase quedaba **sin `implements UserMapper`**
(con el atributo `InconsistentHierarchy`): Spring la registraba como bean pero
sin tipo, y el contexto fallaba con
`No qualifying bean of type 'UserMapper' available`.

---

## Problemas frecuentes

**`No qualifying bean of type 'UserMapper' available`**

Ocurre si se quita `useIncrementalCompilation=false` del `pom.xml`. Limpia y
recompila: `./mvnw clean test`.

**`Cannot invoke "jdk.internal.platform.CgroupInfo.getMountPoint()"`**

Estás con JDK 17.0.2. Cambia a 17.0.10+ (ver [requisitos](#requisitos)).

**`POST /library/books` responde 500 y el body es `Cannot map null into type long`**

El DTO declara un primitivo (`long`) en un campo que el cliente puede omitir.
Jackson falla al deserializar, antes de la validación. Usa el wrapper `Long`
con `@NotNull` en el DTO; es justo lo que hace `BookRequest.availableCopyNumber`
(ver [decisiones de diseño](#long-en-el-dto-long-en-la-entidad)).

**`POST /library/books` responde 400 con `el ISBN-13 introducido no es valido`**

El ISBN tiene 13 dígitos pero el dígito de control no cuadra. Se valida el
formato además de la longitud, así que un dígito mal calculado se rechaza en
lugar de guardarse.

**`POST /library/books` responde 409**

El ISBN ya está dado de alta. Es intencionado: el id lo aporta el cliente y un
alta repetida habría hecho un `merge()` devolviendo 201 sobre un libro existente.

**La app arranca pero no aparecen datos**

H2 es en memoria: se borra al reiniciar.

**`Port 8080 was already in use`**

Otro proceso ocupa el puerto. Cámbialo con
`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`.

---

## Stack tecnológico

| Tecnología | Versión |
|---|---|
| Java | 17 |
| Spring Boot | 4.1.1 |
| Spring Framework | 7.0.9 |
| Spring Data JPA / Hibernate | 7.4.5 |
| MapStruct | 1.6.3 |
| H2 | 2.4.240 |
| JUnit / Mockito / AssertJ | 6.0.3 / 5.23.0 / 3.27.7 |

---

## Referencias

- [Spring Boot Reference](https://docs.spring.io/spring-boot/4.1.1/reference/)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/)
- [MapStruct Reference](https://mapstruct.org/documentation/stable/)
- [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
