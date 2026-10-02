package mexibank.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import mexibank.domain.user.AccessToken;
import mexibank.domain.user.Email;
import mexibank.domain.user.InvalidCredentialsException;
import mexibank.domain.user.LoginCommand;
import mexibank.domain.user.LoginResult;
import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.PasswordHasher;
import mexibank.domain.user.Role;
import mexibank.domain.user.TokenIssuer;
import mexibank.domain.user.User;
import mexibank.domain.user.UserDeactivatedException;
import mexibank.domain.user.UserId;
import mexibank.domain.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests del caso de uso de login.
 *
 * <p><strong>Por que Mockito y no un contexto de Spring.</strong> Lo que importa
 * aqui es que el caso de usoema lo que se le pide a sus puertos, no que Spring
 * sepa cablearlo. Un contexto completo introduciria la base de datos y el
 * hasher real, y un fallo seeria dificil de atribuir: si el login falla, no se
 * sabria si es la orquestacion o el adaptador. Con mocks, cada asercion es sobre
 * una llamada concreta.
 *
 * <p><strong>Por que el reloj es fijo.</strong> El TTL del token es una decision
 * de negocio y se comprueba como argumento del {@code issue}. Con un reloj
 * inyectado el valor es exacto y el test no depende de la hora.
 */
@ExtendWith(MockitoExtension.class)
class LoginUseCaseImplTest {

    private static final Instant AHORA = Instant.parse("2026-03-01T10:15:30Z");

    private static final Email EMAIL = Email.of("ana@correo.com");

    private static final PasswordHash HASH = PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");

    private static final String CONTRASENA = "UnaContrasenaLarga";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TokenIssuer tokenIssuer;

    private LoginUseCaseImpl casoDeUso;

    @BeforeEach
    void preparar() {
        casoDeUso = new LoginUseCaseImpl(
                userRepository,
                passwordHasher,
                tokenIssuer,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("Login correcto")
    class LoginCorrecto {

        @Test
        @DisplayName("devuelve el token emitido y el usuario que se ha autenticado")
        void devuelveTokenYUsuario() {
            User user = usuarioActivo();
            String token = "eyJhbGciOiJIUzI1NiJ9.cuerpo.firma";
            prepararEmision(user, token);

            LoginResult result = casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA));

            assertThat(result.token()).isEqualTo(token);
            assertThat(result.user()).isSameAs(user);
        }

        @Test
        @DisplayName("pide el token con 15 minutos de vigencia, el TTL de negocio")
        void pideElTokenConElTtlDeNegocio() {
            User user = usuarioActivo();
            prepararEmision(user, "token");

            casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA));

            verify(tokenIssuer).issue(user, Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("busca por el correo ya normalizado, no por el texto que envio el cliente")
        void buscaPorElCorreoNormalizado() {
            User user = usuarioActivo();
            prepararEmision(user, "token");

            casoDeUso.login(new LoginCommand("  Ana@Correo.COM  ", CONTRASENA));

            // Si se buscara por la cadena cruda, dos escrituras del mismo correo
            // darian usuarios distintos y el login dependeria de como lo escribio
            // el usuario ese dia.
            verify(userRepository).findByEmail(EMAIL);
        }

        @Test
        @DisplayName("devuelve la caducidad que trae el token, no una calculada aparte")
        void devuelveLaCaducidadDelToken() {
            User user = usuarioActivo();
            Instant caducidad = AHORA.plus(Duration.ofMinutes(15));
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(CONTRASENA, HASH)).thenReturn(true);
            when(tokenIssuer.issue(eq(user), any(Duration.class)))
                    .thenReturn(new AccessToken("token", user.getId(), user.getRoles(),
                            caducidad, AHORA));

            LoginResult result = casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA));

            assertThat(result.expiresAt()).isEqualTo(caducidad);
        }
    }

    @Nested
    @DisplayName("Fallo por correo desconocido")
    class CorreoDesconocido {

        @Test
        @DisplayName("lanza credenciales invalidas, la misma excepcion que una contrasena incorrecta")
        void lanzaCredencialesInvalidas() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("no llega a comprobar la contrasena ni a emitir token")
        void noSigueAdelante() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)))
                    .isInstanceOf(InvalidCredentialsException.class);

            // Un atacante que recorre correos veria como cada consulta fallida
            // cuesta un hasheo de BCrypt si se verificara la contrasena. Dejar
            // el hasheo fuera es lo que hace que el tiempo de respuesta no
            // distinga "no existe" de "existe pero la contrasena no es la buena".
            verify(passwordHasher, never()).matches(anyString(), any());
            verify(tokenIssuer, never()).issue(any(), any());
        }
    }

    @Nested
    @DisplayName("Fallo por contrasena incorrecta")
    class ContrasenaIncorrecta {

        @Test
        @DisplayName("lanza credenciales invalidas")
        void lanzaCredencialesInvalidas() {
            User user = usuarioActivo();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(CONTRASENA, HASH)).thenReturn(false);

            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("no emite ningun token")
        void noEmiteToken() {
            User user = usuarioActivo();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(CONTRASENA, HASH)).thenReturn(false);

            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(tokenIssuer, never()).issue(any(), any());
        }
    }

    @Nested
    @DisplayName("Fallo por cuenta desactivada")
    class CuentaDesactivada {

        @Test
        @DisplayName("comprueba la contrasena antes que el estado, para no filtrar que la cuenta existe")
        void compruebaLaContrasenaPrimero() {
            User user = usuarioDesactivado();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(CONTRASENA, HASH)).thenReturn(false);

            // Con la contrasena incorrecta la respuesta es credenciales invalidas,
            // no "cuenta desactivada". Si se mirase el estado primero, bastaria
            // con acertar el correo para saber que la cuenta existe.
            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("con la contrasena correcta lanza la excepcion de cuenta desactivada, que el borde traduce a 403")
        void conContrasenaCorrectaInformaDelEstado() {
            User user = usuarioDesactivado();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(CONTRASENA, HASH)).thenReturn(true);

            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)))
                    .isInstanceOf(UserDeactivatedException.class);
        }

        @Test
        @DisplayName("no emite token a una cuenta desactivada")
        void noEmiteToken() {
            User user = usuarioDesactivado();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(CONTRASENA, HASH)).thenReturn(true);

            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)))
                    .isInstanceOf(UserDeactivatedException.class);

            verify(tokenIssuer, never()).issue(any(), any());
        }
    }

    @Nested
    @DisplayName("Correo con formato invalido")
    class CorreoConFormatoInvalido {

        @Test
        @DisplayName("responde credenciales invalidas y no consulta la base de datos")
        void noPermiteUsarElLoginComoValidadorDeCorreos() {
            assertThatThrownBy(() -> casoDeUso.login(new LoginCommand("no-es-un-correo", CONTRASENA)))
                    .isInstanceOf(InvalidCredentialsException.class);

            // Un atacante que probara cadenas veria un "formato invalido"
            // distinto del "no existe" y podria distinguir que cadenas son
            // correos plausibles. Ni una consulta a la base de datos sale de aqui.
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("produce la misma excepcion que un correo bien escrito pero desconocido")
        void esIndistinguibleDelCorreoDesconocido() {
            Class<?> porFormato = capturandoExcepcion(() ->
                    casoDeUso.login(new LoginCommand("no-es-un-correo", CONTRASENA)));

            when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
            Class<?> porDesconocido = capturandoExcepcion(() ->
                    casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA)));

            assertThat(porFormato).isEqualTo(porDesconocido);
        }
    }

    @Nested
    @DisplayName("El hash se pasa al hasher, nunca se hashea en el caso de uso")
    class ElHashNoSeManipula {

        @Test
        @DisplayName("pide la verificacion con la contrasena en claro y el hash guardado")
        void verificaConAmbosValores() {
            User user = usuarioActivo();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordHasher.matches(anyString(), any())).thenReturn(true);
            prepararEmision(user, "token");

            casoDeUso.login(new LoginCommand(EMAIL.value(), CONTRASENA));

            ArgumentCaptor<PasswordHash> hashCaptor = ArgumentCaptor.forClass(PasswordHash.class);
            verify(passwordHasher).matches(eq(CONTRASENA), hashCaptor.capture());
            assertThat(hashCaptor.getValue()).isEqualTo(HASH);
        }
    }

    private User usuarioActivo() {
        return User.register(UserId.from(UUID.randomUUID()), EMAIL, HASH,
                Set.of(Role.CUSTOMER), AHORA);
    }

    private User usuarioDesactivado() {
        return User.reconstitute(UserId.from(UUID.randomUUID()), EMAIL, HASH,
                Set.of(Role.CUSTOMER), false, AHORA.minusSeconds(86_400), AHORA);
    }

    private void prepararEmision(User user, String token) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordHasher.matches(CONTRASENA, HASH)).thenReturn(true);
        when(tokenIssuer.issue(eq(user), any(Duration.class)))
                .thenReturn(new AccessToken(token, user.getId(), user.getRoles(),
                        AHORA.plus(Duration.ofMinutes(15)), AHORA));
    }

    /**
     * Ejecuta la accion y devuelve la clase de la excepcion que lanza.
     *
     * <p>Se compara la clase y no el mensaje porque los dos caminos deben ser
     * indistinguibles para el cliente, y comparar la excepcion entera obligaria
     * ademas a fijar el texto, que es un detalle de la implementacion.
     */
    private Class<?> capturandoExcepcion(Runnable accion) {
        try {
            accion.run();
            throw new AssertionError("se esperaba una excepcion");
        } catch (RuntimeException ex) {
            return ex.getClass();
        }
    }
}
