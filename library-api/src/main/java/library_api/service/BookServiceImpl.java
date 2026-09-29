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

    @Override
    @Transactional
    public boolean decreaseAvalaibleCopyNumberBook(String isbn) {
        log.debug("Reservando un ejemplar del libro con isbn={}", isbn);

        // findByIsbnForUpdate bloquea la fila hasta el commit, de modo que el
        // leer-modificar-escribir del contador no se solapa con otra reserva del
        // mismo libro. Sin este bloqueo, dos reservas concurrentes del ultimo
        // ejemplar disponible podrian consumirlo ambas (lost update).
        Book book = bookRepository.findByIsbnForUpdate(isbn)
                .orElseThrow(() -> {
                    log.warn("No existe libro con isbn={}", isbn);
                    return new BookNotFoundException(isbn);
                });

        long availableCopies = book.getAvaliableCopyNumber();
        if (availableCopies < 1) {
            // El libro existe pero esta agotado: no es un error, es un resultado
            // de negocio valido, asi que se informa con false en vez de lanzar.
            log.warn("No hay ejemplares disponibles del libro con isbn={}", isbn);
            return false;
        }

        // Post-decremento en la asignacion: numberAvaliable-- entregaria a
        // setAvaliableCopyNumber() el valor ANTERIOR y solo decrementaria la
        // variable local, con lo que el contador nunca bajaria.
        book.setAvaliableCopyNumber(availableCopies - 1);

        // No hace falta save(): dentro de la transaccion la entidad esta
        // gestionada y Hibernate la sincroniza sola por dirty checking al
        // commitear. Un save() explicito de una entidad ya gestionada seria un
        // no-op.
        log.info("Ejemplar reservado del libro con isbn={}, quedan={}", isbn, book.getAvaliableCopyNumber());

        return true;
    }

    @Override
    @Transactional
    public void increaseAvaliableCopyNumberBook(String isbn) {
        log.debug("Devolviendo un ejemplar del libro con isbn={}", isbn);

        // Devolver un ejemplar no requiere leer el valor previo: sumar sobre el
        // dato almacenado es atomico en un unico UPDATE, sin necesidad de leer y
        // luego escribir. Por eso no se bloquea la fila como en el descuento.
        //
        // El numero de filas afectadas resuelve ademas la existencia del libro
        // en la misma consulta: 0 filas significa que no hay ningun libro con
        // ese isbn.
        int updatedRows = bookRepository.increaseAvaliableCopyNumber(isbn);

        if (updatedRows == 0) {
            log.warn("No existe libro con isbn={}", isbn);
            throw new BookNotFoundException(isbn);
        }

        log.info("Ejemplar devuelto del libro con isbn={}", isbn);
    }
}
