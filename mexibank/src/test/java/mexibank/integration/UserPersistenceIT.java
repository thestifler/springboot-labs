package mexibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mexibank.domain.user.Email;
import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;
import mexibank.domain.user.UserRepository;
import mexibank.infrastructure.persistence.entity.RoleEntity;
import mexibank.infrastructure.persistence.entity.UserEntity;
import mexibank.infrastructure.persistence.springdata.RoleJpaRepository;
import mexibank.infrastructure.persistence.springdata.UserJpaRepository;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests del adaptador de persistencia contra PostgreSQL de verdad.
 *
 * <p><strong>Por que estos tests no pueden correr contra H2.</strong> H2 no
 * comparte con PostgreSQL ni el tipo {@code UUID} nativo, ni la semantica del
 * {@code ON DELETE CASCADE}, ni el mensaje de una violacion de clave unica. Lo que
 * se comprueba aqui es precisamente eso: que el esquema que Flyway crea en
 * PostgreSQL acepta lo que el agregado escribe y que la base de datos impone las
 * restricciones que el dominio da por supuestas.
 *
 * <p><strong>Por que cada test se revierte.</strong> {@code @Transactional} en la
 * clase hace que Spring deshaga los cambios al terminar cada metodo. Estos tests
 * escriben de verdad, y sin la reversion el segundo test encontraria filas del
 * primero y los correos sembrados chocarian con la clave unica. Se prueba el
 * comportamiento real contra la base de datos y, aun asi, cada test empieza en el
 * mismo estado.
 */
@Transactional
class UserPersistenceIT extends AbstractPostgresIT {

    private static final Instant AHORA = Instant.parse("2026-03-01T10:15:30Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserJpaRepository jpaRepository;

    @Autowired
    private RoleJpaRepository roleRepository;

    @Nested
    @DisplayName("Ida y vuelta")
    class IdaYVuelta {

        @Test
        @DisplayName("un usuario guardado se recupera por su id con todos sus datos")
        void seRecuperaPorId() {
            User original = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(original);

            User leido = userRepository.findById(original.getId()).orElseThrow();

            assertThat(leido.getId()).isEqualTo(original.getId());
            assertThat(leido.getEmail()).isEqualTo(original.getEmail());
            assertThat(leido.getPasswordHash()).isEqualTo(original.getPasswordHash());
            assertThat(leido.getRoles()).containsExactly(Role.CUSTOMER);
            assertThat(leido.isActive()).isTrue();
            assertThat(leido.getCreatedAt()).isEqualTo(AHORA);
            assertThat(leido.getUpdatedAt()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("los roles vuelven tal cual, sin perder ni reordenar")
        void losRolesSeConservan() {
            User original = usuario("ana@correo.com", Set.of(Role.ADMIN, Role.TELLER, Role.CUSTOMER));
            userRepository.save(original);

            User leido = userRepository.findById(original.getId()).orElseThrow();

            // El conjunto se compara sin orden porque Set no lo garantiza, pero si
            // se pierde un rol el test falla: eso es lo que se quiere detectar, ya
            // que el rol que falta es el que concede permisos.
            assertThat(leido.getRoles()).containsExactlyInAnyOrder(
                    Role.ADMIN, Role.TELLER, Role.CUSTOMER);
        }

        @Test
        @DisplayName("un usuario inactivo se recupera inactivo, porque el estado viaja en la fila")
        void elEstadoInactivoPersiste() {
            User inactivo = User.reconstitute(
                    UserId.newId(),
                    Email.of("ana@correo.com"),
                    PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv"),
                    Set.of(Role.CUSTOMER),
                    false,
                    AHORA,
                    AHORA);
            userRepository.save(inactivo);

            User leido = userRepository.findById(inactivo.getId()).orElseThrow();

            assertThat(leido.isActive()).isFalse();
            assertThat(leido.canAuthenticate()).isFalse();
        }

        @Test
        @DisplayName("se encuentra por correo, que es como entra el login")
        void seEncuentraPorCorreo() {
            userRepository.save(usuario("ana@correo.com", Set.of(Role.CUSTOMER)));

            assertThat(userRepository.findByEmail(Email.of("ana@correo.com")))
                    .isPresent()
                    .get()
                    .extracting(user -> user.getEmail().value())
                    .isEqualTo("ana@correo.com");
        }

        @Test
        @DisplayName("buscar un correo que no existe devuelve vacio y no lanza")
        void correoInexistente() {
            // El login depende de esto: un correo desconocido no es un error, es
            // la ausencia de usuario, y el caso de uso la traduce a credenciales
            // invalidas.
            assertThat(userRepository.findByEmail(Email.of("nadie@correo.com"))).isEmpty();
        }

        @Test
        @DisplayName("existsByEmail responde sin traer la fila")
        void existePorCorreo() {
            userRepository.save(usuario("ana@correo.com", Set.of(Role.CUSTOMER)));

            assertThat(userRepository.existsByEmail(Email.of("ana@correo.com"))).isTrue();
            assertThat(userRepository.existsByEmail(Email.of("nadie@correo.com"))).isFalse();
        }
    }

    @Nested
    @DisplayName("Modificacion")
    class Modificacion {

        @Test
        @DisplayName("guardar un usuario existente actualiza la fila, no inserta otra")
        void actualizaEnVezDeInsertar() {
            User original = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(original);

            User cambiado = User.reconstitute(
                    original.getId(),
                    original.getEmail(),
                    original.getPasswordHash(),
                    Set.of(Role.TELLER),
                    true,
                    original.getCreatedAt(),
                    AHORA.plusSeconds(60));
            userRepository.save(cambiado);

            User leido = userRepository.findById(original.getId()).orElseThrow();
            assertThat(leido.getRoles()).containsExactly(Role.TELLER);
            assertThat(leido.getUpdatedAt()).isEqualTo(AHORA.plusSeconds(60));
            // La fecha de alta no se toca: es updatable = false y el dominio no
            // tiene forma de cambiarla.
            assertThat(leido.getCreatedAt()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("guardar dos veces el mismo usuario no duplica filas ni roles")
        void guardarDosVecesNoDuplica() {
            User original = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(original);
            userRepository.save(original);

            User leido = userRepository.findById(original.getId()).orElseThrow();
            assertThat(leido.getRoles()).containsExactly(Role.CUSTOMER);
        }

        @Test
        @DisplayName("quitar un rol de la coleccion lo quita de la tabla")
        void quitarUnRolLoBorraDeLaTabla() {
            // Es el caso que distingue actualizar de insertar. Con un mapeo que
            // devolviera una entidad nueva, el rol quitado seguiria en la tabla y
            // el usuario conservaria un permiso que el dominio ya le retiro.
            User original = usuario("ana@correo.com", Set.of(Role.ADMIN, Role.TELLER));
            userRepository.save(original);

            User sinAdmin = User.reconstitute(
                    original.getId(),
                    original.getEmail(),
                    original.getPasswordHash(),
                    Set.of(Role.TELLER),
                    true,
                    original.getCreatedAt(),
                    AHORA.plusSeconds(60));
            userRepository.save(sinAdmin);

            assertThat(userRepository.findById(original.getId()).orElseThrow().getRoles())
                    .containsExactly(Role.TELLER);
        }
    }

    @Nested
    @DisplayName("Restricciones de la base de datos")
    class Restricciones {

        @Test
        @DisplayName("no admite dos usuarios con el mismo correo")
        void elCorreoEsUnico() {
            // El caso de uso comprueba antes con existsByEmail, pero entre la
            // comprobacion y el insert caben dos peticiones simultaneas. La clave
            // unica de la tabla es lo que impide que las dos entren: sin ella, el
            // "correo ya registrado" tendria una carrera.
            //
            // Las dos filas se escriben con SQL y no con el adaptador porque,
            // cuando el id viene asignado, Spring Data hace merge y difiere el
            // INSERT al flush: la restriccion no se comprobaria todavia. Este test
            // mide la restriccion; el siguiente mide cuando se manifiesta a traves
            // del adaptador.
            insertarUsuario("ana@correo.com");

            assertThatThrownBy(() -> insertarUsuario("ana@correo.com"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        /**
         * Inserta una fila en {@code users} directamente, saltandose el ORM.
         *
         * @param correo correo del usuario
         */
        private void insertarUsuario(String correo) {
            jdbcTemplate.update(
                    "INSERT INTO users (id, email, password_hash, enabled, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    java.util.UUID.randomUUID(),
                    correo,
                    "{bcrypt}$2a$10$abcdefghijklmnopqrstuv",
                    true,
                    java.sql.Timestamp.from(AHORA),
                    java.sql.Timestamp.from(AHORA));
        }

        @Test
        @DisplayName("por el adaptador, el correo repetido se manifiesta al vaciar el contexto, no al guardar")
        void elCorreoRepetidoApareceAlFlush() {
            // Documenta el comportamiento real que el caso de uso tiene que
            // conocer: save() con un id ya asignado hace merge y deja el INSERT
            // para el flush. Un caso de uso que confiara en que la excepcion sale
            // en el save no la veria, y el correo duplicado se descubriria al
            // cerrar la transaccion, lejos del punto donde se origino.
            User primero = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(primero);

            UserEntity duplicado = userRepository.findById(primero.getId())
                    .map(user -> {
                        UserEntity entidad = new UserEntity();
                        entidad.setId(java.util.UUID.randomUUID());
                        entidad.setEmail("ana@correo.com");
                        entidad.setPasswordHash(user.getPasswordHash().value());
                        entidad.setEnabled(true);
                        entidad.setCreatedAt(AHORA);
                        entidad.setUpdatedAt(AHORA);
                        entidad.setRoles(Set.of(rolDelCatalogo(Role.TELLER)));
                        return entidad;
                    })
                    .orElseThrow();

            assertThatThrownBy(() -> jpaRepository.saveAndFlush(duplicado))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("la clave primaria compuesta impide el mismo rol dos veces para un usuario")
        void elRolNoSeDuplica() {
            User user = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(user);

            // El agregado ya garantiza el conjunto sin duplicados, pero la tabla
            // no depende de eso: la clave primaria (user_id, role_id) es lo que lo
            // impide aunque otra via escriba en ella. Se inserta a mano con SQL,
            // saltandose el ORM, porque es la unica forma de construir el estado
            // que la restriccion debe rechazar.
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                    user.getId().value(),
                    idDelRol(Role.CUSTOMER)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("el borrado de un usuario arrastra sus roles, sin dejar filas huerfanas")
        void elBorradoEnCascadaFunciona() {
            User user = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(user);

            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId().value());

            // El ON DELETE CASCADE esta en la migracion y es el que evita que la
            // tabla intermedia acumule filas de usuarios que ya no existen. Se
            // comprueba con SQL porque el puerto del dominio no ofrece borrar.
            Integer restantes = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = ?",
                    Integer.class,
                    user.getId().value());
            assertThat(restantes).isZero();
        }

        @Test
        @DisplayName("borrar un usuario no borra su rol del catalogo, porque el rol no es suyo")
        void elBorradoDelUsuarioNoTocaElCatalogo() {
            User user = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(user);

            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId().value());

            // El catalogo es compartido: CUSTOMER lo tienen todos los clientes, asi
            // que borrarlo junto al usuario dejaria al resto apuntando a una fila que
            // ya no existe. El ON DELETE CASCADE va de users a user_roles, nunca de
            // user_roles a roles.
            Integer restantes = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM roles WHERE name = ?",
                    Integer.class,
                    Role.CUSTOMER.name());
            assertThat(restantes).isEqualTo(1);
        }

        @Test
        @DisplayName("una fila de user_roles no puede apuntar a un role_id que no esta en el catalogo")
        void elRolTieneQueExistirEnElCatalogo() {
            User user = usuario("ana@correo.com", Set.of(Role.CUSTOMER));
            userRepository.save(user);

            // La FK fk_user_roles_role convierte "este usuario tiene el rol CUSTOMER"
            // en una afirmacion que la base de datos puede comprobar. Con el modelo
            // anterior el nombre viajaba dentro de la propia fila, de modo que un rol
            // escrito a mano y sin existir en ninguna parte era indistinguible de uno
            // legitimo.
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                    user.getId().value(),
                    java.util.UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Catalogo de roles")
    class CatalogoDeRoles {

        @Test
        @DisplayName("dar de alta un usuario no da de alta su rol")
        void elAltaNoCreaRoles() {
            Integer antes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);

            userRepository.save(usuario("ana@correo.com", Set.of(Role.CUSTOMER, Role.TELLER)));
            userRepository.save(usuario("beto@correo.com", Set.of(Role.TELLER)));

            // El volcado a user_roles lo difiere Hibernate hasta el cierre de la
            // transaccion, asi que sin este flush el recuento seria 0 y el test
            // mediria el momento del volcado, no el esquema.
            jpaRepository.flush();

            // El catalogo no crece: lo que se escribe es una fila en la tabla
            // intermedia por cada pareja, no una copia del rol.
            Integer despues = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
            assertThat(despues).isEqualTo(antes);

            Integer filas = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_roles", Integer.class);
            assertThat(filas).isEqualTo(3);
        }

        @Test
        @DisplayName("cada rol del dominio tiene su fila en el catalogo")
        void todoRolDelDominioExisteEnLaTabla() {
            // El enum Role y la tabla roles estan duplicados a proposito: el enum es
            // el vocabulario que el codigo conoce y la tabla es el que se puede
            // consultar y auditar. El precio de esa duplicacion es este test: si
            // alguien anade una constante al enum y olvida la migracion que siembra
            // el catalogo, el alta de un usuario con ese rol falla al desplegar. Que
            // falle ahi, y no con una fila de rol creada por sorpresa.
            for (Role rol : Role.values()) {
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM roles WHERE name = ?",
                        Integer.class,
                        rol.name()))
                        .as("el rol %s esta en el enum pero no en la tabla roles", rol)
                        .isEqualTo(1);
            }
        }

        @Test
        @DisplayName("el catalogo no admite dos filas con el mismo nombre")
        void elNombreDelRolEsUnico() {
            // Sin esta unicidad, dos filas podrian llamarse CUSTOMER y el catalogo
            // dejaria de ser un catalogo: la tabla intermedia apuntaria a un id
            // distinto segun quien la escribio, y "cual es el id de CUSTOMER" tendria
            // dos respuestas.
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO roles (id, name) VALUES (?, ?)",
                    java.util.UUID.randomUUID(),
                    Role.CUSTOMER.name()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("el alta escribe solo en user_roles, y con el id del rol del catalogo")
        void elAltaEscribeEnLaTablaIntermedia() {
            User original = usuario("ana@correo.com", Set.of(Role.ADMIN, Role.TELLER));
            userRepository.save(original);
            jpaRepository.flush();

            // La fila de la tabla intermedia no lleva el nombre del rol: lleva su id.
            // Es lo que hace que "este usuario es TELLER" sea una relacion con una
            // tabla, y no una cadena comparada con el enum en cada lectura.
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_id = ?",
                    Integer.class,
                    original.getId().value(),
                    idDelRol(Role.ADMIN)))
                    .isEqualTo(1);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_id = ?",
                    Integer.class,
                    original.getId().value(),
                    idDelRol(Role.CUSTOMER)))
                    .isZero();
        }

        @Test
        @DisplayName("quitarle un rol a un usuario deja el rol en el catalogo")
        void quitarUnRolNoBorraElDelCatalogo() {
            User original = usuario("ana@correo.com", Set.of(Role.ADMIN, Role.TELLER));
            userRepository.save(original);

            User sinAdmin = User.reconstitute(
                    original.getId(),
                    original.getEmail(),
                    original.getPasswordHash(),
                    Set.of(Role.TELLER),
                    true,
                    original.getCreatedAt(),
                    AHORA.plusSeconds(60));
            userRepository.save(sinAdmin);

            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class))
                    .isEqualTo(Role.values().length);
        }
    }

    /**
     * Id con el que la migracion sembro un rol del catalogo.
     *
     * <p>Se lee de la base de datos en vez de escribir el literal en el test: asi el
     * test no depende de los ids concretos que elija la migracion, que pueden cambiar
     * sin que cambie el comportamiento.
     *
     * @param rol rol del que se quiere el id
     * @return identificador de su fila en {@code roles}
     */
    private java.util.UUID idDelRol(Role rol) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ?",
                java.util.UUID.class,
                rol.name());
    }

    /**
     * Entidad del catalogo que corresponde a un rol.
     *
     * <p>Se busca en la base de datos por la misma razon que {@link #idDelRol}: para
     * montar el estado de una entidad a mano hay que partir de la fila real, no de una
     * entidad inventada con el id a null, que es justo lo que el modelo normalizado
     * ya no permite escribir.
     *
     * @param rol rol buscado
     * @return entidad del catalogo
     */
    private RoleEntity rolDelCatalogo(Role rol) {
        return roleRepository.findAllByNameIn(Set.of(rol)).get(0);
    }

    private User usuario(String correo, Set<Role> roles) {
        return User.register(
                UserId.newId(),
                Email.of(correo),
                PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv"),
                roles,
                AHORA);
    }
}
