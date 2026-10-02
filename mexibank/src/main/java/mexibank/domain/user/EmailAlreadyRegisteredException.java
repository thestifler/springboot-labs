package mexibank.domain.user;

/**
 * Ya existe un usuario registrado con ese correo.
 *
 * <p><strong>Por que es una excepcion y no un valor de retorno.</strong> El
 * caso de uso no puede "continuar con un usuario que ya existe": no hay
 * continuacion posible. Y devolver {@code false} obligaria a que cada llamador
 * recordara comprobarlo, que es la forma habitual de que se olvide en el camino
 * nuevo. Una excepcion hace imposible continuar sin decidir.
 *
 * <p><strong>Por que el mensaje incluye el correo.</strong> En un alta, saber que
 * "ese correo ya esta en uso" es informacion que el usuario necesita para
 * corregir lo que ha escrito. Aqui no hay riesgo de enumeracion: el usuario ya
 * conoce el correo que ha introducido. El problema de enumeracion aparece en el
 * <em>login</em>, no en el alta, y por eso alli el mensaje si es generico (ver
 * {@link InvalidCredentialsException}).
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    private final Email email;

    public EmailAlreadyRegisteredException(Email email) {
        super("Ya existe un usuario registrado con el correo " + email.value());
        this.email = email;
    }

    /**
     * El correo en conflicto, para que el adaptador pueda responder con un 409 y
     * detalle del campo sin tener que parsear el mensaje.
     */
    public Email email() {
        return email;
    }
}
