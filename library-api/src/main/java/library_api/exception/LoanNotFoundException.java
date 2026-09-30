package library_api.exception;

/**
 * Excepcion de dominio para "no existe ese prestamo".
 *
 * No se resuelve con findByIdAndReturnedDateIsNull, que devolveria un Optional
 * vacio igual para un prestamo inexistente y para uno ya devuelto. Son dos
 * situaciones distintas que el cliente merece poder distinguir (404 frente a 409),
 * asi que se busca por id y el estado se comprueba aparte.
 *
 * GlobalExceptionHandler la traduce a 404 Not Found.
 */
public class LoanNotFoundException extends RuntimeException {

    private final Long id;

    public LoanNotFoundException(Long id) {
        super("No existe un prestamo con id: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
