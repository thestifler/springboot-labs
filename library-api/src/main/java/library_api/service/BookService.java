package library_api.service;

import library_api.dto.BookRequest;
import library_api.dto.BookResponse;

/**
 * Contrato de la capa de negocio de libros.
 *
 * El isbn es un String porque es un ISBN-13: un long no puede representarlo
 * (el prefijo 978/979 mas los 10 digitos restantes, con digito de control) y
 * ademas la clave primaria de Book es un String.
 */
public interface BookService {

    /**
     * @throws library_api.exception.BookNotFoundException si no existe el isbn
     */
    BookResponse getBookByIsbn(String isbn);

    /**
     * @throws library_api.exception.BookAlreadyExistsException si el isbn ya esta dado de alta
     */
    BookResponse addBook(BookRequest book);

    /**
     * Reserva un ejemplar del libro y descuenta una unidad del contador.
     *
     * @return true si se reservo un ejemplar; false si el libro existe pero no
     *         queda ninguno disponible
     * @throws library_api.exception.BookNotFoundException si no existe el isbn
     */
    boolean decreaseAvalaibleCopyNumberBook(String isbn);

    /**
     * Devuelve un ejemplar del libro y aumenta en uno el contador de disponibles.
     *
     * Devuelve void y no boolean porque solo hay dos desenlaces posibles: el
     * libro existe y se incrementa, o no existe y se lanza excepcion. Un boolean
     * que siempre devolveria true no aportaria informacion alguna.
     *
     * @throws library_api.exception.BookNotFoundException si no existe el isbn
     */
    void increaseAvaliableCopyNumberBook(String isbn);

}
