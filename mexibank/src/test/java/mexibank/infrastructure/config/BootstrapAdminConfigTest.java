package mexibank.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.CreateUserUseCase;
import mexibank.domain.user.Email;
import mexibank.domain.user.EmailAlreadyRegisteredException;
import mexibank.domain.user.Role;

/**
 * Cuando se activa el bootstrap y cuando no.
 *
 * <p><strong>Por que se prueba la clase suelta y no arrancando Spring.</strong>
 * El runner se ejecuta una vez por arranque, asi que probarlo de verdad exigiria
 * un contexto de Spring distinto por cada combinacion de propiedades, y el
 * contexto es lo mas caro que hay en un test. Los argumentos de arranque no se
 * leen, de modo que construirlo a mano es equivalente y mucho mas directo. Lo
 * que si necesita Spring de verdad se comprueba en
 * {@code BootstrapAdminIntegrationTest}.
 *
 * <p><strong>Por que el caso de uso va simulado.</strong> Lo que se prueba aqui
 * es la decision del runner: si actua o no, y con que comando. Si el caso de uso
 * fuss un, el test comprobaria de nuevo lo que ya comprueban los tests de la capa
 * de aplicacion.
 */
class BootstrapAdminConfigTest {

    private static final String CORREO = "admin@correo.com";

    private static final String CONTRASENA = "UnaContrasenaLarga";

    private final CreateUserUseCase createUserUseCase = org.mockito.Mockito.mock(CreateUserUseCase.class);

    private final DefaultApplicationArguments args = new DefaultApplicationArguments();

    @Nested
    @DisplayName("Sin propiedades")
    class SinPropiedades {

        @Test
        @DisplayName("no hace nada si faltan las dos")
        void sinNingunaPropiedad() {
            runner("", "").run(args);

            verify(createUserUseCase, never()).createUser(any());
        }

        @Test
        @DisplayName("no hace nada si solo esta el correo")
        void soloCorreo() {
            runner(CORREO, "").run(args);

            verify(createUserUseCase, never()).createUser(any());
        }

        @Test
        @DisplayName("no hace nada si solo esta la contrasena")
        void soloContrasena() {
            runner("", CONTRASENA).run(args);

            verify(createUserUseCase, never()).createUser(any());
        }

        @ParameterizedTest
        @DisplayName("no hace nada con valores en blanco")
        @ValueSource(strings = {"", "   ", "\t"})
        void enBlanco(String valor) {
            // Un espacio es un valor presente a proposito. Si el filtro aceptara
            // "   " como correo, crearia un administrador al que nadie podria
            // iniciar sesion.
            runner(valor, valor).run(args);

            verify(createUserUseCase, never()).createUser(any());
        }
    }

    @Nested
    @DisplayName("Con las dos propiedades")
    class ConPropiedades {

        @Test
        @DisplayName("da de alta un ADMIN con el correo y la contrasena indicados")
        void creaElAdministrador() {
            when(createUserUseCase.createUser(any())).thenReturn(null);

            runner(CORREO, CONTRASENA).run(args);

            ArgumentCaptor<CreateUserCommand> comando =
                    ArgumentCaptor.forClass(CreateUserCommand.class);
            verify(createUserUseCase).createUser(comando.capture());

            assertThat(comando.getValue().email()).isEqualTo(CORREO);
            assertThat(comando.getValue().rawPassword()).isEqualTo(CONTRASENA);
            assertThat(comando.getValue().roles()).containsExactly(Role.ADMIN);
        }

        @Test
        @DisplayName("no falla si el administrador ya existia")
        void idempotente() {
            // Esta es la propiedad que hace que el arranque se pueda repetir. Si
            // la excepcion se propagara, el segundo arranque de un despliegue
            // dejaria la aplicacion sin levantar.
            when(createUserUseCase.createUser(any()))
                    .thenThrow(new EmailAlreadyRegisteredException(Email.of(CORREO)));

            runner(CORREO, CONTRASENA).run(args);
        }

        @Test
        @DisplayName("propaga el fallo si la contrasena no cumple la politica")
        void contrasenaDebilFallaElArranque() {
            // El runner no valida: delega. Y lo que devuelve el caso de uso se
            // propaga, porque una aplicacion que arranca con un administrador
            // invalido parece Sana y no lo esta.
            when(createUserUseCase.createUser(any()))
                    .thenThrow(new IllegalStateException("la contrasena no cumple la politica"));

            assertThatThrownBy(() -> runner(CORREO, "corta").run(args))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private BootstrapAdminConfig runner(String email, String password) {
        return new BootstrapAdminConfig(createUserUseCase, email, password);
    }
}