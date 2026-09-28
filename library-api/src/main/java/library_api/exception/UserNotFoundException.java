package library_api.exception;

/**
 * Excepcion de dominio para "usuario inexistente".
 *
 * Sustituye al Optional.orElseThrow() sin supplier, que lanzaba
 * NoSuchElementException y terminaba en un HTTP 500 sin contexto para el cliente.
 */
public class UserNotFoundException extends RuntimeException {

    private final Long id;

    public UserNotFoundException(Long id) {
        super("No existe un usuario con id: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
