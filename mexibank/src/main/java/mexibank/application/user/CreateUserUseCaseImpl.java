package mexibank.application.user;

import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.CreateUserUseCase;
import mexibank.domain.user.Email;
import mexibank.domain.user.EmailAlreadyRegisteredException;
import mexibank.domain.user.PasswordHasher;
import mexibank.domain.user.PasswordPolicy;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;
import mexibank.domain.user.UserRepository;

import java.time.Clock;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de usuarios.
 *
 * <p><strong>Que hace y que no hace.</strong> Orquesta: valida el formato, comprueba
 * que el correo este libre, hashea la contrasena y guarda. Decide poco: las
 * reglas que decide son las de coordinacion (el correo no puede estar repetido),
 * no las de negocio del agregado, que ya estan en {@link User}.
 *
 * <p><strong>Por que hashea aqui y no en el adaptador.</strong> Si lo hiciera el
 * adaptador, el agregado tendria que aceptar la contrasena en claro. Con el
 * puerto {@link PasswordHasher}, el caso de uso hashea antes de construir el
 * agregado y el dominio solo ve hashes. Es la diferencia entre "nadie deberia
 * registrar esto" y "no hay forma de registrarlo".
 *
 * <p><strong>Por que se comprueba el correo antes de hashear.</strong> BCrypt es
 * deliberadamente lento, unos 100 ms por operacion. Hashear una contrasena que
 * luego se va a descartar porque el correo ya existe es trabajo perdido. El orden
 * es: comprobar lo barato primero.
 *
 * <p><strong>Por que el reloj se inyecta.</strong> {@code Instant.now()} haria que
 * la fecha de alta dependiera del reloj del sistema, y los tests que la comprueban
 * fallarian de forma intermitente. Con el {@link Clock} inyectado, el test fija la
 * hora y comprueba exactamente lo que quiere.
 */
@Service
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateUserUseCaseImpl.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public CreateUserUseCaseImpl(UserRepository userRepository,
                                 PasswordHasher passwordHasher,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    /**
     * Da de alta un usuario.
     *
     * <p><strong>Por que la transaccion va aqui y no en el repositorio.</strong>
     * Porque la operacion de negocio es "comprobar que no existe y guardar", y
     * ambas cosas tienen que ser coherentes entre si. Si el repositorio llevara
     * su propia transaccion, entre la comprobacion y el guardado habria un hueco
     * en el que otro proceso podria insertar el mismo correo, y la segunda
     * escritura fallaria con una excepcion de SQL en vez de con la del dominio.
     *
     * <p>La restriccion de unicidad sigue estando en la base de datos: es la
     * ultima linea de defensa para cuando dos peticiones compiten de verdad. Lo
     * que evita el {@code existsByEmail} es el caso normal, no la carrera.
     */
    @Override
    @Transactional
    public User createUser(CreateUserCommand command) {
        Email email = Email.of(command.email());

        if (userRepository.existsByEmail(email)) {
            // Se registra el correo porque es util para diagnosticar, pero la
            // excepcion se lanza igualmente: el cliente ya sabe que correo ha
            // enviado, asi que no hay riesgo de enumeracion aqui.
            log.info("Intento de alta con un correo ya registrado: {}", email);
            throw new EmailAlreadyRegisteredException(email);
        }

        PasswordPolicy.validate(command.rawPassword());

        // El log va antes del hasheo y sin la contrasena nunca. Si el hasheo
        // falla por alguna razon, el log muestra que se intento dar de alta sin
        // haber registrado el secreto en ningun sitio.
        log.info("Dando de alta al usuario {}", email);

        User user = User.register(
                UserId.newId(),
                email,
                passwordHasher.hash(command.rawPassword()),
                resolverRoles(command.roles()),
                clock.instant());

        userRepository.save(user);
        return user;
    }

    /**
     * Decide los roles con los que se crea el usuario.
     *
     * <p><strong>Por que no se puede crear un cliente desde aqui.</strong> Este
     * caso de uso esta protegido con el rol ADMIN en el adaptador REST, asi que
     * cualquier usuario que llegue por esta via es un administrador. Si el
     * comando no trae roles, se le da el rol por defecto, que es CUSTOMER. La
     * creacion de clientes por el propio cliente (registro abierto) seria otro
     * caso de uso distinto, sin esta proteccion.
     *
     * @param roles roles solicitados
     * @return los roles a asignar, nunca vacios
     */
    private Set<Role> resolverRoles(Set<Role> roles) {
        return (roles == null || roles.isEmpty()) ? Role.defaultRoles() : roles;
    }
}
