package library_api.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import library_api.dto.LoanRequest;
import library_api.dto.LoanResponse;
import library_api.entity.Book;
import library_api.entity.Loan;
import library_api.entity.User;
import library_api.entity.UserStatus;
import library_api.exception.BookNotFoundException;
import library_api.exception.LoanAlreadyLoanedException;
import library_api.exception.LoanAlreadyReturnedException;
import library_api.exception.LoanLimitExceededException;
import library_api.exception.LoanNotFoundException;
import library_api.exception.LoanNotRenewableException;
import library_api.exception.NoAvailableCopiesException;
import library_api.exception.UserNotActiveException;
import library_api.exception.UserNotFoundException;
import library_api.repository.BookRepository;
import library_api.repository.LoanRepository;
import library_api.repository.UserRepository;
import library_api.util.mapper.LoanMapper;

/**
 * Implementacion del servicio de prestamos.
 *
 * Todas las operaciones de escritura van en una transaccion (@Transactional)
 * porque tocan el contador de ejemplares, que es un dato compartido: si el
 * prestamo se guardara pero el contador no se descontara (o al reves), el
 * inventario dejaria de cuadrar y solo se descubriria al auditarlo.
 *
 * Ninguna validacion ocurre despues de la primera escritura. El contador de
 * ejemplares es lo unico que no se puede deshacer y lo que descuadra el sistema
 * para siempre si se toca de mas, porque solo baja.
 */
@Service
public class LoanServiceImpl implements LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanServiceImpl.class);

    /**
     * Numero maximo de prestamos que puede tener abiertos un usuario a la vez.
     *
     * Sin este limite nada impide que un usuario se lleve todas las copias de
     * todos los libros: el contador protege al libro, pero no deja sitio a los
     * demas usuarios. Es una politica de negocio, por eso es una constante con
     * nombre y no un literal suelto.
     */
    static final int MAX_ACTIVE_LOANS = 3;

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final LoanMapper loanMapper;

    public LoanServiceImpl(LoanRepository loanRepository, BookRepository bookRepository,
                           UserRepository userRepository, LoanMapper loanMapper) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.loanMapper = loanMapper;
    }

    @Override
    @Transactional
    public LoanResponse lendBook(LoanRequest request) {
        String isbn = request.isbn();
        Long userId = request.userId();
        log.debug("Prestando el libro con isbn={} al usuario con id={}", isbn, userId);

        // Una sola fecha para toda la operacion. Si loanDate se calculara con
        // LocalDate.now() en varios puntos, un prestamo creado a medianoche
        // tendria loanDate de un dia y dueDate del siguiente, con el plazo
        // descuadrado en un dia entero.
        LocalDate today = LocalDate.now();

        // El usuario se valida el primero y con un findById normal porque es una
        // busqueda por clave primaria, la mas barata, y porque es un 404 que no
        // necesita ninguna escritura. Validarlo antes de tocar el libro evita
        // ademas abrir un SELECT ... FOR UPDATE sobre la fila del libro para
        // acabar inmediatamente con un 404.
        User user = requireActiveUser(userId);

        // El cupo se comprueba con un COUNT y no cargando los prestamos. Un
        // COUNT sobre el indice (user_id, returned_date) resuelve la pregunta
        // "cuantos tiene abiertos" sin tocar ninguna fila de Loan, mientras que
        // findBy... traeria todas las entidades y sus relaciones solo para
        // contarlas. Va antes del bloqueo del libro para fallar rapido sin
        // retener la fila.
        long activeLoans = loanRepository.countByUser_IdAndReturnedDateIsNull(userId);
        if (activeLoans >= MAX_ACTIVE_LOANS) {
            log.warn("El usuario con id={} ha alcanzado el limite de {} prestamos abiertos",
                    userId, MAX_ACTIVE_LOANS);
            throw new LoanLimitExceededException(userId, MAX_ACTIVE_LOANS);
        }

        // findByIsbnForUpdate bloquea la fila hasta el commit (SELECT ... FOR
        // UPDATE). Es imprescindible para que el leer-modificar-escribir del
        // contador de ejemplares no se solape con otro prestamo del mismo libro:
        // sin el bloqueo, dos prestamos concurrentes del ultimo ejemplar
        // disponible lo consumirian ambos (lost update) y el contador acabaria en
        // 0 cuando solo se entrego una copia.
        Book book = bookRepository.findByIsbnForUpdate(isbn)
                .orElseThrow(() -> {
                    log.warn("No existe libro con isbn={}", isbn);
                    return new BookNotFoundException(isbn);
                });

        long availableCopies = book.getAvailableCopyNumber();
        if (availableCopies < 1) {
            // El libro existe pero esta agotado. Se reutiliza
            // NoAvailableCopiesException, que ya esta traducida a 409, en vez de
            // inventar una excepcion nueva con el mismo significado: 400 seria
            // incorrecto (la peticion es valida) y el 404 tambien (el recurso
            // existe).
            log.warn("No hay ejemplares disponibles del libro con isbn={}", isbn);
            throw new NoAvailableCopiesException(isbn);
        }

        // No se presta dos veces el mismo libro al mismo usuario. exists... no
        // carga ninguna entidad, solo responde true/false, y ademas filtra por
        // returnedDate is null para que cuente SOLO el prestamo que sigue abierto:
        // si ya lo devolvio, puede llevarse el mismo libro otra vez.
        if (loanRepository.existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(userId, isbn)) {
            log.warn("El usuario con id={} ya tiene prestado el libro con isbn={}", userId, isbn);
            throw new LoanAlreadyLoanedException(userId, isbn);
        }

        // Post-decremento en la asignacion: availableCopies-- pasaria a
        // setAvailableCopyNumber() el valor ANTERIOR y solo modificaria la
        // variable local, con lo que el contador nunca bajaria.
        book.setAvailableCopyNumber(availableCopies - 1);

        // No hace falta bookRepository.save(): dentro de la transaccion la entidad
        // esta gestionada y Hibernate la sincroniza sola por dirty checking al
        // commitear. Un save() explicito de una entidad ya gestionada seria un
        // no-op.
        Loan loan = loanRepository.save(
                new Loan(book, user, today, Loan.DEFAULT_LOAN_DAYS));

        log.info("Libro con isbn={} prestado al usuario con id={}, vence el {}, quedan={} ejemplares",
                isbn, userId, loan.getDueDate(), book.getAvailableCopyNumber());

        return loanMapper.toResponse(loan);
    }

    @Override
    @Transactional
    public LoanResponse returnBook(Long loanId) {
        log.debug("Registrando la devolucion del prestamo con id={}", loanId);

        Loan loan = findExistingLoan(loanId);

        // markReturned() es idempotente, pero la API no. Si se devolviera dos
        // veces se sumarian dos ejemplares al contador y la biblioteca terminaria
        // con copias fantasma. Por eso se consulta el estado y se responde 409 en
        // vez de un 200 silencioso: el cliente necesita saber que su reintento no
        // surtio efecto.
        if (loan.isReturned()) {
            log.warn("El prestamo con id={} ya estaba devuelto", loanId);
            throw new LoanAlreadyReturnedException(loanId);
        }

        // Marcar primero y contar despues. Si el UPDATE fallara, la transaccion
        // haria rollback de las dos cosas juntas: no queda devuelto un prestamo
        // cuyo ejemplar no se ha reingresado en el inventario, ni al reves.
        loan.markReturned();

        // El contador sube con un UPDATE atomico (increaseAvailableCopyNumber) en
        // lugar de releer el valor y escribirlo. Sumar sobre el dato almacenado no
        // necesita leer antes, asi que no hace falta el FOR UPDATE que si exige
        // lendBook: aqui dos devoluciones concurrentes del MISMO libro solo pueden
        // sumar, nunca restar, y perder una suma no descuadra el inventario.
        int updatedRows = bookRepository.increaseAvailableCopyNumber(loan.getBook().getIsbn());
        if (updatedRows == 0) {
            // El prestamo tiene un libro por clave foranea, asi que llegar aqui
            // seria un fallo de integridad de datos, no un caso de negocio. Se
            // lanza la excepcion de dominio igualmente para no devolver un 200
            // con un prestamo devuelto y un contador sin incrementar.
            log.error("El prestamo con id={} apunta a un libro que ya no existe", loanId);
            throw new BookNotFoundException(loan.getBook().getIsbn());
        }

        // El log NO imprime el contador resultante a proposito:
        // increaseAvailableCopyNumber es un UPDATE que ademas limpia la sesion
        // (clearAutomatically = true), de modo que el valor que tiene Book en
        // memoria es el VIEJO, no el nuevo. Consultar getAvailableCopyNumber() ahi
        // daria un numero falso y, peor, inicializaria un proxy ya desligado de la
        // sesion y reventaria con LazyInitializationException. Por eso el log se
        // queda con el isbn.
        log.info("Prestamo con id={} devuelto; reingresado un ejemplar del libro con isbn={} "
                + "al inventario del usuario con id={}",
                loanId, loan.getBook().getIsbn(), loan.getUser().getId());

        return loanMapper.toResponse(loan);
    }

    @Override
    @Transactional
    public LoanResponse renewLoan(Long loanId) {
        log.debug("Renovando el prestamo con id={}", loanId);

        Loan loan = findExistingLoan(loanId);

        if (loan.isReturned()) {
            // Un libro devuelto no se renueva: ya no esta prestado. Es 409 y no
            // 404 porque el prestamo existe y esta cerrado.
            log.warn("No se puede renovar el prestamo con id={} porque ya fue devuelto", loanId);
            throw new LoanAlreadyReturnedException(loanId);
        }

        // Politica: no se renueva un prestamo vencido. Prorrogarlo no reduce la
        // deuda, solo la esconde mas tiempo, y el usuario podria renovar
        // indefinidamente un libro que debio hace meses. Lo que se hace en ese
        // caso es devolverlo y volver a pedirlo, que ademas crea un prestamo
        // nuevo con su propio historico.
        if (loan.isOverdueOn(LocalDate.now())) {
            log.warn("No se puede renovar el prestamo con id={} porque esta vencido", loanId);
            throw LoanNotRenewableException.porVencimiento(loanId);
        }

        if (loan.hasReachedRenewalLimit()) {
            log.warn("No se puede renovar el prestamo con id={}: limite de renovaciones alcanzado",
                    loanId);
            throw LoanNotRenewableException.porLimiteDeRenovaciones(loanId, Loan.MAX_RENEWALS);
        }

        // La entidad se ocupa de alargar loanDays e incrementar el contador de
        // renovaciones. No hace falta save(): esta gestionada y Hibernate la
        // sincroniza por dirty checking.
        loan.renew(Loan.DEFAULT_LOAN_DAYS);

        log.info("Prestamo con id={} renovado, vence ahora el {} ({} renovaciones)",
                loanId, loan.getDueDate(), loan.getRenewalCount());

        return loanMapper.toResponse(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public LoanResponse getLoan(Long loanId) {
        return loanMapper.toResponse(findExistingLoan(loanId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanResponse> getUserLoans(Long userId) {
        // Se comprueba que el usuario existe para que un id equivocado no
        // devuelva un historial vacio, que el cliente leeria como "no tiene
        // prestamos" en vez de "ese usuario no existe".
        if (!userRepository.existsById(userId)) {
            log.warn("No existe usuario con id={}", userId);
            throw new UserNotFoundException(userId);
        }

        List<LoanResponse> loans = loanRepository.findByUser_IdOrderByLoanDateDescIdDesc(userId)
                .stream()
                .map(loanMapper::toResponse)
                .toList();

        log.debug("Usuario con id={} tiene {} prestamos registrados", userId, loans.size());
        return loans;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanResponse> getOverdueLoans() {
        // La fecha se pasa como parametro en lugar de leerse dentro del query, para
        // que la consulta sea determinista y para que toda la operacion use la
        // misma "fecha de hoy".
        LocalDate today = LocalDate.now();

        List<LoanResponse> loans = loanRepository.findOverdueOn(today)
                .stream()
                .map(loanMapper::toResponse)
                .toList();

        if (!loans.isEmpty()) {
            log.debug("Hay {} prestamos vencidos a fecha {}", loans.size(), today);
        }
        return loans;
    }

    /**
     * Busca el prestamo por clave primaria.
     *
     * A proposito NO usa findByIdAndReturnedDateIsNull: ese metodo devuelve un
     * Optional vacio igual para un prestamo que no existe (404) que para uno ya
     * devuelto (409), y esas dos respuestas deben ser distintas. Aqui el estado se
     * comprueba despues, en cada operacion, con el mensaje que corresponde.
     */
    private Loan findExistingLoan(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> {
                    log.warn("No existe prestamo con id={}", loanId);
                    return new LoanNotFoundException(loanId);
                });
    }

    /**
     * Carga el usuario y comprueba que pueda llevarse prestamos. Reune las dos
     * comprobaciones porque lendBook y returnBook necesitan las dos y duplicar el
     * codigo hacia que cada copia olvide una de ellas al anadir una regla.
     */
    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("No existe usuario con id={}", userId);
                    return new UserNotFoundException(userId);
                });

        // El usuario puede existir y aun asi no poder llevarse prestamos. Es un
        // 409 y no un 404: el recurso existe, lo que choca es su estado. Se pasa el
        // estado concreto para que el mensaje diga cual es, y no un "inactivo o
        // suspendido" que obliga al cliente a consultar el usuario para distinguir.
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("El usuario con id={} esta en estado {} y no puede llevarse prestamos",
                    userId, user.getStatus());
            throw new UserNotActiveException(userId, user.getStatus());
        }

        return user;
    }
}
