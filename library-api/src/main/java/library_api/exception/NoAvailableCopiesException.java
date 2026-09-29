package library_api.exception;

/**
 * Excepcion de dominio para "no quedan ejemplares disponibles".
 *
 * El libro existe, asi que no es un 404 como en BookNotFoundException, pero no
 * se puede completar la reserva. Sustituye al boolean que devolvia el service,
 * donde false no distinguia de un caso negativo por otra causa y dejaba al
 * llamante interpretar el resultado.
 *
 * GlobalExceptionHandler la traduce a 409 Conflict: la peticion es valida, pero
 * choca con el estado actual del recurso.
 */
public class NoAvailableCopiesException extends RuntimeException {

    private final String isbn;

    public NoAvailableCopiesException(String isbn) {
        super("No quedan ejemplares disponibles del libro con isbn: " + isbn);
        this.isbn = isbn;
    }

    public String getIsbn() {
        return isbn;
    }
}
