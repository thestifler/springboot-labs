package library_api.exception;

/**
 * Excepcion de dominio para "isbn ya dado de alta".
 *
 * El isbn es la clave primaria y la aporta el cliente, asi que un alta repetida
 * haria que Spring Data tomara la ruta merge() y devolviera 201 Created
 * enmascarando en realidad un libro existente. Se corta antes, en el service, y
 * GlobalExceptionHandler la traduce a 409 Conflict.
 *
 * El isbn es ademas la primary key de la tabla, asi que la base de datos
 * garantiza la unicidad aunque dos peticiones coincidan en el existsById.
 */
public class BookAlreadyExistsException extends RuntimeException {

    private final String isbn;

    public BookAlreadyExistsException(String isbn) {
        super("Ya existe un libro con isbn: " + isbn);
        this.isbn = isbn;
    }

    public String getIsbn() {
        return isbn;
    }
}
