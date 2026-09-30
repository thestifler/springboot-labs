# Library API

API REST de usuarios, libros y préstamos para una biblioteca, construida con
Spring Boot.

Gestiona tres recursos: usuarios (`/library/users`), libros (`/library/books`) y
préstamos (`/library/loans`). Los datos se persisten con JPA/Hibernate sobre H2
en memoria.

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
| `PATCH` | `/library/users/{id}/status` | Cambiar el estado de un usuario | 200, 400, 404 |
| `GET` | `/library/users/{id}/loans` | Historial de préstamos de un usuario | 200, 400, 404 |
| `GET` | `/library/books/{isbn}` | Obtener un libro por ISBN-13 | 200, 400, 404 |
| `POST` | `/library/books` | Dar de alta un libro | 201, 400, 409 |
| `POST` | `/library/loans` | Prestar un libro a un usuario | 201, 400, 404, 409 |
| `GET` | `/library/loans/{id}` | Consultar un préstamo | 200, 400, 404 |
| `POST` | `/library/loans/{id}/return` | Devolver un libro | 200, 400, 404, 409 |
| `POST` | `/library/loans/{id}/renew` | Prorrogar un préstamo | 200, 400, 404, 409 |
| `GET` | `/library/loans/overdue` | Préstamos vencidos y sin devolver | 200 |

Todos los errores comparten el mismo cuerpo (`ApiError`): `timestamp`, `status`,
`error`, `message` y `fieldErrors` con el detalle campo a campo en los 400 de
validación.

Los verbos de acción van en la ruta (`/return`, `/renew`) y no en el cuerpo porque
la petición no lleva datos. Son `POST` y no `PUT`/`PATCH` porque no actualizan el
recurso: **añaden un hecho al historial** (el libro vuelve, el plazo se prorroga).

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

#### `PATCH /library/users/{id}/status` — Cambiar el estado

```bash
curl -i -X PATCH http://localhost:8080/library/users/1/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"SUSPENDED"}'
```

**200 OK**

```json
{
  "id": 1,
  "name": "Ana",
  "firstLastName": "Gomez",
  "secondLastName": "Ruiz",
  "status": "SUSPENDED"
}
```

Es `PATCH` y no `PUT` porque solo se modifica **una parte** del recurso: un `PUT`
obligaría al cliente a reenviar el nombre y los apellidos, que no cambian, con
riesgo de perderlos.

El estado es un `enum`, no un `String`: un valor que no existe (`"PENDIENTE"`)
rompe la deserialización y se responde **400**, no 500. El mensaje de Spring no se
reenvía al cliente porque revelaría nombres de clases Java.

Sin este endpoint el enum `UserStatus` sería decorativo: todos los usuarios se
crearían `ACTIVE` y `UserNotActiveException` no se dispararía nunca.

#### `GET /library/users/{id}/loans` — Historial de préstamos

```bash
curl -i http://localhost:8080/library/users/1/loans
```

**200 OK** — del préstamo más reciente al más antiguo, incluidos los ya devueltos

```json
[
  {
    "id": 2,
    "isbn": "9780306406157",
    "userId": 1,
    "loanDate": "2026-09-30",
    "loanDays": 14,
    "dueDate": "2026-10-14",
    "returnedDate": null,
    "renewalCount": 0,
    "overdue": false,
    "daysOverdue": 0
  }
]
```

**404 Not Found** — el usuario no existe. Se comprueba a propósito: una lista vacía
se interpretaría como «no tiene préstamos» cuando en realidad el id es incorrecto.

Vive bajo `/library/users/{id}/loans` y no bajo `/library/loans?userId=` porque
«lo que tiene este usuario» es una relación **del usuario**.

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

`BookService` expone dos operaciones de bajo nivel para mover ejemplares. **No
tienen endpoint HTTP**: son el mecanismo que hay debajo de
`LoanService.lendBook` / `returnBook`, y se prueban directamente contra el service.

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

Esa misma distinción es la que usa `LoanService`: `lendBook` bloquea la fila
porque **resta** y decide; `returnBook` no bloquea porque solo **suma**.

---

## Préstamos

Base: `http://localhost:8080/library/loans`

Para probarlos hace falta un usuario y un libro dados de alta.

### `POST /library/loans` — Prestar un libro

```bash
curl -i -X POST http://localhost:8080/library/loans \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"9780306406157","userId":1}'
```

**201 Created** (con cabecera `Location`)

```
HTTP/1.1 201
Location: /library/loans/1

{
  "id": 1,
  "isbn": "9780306406157",
  "userId": 1,
  "loanDate": "2026-09-30",
  "loanDays": 14,
  "dueDate": "2026-10-14",
  "returnedDate": null,
  "renewalCount": 0,
  "overdue": false,
  "daysOverdue": 0
}
```

`dueDate`, `overdue` y `daysOverdue` son **derivados**: no son columnas de la tabla
(ver [decisiones de diseño](#la-fecha-límite-no-se-guarda-se-deriva)).

| Situación | HTTP | `message` |
|---|---|---|
| El usuario no existe | 404 | `No existe un usuario con id: 999` |
| El ISBN no existe | 404 | `No existe un libro con isbn: ...` |
| El usuario está `INACTIVE`/`SUSPENDED` | 409 | `El usuario con id: 1 esta en estado SUSPENDED y no puede llevarse prestamos` |
| El usuario ya tiene 3 préstamos abiertos | 409 | `... maximo de prestamos abiertos permitido: 3` |
| El libro no tiene ejemplares | 409 | `No quedan ejemplares disponibles del libro con isbn: ...` |
| El usuario ya tiene ese libro prestado | 409 | `El usuario con id: 1 ya tiene prestado el libro con isbn: ...` |

### `POST /library/loans/{id}/return` — Devolver un libro

```bash
curl -i -X POST http://localhost:8080/library/loans/1/return
```

**200 OK** con el préstamo ya cerrado y `returnedDate` informado. El ejemplar
vuelve al contador de disponibles.

| Situación | HTTP |
|---|---|
| El préstamo no existe | 404 |
| El préstamo **ya** estaba devuelto | 409 |

Ese 409 al devolver dos veces es deliberado y es la diferencia más importante de
esta API: devolver es la única operación que **suma** ejemplares. Si el endpoint
fuera idempotente y devolviera 200 en el segundo intento, cada reintento del
cliente inflaría el inventario. Que la entidad sea idempotente
(`markReturned()`) y la API **no** es intencionado: lo primero evita perder datos,
lo segundo evita crear copias fantasma.

### `POST /library/loans/{id}/renew` — Prorrogar un préstamo

```bash
curl -i -X POST http://localhost:8080/library/loans/1/renew
```

**200 OK** con `loanDays` ampliado y `renewalCount` incrementado.

| Situación | HTTP |
|---|---|
| El préstamo no existe | 404 |
| El préstamo ya fue devuelto | 409 |
| El préstamo **ya venció** | 409 |
| Se agotaron las renovaciones | 409 |

No se renueva un préstamo vencido:|Prorrogarlo no reduce la deuda, solo la esconde
más tiempo. Lo que se hace en ese caso es devolverlo y volver a pedirlo, que además
crea un préstamo nuevo con su propio histórico.

El límite es `Loan.MAX_RENEWALS = 2`. Sin él la renovación sería infinita y un
usuario podría retener un libro indefinidamente.

### `GET /library/loans/{id}` — Consultar un préstamo

```bash
curl -i http://localhost:8080/library/loans/1
```

**200 OK** con el mismo cuerpo que devuelve el alta, en el estado actual.

**404 Not Found** — el id no existe.

### `GET /library/loans/overdue` — Préstamos vencidos

```bash
curl -i http://localhost:8080/library/loans/overdue
```

**200 OK** con la lista **del más retrasado al menos reciente**, cada uno con
`overdue: true` y su `daysOverdue`. Responde 200 con `[]` si nadie debe nada.

El orden lo pone la consulta (`order by ... asc`), no un `sort` en memoria: la base
de datos ya tiene que recorrer las filas, ordenarlas allí es gratis y cargarlas
todas para ordenarlas después es lo contrario.

> En cuanto este endpoint crezca habrá que paginarlo, y la forma de hacerlo sin
> tocar el service es empezar ya a paginar en SQL.

---

## Estructura del proyecto

```
src/main/java/library_api/
├── LibraryApiApplication.java          # Punto de entrada
├── controller/
│   ├── LibraryController.java          # Endpoint placeholder (GET /library → "ok")
│   ├── BookController.java             # Endpoints de libro
│   ├── LoanController.java             # Endpoints de préstamo
│   └── UserController.java             # Endpoints de usuario (+ su historial)
├── dto/
│   ├── BookRequest.java                # Entrada (alta de libro)
│   ├── BookResponse.java               # Salida (lectura de libro)
│   ├── LoanRequest.java                # Entrada (préstamo: isbn + userId)
│   ├── LoanResponse.java               # Salida (préstamo, con campos derivados)
│   ├── UserRequest.java                # Entrada (alta). Sin id
│   ├── UserResponse.java               # Salida (lectura). Con id
│   └── UserStatusRequest.java          # Entrada (PATCH de estado). Solo status
├── entity/
│   ├── Book.java                       # Entidad JPA. @Id = ISBN-13
│   ├── Loan.java                       # Entidad JPA de préstamo (N:M con atributos)
│   ├── User.java                       # Entidad JPA
│   └── UserStatus.java                 # Enum de estados
├── exception/
│   ├── ApiError.java                   # Cuerpo uniforme de error
│   ├── BookAlreadyExistsException.java # ISBN duplicado → 409
│   ├── BookNotFoundException.java      # ISBN inexistente → 404
│   ├── GlobalExceptionHandler.java     # Excepciones → respuestas HTTP
│   ├── LoanAlreadyLoanedException.java # Préstamo duplicado → 409
│   ├── LoanAlreadyReturnedException.java # Préstamo ya devuelto → 409
│   ├── LoanLimitExceededException.java # Cupo de préstamos agotado → 409
│   ├── LoanNotFoundException.java      # Préstamo inexistente → 404
│   ├── LoanNotRenewableException.java  # Vencido o sin renovaciones → 409
│   ├── NoAvailableCopiesException.java # Sin ejemplares → 409
│   ├── UserNotActiveException.java     # Usuario no activo → 409 (lleva el estado)
│   └── UserNotFoundException.java      # Usuario inexistente → 404
├── repository/
│   ├── BookRepository.java             # JpaRepository<Book, String>
│   ├── LoanRepository.java             # Consultas de préstamos pendientes/vencidos
│   └── UserRepository.java             # JpaRepository<User, Long>
├── service/
│   ├── BookService.java                # Contrato
│   ├── BookServiceImpl.java            # Implementación
│   ├── LoanService.java                # Contrato
│   ├── LoanServiceImpl.java            # Implementación
│   ├── UserService.java                # Contrato
│   └── UserServiceImpl.java            # Implementación
└── util/mapper/
    ├── BookMapper.java                 # MapStruct (genera BookMapperImpl)
    ├── LoanMapper.java                 # MapStruct (genera LoanMapperImpl)
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

Todo el mapeo pasa por `UserMapper`, `BookMapper` y `LoanMapper` (MapStruct),
declarados con `componentModel = SPRING` para que las implementaciones generadas
se registren como beans. Antes coexistían dos estrategias (el mapper y un factory
estático `UserDto.fromEntity`), que se desincronizaban y perdían campos en
silencio.

`BookMapper` no ignora ningún campo a propósito: el `isbn` es la clave primaria y
llega en el request, así que `BookRequest` y `Book` tienen exactamente los mismos
campos y MapStruct los copia todos en ambos sentidos.

`LoanMapper` solo mapea entidad → DTO (nunca al revés: no existe alta de préstamo
con datos del cliente, se indica isbn y userId). Tres cosas no se copian solas:

- `isbn` y `userId` llevan `@Mapping` explícito porque en la entidad cuelgan de
  `book.isbn` y `user.id`.
- `dueDate` **sí** se copia solo: MapStruct encuentra el getter `getDueDate()` y lo
  llama. Esa es la prueba de que un método derivado puede vivir en la entidad sin
  columna detrás.
- `overdue` y `daysOverdue` llevan `expression`, porque dependen de «hoy» y no son
  atributos: son métodos.

### Excepciones de dominio

`UserService.getUser` lanza `UserNotFoundException` cuando el id no existe, en
lugar de devolver `null`. `GlobalExceptionHandler` la traduce a **404**, junto
con el resto de errores de validación (400) y una red de seguridad para
excepciones inesperadas que no filtra detalles internos al cliente.

Los libros siguen el mismo patrón: `BookNotFoundException` → **404**,
`BookAlreadyExistsException` → **409** y `NoAvailableCopiesException` → **409**.

Los dos 409 son casos distintos y conviene no confundirlos. El duplicado es un
conflicto de identidad: el isbn ya está dado de alta. El de ejemplares es un
conflicto de estado: el isbn es válido y el libro existe, pero no queda ninguno
libre. Por eso el agotamiento **no** es un 404, que sugeriría que el recurso no
existe.

Esta distinción es justamente lo que se perdió cuando `reserveCopy` devolvía un
`boolean`: `false` servía tanto para «no existe» como para «agotado», y el
llamante no tenía forma de saber cuál de los dos había ocurrido salvo por
mirar antes el propio recurso.

Los préstamos añaden cuatro más, y la misma lógica de 404 frente a 409 es lo que
los separa: **404 = no existe; 409 = existe, pero su estado no lo permite.**

| Excepción | HTTP | Cuándo |
|---|---|---|
| `LoanNotFoundException` | 404 | El préstamo no existe |
| `LoanAlreadyLoanedException` | 409 | Ya tiene ese libro prestado y sin devolver |
| `LoanAlreadyReturnedException` | 409 | Se intenta devolver dos veces el mismo préstamo |
| `LoanLimitExceededException` | 409 | Ya tiene 3 préstamos abiertos |
| `LoanNotRenewableException` | 409 | Vencido, o sin renovaciones disponibles |

`LoanAlreadyReturnedException` merece una nota porque parece la excepción más
injusta de la lista: la entidad `markReturned()` **sí** es idempotente, así que
técnicamente la operación podría responder 200 sin hacer nada. Se rechaza esa
opción a propósito. Devolver es la única operación que **suma** ejemplares, así que
un 200 en el segundo intento haría que el cliente creyera que su reintento surtió
efecto cuando en realidad no lo surtió, y si además se hubiera ejecutado el
`UPDATE` el inventario quedaría inflado. La entidad tolera la repetición; la API
no.

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

### El préstamo es una entidad, no un atributo

`Loan` modela la entrega de **una copia** de un libro a un usuario. No se guarda
como atributo de `Book` ni de `User` porque describe una relación N:M **con
atributos**: la misma pareja usuario/libro puede tener varios préstamos a lo
largo del tiempo, y cada uno tiene su propia fecha de salida y su propia
devolución. Meterlo en `Book` borraría el histórico.

Tres decisiones del modelo:

- **Relaciones reales, no ids sueltos.** `Loan` declara `@ManyToOne` a `Book` y a
  `User` en lugar de un `long idUser` y un `String bookIsbn`. Así la base de
  datos genera las claves foráneas y no puede existir un préstamo de un libro o
  de un usuario inexistente. La carga es `LAZY` para no arrastrar el libro
  entero al listar préstamos, y **no** hay `cascade`: borrar un libro no debe
  borrar su histórico de préstamos.
- **La fecha límite no se guarda, se deriva.** `dueDate` es
  `loanDate + loanDays` (un método, sin setter). Almacenarla duplicaría un dato
  que depende de otros dos y podría quedar desincronizada al corregir alguno.
  Que no exista la columna es justamente lo que garantiza la coherencia.
- **El estado sale de `returnedDate`.** No hay enum `LoanStatus`: «devuelto» es
  exactamente `returnedDate != null`, así que es imposible tener un préstamo
  marcado como devuelto con la fecha vacía. A diferencia de `User`, aquí el
  estado **sí** es derivable, y guardar las dos cosas sería redundante.
  `isOverdue()` también es derivado (y compara en sentido estricto, para que devolver
  el libro el día del vencimiento no cuente como retraso).
- **`renewalCount` se guarda; no se deriva.** El límite de renovaciones no puede
  deducirse de las fechas: dos préstamos de 14 días con la misma `loanDate` pueden
  haberse renovado cero o dos veces. Es un contador y no un booleano porque la
  política es «hasta `MAX_RENEWALS` veces».

`markReturned()` es la única vía para cerrar el préstamo y es **idempotente**: si
se invoca dos veces conserva la fecha real de la primera, porque un reintento del
cliente no debe falsear el histórico ni hacer subir el contador de ejemplares dos
veces. `renew(int)` funciona igual: prolonga `loanDays` en vez de recalcular una
fecha, así que el vencimiento derivado no puede desincronizarse.

Ninguna de las dos comprueba si la operación tiene sentido desde el punto de vista
de negocio (no renovar lo vencido, no prestar dos veces el mismo libro): eso es
regla de negocio y vive en `LoanService`, que es quien tiene el contexto y lanza
la excepción de dominio. La entidad solo aplica el cambio de estado.

#### Cómo se detecta un préstamo no devuelto

«No lo ha devuelto» **no es un estado guardado, es la ausencia de fecha**:
`returned_date is null`. Como no hay columna de estado, la condición se apoya
solo en la nulidad de `returnedDate`, y esa es justamente la garantía de que un
préstamo cerrado nunca se confunda con uno pendiente.

`LoanRepository` expone las tres comprobaciones:

| Necesidad | Método |
|---|---|
| Qué libros debe este usuario | `findByUser_IdAndReturnedDateIsNull(userId)` |
| No prestarle dos veces el mismo libro | `existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(userId, isbn)` |
| Préstamos vencidos | `findOverdueOn(hoy)` |
| Historial completo, del más reciente al más antiguo | `findByUser_IdOrderByLoanDateDescIdDesc(userId)` |
| Cupo ocupado por el usuario | `countByUser_IdAndReturnedDateIsNull(userId)` |

El desempate por `id` descendente en el historial no es decorativo: dos préstamos
del mismo día tienen la misma `loanDate`, y sin él el orden de esas dos filas
dependería del plan de ejecución y podría cambiar entre llamadas.

`findOverdueOn` es la que responde a «pasó la fecha y no lo devolvió». Su SQL es
`where returned_date is null and dateadd(day, loan_days, loan_date) < ?`, y las
tres partes importan:

- El `returned_date is null` es lo que excluye los libros devueltos **tarde**: su
  plazo venció, pero ya no deben nada y no pueden salir como vencidos.
- El `dateadd` es el precio de no guardar `dueDate`. Con la fecha límite en
  columna el filtro sería `due_date < ?` y aprovecharía un índice; aquí el cálculo
  va en el SQL sobre dos columnas, así que **no puede usar un índice**. Para una
  biblioteca es irrelevante; si el histórico creciera mucho, la solución es una
  columna `generated` (calculada por la base de datos, que por eso tampoco puede
  desincronizarse) e indexarla.
- La comparación es **estricta** (`<`), igual que en `isOverdue()`: devolver el
  libro el día del vencimiento no es retraso.

El «hoy» se pasa **como parámetro** en lugar de llamar a `LocalDate.now()` dentro
de la consulta: el test deja de depender del reloj y, sobre todo, el service
aplica la misma fecha a todo el cálculo de una petición, de modo que un préstamo
no puede quedar vencido en una consulta y sin vencer en la siguiente.

### Prestar un libro: `LoanService.lendBook`

`LoanServiceImpl.lendBook` valida y escribe dentro de **una única transacción**.
El orden de las comprobaciones no es arbitrario:

1. **Usuario** (`findById`, 404). Se valida primero porque es una búsqueda por
   clave primaria, la más barata, y porque así se evita abrir un
   `SELECT ... FOR UPDATE` sobre el libro para acabar inmediatamente con un 404.
2. **Usuario activo** (409). Puede existir y no poder llevarse préstamos.
3. **Cupo de préstamos abiertos** (409). Se comprueba con un `COUNT`, no cargando
   los préstamos: un `COUNT` resuelve «cuántos tiene abiertos» sin tocar ninguna
   fila de `Loan`. Va **antes** del bloqueo del libro para fallar rápido sin
   retener la fila.
4. **Libro** (`findByIsbnForUpdate`, 404). El bloqueo de fila es lo que hace
   segura la operación leer-modificar-escribir del contador ante dos préstamos
   simultáneos del último ejemplar.
5. **Ejemplares disponibles** (409).
6. **No lo tiene ya prestado** (409). Filtra por `returnedDate is null`, así que
   si ya lo devolvió puede llevárselo otra vez.

Solo después de las seis validaciones se descuenta el contador y se guarda el
`Loan`. Esa es la parte crítica: **ningún camino que falla toca el contador**. Un
409 que hubiera descontado un ejemplar dejaría el contador descuadrado para
siempre, porque el número de ejemplares solo baja y el cliente reintentaría
recibiendo 409 una y otra vez.

| Situación | Excepción | HTTP |
|---|---|---|
| El usuario no existe | `UserNotFoundException` | 404 |
| El libro no existe | `BookNotFoundException` | 404 |
| El usuario está `INACTIVE`/`SUSPENDED` | `UserNotActiveException` | 409 |
| El usuario ya tiene 3 préstamos abiertos | `LoanLimitExceededException` | 409 |
| El libro no tiene ejemplares | `NoAvailableCopiesException` | 409 |
| El usuario ya tiene ese libro prestado | `LoanAlreadyLoanedException` | 409 |

Los 409 reutilizan `NoAvailableCopiesException`, que ya estaba traducida, en vez
de crear excepciones nuevas con el mismo significado. Ninguno es un 400: la
petición es válida (el usuario y el libro existen) y lo que choca es el estado
del recurso.

El cupo (`LoanServiceImpl.MAX_ACTIVE_LOANS = 3`) existe porque el contador de
ejemplares protege **al libro**, pero no deja sitio a los demás usuarios: sin él,
un usuario podría llevarse todas las copias de todos los títulos.

El `loanDate` se fija **una vez** al principio del método y se reutiliza, en lugar
de llamar a `LocalDate.now()` en cada punto: si el préstamo se creara a medianoche
con dos llamadas al reloj, tendría `loanDate` de un día y `dueDate` del siguiente,
con el plazo descuadrado en un día entero.

### Devolver un libro: `LoanService.returnBook`

La operación inversa merece su propia sección porque **es la única que suma
ejemplares**, y sumar es la mitad del peligro.

1. **Préstamo** (`findById`, 404).
2. **No estaba devuelto** (409). A propósito **no** se usa
   `findByIdAndReturnedDateIsNull`: devolvería un `Optional` vacío igual para un
   préstamo inexistente (404) que para uno ya devuelto (409), y el cliente merece
   poder distinguir dos situaciones distintas. Por eso se busca por id y el
   estado se comprueba aparte.
3. `markReturned()` y **después** `increaseAvailableCopyNumber`.

El orden importa: si el `UPDATE` fallara, la transacción haría rollback de las dos
cosas juntas, así que no queda devuelto un préstamo cuyo ejemplar no se ha
reingresado en el inventario.

Aquí **no** hace falta el `FOR UPDATE` que sí exige `lendBook`, porque devolver
solo suma: dos devoluciones concurrentes del mismo libro no pueden restar ni
pisarse, y perder una suma no descuadra el inventario. Es un `UPDATE` atómico, un
solo viaje a la base de datos.

El log de `returnBook` **no** imprime el contador resultante, y no por descuido:
`increaseAvailableCopyNumber` está declarado con `clearAutomatically = true`, así
que el valor que tiene `Book` en memoria es el viejo, no el nuevo. Consultar
`getAvailableCopyNumber()` ahí devolvería un número falso y, peor, inicializaría un
proxy ya desligado de la sesión y reventaría con `LazyInitializationException`.

### N+1 en los listados

Al mapear un préstamo, `LoanMapper` lee `loan.getBook().getIsbn()` y
`loan.getUser().getId()`. Con `@ManyToOne(LAZY)` eso *parece* un N+1, pero no lo
es: **ambos son la clave primaria de su entidad** (`Book.isbn` y `User.id`), y
Hibernate intercepta las llamadas al getter del identificador para devolverlo
desde el proxy **sin inicializarlo**. Un `SELECT` por listar, no uno por préstamo.

Si algún día `LoanResponse` necesitara el título del libro o el nombre del
usuario, la regla sería: `join fetch` en la consulta, nunca inicializar proxies a
mano en el bucle.

### Índices

| Índice | Para qué |
|---|---|
| `idx_loans_user_id` | Préstamos de un usuario |
| `idx_loans_book_isbn` | Préstamos de un libro |
| `idx_loans_user_returned` (`user_id, returned_date`) | La consulta **más frecuente**: préstamos **abiertos** de un usuario |

El compuesto no es un lujo. Con los índices sueltos, esa consulta tiene que
descartar en memoria todas las filas del usuario para quedarse con las que
`returned_date is null`; el compuesto filtra ya en el índice.

`dueDate` **no** se indexa porque no existe como columna (ver
[la fecha límite no se guarda](#la-fecha-límite-no-se-guarda-se-deriva)). El coste
está documentado en `findOverdueOn`.

### Transacciones y logging

- `@Transactional(readOnly = true)` en las lecturas, `@Transactional` en la escritura.
- Logging SLF4J: `debug` al entrar, `info` al crear (con el id generado),
  `warn` cuando no se encuentra el recurso o cuando se rechaza un ISBN duplicado.
- Los tests de persistencia hacen `flush()` + `clear()` antes de releer. Sin el
  `clear()`, `findById` devolvería la entidad cacheada con el valor viejo y el test
  pasaría aunque el `UPDATE` no se hubiera escrito.

### Validación en el borde, no en la entidad

Las restricciones (`@NotBlank`, `@Size`, `@ISBN`, `@NotNull`, `@PastOrPresent`,
`@PositiveOrZero`) viven en `BookRequest`, no en `Book`. Una petición inválida se
rechaza con un 400 y el detalle por campo sin llegar a abrir una transacción
contra la base de datos. En `Book` solo quedan las restricciones de esquema
(`nullable`, `length`), que documentan la forma de la tabla.

Hay una cuarta capa, más adelante y fuera del control de Bean Validation: un
cuerpo que **no se puede convertir** al DTO (JSON mal formado, tipo equivocado,
enum inexistente) falla en Jackson, antes de que el servicio exista. Se traduce a
400 en `handleUnreadableBody`, y no por la red de seguridad porque
`HttpMessageNotReadableException` no implementa `ErrorResponse`: sin ese handler
específico, un `{"status": "PENDIENTE"}` habría devuelto 500.

Y una quinta, ya en el enrutado: `GET /library/loans/no-es-un-numero` falla al
convertir el id de ruta a `Long`. Se traduce a 400 en `handleTypeMismatch`, y
también necesita handler propio porque **en Spring Framework 7
`MethodArgumentTypeMismatchException` ha dejado de implementar `ErrorResponse`**
(ahora hereda de `TypeMismatchException`). Antes de Spring 7 caía en la red de
seguridad y devolvía 500 por algo que es culpa del cliente.

> **Trampa de Spring 6.1+ que rompe `fieldErrors`.** Un `@Valid @RequestBody` puede
> fallar de dos maneras distintas según el método:
> `MethodArgumentNotValidException` (detalle por campo disponible) o
> `HandlerMethodValidationException` (sin detalle). La diferencia la decide si el
> método tiene **alguna restricción propia**: en cuanto existe una —como el
> `@Positive` del id de ruta en `PATCH /library/users/{id}/status`— Spring también
> canaliza por ahí los errores del cuerpo.
>
> Consecuencia observada: `PATCH /library/users/{id}/status` con `{}` devolvía un 400
> con `fieldErrors` vacío y el mensaje `400 BAD_REQUEST "Validation failure"`, mientras
> que `POST /library/users` con el mismo tipo de error sí daba el detalle. Para
> arreglarlo, `handleInvalidMethodArgument` recorre `getBeanResults()` (la única vía
> que devuelve `FieldError`) en vez de copiar `ex.getMessage()`.

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

| Clase | Tipo | Qué cubre |
|---|---|---|
| `BookServiceImplTest` | Unitario (Mockito) | Libro encontrado, no encontrado (**404**), persistencia, ISBN duplicado (**409**), reserva (incluido el agotado), devolución y alternancia de ambas |
| `BookControllerTest` | Slice web (`@WebMvcTest`) | 200, 404, **409**, 400 con errores por campo, 400 por formato de ISBN, 400 por fecha futura, cabecera `Location` |
| `BookControllerIntegrationTest` | Integración (`@SpringBootTest`) | Recorrido completo controller → service → mapper → repository → H2: alta y lectura, ISBN como clave primaria, 409 sin duplicar, reserva y devolución persistidas de verdad |
| `LoanTest` | Unitario | Fecha límite derivada, vencimiento, días de retraso, `markReturned()` idempotente, `renew()` y el límite de renovaciones |
| `LoanRepositoryIntegrationTest` | Integración | Que el HQL se traduzca a SQL válido: filtrado por `returned_date is null`, el `dateadd` de `findOverdueOn` y su orden, y el orden del historial con desempate por id |
| `LoanServiceImplTest` | Unitario (Mockito) | Las seis operaciones del service, los 404 y los 409, y el invariante crítico: **ningún camino que falla toca el contador de ejemplares ni guarda un `Loan`** |
| `LoanServiceIntegrationTest` | Integración | Que el contador baje y suba de verdad en la base de datos, que el cupo se respete, que `dueDate` siga siendo derivado tras renovar, y que la clave foránea impida préstamos huérfanos |
| `LoanControllerIntegrationTest` | Integración | Los seis endpoints de extremo a extremo, los 404/409 por HTTP, que `/overdue` no choque con `/{id}`, y que `SUSPENDED` bloquee de verdad un préstamo posterior |
| `GlobalExceptionHandlerTest` | Unitario | Que cada excepción se traduzca a su código: 404 de recurso ausente, los cinco 409 distintos, 400 de validación con errores por campo, 400 por cuerpo ilegible, y que una excepción inesperada no filtre su mensaje al cliente |
| `UserServiceImplTest` | Unitario (Mockito) | Usuario encontrado, no encontrado, persistencia, no pérdida de campos, `status` por defecto, y `updateUserStatus` (idempotente, no toca los demás campos) |
| `UserControllerTest` | Slice web (`@WebMvcTest`) | 200, **404** en recurso inexistente, 400 con errores por campo, 400 por id no positivo, cabecera `Location`, id del cliente descartado, `PATCH` de estado y el historial |
| `LibraryApiApplicationTests` | Contexto | Carga completa de la aplicación |

Los tests de integración son la única capa que valida lo que los unitarios no
pueden ver: que MapStruct genera la implementación que Spring registra, que el
ISBN se persiste como clave primaria, que el esquema se crea con los tipos
correctos y que los campos **derivados** (`dueDate`, `overdue`, `daysOverdue`)
llegan de verdad al JSON del cliente.

Dos patrones aparecen una y otra vez y conviene no olvidarlos al escribir tests
nuevos:

- **`flush()` + `clear()` antes de releer.** Sin el `clear()`, `findById` devuelve
  la entidad cacheada con el valor viejo y el test pasa aunque el `UPDATE` no se
  hubiera escrito. Afecta sobre todo a `returnBook`, cuyo `UPDATE` además limpia
  la sesión por `clearAutomatically`.
- **Fijar la fecha como parámetro.** `findOverdueOn(hoy)` y `isOverdueOn(fecha)`
  reciben la fecha, así que el test no depende del reloj y no se rompe al cambiar
  de día. Solo los tests que pasan por el service usan `LocalDate.now()`, y aun
  así comparan contra un intervalo (`isBetween`) para no caer en la medianoche.

### Cobertura (JaCoCo)

```bash
./mvnw test
# reporte en target/site/jacoco/index.html
```

El `jacoco-maven-plugin` (0.8.15) arranca los tests con el agente y genera el
reporte HTML/CSV en cada `test`. Las clases generadas por MapStruct
(`UserMapperImpl`, `BookMapperImpl`, `LoanMapperImpl`) están **excluidas del
reporte**:

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

**2. Compilar siempre con `clean`**

`./mvnw test` sin `clean` puede compilar contra clases obsoletas de `target/` y
producir `java.lang.Error: Unresolved compilation problem` en tiempo de ejecución,
con un mensaje que no señala el fichero culpable. Es especialmente traicionero
tras añadir una columna a una entidad, porque el esquema de H2 se regenera pero
las clases viejas siguen ahí. La secuencia fiable es `./mvnw -o clean test`.

**3. `useIncrementalCompilation=false`**

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

**`POST /library/books` responde 400 con `el availableCopyNumber es obligatorio`
pero el JSON sí traía un número de ejemplares**

El campo se renombró de `avaliableCopyNumber` (errata) a `availableCopyNumber`,
y el nombre importa: el JSON es el contrato. Un cliente que aún mande el nombre
antiguo no falla con un «campo desconocido», sino con un `null` silencioso que
la validación reporta como ausente. Si ves este error y estás seguro de haberlo
enviado, casi siempre es que usas el nombre viejo. En la respuesta, `fieldErrors`
solo nombra los campos que Spring sí vio: si el tuyo no aparece ahí, es que
viaja con otro nombre.

**`POST /library/books` responde 500 y el body es `Cannot map null into type long`**

El DTO declara un primitivo (`long`) en un campo que el cliente puede omitir.
Jackson falla al deserializar, antes de la validación. Usa el wrapper `Long`
con `@NotNull` en el DTO; es justo lo que hace `BookRequest.availableCopyNumber`
(ver [decisiones de diseño](#long-en-el-dto-long-en-la-entidad)).

**Un id no numérico en la URL devuelve 500**

Ya no. Antes caía en la red de seguridad porque
`MethodArgumentTypeMismatchException` dejó de implementar `ErrorResponse` en
Spring Framework 7. Ahora devuelve 400 con un mensaje que nombra el parámetro
culpable: `El valor del parametro 'id' no tiene un formato valido`.

**`PATCH /library/users/{id}/status` devuelve 400 pero con `fieldErrors` vacío**

No debería pasar. Si el método del controller tiene alguna restricción propia
(el `@Positive` del id), Spring 6.1+ canaliza también los errores del cuerpo por
`HandlerMethodValidationException` en vez de `MethodArgumentNotValidException`, y
hay que leerlos de `getBeanResults()` (ver
[validación en el borde](#validacion-en-el-borde-no-en-la-entidad)).

**`POST /library/books` responde 400 con `el ISBN-13 introducido no es valido`**

El ISBN tiene 13 dígitos pero el dígito de control no cuadra. Se valida el
formato además de la longitud, así que un dígito mal calculado se rechaza en
lugar de guardarse.

**`POST /library/books` responde 409**

El ISBN ya está dado de alta. Es intencionado: el id lo aporta el cliente y un
alta repetida habría hecho un `merge()` devolviendo 201 sobre un libro existente.

**`POST /library/loans` responde 409 con «ya tiene prestado el libro» pero
acabamos de devolverlo**

El mensaje significa que el préstamo **anterior** sigue abierto, no que este
intento haya fallado. Mira `GET /library/users/{id}/loans`: si hay una entrada con
`returnedDate: null` para ese ISBN, ese es el préstamo abierto que bloquea.

**`PATCH /library/users/{id}/status` responde 500**

Un `status` que no existe en el enum rompe la deserialización. Antes de que
`HttpMessageNotReadableException` tuviera su propio handler, caía en la red de
seguridad y devolvía 500 por un fallo del cliente. Ahora es 400. Si lo que llega es
un cuerpo mal formado en general, la respuesta es la misma.

**`POST /library/loans` responde 409 con «maximo de prestamos abiertos»**

El usuario ya tiene 3 préstamos sin devolver
(`LoanServiceImpl.MAX_ACTIVE_LOANS`). Devolver alguno con
`POST /library/loans/{id}/return` libera el hueco. El contador mira solo los
préstamos **abiertos**: los ya devueltos no ocupan cupo.

**`POST /library/loans/{id}/return` responde 409 y no parece justo**

Es el comportamiento correcto. Devolver es la única operación que **suma**
ejemplares, así que un segundo intento aceptado inflaría el inventario. Si ves el
409, el primer `return` ya se aplicó: consulta `GET /library/loans/{id}` para
comprobarlo.

**`POST /library/loans/{id}/renew` responde 409**

O el préstamo ya venció (primero devuélvelo) o ya consumió sus 2 renovaciones. El
mensaje dice cuál de los dos.

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
| Jackson | 3.x (`tools.jackson.databind`) |

> **Java 17, no 21.** Spring Boot 4 sobre un target de 17 es una combinación
> legítima, pero tiene un efecto visible al escribir tests: `List.getFirst()` y
> `List.getLast()` son API de **Java 21** (`SequencedCollection`) y no compilan
> aquí. Usa `get(0)` y `get(size() - 1)`. Del mismo modo, el
> `HttpMessageNotReadableException` de Spring Framework 7 ya **no** tiene
> constructor de un solo argumento: hay que pasar también el `HttpInputMessage`.

---

## Referencias

- [Spring Boot Reference](https://docs.spring.io/spring-boot/4.1.1/reference/)
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/)
- [MapStruct Reference](https://mapstruct.org/documentation/stable/)
- [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
