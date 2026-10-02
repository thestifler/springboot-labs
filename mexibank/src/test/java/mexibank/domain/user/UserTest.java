package mexibank.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class UserTest {

    /**
     * Instante fijo para todos los tests.
     *
     * <p>Una constante y no {@code Instant.now()}: los tests que comprueban fechas
     * fallan a medianoche o en el instante en que cambia el dia, y ese fallo no
     * dice nada sobre el codigo.
     */
    private static final Instant AHORA = Instant.parse("2026-03-01T10:15:30Z");

    private static final UserId ID = UserId.from(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private static final Email EMAIL = Email.of("ana@correo.com");

    private static final PasswordHash HASH = PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");

    private static final PasswordHash OTRO_HASH = PasswordHash.of("{bcrypt}$2a$10$zyxwvutsrqponmlkjihgfedcba");

    private Clock reloj;

    @BeforeEach
    void prepararReloj() {
        reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("nace activo, con los roles indicados y con las dos fechas iguales al instante del alta")
        void registraElAltaCompleta() {
            User user = User.register(ID, EMAIL, HASH, Set.of(Role.CUSTOMER), reloj.instant());

            assertThat(user.getId()).isEqualTo(ID);
            assertThat(user.getEmail()).isEqualTo(EMAIL);
            assertThat(user.getPasswordHash()).isEqualTo(HASH);
            assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
            assertThat(user.isActive()).isTrue();
            assertThat(user.canAuthenticate()).isTrue();
            assertThat(user.getCreatedAt()).isEqualTo(AHORA);
            assertThat(user.getUpdatedAt()).isEqualTo(AHORA);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("no admite un usuario sin roles, porque no podria hacer nada")
        void exigeAlMenosUnRol(Set<Role> roles) {
            assertThatThrownBy(() -> User.register(ID, EMAIL, HASH, roles, reloj.instant()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("al menos un rol");
        }

        @Test
        @DisplayName("copia el conjunto de roles: modificar el original no toca el usuario")
        void copiaElConjuntoDeRoles() {
            Set<Role> roles = new HashSet<>(Set.of(Role.CUSTOMER));
            User user = User.register(ID, EMAIL, HASH, roles, reloj.instant());

            roles.add(Role.ADMIN);

            assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        }
    }

    @Nested
    @DisplayName("Reconstruccion")
    class Reconstruccion {

        @Test
        @DisplayName("reconstitute no revalida el alta: un usuario antiguo con una contrasena corta sigue cargando")
        void noRevalidaLasReglasDelAlta() {
            // Hash de una contrasena de 4 caracteres, que hoy no cumpliria la
            // politica. Si la rehidratacion pasara por register(), este usuario
            // quedaria sin poder entrar el dia que la politica se endureciera.
            PasswordHash hashAntiguo = PasswordHash.of("{bcrypt}$2a$10$corto");

            User user = User.reconstitute(ID, EMAIL, hashAntiguo,
                    Set.of(Role.CUSTOMER), true, AHORA.minusSeconds(86_400), AHORA);

            assertThat(user.getPasswordHash()).isEqualTo(hashAntiguo);
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("reconstitute preserva un usuario desactivado")
        void preservaElEstadoDesactivado() {
            User user = User.reconstitute(ID, EMAIL, HASH,
                    Set.of(Role.CUSTOMER), false, AHORA, AHORA);

            assertThat(user.canAuthenticate()).isFalse();
        }
    }

    @Nested
    @DisplayName("Cambio de contrasena")
    class CambioDeContrasena {

        @Test
        @DisplayName("sustituye el hash y mueve la fecha de modificacion")
        void cambiaElHash() {
            User user = usuarioDePrueba();
            Instant despues = AHORA.plus(Duration.ofMinutes(5));

            user.changePassword(OTRO_HASH, HASH, despues);

            assertThat(user.getPasswordHash()).isEqualTo(OTRO_HASH);
            assertThat(user.getUpdatedAt()).isEqualTo(despues);
        }

        @Test
        @DisplayName("no mueve la fecha de alta, que es inmutable")
        void noTocaLaFechaDeAlta() {
            User user = usuarioDePrueba();

            user.changePassword(OTRO_HASH, HASH, AHORA.plusSeconds(60));

            assertThat(user.getCreatedAt()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("rechaza el cambio si el hash actual no es el que se cree correcto")
        void exigeElHashActualCorrecto() {
            User user = usuarioDePrueba();

            assertThatThrownBy(() ->
                    user.changePassword(OTRO_HASH, PasswordHash.of("{bcrypt}$2a$10$otro"), AHORA))
                    .isInstanceOf(InvalidCurrentPasswordException.class);
        }

        @Test
        @DisplayName("no deja cambiar la contrasena por la misma, para no fingir un cambio que no ocurre")
        void rechazaLaMismaContrasena() {
            User user = usuarioDePrueba();

            assertThatThrownBy(() -> user.changePassword(HASH, HASH, AHORA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("distinta");
        }
    }

    @Nested
    @DisplayName("Cambio de roles")
    class CambioDeRoles {

        @Test
        @DisplayName("sustituye el conjunto completo y mueve la fecha de modificacion")
        void reemplazaLosRoles() {
            User user = usuarioDePrueba();
            Instant despues = AHORA.plus(Duration.ofMinutes(1));

            user.replaceRoles(Set.of(Role.TELLER), despues);

            assertThat(user.getRoles()).containsExactly(Role.TELLER);
            assertThat(user.getUpdatedAt()).isEqualTo(despues);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("no deja quedarse sin roles")
        void exigeConservarAlMenosUnRol(Set<Role> roles) {
            User user = usuarioDePrueba();

            assertThatThrownBy(() -> user.replaceRoles(roles, AHORA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("al menos un rol");
        }

        @Test
        @DisplayName("un usuario no puede quedarse sin roles, ni siquiera con un cambio rechazado")
        void elInvarianteViveEnElAgregado() {
            User user = usuarioDePrueba();

            assertThatThrownBy(() -> user.replaceRoles(Set.of(), AHORA))
                    .isInstanceOf(IllegalArgumentException.class);

            // El agregado valida antes de tocar el estado. Si hubiera asignado el
            // conjunto vacio y despues hubiera lanzado, el usuario habria quedado
            // sin roles, que es justo el estado que el invariante prohibe.
            assertThat(user.getRoles()).containsExactly(Role.CUSTOMER);
        }
    }

    @Nested
    @DisplayName("Inmutabilidad de lo que no se toca")
    class Inmutabilidad {

        @ParameterizedTest
        @EnumSource(Role.class)
        @DisplayName("getRoles devuelve un conjunto inmutable")
        void getRolesEsInmutable(Role rol) {
            User user = usuarioDePrueba();

            assertThatThrownBy(() -> user.getRoles().add(rol))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("el correo no tiene setter: cambiarlo obligaria a reidentificarse")
        void elCorreoNoCambia() {
            assertThat(User.class.getDeclaredMethods())
                    .noneMatch(m -> m.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains("setemail"));
        }

        @Test
        @DisplayName("el hash solo cambia a traves de changePassword, que comprueba la contrasena actual")
        void elHashSoloCambiaPorChangePassword() {
            assertThat(User.class.getDeclaredMethods())
                    .filteredOn(m -> m.getName().toLowerCase(java.util.Locale.ROOT).startsWith("set"))
                    .noneMatch(m -> m.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains("passwordhash"));
        }
    }

    private User usuarioDePrueba() {
        return User.register(ID, EMAIL, HASH, Set.of(Role.CUSTOMER), reloj.instant());
    }
}