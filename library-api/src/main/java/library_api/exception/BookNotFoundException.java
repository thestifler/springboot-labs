package library_api.exception;

/**
 * Excepcion de dominio para "libro inexistente".
 *
 * Sustituye al Optional.orElseThrow() sin supplier, que lanzaba
 * NoSuchElementException y terminaba en un HTTP 500 sin contexto para el cliente.
 * GlobalExceptionHandler la traduce a 404.
 */
public class BookNotFoundException extends RuntimeException {

    private final String isbn;

    public BookNotFoundException(String isbn) {
        super("No existe un libro con isbn: " + isbn);
        this.isbn = isbn;
    }

    public String getIsbn() {
        return isbn;
    }
}
