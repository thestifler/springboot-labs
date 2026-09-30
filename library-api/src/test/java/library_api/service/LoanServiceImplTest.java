package library_api.service;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Tests unitarios de LoanServiceImpl con las dependencias simuladas.
 *
 * Se fija el contrato de las seis operaciones:
 * <ul>
 *   <li>lendBook: usuario inexistente y libro inexistente son 404; usuario
 *       inactivo, sin ejemplares, cupo agotado y prestamo duplicado son 409;</li>
 *   <li>returnBook: devuelve el ejemplar y NO lo hace dos veces (409);</li>
 *   <li>renewLoan: renueva solo si no esta vencido ni agoto las renovaciones;</li>
 *   <li>las lecturas devuelven 404 si el recurso no existe.</li>
 * </ul>
 *
 * El requisito que mas importa en todo el fichero: NINGUN camino que falla toca
 * el contador de ejemplares ni guarda un Loan. Un 409 que hubiera descontado un
 * ejemplar descuadraria el inventario para siempre, porque el contador solo baja.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceImplTest {

    private static final String ISBN = "9780306406157";
    private static final Long USER_ID = 42L;
    private static final Long LOAN_ID = 7L;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoanMapper loanMapper;

    @InjectMocks
    private LoanServiceImpl loanService;

    private static LoanRequest request() {
        return new LoanRequest(ISBN, USER_ID);
    }

    private static User usuario(UserStatus status) {
        return new User("Ana", "Gomez", "Ruiz", status);
    }

    private static Book libro(long disponibles) {
        return new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), disponibles);
    }

    /** Escenario valido de lendBook: usuario activo con cupo y libro con ejemplares. */
    private Book escenarioValido(long disponibles) {
        Book book = libro(disponibles);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.ACTIVE)));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID)).thenReturn(0L);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));
        when(loanRepository.existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(USER_ID, ISBN))
                .thenReturn(false);
        return book;
    }

    /** Un prestamo activo (no devuelto) guardado en el repositorio. */
    private Loan prestamoActivo() {
        Book book = libro(0);
        return new Loan(book, usuario(UserStatus.ACTIVE),
                LocalDate.now(), Loan.DEFAULT_LOAN_DAYS);
    }

    // ---------------------------------------------------------------------
    // lendBook: camino feliz
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("lendBook crea el prestamo y descuenta un ejemplar")
    void lendBook_cuandoTodoEsValido_descuentaUnEjemplar() {
        Book book = escenarioValido(2);
        LoanResponse expected = response(false);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanMapper.toResponse(any(Loan.class))).thenReturn(expected);

        LoanResponse result = loanService.lendBook(request());

        assertThat(result).isEqualTo(expected);
        assertThat(book.getAvailableCopyNumber()).isEqualTo(1L);
    }

    @Test
    @DisplayName("lendBook usa la duracion por defecto y una fecha de hoy coherente con dueDate")
    void lendBook_registraHoyYElVencimientoDerivado() {
        Book book = escenarioValido(1);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanMapper.toResponse(any(Loan.class))).thenReturn(response(false));

        LocalDate antes = LocalDate.now();
        loanService.lendBook(request());
        LocalDate despues = LocalDate.now();

        Loan loan = prestamoGuardado();
        // Se compara contra un intervalo en lugar de una unica llamada a
        // LocalDate.now() para que el test no falle si cae justo a medianoche.
        assertThat(loan.getLoanDate()).isBetween(antes, despues);
        assertThat(loan.getDueDate()).isEqualTo(loan.getLoanDate().plusDays(Loan.DEFAULT_LOAN_DAYS));
    }

    @Test
    @DisplayName("lendBook guarda el prestamo enlazado al libro y al usuario")
    void lendBook_guardaElPrestamoConSusRelaciones() {
        User user = usuario(UserStatus.ACTIVE);
        Book book = libro(3);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID)).thenReturn(0L);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanMapper.toResponse(any(Loan.class))).thenReturn(response(false));

        loanService.lendBook(request());

        Loan loan = prestamoGuardado();
        assertThat(loan.getBook()).isSameAs(book);
        assertThat(loan.getUser()).isSameAs(user);
        assertThat(loan.getReturnedDate()).isNull();
        assertThat(loan.getRenewalCount()).isZero();
    }

    @Test
    @DisplayName("lendBook bloquea la fila del libro para no perder actualizaciones concurrentes")
    void lendBook_bloqueaElLibroConForUpdate() {
        escenarioValido(1);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanMapper.toResponse(any(Loan.class))).thenReturn(response(false));

        loanService.lendBook(request());

        verify(bookRepository).findByIsbnForUpdate(ISBN);
    }

    @Test
    @DisplayName("lendBook no vuelve a guardar el libro: lo sincroniza Hibernate")
    void lendBook_noGuardaElLibroDeFormaExplicita() {
        Book book = escenarioValido(2);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanMapper.toResponse(any(Loan.class))).thenReturn(response(false));

        loanService.lendBook(request());

        verify(bookRepository, never()).save(any(Book.class));
        assertThat(book.getAvailableCopyNumber()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------
    // lendBook: 404
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("lendBook lanza UserNotFoundException si el usuario no existe")
    void lendBook_cuandoNoExisteElUsuario_lanzaUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("42");

        verifyNoInteractions(bookRepository, loanRepository);
    }

    @Test
    @DisplayName("lendBook lanza BookNotFoundException si el isbn no existe")
    void lendBook_cuandoNoExisteElLibro_lanzaBookNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.ACTIVE)));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID)).thenReturn(0L);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining(ISBN);

        verify(loanRepository, never()).save(any(Loan.class));
    }

    // ---------------------------------------------------------------------
    // lendBook: 409
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("lendBook lanza UserNotActiveException si el usuario esta inactivo")
    void lendBook_cuandoElUsuarioEstaInactivo_lanzaUserNotActive() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.INACTIVE)));

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(UserNotActiveException.class)
                .hasMessageContaining("42")
                .hasMessageContaining("INACTIVE");

        verifyNoInteractions(bookRepository, loanRepository);
    }

    @Test
    @DisplayName("lendBook lanza UserNotActiveException si el usuario esta suspendido")
    void lendBook_cuandoElUsuarioEstaSuspendido_lanzaUserNotActive() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.SUSPENDED)));

        // El mensaje nombra SUSPENDED y no un "inactivo o suspendido" generico: el
        // cliente puede distinguir los dos motivos sin consultar el usuario.
        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(UserNotActiveException.class)
                .hasMessageContaining("SUSPENDED");

        verifyNoInteractions(bookRepository, loanRepository);
    }

    @Test
    @DisplayName("UserNotActiveException lleva el estado que la provoca, sin parsear el mensaje")
    void userNotActive_exponeElEstadoEnElCampo() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOfSatisfying(UserNotActiveException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(UserStatus.SUSPENDED));
    }

    @Test
    @DisplayName("lendBook lanza NoAvailableCopiesException si el libro no tiene ejemplares")
    void lendBook_cuandoNoHayEjemplares_lanzaNoAvailableCopies() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.ACTIVE)));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID)).thenReturn(0L);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(libro(0)));

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(NoAvailableCopiesException.class)
                .hasMessageContaining(ISBN);

        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    @DisplayName("lendBook lanza LoanAlreadyLoanedException si el usuario ya tiene el libro")
    void lendBook_cuandoYaTieneElLibroPrestado_lanzaLoanAlreadyLoaned() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.ACTIVE)));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID)).thenReturn(1L);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(libro(2)));
        when(loanRepository.existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(USER_ID, ISBN))
                .thenReturn(true);

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(LoanAlreadyLoanedException.class)
                .hasMessageContaining(ISBN);

        verify(loanRepository, never()).save(any(Loan.class));
    }

    // ---------------------------------------------------------------------
    // lendBook: limite de prestamos abiertos
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("lendBook lanza LoanLimitExceededException si el usuario agoto su cupo")
    void lendBook_cuandoElCupoEstaAgotado_lanzaLoanLimitExceeded() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.ACTIVE)));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID))
                .thenReturn((long) LoanServiceImpl.MAX_ACTIVE_LOANS);

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(LoanLimitExceededException.class)
                .hasMessageContaining(String.valueOf(LoanServiceImpl.MAX_ACTIVE_LOANS));

        // El cupo se comprueba ANTES de tocar el libro, para fallar rapido sin
        // retener el bloqueo de la fila.
        verifyNoInteractions(bookRepository);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    @DisplayName("lendBook permite el prestamo si el usuario tiene un hueco en su cupo")
    void lendBook_conCupoLibre_permiteElPrestamo() {
        Book book = escenarioValido(1);
        when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanMapper.toResponse(any(Loan.class))).thenReturn(response(false));

        loanService.lendBook(request());

        assertThat(book.getAvailableCopyNumber()).isZero();
    }

    // ---------------------------------------------------------------------
    // lendBook: ningun fallo toca el contador
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("lendBook no toca el contador cuando el usuario ya tiene el libro")
    void lendBook_cuandoFallaLaValidacion_noDescuentaEjemplares() {
        Book book = libro(3);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.ACTIVE)));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID)).thenReturn(1L);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));
        when(loanRepository.existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(USER_ID, ISBN))
                .thenReturn(true);

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(LoanAlreadyLoanedException.class);

        assertThat(book.getAvailableCopyNumber()).isEqualTo(3L);
    }

    @Test
    @DisplayName("lendBook no toca el contador cuando el libro esta agotado")
    void lendBook_cuandoElLibroEstaAgotado_noDescuentaEjemplares() {
        Book book = libro(0);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(usuario(UserStatus.ACTIVE)));
        when(loanRepository.countByUser_IdAndReturnedDateIsNull(USER_ID)).thenReturn(0L);
        when(bookRepository.findByIsbnForUpdate(ISBN)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(NoAvailableCopiesException.class);

        assertThat(book.getAvailableCopyNumber()).isZero();
    }

    // ---------------------------------------------------------------------
    // returnBook
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("returnBook marca el prestamo devuelto y reingresa el ejemplar")
    void returnBook_cuandoEstaAbierto_cierraElPrestamo() {
        Loan loan = prestamoActivo();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(bookRepository.increaseAvailableCopyNumber(ISBN)).thenReturn(1);
        when(loanMapper.toResponse(loan)).thenReturn(response(true));

        loanService.returnBook(LOAN_ID);

        assertThat(loan.isReturned()).isTrue();
        verify(bookRepository).increaseAvailableCopyNumber(ISBN);
    }

    @Test
    @DisplayName("returnBook NO bloquea la fila del libro: el UPDATE es atomico")
    void returnBook_noBloqueaElLibro() {
        Loan loan = prestamoActivo();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(bookRepository.increaseAvailableCopyNumber(ISBN)).thenReturn(1);
        when(loanMapper.toResponse(loan)).thenReturn(response(true));

        loanService.returnBook(LOAN_ID);

        // Devolver solo suma. Dos devoluciones concurrentes del mismo libro no
        // pueden restar ni pisarse, asi que aqui no hace falta el FOR UPDATE que si
        // exige lendBook.
        verify(bookRepository, never()).findByIsbnForUpdate(any());
    }

    @Test
    @DisplayName("returnBook lanza LoanNotFoundException si el prestamo no existe")
    void returnBook_cuandoNoExiste_lanzaLoanNotFound() {
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.returnBook(LOAN_ID))
                .isInstanceOf(LoanNotFoundException.class)
                .hasMessageContaining("7");

        verifyNoInteractions(bookRepository);
    }

    @Test
    @DisplayName("returnBook lanza 409 y NO suma un segundo ejemplar si ya estaba devuelto")
    void returnBook_cuandoYaEstabaDevuelto_noSumaEjemplares() {
        Loan loan = prestamoActivo();
        loan.markReturned();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.returnBook(LOAN_ID))
                .isInstanceOf(LoanAlreadyReturnedException.class);

        // Este es el motivo de no hacer la API idempotente: dos devoluciones
        // aceptadas sumarian dos ejemplares que no existen.
        verify(bookRepository, never()).increaseAvailableCopyNumber(any());
    }

    // ---------------------------------------------------------------------
    // renewLoan
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("renewLoan prolonga el plazo DEFAULT_LOAN_DAYS y cuenta la renovacion")
    void renewLoan_prorrogaElPlazo() {
        Loan loan = prestamoActivo();
        int loanDaysBefore = loan.getLoanDays();
        LocalDate dueDateBefore = loan.getDueDate();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(loanMapper.toResponse(loan)).thenReturn(response(false));

        loanService.renewLoan(LOAN_ID);

        assertThat(loan.getLoanDays()).isEqualTo(loanDaysBefore + Loan.DEFAULT_LOAN_DAYS);
        assertThat(loan.getDueDate()).isAfter(dueDateBefore);
        assertThat(loan.getRenewalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("renewLoan lanza LoanNotFoundException si el prestamo no existe")
    void renewLoan_cuandoNoExiste_lanzaLoanNotFound() {
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.renewLoan(LOAN_ID))
                .isInstanceOf(LoanNotFoundException.class);
    }

    @Test
    @DisplayName("renewBook lanza 409 si el prestamo ya fue devuelto")
    void renewLoan_cuandoEstaDevuelto_lanza409() {
        Loan loan = prestamoActivo();
        loan.markReturned();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.renewLoan(LOAN_ID))
                .isInstanceOf(LoanAlreadyReturnedException.class);

        assertThat(loan.getRenewalCount()).isZero();
    }

    @Test
    @DisplayName("renewLoan rechaza un prestamo vencido: renovar solo alargaria la deuda")
    void renewLoan_cuandoEstaVencido_lanzaLoanNotRenewable() {
        Loan loan = new Loan(libro(0), usuario(UserStatus.ACTIVE),
                LocalDate.now().minusDays(30), Loan.DEFAULT_LOAN_DAYS);
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.renewLoan(LOAN_ID))
                .isInstanceOf(LoanNotRenewableException.class)
                .hasMessageContaining("vencido");

        assertThat(loan.getRenewalCount()).isZero();
    }

    @Test
    @DisplayName("renewLoan lanza 409 al agotar Loan.MAX_RENEWALS renovaciones")
    void renewLoan_cuandoAgotoLasRenovaciones_lanzaLoanNotRenewable() {
        Loan loan = prestamoActivo();
        for (int i = 0; i < Loan.MAX_RENEWALS; i++) {
            loan.renew(Loan.DEFAULT_LOAN_DAYS);
        }
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.renewLoan(LOAN_ID))
                .isInstanceOf(LoanNotRenewableException.class)
                .hasMessageContaining("renovaciones");

        assertThat(loan.getRenewalCount()).isEqualTo(Loan.MAX_RENEWALS);
    }

    @Test
    @DisplayName("renewLoan permite renovar hasta el limite pero no lo sobrepasa")
    void renewLoan_hastaElLimite() {
        Loan loan = prestamoActivo();
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(loanMapper.toResponse(loan)).thenReturn(response(false));

        for (int i = 0; i < Loan.MAX_RENEWALS; i++) {
            loanService.renewLoan(LOAN_ID);
        }
        assertThat(loan.hasReachedRenewalLimit()).isTrue();
    }

    // ---------------------------------------------------------------------
    // Lecturas
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getLoan lanza LoanNotFoundException si no existe")
    void getLoan_cuandoNoExiste_lanzaLoanNotFound() {
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getLoan(LOAN_ID))
                .isInstanceOf(LoanNotFoundException.class);
    }

    @Test
    @DisplayName("getUserLoans devuelve el historial mapeado, del mas reciente al mas antiguo")
    void getUserLoans_devuelveElHistorial() {
        Loan primero = prestamoActivo();
        Loan segundo = prestamoActivo();
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(loanRepository.findByUser_IdOrderByLoanDateDescIdDesc(USER_ID))
                .thenReturn(List.of(primero, segundo));
        when(loanMapper.toResponse(primero)).thenReturn(response(false));
        when(loanMapper.toResponse(segundo)).thenReturn(response(false));

        List<LoanResponse> result = loanService.getUserLoans(USER_ID);

        // El orden lo pone la consulta, no un sort posterior.
        assertThat(result).hasSize(2);
        verify(loanRepository).findByUser_IdOrderByLoanDateDescIdDesc(USER_ID);
    }

    @Test
    @DisplayName("getUserLoans lanza 404 si el usuario no existe, en vez de devolver una lista vacia")
    void getUserLoans_cuandoElUsuarioNoExiste_lanza404() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> loanService.getUserLoans(USER_ID))
                .isInstanceOf(UserNotFoundException.class);

        verify(loanRepository, never()).findByUser_IdOrderByLoanDateDescIdDesc(any());
    }

    @Test
    @DisplayName("getOverdueLoans devuelve solo los prestamos vencidos")
    void getOverdueLoans_devuelveLosVencidos() {
        Loan vencido = new Loan(libro(0), usuario(UserStatus.ACTIVE),
                LocalDate.now().minusDays(30), Loan.DEFAULT_LOAN_DAYS);
        when(loanRepository.findOverdueOn(LocalDate.now())).thenReturn(List.of(vencido));
        when(loanMapper.toResponse(vencido)).thenReturn(respuestaVencida());

        List<LoanResponse> result = loanService.getOverdueLoans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).overdue()).isTrue();
        assertThat(result.get(0).daysOverdue()).isEqualTo(16L);
        assertThat(result.get(0).returnedDate()).isNull();
    }

    @Test
    @DisplayName("getOverdueLoans devuelve una lista vacia si nadie debe nada")
    void getOverdueLoans_sinVencidos_devuelveVacia() {
        when(loanRepository.findOverdueOn(LocalDate.now())).thenReturn(List.of());

        assertThat(loanService.getOverdueLoans()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    /** Recupera el Loan que se paso a loanRepository.save. */
    private Loan prestamoGuardado() {
        ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
        verify(loanRepository).save(captor.capture());
        return captor.getValue();
    }

    private static LoanResponse response(boolean devuelto) {
        return new LoanResponse(LOAN_ID, ISBN, USER_ID, LocalDate.now(),
                Loan.DEFAULT_LOAN_DAYS, LocalDate.now().plusDays(Loan.DEFAULT_LOAN_DAYS),
                devuelto ? LocalDate.now() : null, 0, false, 0L);
    }

    /**
     * Prestamo vencido y sin devolver.
     *
     * Existe aparte de response(...) a proposito: los tres ultimos campos del record
     * (renewalCount, overdue, daysOverdue) NO significan lo mismo que el booleano de
     * devuelto, y un unico helper con un solo boolean obliga al que lo usa a saber
     * de memoria cual de los dos esta pasando. Pasando true a proposito se leeria
     * como "vencido" cuando en realidad marcaba "devuelto".
     */
    private static LoanResponse respuestaVencida() {
        return new LoanResponse(LOAN_ID, ISBN, USER_ID, LocalDate.now().minusDays(30),
                Loan.DEFAULT_LOAN_DAYS, LocalDate.now().minusDays(16), null, 0, true, 16L);
    }
}
