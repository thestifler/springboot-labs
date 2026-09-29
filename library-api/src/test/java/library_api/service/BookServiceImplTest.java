package library_api.service;

import library_api.dto.BookRequest;
import library_api.dto.BookResponse;
import library_api.entity.Book;
import library_api.exception.BookAlreadyExistsException;
import library_api.exception.BookNotFoundException;
import library_api.exception.NoAvailableCopiesException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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
        assertThat(result.availableCopyNumber()).isEqualTo(7);
    }

    // ---------------------------------------------------------------------
    // reserveCopy
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("reserveCopy descuenta exactamente un ejemplar")
    void reserveCopy_cuandoHayCopias_descuentaUna() {
        Book book = libro(ISBN, "Autor", "Titulo", PUBLICACION, 3);

        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));

        bookService.reserveCopy(ISBN);

        // Regresion del post-decremento: setAvailableCopyNumber(n--) asignaba
        // el valor anterior y el contador no bajaba nunca.
        assertThat(book.getAvailableCopyNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("reserveCopy no persiste explicitamente: la entidad gestionada se sincroniza sola")
    void reserveCopy_noLlamaASave() {
        Book book = libro(ISBN, "Autor", "Titulo", PUBLICACION, 3);

        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));

        bookService.reserveCopy(ISBN);

        // Dentro de @Transactional la entidad esta gestionada y Hibernate la
        // escribe por dirty checking. Un save() aqui seria redundante.
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserveCopy agota el ultimo ejemplar sin lanzar")
    void reserveCopy_conLaUltimaCopia_dejaACero() {
        Book book = libro(ISBN, "Autor", "Titulo", PUBLICACION, 1);

        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));

        assertThatCode(() -> bookService.reserveCopy(ISBN)).doesNotThrowAnyException();

        assertThat(book.getAvailableCopyNumber()).isZero();
    }

    @Test
    @DisplayName("reserveCopy lanza NoAvailableCopiesException si no hay ejemplares")
    void reserveCopy_sinCopias_lanzaNoAvailableCopiesException() {
        Book book = libro(ISBN, "Autor", "Titulo", PUBLICACION, 0);

        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));

        // Sustituye al boolean que devolvia false: el agotamiento es un
        // resultado de negocio con entidad propia, no un false ambiguo.
        assertThatThrownBy(() -> bookService.reserveCopy(ISBN))
                .isInstanceOf(NoAvailableCopiesException.class)
                .hasMessageContaining(ISBN);

        // Un libro agotado no puede quedar en negativo.
        assertThat(book.getAvailableCopyNumber()).isZero();
        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserveCopy distingue agotado de inexistente: son excepciones distintas")
    void reserveCopy_distingueAgotadoDeInexistente() {
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.reserveCopy(ISBN))
                .isInstanceOf(BookNotFoundException.class)
                .isNotInstanceOf(NoAvailableCopiesException.class);
    }

    @Test
    @DisplayName("reserveCopy consulta con la variante bloqueada, no con findById")
    void reserveCopy_usaElFindBloqueado() {
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(libro(ISBN, "A", "T", PUBLICACION, 2)));

        bookService.reserveCopy(ISBN);

        // findById sin bloqueo permitiria que dos reservas concurrentes del
        // ultimo ejemplar lo consumieran ambas (lost update).
        verify(bookRepository).findByIsbnForUpdate(ISBN);
        verify(bookRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Varias reservas consecutivas van descontando una a una")
    void reserveCopy_variasVecesDescuentaDeUnaEnUna() {
        Book book = libro(ISBN, "Autor", "Titulo", PUBLICACION, 3);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));

        bookService.reserveCopy(ISBN);
        assertThat(book.getAvailableCopyNumber()).isEqualTo(2);
        bookService.reserveCopy(ISBN);
        assertThat(book.getAvailableCopyNumber()).isEqualTo(1);
        bookService.reserveCopy(ISBN);
        assertThat(book.getAvailableCopyNumber()).isZero();

        // A la cuarta ya no queda ninguno y el service lo dice con su excepcion.
        assertThatThrownBy(() -> bookService.reserveCopy(ISBN))
                .isInstanceOf(NoAvailableCopiesException.class);
        assertThat(book.getAvailableCopyNumber()).isZero();
    }

    // ---------------------------------------------------------------------
    // releaseCopy
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("releaseCopy delega el incremento en una unica consulta de actualizacion")
    void releaseCopy_usaElUpdateAtomico() {
        when(bookRepository.increaseAvailableCopyNumber(ISBN)).thenReturn(1);

        bookService.releaseCopy(ISBN);

        // No debe leer antes de escribir: el UPDATE es atomico y no necesita el
        // valor previo, asi que un findById seria un viaje de mas.
        verify(bookRepository).increaseAvailableCopyNumber(ISBN);
        verify(bookRepository, never()).findById(any());
        verify(bookRepository, never()).findByIsbnForUpdate(any());
    }

    @Test
    @DisplayName("releaseCopy no lanza excepcion si el UPDATE afecta a una fila")
    void releaseCopy_cuandoElLibroExiste_noLanza() {
        when(bookRepository.increaseAvailableCopyNumber(ISBN)).thenReturn(1);

        assertThatCode(() -> bookService.releaseCopy(ISBN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("releaseCopy lanza BookNotFoundException si el UPDATE no afecta a ninguna fila")
    void releaseCopy_cuandoElLibroNoExiste_lanzaBookNotFoundException() {
        // 0 filas afectadas = no existe ningun libro con ese isbn.
        when(bookRepository.increaseAvailableCopyNumber(ISBN)).thenReturn(0);

        assertThatThrownBy(() -> bookService.releaseCopy(ISBN))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining(ISBN);
    }

    @Test
    @DisplayName("releaseCopy no toca la entidad en memoria ni persiste con save")
    void releaseCopy_noGuardaLaEntidad() {
        when(bookRepository.increaseAvailableCopyNumber(ISBN)).thenReturn(1);

        bookService.releaseCopy(ISBN);

        // El UPDATE se ejecuta en la base de datos; la entidad no se carga ni se
        // modifica en memoria, asi que no hay nada que guardar.
        verify(bookRepository, never()).save(any());
        verifyNoInteractions(bookMapper);
    }

    @Test
    @DisplayName("reserveCopy y releaseCopy se alternan dejando el contador coherente")
    void reserveCopy_yReleaseCopy_seAlternan() {
        Book book = libro(ISBN, "Autor", "Titulo", PUBLICACION, 2);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));
        when(bookRepository.increaseAvailableCopyNumber(ISBN)).thenReturn(1);

        // Devolver un ejemplar (UPDATE atomico) y reservar otro (entidad en
        // memoria) tienen que dejar el contador donde estaba.
        bookService.releaseCopy(ISBN);
        bookService.reserveCopy(ISBN);

        assertThat(book.getAvailableCopyNumber()).isEqualTo(1);
        verify(bookRepository).increaseAvailableCopyNumber(ISBN);
        verify(bookRepository).findByIsbnForUpdate(ISBN);
    }

    private BookRequest request(String isbn, String author, String title, LocalDate publicationDate,
            long availableCopies) {
        return new BookRequest(isbn, author, title, publicationDate, availableCopies);
    }

    private Book libro(String isbn, String author, String title, LocalDate publicationDate, long availableCopies) {
        return new Book(isbn, author, title, publicationDate, availableCopies);
    }
}
