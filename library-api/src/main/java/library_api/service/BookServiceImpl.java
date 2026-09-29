package library_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import library_api.dto.BookRequest;
import library_api.dto.BookResponse;
import library_api.entity.Book;
import library_api.exception.BookAlreadyExistsException;
import library_api.exception.BookNotFoundException;
import library_api.repository.BookRepository;
import library_api.util.mapper.BookMapper;

/**
 * Implementacion del servicio de libros.
 *
 * El service es el unico sitio donde se decide el resultado de una busqueda: no
 * devuelve null, lanza una excepcion de dominio que GlobalExceptionHandler
 * traduce a un codigo HTTP con sentido. La logica se mantiene aqui y no en el
 * controller para que la capa web sea un simple adaptador de protocolo.
 */
@Service
public class BookServiceImpl implements BookService {

    private static final Logger log = LoggerFactory.getLogger(BookServiceImpl.class);

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    public BookServiceImpl(BookRepository bookRepository, BookMapper bookMapper) {
        this.bookRepository = bookRepository;
        this.bookMapper = bookMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getBookByIsbn(String isbn) {
        log.debug("Buscando libro con isbn={}", isbn);

        Book book = bookRepository.findById(isbn)
                .orElseThrow(() -> {
                    log.warn("No existe libro con isbn={}", isbn);
                    return new BookNotFoundException(isbn);
                });

        return bookMapper.toResponse(book);
    }

    @Override
    @Transactional
    public BookResponse addBook(BookRequest book) {
        String isbn = book.isbn();
        log.debug("Creando libro con isbn={}", isbn);

        // El isbn lo aporta el cliente y es la primary key, asi que sin esta
        // comprobacion un alta repetida haria un merge() y devolveria 201
        // Created sobre un libro que ya existia. La restriccion de la base de
        // datos sigue siendo la garantia final de unicidad.
        if (bookRepository.existsById(isbn)) {
            log.warn("Intento de alta de un isbn ya registrado: {}", isbn);
            throw new BookAlreadyExistsException(isbn);
        }

        Book saved = bookRepository.save(bookMapper.toEntity(book));

        log.info("Libro creado con isbn={}", saved.getIsbn());
        return bookMapper.toResponse(saved);
    }
}
