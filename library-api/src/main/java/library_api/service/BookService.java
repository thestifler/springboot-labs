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
     * @throws library_api.exception.BookNotFoundException si no existe el isbn
     * @throws library_api.exception.NoAvailableCopiesException si el libro existe
     *         pero no queda ningun ejemplar disponible
     */
    void reserveCopy(String isbn);

    /**
     * Devuelve un ejemplar del libro y aumenta en uno el contador de disponibles.
     *
     * @throws library_api.exception.BookNotFoundException si no existe el isbn
     */
    void releaseCopy(String isbn);

}
