package mexibank.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.Email;
import mexibank.domain.user.EmailAlreadyRegisteredException;
import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.PasswordHasher;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserRepository;
import mexibank.domain.user.WeakPasswordException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseImplTest {

    private static final Instant AHORA = Instant.parse("2026-03-01T10:15:30Z");

    private static final Email EMAIL = Email.of("ana@correo.com");

    private static final String CONTRASENA = "UnaContrasenaLarga";

    private static final PasswordHash HASH = PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    private CreateUserUseCaseImpl casoDeUso;

    @BeforeEach
    void preparar() {
        casoDeUso = new CreateUserUseCaseImpl(
                userRepository,
                passwordHasher,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("Alta correcta")
    class AltaCorrecta {

        @Test
        @DisplayName("guarda un usuario con el hash, nunca con la contrasena en claro")
        void guardaElHashNoLaContrasena() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordHasher.hash(CONTRASENA)).thenReturn(HASH);

            casoDeUso.createUser(new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of(Role.CUSTOMER)));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User guardado = captor.getValue();
            assertThat(guardado.getPasswordHash()).isEqualTo(HASH);
            assertThat(guardado.getPasswordHash().value()).doesNotContain(CONTRASENA);
        }

        @Test
        @DisplayName("devuelve el usuario agregado, para que el borde lo projete")
        void devuelveElAgregado() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordHasher.hash(CONTRASENA)).thenReturn(HASH);

            User creado = casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of(Role.CUSTOMER)));

            assertThat(creado.getEmail()).isEqualTo(EMAIL);
            assertThat(creado.isActive()).isTrue();
        }

        @Test
        @DisplayName("nace con la fecha del reloj inyectado, no con la del sistema")
        void usaElRelojInyectado() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordHasher.hash(CONTRASENA)).thenReturn(HASH);

            User creado = casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of(Role.CUSTOMER)));

            assertThat(creado.getCreatedAt()).isEqualTo(AHORA);
            assertThat(creado.getUpdatedAt()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("asigna los roles solicitados, para que se pueda crear un TELLER")
        void asignaLosRolesSolicitados() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordHasher.hash(CONTRASENA)).thenReturn(HASH);

            User creado = casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of(Role.TELLER)));

            assertThat(creado.getRoles()).containsExactly(Role.TELLER);
        }

        @Test
        @DisplayName("sin roles, asigna el rol por defecto, que es CUSTOMER")
        void sinRolesAsignaElRolPorDefecto() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordHasher.hash(CONTRASENA)).thenReturn(HASH);

            User creado = casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of()));

            assertThat(creado.getRoles()).containsExactly(Role.CUSTOMER);
        }

        @Test
        @DisplayName("comprueba el correo antes de hashear, que es la operacion cara")
        void compruebaElCorreoAntesDeHashear() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordHasher.hash(CONTRASENA)).thenReturn(HASH);

            casoDeUso.createUser(new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of(Role.CUSTOMER)));

            // BCrypt cuesta alrededor de 100 ms. Hashear una contrasena que
            // luego se va a descartar por un correo repetido es trabajo perdido,
            // y el orden de las llamadas deja esa decision escrita.
            InOrder orden = inOrder(userRepository, passwordHasher);
            orden.verify(userRepository).existsByEmail(EMAIL);
            orden.verify(passwordHasher).hash(CONTRASENA);
        }
    }

    @Nested
    @DisplayName("Correo ya registrado")
    class CorreoYaRegistrado {

        @Test
        @DisplayName("lanza la excepcion de duplicado, que el borde traduce a 409")
        void lanzaDuplicado() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of(Role.CUSTOMER))))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);
        }

        @Test
        @DisplayName("no hashea ni guarda nada")
        void noLlegaAHashearNiGuardar() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), CONTRASENA, Set.of(Role.CUSTOMER))))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);

            verify(passwordHasher, never()).hash(anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("compara por el correo normalizado, de modo que Ana@Correo.com duplica ana@correo.com")
        void comparaPorElCorreoNormalizado() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> casoDeUso.createUser(
                    new CreateUserCommand("  Ana@Correo.COM  ", CONTRASENA, Set.of(Role.CUSTOMER))))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);

            // Sin normalizar habria dos filas para la misma persona y el
            // "correo ya existe" solo saldria si el cliente tecleara igual que
            // la primera vez.
            verify(userRepository).existsByEmail(EMAIL);
        }
    }

    @Nested
    @DisplayName("Contrasena que no cumple la politica")
    class ContrasenaDebil {

        @Test
        @DisplayName("lanza la excepcion de contrasena debil, que el borde traduce a 400")
        void lanzaContrasenaDebil() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

            assertThatThrownBy(() -> casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), "corta", Set.of(Role.CUSTOMER))))
                    .isInstanceOf(WeakPasswordException.class);
        }

        @Test
        @DisplayName("no guarda nada")
        void noGuarda() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

            assertThatThrownBy(() -> casoDeUso.createUser(
                    new CreateUserCommand(EMAIL.value(), "corta", Set.of(Role.CUSTOMER))))
                    .isInstanceOf(WeakPasswordException.class);

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Correo con formato invalido")
    class CorreoInvalido {

        @Test
        @DisplayName("falla antes de tocar la base de datos")
        void fallaAntesDeConsultar() {
            assertThatThrownBy(() -> casoDeUso.createUser(
                    new CreateUserCommand("no-es-un-correo", CONTRASENA, Set.of(Role.CUSTOMER))))
                    .isInstanceOf(IllegalArgumentException.class);

            // Aqui, a diferencia del login, no hay nada que proteger con
            // equivalencia: el cliente ya sabe que correo ha enviado, asi que
            // puede y debe saber que no tiene un formato valido.
            verify(userRepository, never()).existsByEmail(any());
        }
    }
}
