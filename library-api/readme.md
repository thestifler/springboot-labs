# Library API

API REST de usuarios para una biblioteca, construida con Spring Boot.

Gestiona usuarios mediante un recurso `/library/users`: consulta por id y alta.
Los datos se persisten con JPA/Hibernate sobre H2 en memoria.

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

Base: `http://localhost:8080/library/users`

### `GET /library/users/{id}` — Obtener un usuario

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

### `POST /library/users` — Crear un usuario

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

## Estructura del proyecto

```
src/main/java/library_api/
├── LibraryApiApplication.java          # Punto de entrada
├── controller/
│   ├── LibraryController.java          # Endpoint placeholder (GET /library → "ok")
│   └── UserController.java             # Endpoints de usuario
├── dto/
│   ├── UserRequest.java                # Entrada (alta). Sin id
│   └── UserResponse.java               # Salida (lectura). Con id
├── entity/
│   ├── User.java                       # Entidad JPA
│   └── UserStatus.java                 # Enum de estados
├── exception/
│   ├── ApiError.java                   # Cuerpo uniforme de error
│   ├── GlobalExceptionHandler.java     # Excepciones → respuestas HTTP
│   └── UserNotFoundException.java      # Excepción de dominio
├── repository/
│   └── UserRepository.java             # JpaRepository<User, Long>
├── service/
│   ├── UserService.java                # Contrato
│   └── UserServiceImpl.java            # Implementación
└── util/mapper/
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

Todo el mapeo pasa por `UserMapper` (MapStruct), declarado con
`componentModel = SPRING` para que la implementación generada se registre como
bean. Antes coexistían dos estrategias (el mapper y un factory estático
`UserDto.fromEntity`), que se desincronizaban y perdían campos en silencio.

### Excepciones de dominio

`UserService.getUser` lanza `UserNotFoundException` cuando el id no existe, en
lugar de devolver `null`. `GlobalExceptionHandler` la traduce a **404**, junto
con el resto de errores de validación (400) y una red de seguridad para
excepciones inesperadas que no filtra detalles internos al cliente.

### Entidad JPA

- `status` es un **enum** con `@Enumerated(EnumType.STRING)`, no un `String` libre.
- `equals`/`hashCode` usan el patrón recomendado para JPA: identidad por `id` y
  `hashCode` constante. Un `equals` sobre todos los campos (lo que genera
  Lombok con `@Data`) rompe las colecciones de sesión de Hibernate, porque la
  entidad cambia de estado al ser gestionada.

### Transacciones y logging

- `@Transactional(readOnly = true)` en las lecturas, `@Transactional` en la escritura.
- Logging SLF4J: `debug` al entrar, `info` al crear (con el id generado),
  `warn` cuando no se encuentra el recurso.

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

12 tests repartidos en tres clases:

| Clase | Tipo | Qué cubre |
|---|---|---|
| `UserServiceImplTest` | Unitario (Mockito) | Usuario encontrado, no encontrado, persistencia, no pérdida de campos, `status` por defecto |
| `UserControllerTest` | Slice web (`@WebMvcTest`) | 200, **404** en recurso inexistente, 400 con errores por campo, 400 por id no positivo, cabecera `Location`, id del cliente descartado |
| `LibraryApiApplicationTests` | Contexto | Carga completa de la aplicación |

---

## Notas de compilación

Dos detalles del `pom.xml` que no son obvios y conviene no deshacer:

**1. `mapstruct-processor` como dependencia, no en `annotationProcessorPaths`**

El bloque `annotationProcessorPaths` estaba dentro de `spring-boot-maven-plugin`,
donde **solo aplica al goal `repackage`**. Durante la compilación nunca se
ejecutaba, así que MapStruct no generaba `UserMapperImpl` y la aplicación no
arrancaba.

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
