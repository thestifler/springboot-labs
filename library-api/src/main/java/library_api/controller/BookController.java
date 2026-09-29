package library_api.controller;

import library_api.dto.BookRequest;
import library_api.dto.BookResponse;
import library_api.service.BookService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del recurso /library/books.
 *
 * El controller no contiene logica de negocio: valida el formato de la entrada,
 * delega en BookService y traduce el resultado a HTTP. Los errores de dominio
 * (404 y 409) y los de validacion (400) los resuelve GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/library/books")
public class BookController {

    /**
     * Solo se acepta un ISBN-13 de 13 digitos, que es exactamente la clave
     * primaria que se guarda. Un valor con otro formato (letras, guiones, longitud
     * distinta) no puede existir en la base de datos, asi que se responde 400 en
     * lugar de hacer una consulta que solo va a fallar.
     */
    private static final String ISBN_13 = "[0-9]{13}";

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/{isbn}")
    public ResponseEntity<BookResponse> getBookByIsbn(
            @PathVariable
            @Pattern(regexp = ISBN_13, message = "el isbn debe tener 13 digitos")
            String isbn) {

        return ResponseEntity.ok(bookService.getBookByIsbn(isbn));
    }

    @PostMapping
    public ResponseEntity<BookResponse> addBook(@Valid @RequestBody BookRequest bookRequest) {
        BookResponse created = bookService.addBook(bookRequest);
        return ResponseEntity
                .created(URI.create("/library/books/" + created.isbn()))
                .body(created);
    }
}
