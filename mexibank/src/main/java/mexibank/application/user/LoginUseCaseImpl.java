package mexibank.application.user;

import mexibank.domain.user.AccessToken;
import mexibank.domain.user.Email;
import mexibank.domain.user.InvalidCredentialsException;
import mexibank.domain.user.LoginCommand;
import mexibank.domain.user.LoginResult;
import mexibank.domain.user.LoginUseCase;
import mexibank.domain.user.PasswordHasher;
import mexibank.domain.user.TokenIssuer;
import mexibank.domain.user.User;
import mexibank.domain.user.UserDeactivatedException;
import mexibank.domain.user.UserRepository;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticacion por correo y contrasena.
 *
 * <p><strong>Por que las tres causas de fallo se reportan igual.</strong> Cuando el
 * correo no existe, cuando la contrasena no coincide y cuando el usuario esta
 * desactivado, la respuesta al cliente es identica
 * ({@link InvalidCredentialsException}). Distinguirlas permitiria a un atacante
 * recorrer una lista de correos y descubrir quien tiene cuenta en el banco. El
 * detalle real va al log del servidor, que es interno.
 *
 * <p><strong>Por que se comprueba la contrasena antes que el estado.</strong> Al
 * reves, un usuario desactivado con la contrasena correcta sabria que lo es. El
 * orden aqui es: primero la contrasena, luego el estado. Quien tiene la
 * contrasena correcta ya esta autenticado a efectos practicos, y en ese punto
 * puede saber que su cuenta esta bloqueada sin que eso ayude a un atacante.
 *
 * <p><strong>Por que la transaccion es de solo lectura.</strong> El login no
 * escribe nada: lee el usuario, verifica el hash y emite un token. Se declara
 * igualmente {@code readOnly = true} porque durante el login puede passar por
 * adaptadores que si lean varias tablas, y en PostgreSQL una transaccion de solo
 * lectura puede ir por una via mas barata y no toma bloqueos de escritura.
 */
@Service
public class LoginUseCaseImpl implements LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCaseImpl.class);

    /**
     * Tiempo de vida del token.
     *
     * <p><strong>Por que 15 minutos y no mas.</strong> Los roles viajan dentro
     * del token, asi que un token de vida larga mantiene permisos obsoletos hasta
     * que caduca. Con 15 minutos, revoke un rol o desactivar una cuenta surte
     * efecto en, como mucho, cuarto de hora, sin necesidad de lista de revocados.
     * Si el banco necesita efecto inmediato, la solucion es consultar al usuario
     * en cada peticion, que cuesta una consulta por request.
     */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public LoginUseCaseImpl(UserRepository userRepository,
                            PasswordHasher passwordHasher,
                            TokenIssuer tokenIssuer,
                            Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(LoginCommand command) {
        Email email = resolverEmail(command.email());

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Se registra con el motivo real ("no existe") porque el log es
            // interno, pero se lanza la excepcion generica. Que el log diga mas
            // que la respuesta es justamente lo que permite diagnosticar sin
            // abrir la puerta de la enumeracion.
            log.info("Login con correo desconocido: {}", email);
            throw new InvalidCredentialsException();
        }

        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            log.info("Login con contrasena incorrecta para el usuario {}", user.getId());
            throw new InvalidCredentialsException();
        }

        if (!user.canAuthenticate()) {
            log.info("Login rechazado: el usuario {} esta desactivado", user.getId());
            throw new UserDeactivatedException();
        }

        AccessToken token = tokenIssuer.issue(user, TOKEN_TTL);

        log.info("Login correcto del usuario {} con roles {}",
                user.getId(),
                user.getRoles());

        return new LoginResult(token.value(), user, token.expiresAt());
    }

    /**
     * Normaliza el correo del comando.
     *
     * <p><strong>Por que un correo con formato invalido produce credenciales
     * invalidas y no un error de formato.</strong> Si un atacante puede distinguir
     * "el correo no tiene formato" de "ese correo no existe", obtiene un oraculo
     * para filtrar que cadenas son correos validos. Se responde igual en ambos
     * casos.
     *
     * <p>El caso de uso no lanza la excepcion de formato porque el usuario ha
     * escrito mal su correo y merece saberlo; lo que se evita es que el atacante
     * pueda usar esa diferencia. Por eso el mensaje de credenciales invalidas
     * cubre tambien este camino.
     */
    private Email resolverEmail(String rawEmail) {
        try {
            return Email.of(rawEmail);
        } catch (IllegalArgumentException ex) {
            log.debug("Login con correo de formato invalido");
            throw new InvalidCredentialsException();
        }
    }
}
