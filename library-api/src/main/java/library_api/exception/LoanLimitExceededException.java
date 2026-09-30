package library_api.exception;

/**
 * Excepcion de dominio para "el usuario ya tiene demasiados prestamos abiertos".
 *
 * Sin este limite nada impide que un usuario se lleve todas las copias de todos
 * los libros: el contador de ejemplares protege al libro, pero no a los demas
 * usuarios. Es la regla que convierte el contador en algo justo.
 *
 * GlobalExceptionHandler la traduce a 409 Conflict: la peticion es valida (el
 * usuario y el libro existen) pero choca con el estado del usuario, que ya ha
 * agotado su cupo.
 */
public class LoanLimitExceededException extends RuntimeException {

    private final Long userId;
    private final int limit;

    public LoanLimitExceededException(Long userId, int limit) {
        super("El usuario con id: " + userId + " ya tiene el maximo de prestamos abiertos permitido: " + limit);
        this.userId = userId;
        this.limit = limit;
    }

    public Long getUserId() {
        return userId;
    }

    public int getLimit() {
        return limit;
    }
}
