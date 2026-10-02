# Mexibank

API REST bancaria construida con Spring Boot y **arquitectura hexagonal**.

> **Estado.** El esqueleto hexagonal esta montado y verificado por tests, y ya
> existe el primer modulo vertical completo: **usuarios y autenticacion**.
> Hay agregado `User`, casos de uso de alta y login, persistencia JPA, JWT y
> autorizacion por rol. Todavia **no hay cuentas ni transferencias**: los
> paquetes existen (con su `package-info`), pero no el codigo.

---

## Requisitos

| | |
|---|---|
| **JDK** | 17 |
| Maven | 3.9+ (incluido: `./mvnw`) |
| Docker | Solo para los tests de integracion (`*IT`, con Testcontainers) |

Configura el JDK correcto antes de construir:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/17.0.10-tem
```

---

## Puesta en marcha

```bash
./mvnw clean test        # unitarios + integracion sobre H2 (no requiere Docker)
./mvnw clean verify      # ademas, los *IT contra PostgreSQL en un contenedor (requiere Docker)
./mvnw spring-boot:run   # arranque
```

El `clean` no es opcional. La razon esta en "No qualifying bean of type
'UserMapper' available: no era MapStruct", mas abajo: sin el, Maven puede
ejecutar clases que javac nunca habria generado.

Variables de entorno disponibles en `.env.example`:

```
DB_URL=jdbc:postgresql://localhost:5432/mexibank
DB_USERNAME=mexibank_user
DB_PASSWORD=mexibank_password
SERVER_PORT=8080
JWT_SECRET=<secreto base64>
JWT_EXPIRATION_MINUTES=15
```

> `DB_PASSWORD` **no tiene** valor por defecto: si no se exporta, la conexion
> falla. Es lo unico sensato para una contrasena, y el motivo esta escrito en el
> propio `application.properties`.
>
> `JWT_SECRET` si tiene un valor por defecto, y es un placeholder deliberado
> (`PLACEHOLDER-LOCAL-32-BYTES-...`) para que el proyecto arranque recien clonado.
> **No sirve para produccion**: si no se sobreescribe, todos los despliegues
> comparten la misma clave de firma y cualquiera que lea el repositorio puede
> emitir tokens validos.

---

## Arquitectura

El nucleo (dominio + casos de uso) no conoce Spring, JPA ni HTTP. Todo lo demas
entra por interfaces, y las dependencias apuntan hacia dentro.

```
 ┌───────────────┐            ┌───────────────┐
   │ REST (entrada)│            │ JPA (salida)  │
   │  Controller   │            │  Repository   │
   └───────┬───────┘            └───────▲───────┘
     implementan implementan
           │                              │
           ▼                              │
   ┌───────────────────────────────────────────┐
   │  APPLICATION — casos de uso               │
   │  *UseCaseImpl  @Transactional @Service     │
   └────────────────▲──────────────────────────┘
                    │ usa
   ┌────────────────┴──────────────────────────┐
   │  DOMAIN — Java puro                       │
   │  User, Email, PasswordHash, UserRepository│
   └───────────────────────────────────────────┘
```

### Estructura de paquetes

```
src/main/java/mexibank/
├── MexibankApplication.java               # Punto de entrada (debe quedarse en la raiz)
│
├── domain/                                # ── CENTRO: sin Spring, sin JPA
│   ├── account/                           #    (pendiente)
│   ├── transfer/                          #    (pendiente)
│   ├── shared/                            #    Value objects transversales
│   ├── exception/                         #    Excepciones de dominio (sin semantica HTTP)
│   └── user/                              #    Agregado User + puertos + value objects
│
├── application/                           # ── CASOS DE USO: orquesta, no decide
│   ├── account/                           #    (pendiente)
│   ├── transfer/                          #    (pendiente)
│   ├── auth/                              #    LoginUseCaseImpl
│   └── user/                              #    CreateUserUseCaseImpl
│
└── infrastructure/                        # ── ADAPTADORES: sustituibles
    ├── config/                            #    Beans que no se autodetectan (Clock)
    ├── persistence/                       #    Adaptador de SALIDA (JPA)
    │   ├── entity/                        #      @Entity: modelo de persistencia
    │   ├── springdata/                    #      interfaces JpaRepository + @Query/@Lock
    │   ├── adapter/                       #      implementa los puertos del dominio
    │   └── mapper/                        #      mapeo dominio <-> entidad (MapStruct)
    ├── security/                          #    Adaptador de seguridad
    │   ├── config/                        #      SecurityConfig: cadena y beans
    │   ├── filter/                        #      BearerTokenAuthenticationFilter + handlers
    │   └── support/                       #      JwtTokenIssuer, BCryptPasswordHasher, ...
    └── rest/                              #    Adaptador de ENTRADA
        ├── dto/                           #      Request/Response + validacion
        └── error/                         #      GlobalExceptionHandler + ApiError -> HTTP

src/main/resources/
├── application.properties                 # Configuracion base
├── db/migration/                          # Flyway: dueno del esquema
│   ├── V1__baseline.sql                    #   (vacia: solo fija la linea base)
│   ├── V2__create_users_table.sql          #   users (el rol vivia dentro, ya no)
│   └── V3__roles_table.sql                 #   roles + user_roles (N:M), portable a H2
```

Cada paquete lleva un `package-info.java` que documenta de que puede depender.
No es cosmetico: es lo que hace que git rastree los paquetes vacios y lo que deja
la regla escrita donde se mira. `HexagonalArchitectureTest` falla si un paquete
declarado desaparece.

### Regla de dependencia

Las dependencias apuntan hacia dentro. El dominio no depende de `application`,
ni de `infrastructure`, ni de Spring, ni de `jakarta.persistence`. La
verificacion la hace ArchUnit en `ArchitectureTest` y falla la build.

| Capa | Puede depender de | No puede depender de |
|---|---|---|
| `domain` | Solo del JDK | Todo lo demas |
| `application` | `domain`, Spring (`@Service`, `@Transactional`) | `infrastructure` |
| `infrastructure` | `application`, `domain`, Spring, JPA, HTTP | — |

---

## Usuarios y seguridad

### Endpoints

| Metodo | Ruta | Acceso | Descripcion |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Publico | Devuelve un access token JWT |
| `POST` | `/api/v1/users` | `ADMIN` | Da de alta un usuario |
| `GET` | `/actuator/health`, `/actuator/info` | Publico | Sondas de disponibilidad |
| `GET` | `/actuator/metrics` | Autenticado | Nombres de las metricas de la JVM |

Las tres rutas de actuator salen de una sola propiedad, y por eso estan o no
publicas juntas:

```properties
management.endpoints.web.exposure.include=health,info,metrics
```

Lo que decide quien es publico no es esa linea sino `SecurityConfig`, que solo
deja pasar `health` e `info`. `metrics` responde `401` sin token porque expone
nombres y valores de las metricas de la JVM, y eso describe el estado interno del
proceso: es informacion que un atacante usaria para decidir cuando atacar, no
para hacer su trabajo. Quien tenga cualquier rol valido puede leerla, porque no
revela datos de negocio.

Ademas de lo que hay aqui, la API publica su propio contrato en formato
OpenAPI, navegable e interactivo:

| Ruta | Que es |
|---|---|
| `/swagger-ui.html` | Swagger UI: permite probar los endpoints desde el navegador |
| `/v3/api-docs` | El contrato OpenAPI en JSON, tal cual lo consume Swagger UI |

`/swagger-ui.html` responde `302` y lleva a `/swagger-ui/index.html`, que es la
ruta que sirve la interfaz. El contrato es OpenAPI `3.1.0` y contiene las dos
rutas de negocio y cinco schemas (`LoginRequest`, `CreateUserRequest`,
`LoginResponse`, `UserResponse`, `ApiError`). **Las rutas de actuator no aparecen
ahi**: no son codigo de la aplicacion sino del starter, asi que springdoc no las
documenta y su contrato se describe mas abajo.

Springdoc deduce el contrato de los controladores y de los DTO, y las
anotaciones `@Operation`, `@ApiResponses` y `@Schema` lo enriquecen con las
descripciones, los ejemplos y los cuerpos de error. Ambas rutas son publicas a
proposito: describen como conseguir el token, asi que exigirlas autenticadas
solo serviria para que las lea quien ya conoce la API.

En un despliegue real se apagan sin tocar codigo:

```properties
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

### Login

`POST /api/v1/auth/login` — publico.

`Content-Type: application/json` es obligatorio: cualquier otro devuelve `415`.

Peticion:

```json
{
  "email": "ana@correo.com",
  "password": "UnaContrasenaLarga"
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `email` | string | si | debe tener formato de correo (`@Email`) |
| `password` | string | si | no vacia |

Que la regla sea "no vacia" y no la `PasswordPolicy` de 12 a 72 caracteres es
deliberado: en el login la contrasena solo se compara. Aplicar la politica de
alta impediria iniciar sesion a un usuario cuya contrasena se creo antes de que
esa politica existiera.

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ana@correo.com","password":"UnaContrasenaLarga"}'
```

Respuesta `200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2ZjBh...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "6f0a1c2e-7b3d-4a91-9f52-1c0e8a4d2b77",
    "email": "ana@correo.com",
    "roles": ["CUSTOMER"],
    "active": true,
    "createdAt": "2026-03-01T10:15:30Z"
  }
}
```

| Campo | Tipo | Descripcion |
|---|---|---|
| `accessToken` | string | JWT firmado con HS256, para enviar como `Authorization: Bearer <token>` |
| `tokenType` | string | siempre `Bearer` |
| `expiresIn` | number | segundos de vigencia del token (900 por defecto) |
| `user` | object | datos publicos del usuario; misma forma que devuelve el alta |

Errores: `401` credenciales invalidas (mismo mensaje tanto si el correo no existe como si la contrasena no coincide); `403` cuenta desactivada; `400` correo con formato invalido; `415` `Content-Type` distinto de `application/json`.

Respuestas reales, no de ejemplo:

```json
{"timestamp":"2026-03-01T10:15:31Z","status":401,"error":"Unauthorized","message":"Las credenciales no son validas","fieldErrors":{}}
```

### Alta de usuario

`POST /api/v1/users` — requiere rol `ADMIN`.

Cabeceras de la peticion:

| Cabecera | Valor |
|---|---|
| `Content-Type` | `application/json` (obligatoria) |
| `Authorization` | `Bearer <accessToken>` (obligatoria) |

Si falta el token, la respuesta es `401` e incluye `WWW-Authenticate: Bearer`.
Esa cabecera es la que dice al cliente que esquema usar para reintentar, y por
eso se emite: un `401` sin ella deja al cliente adivinar si debe mandar
`Bearer`, `Basic` o nada.

Peticion:

```json
{
  "email": "nuevo@correo.com",
  "password": "OtraContrasenaLarga",
  "roles": ["TELLER"]
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `email` | string | si | no vacio |
| `password` | string | si | entre 12 y 72 caracteres (`PasswordPolicy`) |
| `roles` | string[] | no | subconjunto de `CUSTOMER`, `TELLER`, `ADMIN`. Ausente o vacio significa `CUSTOMER` |

El rol `ADMIN` se acepta en el campo `roles`, pero no hay ningun endpoint que lo
otorgue: el primer administrador se crea desde la consola. Si se pudiera dar de
alta un `ADMIN` por API, bastaria una llamada para que cualquiera se nombrara
administrador del banco.

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"email":"nuevo@correo.com","password":"OtraContrasenaLarga","roles":["TELLER"]}'
```

Respuesta `201 Created`, con cabecera `Location: /api/v1/users/<id>`:

```json
{
  "id": "6f0a1c2e-7b3d-4a91-9f52-1c0e8a4d2b77",
  "email": "nuevo@correo.com",
  "roles": ["TELLER"],
  "active": true,
  "createdAt": "2026-03-01T10:15:30Z"
}
```

| Campo | Tipo | Descripcion |
|---|---|---|
| `id` | string | UUID del usuario, el mismo que va en `sub` del token |
| `email` | string | correo con el que se puede iniciar sesion |
| `roles` | string[] | roles efectivos, ya resueltos (vacio en la peticion significa `CUSTOMER`) |
| `active` | boolean | siempre `true` en el alta; un usuario nace activo |
| `createdAt` | string | instante del alta en ISO-8601 |

El hash de la contrasena no aparece en ninguna respuesta, ni en el alta ni en el
login. No es una omision: si se filtra, el atacante no necesita crackear nada, solo
copiar el hash y usarlo.

Errores: `400` fallo de validacion o rol desconocido; `401` sin token, token invalido o caducado; `403` sin rol `ADMIN`; `409` correo ya registrado; `415` `Content-Type` distinto de `application/json`.

### Formato de error

Todos los fallos —del controlador, de la validacion o de Spring Security— devuelven el mismo cuerpo (`ApiError`):

```json
{
  "timestamp": "2026-03-01T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Hay campos con formato o valor incorrecto",
  "fieldErrors": {
    "password": "La contrasena debe tener entre 12 y 72 caracteres"
  }
}
```

| Campo | Tipo | Descripcion |
|---|---|---|
| `timestamp` | string | instante del error en ISO-8601 |
| `status` | number | codigo HTTP, repetido en el cuerpo |
| `error` | string | nombre del estado HTTP, por ejemplo `Unauthorized` |
| `message` | string | descripcion legible |
| `fieldErrors` | object | errores por campo; `{}` si el fallo no es de validacion |

| Codigo | Causa |
|---|---|
| `400` | validacion del borde, rol desconocido, JSON ilegible o contrasena que no cumple la politica |
| `401` | credenciales de login invalidas, o token ausente/invalido/caducado |
| `403` | cuenta desactivada, o rol insuficiente para la ruta |
| `409` | el correo ya esta registrado |
| `415` | el `Content-Type` no es `application/json` |
| `500` | fallo no controlado (mensaje generico; el detalle va al log del servidor) |

El `415` merece su propio manejador en `GlobalExceptionHandler`. Sin el, Spring
deja `HttpMediaTypeNotSupportedException` fuera del advice y cae en el
`@ExceptionHandler(Exception.class)`, que responde `500` y registra un error con
pila. Es una respuesta que miente: un `500` dice "el servidor se ha roto, vuelve
a intentarlo", cuando reintentar es imposible porque el problema esta en una
cabecera que controla el cliente. Un fallo de configuracion del cliente es un
error del cliente.

### Sondas (actuator)

Las tres son `GET`, no piden cuerpo y no aceptan parametros. Las que responden
con cuerpo usan el mismo formato que el resto de la API.

`/actuator/health` - publico. Sin token responde solo el veredicto:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

Con un token valido se anaden los componentes, porque lo pides asi:

```properties
management.endpoint.health.show-details=when-authorized
```

Es el punto del equilibrio: el balanceador necesita saber si el proceso
responde, y eso no se le puede preguntar a un endpoint que devuelve `401`.

`/actuator/info` - publico:

```json
{}
```

Vacio, y no por error. Devuelve lo que el plugin `build-info` escriba en
`META-INF/build-info.properties`, y ese plugin no esta declarado. Con solo esto
esta, cualquier dato que se configure ahi sale de verdad.

`/actuator/metrics` - autenticado (cualquier rol). Sin token:

```json
{"timestamp":"2026-03-01T10:15:31Z","status":401,"error":"Unauthorized","message":"No autenticado: falta un token valido en la cabecera Authorization","fieldErrors":{}}
```

Con token devuelve los nombres de las metricas disponibles:

```json
{"names":["jvm.memory.used","jvm.threads.live","http.server.requests"]}
```

El cuerpo de la derecha es una ilustracion del formato; la lista real es mas
larga porque el starter publica lo que encuentra en el classpath.

Las tres rutas cuelgan de `/actuator`, y esa raiz no es publica: sin token
devuelve `401`. Una ruta inexistente tambien devuelve `401` en vez de `404`,
porque `anyRequest().authenticated()` se evalua antes que la busqueda del
controlador. Quien necesite distinguir "no existe" de "no autorizado" lo deduce
del token, no del codigo.

### Como viaja la identidad

1. `POST /auth/login` valida el correo y la contrasena y firma un JWT firmado
   con **HMAC-SHA256**. El token lleva `sub` (el id del usuario), `iss`
   (`mexibank`), `iat`, `exp` y los roles.
2. En cada peticion siguiente, `BearerTokenAuthenticationFilter` lee la cabecera
   `Authorization: Bearer <token>`, verifica la firma y **vuelve a leer el
   usuario de la base de datos**.
3. Con los roles del agregado (no los del token) construye las autoridades y
   deja la peticion autenticada. `authorizeHttpRequests` decide despues.

### Decisiones que no son obvias

**Filtro propio, no `spring-boot-starter-oauth2-resource-server`.** El starter
valida el token y confia en su contenido. Aqui se necesita lo contrario: que el
rol y el estado salgan de la base de datos en cada peticion. Se usa
`spring-security-oauth2-jose` (Nimbus) solo como libreria de firma, no como
starter de recurso.

**Se relee el usuario en cada peticion.** Es el precio de que la revocacion sea
inmediata. Confiar en los roles del token seria mas rapido, pero un usuario
desactivado conservaria el acceso hasta que caducara (15 minutos por defecto).
Para un banco, 15 minutos de acceso a una cuenta bloqueada es un incidente. El
coste es una consulta por peticion autenticada.

**Los roles salen del agregado, no del token.** Un token emitido cuando el
usuario era `ADMIN` deja de servir en cuanto se le retira el rol: la peticion
siguiente relee el agregado y le niega el permiso. Los roles viajan en el token
solo como informacion de emision, no como fuente de autoridad.

**El principal es `AuthenticatedUser` (el id), no el agregado `User`.** El
agregado lleva dentro el hash de la contrasena; si estuviera en el contexto de
seguridad, cualquier log de auditoria o `Authentication.toString()` podria
volcarlo. El contexto solo guarda *quien* es; los permisos ya estan en las
autoridades.

**Un token invalido no corta la cadena.** El filtro deja pasar la peticion sin
autenticar y es el paso de autorizacion el que decide. Si cortara con un 401, un
endpoint publico no cargaria para un cliente con un token caducado.

**El contexto se limpia siempre al terminar.** Spring Security reutiliza hilos de
un pool; sin limpiar, el contexto de una peticion podria filtrarse a la
siguiente y un usuario veria la sesion de otro.

**Ficheros estaticos fuera del filtro.** `shouldNotFilter` excluye `/css`, `/js`,
`/images`, `/webjars` y `/favicon.ico`: cada peticion a un estatico abriria una
consulta a la base de datos para nada.

**HS256 hay que declararlo explicitamente.** `NimbusJwtEncoder` elige `RS256` por
defecto, asi que con una clave simetrica falla con `Failed to select a JWK
signing key`. `JwtTokenIssuer` pasa un `JwsHeader` con `MacAlgorithm.HS256`. El
`JwtEncoder` se construye desde un `ImmutableSecret` (un JWK `oct`), de modo que
migrar a RSA es cambiar la fuente de claves, no la estructura.

**El emisor se valida.** `jwtDecoder` usa
`JwtValidators.createDefaultWithIssuer("mexibank")`. El validador por defecto solo
comprueba las fechas; sin esto, un token de otro sistema con el mismo secreto
compartido valdria.

**Login sin enumeracion de usuarios.** Un correo desconocido y una contrasena
incorrecta producen la misma respuesta. Un correo con formato invalido es un 400
que rechaza el `@Email` del DTO en el borde, antes de llegar al caso de uso.

**Un unico cuerpo de error.** `ApiError` (`timestamp`, `status`, `error`,
`message`, `fieldErrors`) lo usan tanto `GlobalExceptionHandler` como el
`AuthenticationEntryPoint` (401) y el `AccessDeniedHandler` (403). Antes el 401 y
el 403 tenian otra forma, y un cliente tenia que parsear dos contratos.

**Solo access token.** No hay refresh token. Anadirlo es una decision aparte
(rotacion, revocacion, almacenamiento) y no estaba en el alcance.

---

## Decisiones de diseno

### El dominio no depende del modelo de persistencia

`domain.user.User` y `infrastructure.persistence.entity.UserEntity` son **clases
distintas**, y un mapper (MapStruct) traduce entre ellas.

El coste es real: un mapper por agregado y decisiones de fetch a mano. A cambio,
el dominio se testea con JUnit pelado sin levantar Spring, y cambiar JPA por otra
tecnologia no lo toca.

`UserMapper.toDomain` es un metodo `default` escrito a mano que delega en
`UserFactory.construir`; `toEntity` y el volcado si los genera MapStruct. La
razon esta en `UserFactory`: la reconstruccion pasa por `User.reconstitute` (no
revalida) y el volcado se hace **sobre la entidad gestionada**, no sobre una
nueva. Si MapStruct devolviera una entidad nueva en cada actualizacion, Hibernate
no reconoceria la fila y `save` insertaria un duplicado en vez de actualizar.

### `@Service` y `@Transactional` viven en `application`

Es el precio consciente de cablear con Spring en vez de con una configuracion de
multiples `@Bean`. Lo que no se negocia es que **`domain/` no tiene ni una sola
anotacion de Spring**: ahi la pureza se cumple de verdad.

`@Transactional` va en el metodo del caso de uso, nunca en el agregado ni en el
adaptador. Es el caso de uso quien abre la unidad de trabajo; el adaptador solo
participa de la que ya existe.

### El alcance de la unidad de trabajo se nota al persistir

Porque el adaptador no abre transaccion, guardar fuera de un `@Transactional`
deja la entidad desconectada y la modificacion no se escribe. Y porque Spring
Data hace `merge` cuando el id ya viene asignado, la violacion de una clave unica
**no salta en `save` sino al vaciar el contexto** (el commit). Un caso de uso que
confiara en capturarla en el `save` no la veria. Es la razon de la carrera que
`existsByEmail` no cierra del todo: su comprobacion y el `INSERT` no son
atomicos. El test `UserPersistenceIT.elCorreoRepetidoApareceAlFlush` documenta el
comportamiento real.

### Los roles tienen tabla propia, y el alta no los crea

`roles` es un catalogo con `id` y `name` unico. `user_roles` es la tabla
intermedia con `(user_id, role_id)`, y `UserEntity` la declara como
`@ManyToMany` con el lado propietario en el usuario.

Antes el rol era una columna de texto dentro de `user_roles`. Eso era una
relacion sin relacion: el catalogo de roles quedaba duplicado una vez por cada
usuario, y no habia ninguna clave por la que preguntar *quien tiene el rol
TELLER*. Ahora el nombre del rol esta en un solo sitio y la pertenencia es una
referencia.

La consecuencia que importa al dar de alta: **`save` no crea el rol, lo busca**.
`UserPersistenceAdapter` resuelve los nombres del agregado contra la tabla
`roles` (`findAllByNameIn`) y escribe ids. Si un rol del enum no esta en la
tabla, falla con un mensaje que lo dice, en vez de insertar una fila de rol por
sorpresa.

Los ids de los tres roles los siembra la migracion `V3__roles_table.sql` con
literales: la tabla tiene que existir antes de la primera fila de `user_roles`,
y un UUID aleatorio en una migracion no puede devolver el mismo resultado en
PostgreSQL y en H2. Anadir un rol al enum `Role` exige una migracion que lo
inserte; `UserPersistenceIT.todoRolDelDominioExisteEnLaTabla` es el que avisa si
se olvida.

La copia de datos de `V3` usa una tabla intermedia en vez de `ALTER TABLE ...
DROP COLUMN` porque la clave primaria que creo `V2` no tiene nombre explicito, y
el nombre que le pone el motor es distinto en H2 y en PostgreSQL.

### El tiempo se inyecta con `Clock`

El dominio nunca llama a `Instant.now()` ni a `LocalDate.now()`: recibe un
`java.time.Clock` por inyeccion. `ClockConfig` publica el bean. Es una clase del
JDK, asi que no rompe la pureza del dominio, y permite congelar el tiempo en los
tests (`Clock.fixed(...)`) en lugar de depender del reloj del sistema.

### Las excepciones de dominio no conocen HTTP

Viven en `domain.user` y no importan `HttpStatus`. Traducirlas a 401/409 es
trabajo de `infrastructure.rest.error`. Si manana el sistema se expone por Kafka
en vez de por HTTP, las mismas excepciones sirven intactas.

### La politica de contrasenas vive en el dominio

`PasswordPolicy` decide que es una contrasena aceptable; `PasswordHasher` es el
**puerto** que hashea. El dominio no sabe (ni debe) si por debajo hay BCrypt.
`BCryptPasswordHasher` es el adaptador, y su test comprueba que el prefijo
`{bcrypt}` no se pierde y que dos hashes de la misma contrasena son distintos
(salt por invocacion).

### Flyway es el dueno del esquema

`ddl-auto=validate`. Hibernate comprueba que el esquema coincide; no lo crea ni lo
modifica. Un desajuste entre entidad y migracion falla al arrancar, no al
desplegar.

La migracion se escribe **portable** entre PostgreSQL y H2: sin
`gen_random_uuid()` (los ids se generan en Java) ni tipos exclusivos de un motor.
Asi los tests que no usan contenedor arrancan igual.

### No se fija `hibernate.dialect`

Hibernate lo detecta desde el JDBC. Declararlo a mano produce el aviso
`HHH90000025` y, si la version de la base dejara de estar soportada, se pierde
funcionalidad en silencio.

### `spring-boot-flyway` es un modulo en Boot 4

En Spring Boot 4 el soporte de Flyway ya no viene con el starter de JDBC: hay que
anadir `org.springframework.boot:spring-boot-flyway`. Sin el, las migraciones
**nunca se ejecutan** y el arranque falla con
`Schema validation: missing table [roles]`. El fallo aparece como un
problema de esquema, no como un modulo que falta, asi que conviene saberlo.

### Configuracion en `.properties`, y solo una copia

La jerarquia se expresa con puntos en la clave en vez de con indentacion.

El perfil `test` vive **unicamente** en `src/test/resources`. No hay una copia en
`src/main/resources`: si existiera, Spring cargaria las dos y la de test ganaria
siempre por orden de classpath, dejando la otra como codigo muerto que parece
funcionar.

---

## Tests

```bash
./mvnw test      # unitarios + integracion H2 (sin Docker)
./mvnw verify    # + los *IT contra PostgreSQL (con Docker)
```

| Clase | Que comprueba |
|---|---|
| `ArchitectureTest` | Reglas de dependencia (ArchUnit): el dominio no conoce adaptadores, los `@Entity` solo en `persistence.entity`, los `@RestController` solo en `rest`, los `*UseCaseImpl` son `@Service`, y todo puerto tiene adaptador |
| `HexagonalArchitectureTest` | Que los paquetes declarados existan y que ninguna clase quede fuera de las tres capas |
| `EmailTest`, `PasswordHashTest`, `PasswordPolicyTest`, `RoleTest`, `UserIdTest`, `UserTest`, `AccessTokenTest` | Value objects e invariantes del agregado `User`, sin Spring |
| `LoginUseCaseImplTest`, `CreateUserUseCaseImplTest` | Casos de uso con colaboradores simulados (Mockito) y reloj fijo |
| `JwtTokenIssuerTest` | Emision y verificacion HS256 contra Nimbus real: cabecera, claims, expiracion, token manipulado, clave ajena y emisor extrano |
| `BCryptPasswordHasherTest` | Hash y verificacion, salt distinta por invocacion, hash externo, prefijo ausente |
| `RoleAuthorityMapperTest` | Traduccion rol -> `ROLE_*`, orden determinista |
| `BearerTokenAuthenticationFilterTest` | El filtro por separado: a quien autentica, a quien no, estaticos, limpieza del contexto. Sin Spring MVC |
| `SecurityFlowTest` | Flujo completo sobre H2: login, 401/403, alta de usuario, forma de `ApiError` |
| `MexibankApplicationTests` | Que el contexto de Spring se construye (H2, sin Docker) |
| `UserPersistenceIT` | Adaptador contra PostgreSQL real: ida y vuelta, roles, actualizacion frente a insercion, restricciones de clave primaria y FK, y que el catalogo de roles no crece al dar de alta usuarios |
| `AuthSecurityFlowIT` | Seguridad contra PostgreSQL real: revocacion inmediata al desactivar, cambio de roles, reactivacion |

### H2 es solo un respaldo

`application-test.properties` apunta a H2 en memoria para que los tests que no
necesitan base de datos real arranquen sin Docker. **No se usa para probar
persistencia**: H2 miente en bloqueos pesimistas, secuencias y precision de
`numeric`.

### Unitario, integracion H2 e integracion PostgreSQL

Tres niveles con proposito distinto:

- Los `*Test` **unitarios** no levantan contexto.
- Los `*Test` de integracion corren sobre H2 y comprueban el cableado.
- Los `*IT` corren sobre PostgreSQL de verdad y comprueban lo que H2 no puede.

`AbstractPostgresIT` es la base de los `*IT`: levanta un contenedor con
`@ServiceConnection` (Spring lee la url y las credenciales del contenedor) y esta
anotada con `@Testcontainers`. Las dos anotaciones hacen falta: `@Container` es
solo una marca, y sin la extension de JUnit que registra `@Testcontainers` el
contenedor nunca arranca. Como es `@Inherited`, se declara una vez en la base.

### `test` no ejecuta los `*IT`

El plugin `maven-failsafe-plugin` los reserva para `verify`, despues de
`package`. La frontera no es arbitraria: los `*IT` necesitan Docker, y quien no
lo tenga no debe ver fallos que no son del codigo. Ademas, un test de integracion
que pasara sobre un artefacto que no se empaqueta no valdria como senal.

### MockMvc no monta la cadena de seguridad

`MockMvcBuilders.standaloneSetup` y `webAppContextSetup` **no** instalan el filtro
de seguridad. Los tests que comprueban autorizacion tienen que anadir
`.apply(springSecurity())`, o pasarian por el motivo equivocado.

---

## Notas de compilacion

### MapStruct necesita un ajuste en `maven-compiler-plugin`

`annotationProcessorPaths` se declara en `maven-compiler-plugin`, **no** en
`spring-boot-maven-plugin`. Alli solo aplica al goal `repackage`, asi que durante
la compilacion nunca se ejecutaba y MapStruct no generaba ninguna
implementacion.

Compilar siempre con `clean`: `./mvnw clean test`. La razon esta dos secciones
abajo y no es supersticion.

### `No qualifying bean of type 'UserMapper' available`: no era MapStruct

Este fallo salia de forma intermitente y durante mucho se atribuyo a una carrera
de rondas de anotaciones entre Lombok y MapStruct: MapStruct llegaria a
procesar el mapper antes de que Lombok hubiera generado los accesores de `User`
y `UserEntity`, escribiria su stub de error, y javac lo compilaria tal cual.

**Era una atribucion equivocada.** MapStruct no puede producir ese fallo, y hay
dos comprobaciones que lo descartan.

La primera es que javac no acepta codigo que no compila. Compilado con el mismo
classpath, un metodo inexistente da:

```
error: cannot find symbol
```

No existe ninguna via por la que javac produzca una clase con errores dentro, y
por tanto tampoco una por la que compile el stub de MapStruct. La premisa
"javac lo compila tal cual" era falsa.

La segunda es que la clase rota si existia, y llevaba dentro un error de **ECJ**,
el compilador del servidor de lenguaje de Java de VS Code (Eclipse JDT):

```
Unresolved compilation problems:
    The import mexibank.domain cannot be resolved
    UserMapper cannot be resolved to a type
    Role cannot be resolved to a type
```

Esos son errores de un compilador que emite bytecode **pese** al error, y la
clase resultante no implementa la interfaz:

```
public class mexibank.infrastructure.persistence.mapper.UserMapperImpl {
  public UserMapperImpl();
  public UserEntity toEntity(User);
}
```

Por eso Spring recibe un bean sin tipo en vez de fallar al compilar, y por eso el
contexto no arranca.

El mecanismo, paso a paso:

1. `./mvnw clean` borra `target/classes`.
2. El servidor de lenguaje lo nota y empieza a recompilar por su cuenta.
3. ECJ procesa el fuente generado por MapStruct **antes** de que exista
   `target/classes`, de donde vienen `User`, `UserEntity` y `Role`. De ahi los
   `import ... cannot be resolved`.
4. Su clase rota sobrescribe la correcta que javac acababa de escribir.
5. El contexto recibe un bean que no es un `UserMapper`.

Comprobado sin ejecutar Maven: al borrar la clase, tocar el fuente y esperar
cinco segundos, la clase reaparece sola con el error dentro.

Medido con el mismo codigo, el mismo goal y la misma maquina, cambiando solo si
el IDE vigila el directorio (`clean package -DskipTests`, comprobando si la clase
implementa la interfaz):

| Donde | Correctas | Rotas |
|---|---|---|
| En el workspace, con el IDE vigilando | 7 | **5 de 12** |
| Copia aislada fuera del workspace | 12 | **0 de 12** |
| En el workspace, con el autobuild apagado | 12 | **0 de 12** |

La tercera fila es la misma que la primera con el arreglo puesto, y es la que
cierra el diagnostico: cambia solo el ajuste del IDE.

Con `clean test-compile`, que es una fase mas corta, salen 20 de 20 correctas en
los dos sitios: cuanto menos dura la fase, menos se solapa con la recompilacion
del IDE. Eso explica el patron que se habia medido antes, de que "el disparador
es el tiempo que dura la fase", y explica por que nunca se pudo reproducir con
`clean compile`.

Que lo arregla:

- **`./mvnw clean`.** Borra `target/` y obliga a compilar de verdad. Es la
  unica defensa desde Maven.
- **`"java.autobuild.enabled": false`** en el `.vscode/settings.json` de la
  **raiz** del workspace, que aqui es `springboot-labs/`. En el modulo no vale:
  VS Code, en un workspace de carpeta unica, solo lee el de la raiz. Puesto en
  `mexibank/.vscode/settings.json` no tiene ningun efecto, y se comprobo.

Lo segundo ya esta aplicado. Lo que hace no es apagar los underlines de error
ni el autocompletado, sino solo dejar de escribir bytecode: el diagnostique del
IDE sigue funcionando y `target/` pasa a pertenecer unicamente a Maven.

Que NO lo arregla, comprobado uno a uno:

- `useIncrementalCompilation=true`. No significa "recompila siempre", sino
  "cuando algo este obsoleto, recompila todo en vez de la parte que toca". Con
  el y sin el se comparan las mismas fechas, y aqui no hay nada obsoleto: la
  clase del IDE es mas nueva que el fuente, asi que Maven responde "Nothing to
  compile" y ejecuta lo que escribio el IDE.
- `staleMillis`. Es un `int`, y la version 3.15 del plugin lo acepta pero lo
  ignora al decidir.
- `lombok-mapstruct-binding`. Regula el orden de los procesadores de anotaciones
  **de javac**. Como javac nunca tuvo el fallo, no podia arreglarlo, y por eso
  se mantuvo puesto sin que aportara nada.
- Escribir `UserMapperImpl` a mano. Habria quitado el sintoma sin tocar la causa:
  ECJ seguiria compilando lo que encontrara en `target/`, y ademas el fuente
  sintetico de MapStruct ya no existiria, con lo que el problema se habria
  simplesmente escondido. Sigue sin hacerse, porque es un cambio de dependencias
  que no hace falta para arreglar esto.

### Testcontainers 2.x

`PostgreSQLContainer` **ya no es generica**. En la linea 1.x se declaraba
`PostgreSQLContainer<?>`; contra la 2.x no compila y hay que usar
`PostgreSQLContainer` a secas.

### Jackson 3

Spring Boot 4 usa Jackson 3: el paquete es `tools.jackson.databind`, no
`com.fasterxml.jackson.databind`. Una dependencia que arrastre Jackson 2 a mano
deja dos mapeadores distintos conviviendo y los DTOs se serializan con el que no
toca.

### `@MockBean` ya no existe

En Spring Framework 7 se sustituyo por `@MockitoBean`. El `@MockBean` no compila.

---

## Problemas frecuentes

**`No qualifying bean of type 'MiMapper' available`**

Falta `useIncrementalCompilation=false` en el `maven-compiler-plugin`, o se ha
movido `annotationProcessorPaths` al plugin equivocado. Ver [Notas de
compilacion](#mapstruct-necesita-dos-ajustes-en-maven-compiler-plugin).

**`Schema validation: missing table [roles]`**

Falta `org.springframework.boot:spring-boot-flyway`. Sin ese modulo Flyway no
ejecuta las migraciones y Hibernate valida contra una base vacia.

**`Connection to localhost:5432 refused` al arrancar**

No hay PostgreSQL levantado, o las variables `DB_URL`, `DB_USERNAME` y
`DB_PASSWORD` no apuntan al sitio correcto. Revisa `.env.example`.

**`Failed to select a JWK signing key` al hacer login**

El `JwtEncoder` esta firmando con el algoritmo por defecto (`RS256`) sobre una
clave simetrica. Hay que pasar un `JwsHeader` con `MacAlgorithm.HS256`.

**Un endpoint protegido responde 403 a quien deberia dar 401**

El filtro deja pasar la peticion sin autenticar cuando el token no es valido; es
el paso de autorizacion el que responde. Sin credenciales, un endpoint que exige
autenticacion devuelve 401 mediante el `AuthenticationEntryPoint`; con
credenciales insuficientes, 403. Comprobar que el matcher del endpoint este
declarado donde toca.

**El test de contexto pide Docker**

`MexibankApplicationTests` **no** debe extender `AbstractPostgresIT`. Solo los
`*IT` usan Testcontainers.

**`@ServiceConnection` no hace nada**

Sin la anotacion, Spring no lee el contenedor y aplica el `spring.datasource.url`
de `application.properties`, que apunta a la base de datos de desarrollo.

**`ClassNotFoundException` con nombres raros al arrancar los tests**

Un controlador de prueba anotado con `@RestController` fuera de
`infrastructure.rest` rompe `ArchitectureTest`. Los dobles de test no deben
usar anotaciones que la arquitectura reserva a una capa.
