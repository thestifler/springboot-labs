package mexibank.infrastructure.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.CreateUserUseCase;
import mexibank.domain.user.EmailAlreadyRegisteredException;
import mexibank.domain.user.Role;

/**
 * Crea el primer administrador, y solo si se le pide explicitamente.
 *
 * <p><strong>Por que hace falta.</strong> {@code POST /api/v1/users} exige el rol
 * {@code ADMIN}, de modo que sin un administrador ya existente no hay forma de
 * crear el primero: la API se cierra sobre si misma. Ese bucle se rompe por
 * fuera, con las credenciales de un despliegue, que es el unico sitio donde un
 * administrador puede nacer legitimamente.
 *
 * <p><strong>Por que el alta pasa por el caso de uso y no por un INSERT.</strong>
 * Porque el administrador tiene que ser un usuario como todos los demas. Si se
 * insertara la fila a mano, seria el unico usuario del sistema cuyo hash no
 * habria pasado por {@code BCryptPasswordHasher}, ni su contrasena por
 * {@code PasswordPolicy}, ni su id por {@code UserId.newId()}. Ese usuario
 * arrastraria esas diferencias el resto de su vida sin que nadie lo notase.
 * Delegando en {@link CreateUserUseCase} no queda nada que el mismo camino no
 * haga por los demas.
 *
 * <p><strong>Por que es idempotente.</strong> Un arranque no es un evento
 * unico: un despliegue puede reiniciar el proceso, y un {@code docker compose
 * up} repetido tambien. Si el alta fallara porque el correo ya existe, la
 * aplicacion no arrancaria la segunda vez. Capturar
 * {@link EmailAlreadyRegisteredException} y salir es lo que hace que se pueda
 * dejar la configuracion puesta de forma permanente en lugar de recordar
 * quitarla entre arranques.
 *
 * <p><strong>Por que no valida nada.</strong> El correo, la contrasena y los
 * roles los comprueba ya el caso de uso, con las mismas reglas que aplica a una
 * peticion HTTP. Si esta clase duplicara esas comprobaciones, existiria un
 * segundo criterio de admision que podria divergir del primero.
 *
 * <p><strong>Por que falla el arranque si los datos no valen.</strong> Un
 * administrador con una contrasena que no cumple la politica es un
 * administrador que no deberia existir, y seguir arrancando dejaria un sistema
 * en marcha que parece bueno y no lo es. Se propaga la excepcion.
 */
@Configuration
public class BootstrapAdminConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfig.class);

    private final CreateUserUseCase createUserUseCase;

    private final String email;

    private final String password;

    public BootstrapAdminConfig(CreateUserUseCase createUserUseCase,
                                @Value("${mexibank.bootstrap.admin.email:}") String email,
                                @Value("${mexibank.bootstrap.admin.password:}") String password) {
        this.createUserUseCase = createUserUseCase;
        this.email = email;
        this.password = password;
    }

    /**
     * Da de alta al administrador si las propiedades estan informadas.
     *
     * <p>Si falta cualquiera de las dos no se hace nada, y con normalidad. El caso
     * de uso normal de esta clase es estar ausente: solo se activa en el arranque
     * en el que hace falta.
     *
     * @param args argumentos de arranque de Spring; no se usan
     */
    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }

        try {
            createUserUseCase.createUser(
                    new CreateUserCommand(email, password, Set.of(Role.ADMIN)));
            // El correo si se registra y la contrasena nunca: saber que el
            // administrador existe y en que direccion es lo que hace util este
            // log, y la contrasena no tiene ninguna utilidad que no sea
            // dejarla escrita en un archivo de log.
            log.info("Administrador inicial creado: {}", email);
        } catch (EmailAlreadyRegisteredException yaExiste) {
            log.info("El administrador inicial ya existia; no se hace nada: {}", email);
        }
    }
}