package library_api.exception;

/**
 * Excepcion de dominio para "este usuario ya tiene este libro prestado".
 *
 * Se lanza cuando se intenta crear un segundo Loan del mismo libro para el mismo
 * usuario y el primero sigue abierto (returnedDate a null).
 *
 * La comprobacion va contra LoanRepository.existsByUser_IdAndBook_IsbnAndReturnedDateIsNull
 * y no contra la coleccion de prestamos del usuario: la consulta devuelve un
 * booleano sin cargar ninguna entidad, de modo que el caso mas frecuente (no hay
 * ningun prestamo abierto) no cuesta leer prestamos.
 *
 * GlobalExceptionHandler la traduce a 409 Conflict por el mismo motivo que
 * NoAvailableCopiesException: la peticion es valida (el usuario y el libro existen)
 * pero choca con el estado actual del recurso, que es que ya tiene ese libro
 * consigo. No es un 400 porque el cliente no ha mandado nada mal formado, ni un
 * 404 porque el recurso existe.
 */
public class LoanAlreadyLoanedException extends RuntimeException {

    private final Long userId;
    private final String bookIsbn;

    public LoanAlreadyLoanedException(Long userId, String bookIsbn) {
        super("El usuario con id: " + userId + " ya tiene prestado el libro con isbn: " + bookIsbn);
        this.userId = userId;
        this.bookIsbn = bookIsbn;
    }

    public Long getUserId() {
        return userId;
    }

    public String getBookIsbn() {
        return bookIsbn;
    }
}
