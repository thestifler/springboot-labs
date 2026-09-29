package library_api.service;

import library_api.dto.BookRequest;
import library_api.dto.BookResponse;
import library_api.entity.Book;
import library_api.exception.BookAlreadyExistsException;
import library_api.exception.BookNotFoundException;
import library_api.repository.BookRepository;
import library_api.util.mapper.BookMapper;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del service con las dependencias simuladas.
 *
 * Se fija el contrato de los dos metodos: getBookByIsbn lanza
 * BookNotFoundException (no devuelve null) y addBook lanza
 * BookAlreadyExistsException antes de escribir cuando el isbn ya existe, de modo
 * que un alta repetida nunca degrade en un merge silencioso con un 201Created.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    private static final String ISBN = "9780306406157";
    private static final LocalDate PUBLICACION = LocalDate.of(1997, 3, 3);

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookMapper bookMapper;

    @InjectMocks
    private BookServiceImpl bookService;

    @Test
    @DisplayName("getBookByIsbn devuelve el libro cuando existe")
    void getBookByIsbn_cuandoExiste_devuelveElLibro() {
        Book entity = libro(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness", PUBLICACION, 4);
        BookResponse expected = new BookResponse(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness",
                PUBLICACION, 4);

        when(bookRepository.findById(ISBN)).thenReturn(Optional.of(entity));
        when(bookMapper.toResponse(entity)).thenReturn(expected);

        BookResponse result = bookService.getBookByIsbn(ISBN);

        assertThat(result).isEqualTo(expected);
        verify(bookRepository).findById(ISBN);
    }

    @Test
    @DisplayName("getBookByIsbn lanza BookNotFoundException con el isbn cuando no existe")
    void getBookByIsbn_cuandoNoExiste_lanzaBookNotFoundException() {
        when(bookRepository.findById(ISBN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.getBookByIsbn(ISBN))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining(ISBN);

        // El mapper no se invoca: no se proyecta una entidad que no existe.
        verifyNoInteractions(bookMapper);
    }

    @Test
    @DisplayName("addBook guarda la entidad y devuelve el libro creado")
    void addBook_persisteYDevuelveElLibroCreado() {
        BookRequest request = request(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness", PUBLICACION, 4);
        Book mapped = libro(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness", PUBLICACION, 4);
        Book saved = libro(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness", PUBLICACION, 4);
        BookResponse expected = new BookResponse(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness",
                PUBLICACION, 4);

        when(bookRepository.existsById(ISBN)).thenReturn(false);
        when(bookMapper.toEntity(request)).thenReturn(mapped);
        when(bookRepository.save(mapped)).thenReturn(saved);
        when(bookMapper.toResponse(saved)).thenReturn(expected);

        BookResponse result = bookService.addBook(request);

        assertThat(result).isEqualTo(expected);
        verify(bookRepository).save(mapped);
    }

    @Test
    @DisplayName("addBook lanza BookAlreadyExistsException y no escribe si el isbn ya existe")
    void addBook_cuandoElIsbnYaExiste_lanzaBookAlreadyExistsException() {
        BookRequest request = request(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness", PUBLICACION, 4);

        when(bookRepository.existsById(ISBN)).thenReturn(true);

        assertThatThrownBy(() -> bookService.addBook(request))
                .isInstanceOf(BookAlreadyExistsException.class)
                .hasMessageContaining(ISBN);

        // La comprobacion va antes del mapeo: no se llega a construir ni a persistir
        // la entidad, de modo que un isbn repetido no degrada en un merge.
        verify(bookRepository, never()).save(any());
        verifyNoInteractions(bookMapper);
    }

    @Test
    @DisplayName("addBook conserva todos los campos, incluida la fecha de publicacion y las copias")
    void addBook_mapeaTodosLosCampos() {
        BookRequest request = request(ISBN, "Robert C. Martin", "Clean Code", LocalDate.of(2008, 8, 1), 7);
        Book mapped = libro(ISBN, "Robert C. Martin", "Clean Code", LocalDate.of(2008, 8, 1), 7);
        Book saved = libro(ISBN, "Robert C. Martin", "Clean Code", LocalDate.of(2008, 8, 1), 7);

        when(bookRepository.existsById(ISBN)).thenReturn(false);
        when(bookMapper.toEntity(request)).thenReturn(mapped);
        when(bookRepository.save(mapped)).thenReturn(saved);
        when(bookMapper.toResponse(saved)).thenReturn(
                new BookResponse(ISBN, "Robert C. Martin", "Clean Code", LocalDate.of(2008, 8, 1), 7));

        BookResponse result = bookService.addBook(request);

        // El isbn es la clave primaria y lo aporta el cliente: el mapper no lo ignora.
        assertThat(result.isbn()).isEqualTo(ISBN);
        assertThat(result.publicationDate()).isEqualTo(LocalDate.of(2008, 8, 1));
        assertThat(result.avaliableCopyNumber()).isEqualTo(7);
    }

    private BookRequest request(String isbn, String author, String title, LocalDate publicationDate,
            long availableCopies) {
        return new BookRequest(isbn, author, title, publicationDate, availableCopies);
    }

    private Book libro(String isbn, String author, String title, LocalDate publicationDate, long availableCopies) {
        return new Book(isbn, author, title, publicationDate, availableCopies);
    }
}
