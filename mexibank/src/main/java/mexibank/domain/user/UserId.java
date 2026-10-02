package mexibank.domain.user;

import java.util.UUID;

/**
 * Identificador de un usuario.
 *
 * <p><strong>Por que no se usa {@link UUID} directamente.</strong> Un UUID
 * desnudo no dice de que es. En un sistema con cuentas, usuarios y
 * transferencias hay tres UUID en circulacion, y el compilador no ayuda a
 * distinguir un id de usuario de un id de cuenta: ambos son {@code UUID} y se
 * pueden pasar el uno por el otro sin avisar. Con este record, el metodo se
 * llama {@code userId()} en vez de {@code id()}, lo que hace explicito de que es
 * al leerlo, y el tipo impide el intercambio entre ids.
 *
 * <p>Es el mismo argumento que sostiene {@link Email} y {@link PasswordHash}:
 * los tipos del negocio llevan sus propias garantias, en vez de confiar en que
 * cada quien recuerda la regla.
 */
public record UserId(UUID value) {

    /**
     * @throws IllegalArgumentException si el UUID es nulo
     */
    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("El identificador de usuario no puede ser nulo");
        }
    }

    /**
     * Genera un identificador nuevo.
     *
     * <p>La generacion vive en el value object y no en el caso de uso para que
     * sea siempre la misma, sin importar por donde entre el alta. Si el caso de
     * uso generara ids, cada forma nueva de crear un usuario tendria que
     * acordarse de hacerlo.
     */
    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    /**
     * Reconstruye un identificador desde su representacion persistida.
     *
     * <p>Existe para dejar explicito, en el punto donde se rehidrata el agregado,
     * que no se esta generando un id nuevo. Confundir las dos operaciones es
     * como se pierde un usuario: se inserta una fila con id distinto y los datos
     * quedan huerfanos.
     */
    public static UserId from(UUID value) {
        return new UserId(value);
    }
}
