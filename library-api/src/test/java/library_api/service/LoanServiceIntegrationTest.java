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

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integracion de LoanService contra H2.
 *
 * LoanServiceImplTest comprueba las ramas del servicio con los repositorios
 * simulados, pero no ve la base de datos. Aqui se valida lo que solo aparece al
 * escribir de verdad:
 * <ul>
 *   <li>que el prestamo se persiste enlazado al libro y al usuario;</li>
 *   <li>que el contador de ejemplares baja y sube DE VERDAD en la misma
 *       transaccion, no solo en memoria;</li>
 *   <li>que el LoanMapper de MapStruct se registro como bean y devuelve dueDate,
 *       el valor DERIVADO que no existe como columna, y que ese valor sigue siendo
 *       coherente despues de renovar;</li>
 *   <li>que el cupo de prestamos abiertos se mide de verdad sobre la tabla;</li>
 *   <li>que la clave foranea existe: no se puede dejar un Loan apuntando a un
 *       libro o a un usuario inexistente, cosa que el id suelto del modelo
 *       anterior si permitia.</li>
 * </ul>
 *
 * Los tests que comprueban persistencia hacen flush() + clear() antes de releer.
 * Sin el clear(), findById devuelve la entidad cacheada con el valor viejo y el
 * test pasaria aunque el UPDATE no se hubiera escrito.
 *
 * @Transactional hace que cada test corra en su propia transaccion y se deshaga
 * al terminar, dejando la base de datos como estaba.
 */
@SpringBootTest
@Transactional
class LoanServiceIntegrationTest {

    private static final String ISBN = "9780306406157";
    private static final Long USER_ID_INEXISTENTE = 999_999L;
    private static final String ISBN_INEXISTENTE = "9788491050469";
    /**
     * Segundo libro, necesario para los tests de historial y de cupo.
     *
     * El ultimo digito es el de control y esta calculado para que la suma ponderada
     * (pesos 1,3,1,3...) sea multiplo de 10: 978849105047 -> 124 -> digito 6. Book
     * valida el ISBN con @ISBN(ISBN_13) al insertar, asi que un digito de control
     * cualquiera hace fallar el test por el motivo equivocado.
     */
    private static final String ISBN_SEGUNDO_LIBRO = "9788491050476";
    /** Libros adicionales para agotar el cupo, tambien con digito de control valido. */
    private static final List<String> ISBN_PARA_LLENAR_EL_CUPO =
            List.of("9788491050483", "9788491050506", "9788491050513");

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User usuario;
    private Book libro;

    @BeforeEach
    void setUp() {
        usuario = userRepository.save(new User("Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));
        libro = bookRepository.save(
                new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 2));
    }

    private LoanRequest request() {
        return new LoanRequest(ISBN, usuario.getId());
    }

    @Test
    @DisplayName("lendBook persiste el prestamo y descuenta un ejemplar")
    void lendBook_persisteElPrestamoYDescuentaElEjemplar() {
        LoanResponse response = loanService.lendBook(request());

        // El contador se descuenta en la base de datos, no solo en memoria: sin el
        // clear(), findById devolveria la entidad cacheada con el valor viejo y el
        // aserto pasaria aunque el UPDATE no se hubiera escrito.
        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber())
                .isEqualTo(1L);

        Loan persisted = loanRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getBook().getIsbn()).isEqualTo(ISBN);
        assertThat(persisted.getUser().getId()).isEqualTo(usuario.getId());
        assertThat(persisted.getReturnedDate()).isNull();
    }

    @Test
    @DisplayName("lendBook devuelve dueDate aunque no exista esa columna")
    void lendBook_devuelveElVencimientoDerivado() {
        LoanResponse response = loanService.lendBook(request());

        // dueDate es loanDate + loanDays calculado por la entidad. Si apareciera en
        // la respuesta es que MapStruct copio el metodo getDueDate() y que la
        // entidad de verdad responde.
        assertThat(response.dueDate()).isEqualTo(response.loanDate().plusDays(response.loanDays()));
        assertThat(response.loanDays()).isEqualTo(Loan.DEFAULT_LOAN_DAYS);
        assertThat(response.returnedDate()).isNull();
    }

    @Test
    @DisplayName("lendBook deja el prestamo pendiente y contable para el usuario")
    void lendBook_dejaElPrestamoPendiente() {
        loanService.lendBook(request());

        List<Loan> pendientes = loanRepository
                .findByUser_IdAndReturnedDateIsNull(usuario.getId());

        assertThat(pendientes).hasSize(1);
        assertThat(loanRepository.countByUser_IdAndReturnedDateIsNull(usuario.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Prestar dos veces el mismo libro al mismo usuario da 409 y no descuenta dos ejemplares")
    void lendBook_dosVecesElMismoLibro_fallaYNoDescuentaDosVeces() {
        loanService.lendBook(request());

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(LoanAlreadyLoanedException.class);

        // El segundo intento NO puede haber bajado el contador: si lo hiciera, el
        // segundo ejemplar se habria perdido sin que exista prestamo que lo
        // justifique.
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber())
                .isEqualTo(1L);
        assertThat(loanRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Devuelto el libro, el mismo usuario puede volver a llevárselo")
    void lendBook_trasDevolver_elUsuarioPuedeVolverALlevarselo() {
        LoanResponse primero = loanService.lendBook(request());
        Loan loan = loanRepository.findById(primero.id()).orElseThrow();
        loan.markReturned();
        loanRepository.flush();

        LoanResponse segundo = loanService.lendBook(request());

        // El segundo prestamo se crea sin error porque el primero ya no esta
        // abierto: la comprobacion filtra por returnedDate is null.
        assertThat(segundo.id()).isNotEqualTo(primero.id());
        // Los dos prestamos son reales, asi que el contador bajo en los dos casos:
        // el libro tenia 2 ejemplares y los dos se han salido.
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber())
                .isZero();
    }

    @Test
    @DisplayName("Sin ejemplares el contador llega a 0 y el siguiente prestamo falla con 409")
    void lendBook_sinEjemplares_fallaCon409() {
        bookRepository.findByIsbnForUpdate(ISBN).orElseThrow().setAvailableCopyNumber(0);
        bookRepository.flush();

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(NoAvailableCopiesException.class);

        assertThat(loanRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Un usuario inexistente da 404 y no crea ningun prestamo")
    void lendBook_usuarioInexistente_da404() {
        assertThatThrownBy(() -> loanService.lendBook(new LoanRequest(ISBN, USER_ID_INEXISTENTE)))
                .isInstanceOf(UserNotFoundException.class);

        assertThat(loanRepository.findAll()).isEmpty();
        // El contador ni se ha tocado.
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber())
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("Un isbn inexistente da 404 y no crea ningun prestamo")
    void lendBook_isbnInexistente_da404() {
        assertThatThrownBy(() -> loanService.lendBook(new LoanRequest(ISBN_INEXISTENTE, usuario.getId())))
                .isInstanceOf(BookNotFoundException.class);

        assertThat(loanRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Un usuario inactivo da 409 y no crea ningun prestamo")
    void lendBook_usuarioInactivo_da409() {
        usuario.setStatus(UserStatus.SUSPENDED);
        userRepository.flush();

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(UserNotActiveException.class);

        assertThat(loanRepository.findAll()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // lendBook: limite de prestamos abiertos
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Al superar el cupo de prestamos abiertos da 409 y no toca el contador")
    void lendBook_cuandoElCupoEstaAgotado_da409() {
        llenarCupo();

        assertThatThrownBy(() -> loanService.lendBook(request()))
                .isInstanceOf(LoanLimitExceededException.class);

        // Ni el contador ni el historial se han tocado al rechazar el prestamo.
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
        assertThat(loanRepository.countByUser_IdAndReturnedDateIsNull(usuario.getId()))
                .isEqualTo(LoanServiceImpl.MAX_ACTIVE_LOANS);
    }

    @Test
    @DisplayName("Devolver un prestamo libera cupo: el usuario puede volver a pedir hasta el limite")
    void lendBook_devueltoElPrestamo_liberaCupo() {
        llenarCupo();

        // Con el cupo lleno solo queda una salida: devolver algo.
        LoanResponse primero = loanService.getUserLoans(usuario.getId()).get(0);
        loanService.returnBook(primero.id());

        LoanResponse nuevo = loanService.lendBook(request());

        assertThat(nuevo.id()).isNotEqualTo(primero.id());
        assertThat(loanRepository.countByUser_IdAndReturnedDateIsNull(usuario.getId()))
                .isEqualTo(LoanServiceImpl.MAX_ACTIVE_LOANS);
    }

    /**
     * Llena el cupo del usuario de setUp con MAX_ACTIVE_LOANS prestamos de libros
     * distintos, porque prestar dos veces el mismo libro al mismo usuario esta
     * prohibido y ensuciaria el aserto.
     *
     * Los ISBN estan escritos a mano CON su digito de control ya calculado. No es
     * opcional: Book lleva @ISBN(ISBN_13) y el INSERT lo dispara Hibernate al
     * hacer autflush, que es justo lo que hace lendBook al buscar el libro. Un ISBN
     * con el digito de control mal puesto revienta con un
     * ConstraintViolationException desde dentro del service, y el test falla
     * informando de un problema de isbn que no es lo que queria comprobar.
     */
    private void llenarCupo() {
        for (String isbn : ISBN_PARA_LLENAR_EL_CUPO) {
            bookRepository.save(new Book(isbn, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 1));
            loanService.lendBook(new LoanRequest(isbn, usuario.getId()));
        }
    }

    // ---------------------------------------------------------------------
    // returnBook
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("returnBook persiste la devolucion y reingresa el ejemplar en la base de datos")
    void returnBook_persisteLaDevolucionYElContador() {
        LoanResponse creado = loanService.lendBook(request());

        LoanResponse devuelto = loanService.returnBook(creado.id());

        // flush y clear obligan a releer de la base de datos. Sin el clear(),
        // findById devolveria la entidad cacheada con el valor viejo y el test
        // pasaria aunque ni el UPDATE ni el UPDATE del Loan se hubieran escrito.
        entityManager.flush();
        entityManager.clear();

        assertThat(devuelto.returnedDate()).isNotNull();
        assertThat(devuelto.renewalCount()).isZero();

        Loan persistido = loanRepository.findById(creado.id()).orElseThrow();
        assertThat(persistido.isReturned()).isTrue();
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
        assertThat(loanRepository.countByUser_IdAndReturnedDateIsNull(usuario.getId())).isZero();
    }

    @Test
    @DisplayName("Devolver dos veces el mismo prestamo da 409 y NO suma un segundo ejemplar")
    void returnBook_dosVeces_da409YNoSumaEjemplares() {
        LoanResponse creado = loanService.lendBook(request());
        loanService.returnBook(creado.id());

        assertThatThrownBy(() -> loanService.returnBook(creado.id()))
                .isInstanceOf(LoanAlreadyReturnedException.class);

        entityManager.flush();
        entityManager.clear();

        // El motivo de que la API no sea idempotente: si el segundo 200 pasara,
        // el inventario marcaria 3 ejemplares de un libro que solo tenia 2.
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Devolver un prestamo inexistente da 404 y no suma ejemplares")
    void returnBook_prestamoInexistente_da404() {
        assertThatThrownBy(() -> loanService.returnBook(999_999L))
                .isInstanceOf(LoanNotFoundException.class);

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Prestar y devolver deja el contador de ejemplares donde estaba")
    void prestarYDevolver_dejaElContadorIgual() {
        LoanResponse creado = loanService.lendBook(request());   // 2 -> 1
        loanService.returnBook(creado.id());                    // 1 -> 2

        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
    }

    // ---------------------------------------------------------------------
    // renewLoan
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("renewLoan alarga el plazo y el vencimiento queda siempre derivado de loanDays")
    void renewLoan_prorrogaElPlazoYElVencimientoSigueSiendoCoherente() {
        LoanResponse creado = loanService.lendBook(request());

        LoanResponse renovado = loanService.renewLoan(creado.id());

        entityManager.flush();
        entityManager.clear();

        assertThat(renovado.loanDays()).isEqualTo(Loan.DEFAULT_LOAN_DAYS * 2);
        assertThat(renovado.renewalCount()).isEqualTo(1);
        // La invariante clave: dueDate NUNCA se guarda, asi que tiene que seguir
        // siendo loanDate + loanDays despues de la renovacion.
        assertThat(renovado.dueDate())
                .isEqualTo(renovado.loanDate().plusDays(renovado.loanDays()));

        Loan persistido = loanRepository.findById(creado.id()).orElseThrow();
        assertThat(persistido.getRenewalCount()).isEqualTo(1);
        assertThat(persistido.getDueDate()).isEqualTo(renovado.dueDate());
    }

    @Test
    @DisplayName("renewLoan agota Loan.MAX_RENEWALS y despues responde 409")
    void renewLoan_agotaElLimiteDeRenovaciones() {
        LoanResponse creado = loanService.lendBook(request());

        for (int i = 0; i < Loan.MAX_RENEWALS; i++) {
            loanService.renewLoan(creado.id());
        }

        assertThatThrownBy(() -> loanService.renewLoan(creado.id()))
                .isInstanceOf(LoanNotRenewableException.class);

        entityManager.flush();
        entityManager.clear();

        // El ultimo intento rechazado no debe haber_PRORROGADO nada.
        assertThat(loanRepository.findById(creado.id()).orElseThrow().getRenewalCount())
                .isEqualTo(Loan.MAX_RENEWALS);
    }

    @Test
    @DisplayName("renewLoan rechaza un prestamo ya devuelto")
    void renewLoan_prestamoDevuelto_da409() {
        LoanResponse creado = loanService.lendBook(request());
        loanService.returnBook(creado.id());

        assertThatThrownBy(() -> loanService.renewLoan(creado.id()))
                .isInstanceOf(LoanAlreadyReturnedException.class);
    }

    @Test
    @DisplayName("renewLoan no renueva un prestamo vencido: solo alargaria la deuda")
    void renewLoan_prestamoVencido_da409() {
        // Salida hace 30 dias con un plazo de 14: vencio hace 16.
        Loan vencido = loanRepository.save(new Loan(
                libro, usuario, LocalDate.now().minusDays(30), Loan.DEFAULT_LOAN_DAYS));
        entityManager.flush();

        assertThatThrownBy(() -> loanService.renewLoan(vencido.getId()))
                .isInstanceOf(LoanNotRenewableException.class);

        entityManager.flush();
        entityManager.clear();

        assertThat(loanRepository.findById(vencido.getId()).orElseThrow().getRenewalCount()).isZero();
    }

    // ---------------------------------------------------------------------
    // Historial y vencidos
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("getUserLoans devuelve el historial del mas reciente al mas antiguo")
    void getUserLoans_ordenaElHistorial() {
        LoanResponse primero = loanService.lendBook(request());
        LoanResponse segundo = prestarOtroLibro();

        List<LoanResponse> historial = loanService.getUserLoans(usuario.getId());

        // Los dos prestamos tienen la misma loanDate (hoy), asi que el desempate por
        // id descendente es lo que hace el orden estable entre llamadas.
        assertThat(historial).extracting(LoanResponse::id)
                .containsExactly(segundo.id(), primero.id());
    }

    @Test
    @DisplayName("getUserLoans da 404 si el usuario no existe, en vez de una lista vacia")
    void getUserLoans_usuarioInexistente_da404() {
        assertThatThrownBy(() -> loanService.getUserLoans(USER_ID_INEXISTENTE))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("getUserLoans incluye los prestamos ya devueltos: es el historial")
    void getUserLoans_incluyeLosPrestadosYMarcados() {
        LoanResponse lending = loanService.lendBook(request());
        LoanResponse devuelto = prestarOtroLibro();
        loanService.returnBook(lending.id());

        List<LoanResponse> historial = loanService.getUserLoans(usuario.getId());

        assertThat(historial).hasSize(2);
        assertThat(historial).extracting(LoanResponse::id).contains(devuelto.id(), lending.id());
        // Solo uno tiene fecha de devolucion: el que se cerro.
        assertThat(historial).filteredOn(loan -> loan.returnedDate() != null).hasSize(1);
    }

    @Test
    @DisplayName("getOverdueLoans solo lista los prestamos vencidos y sin devolver")
    void getOverdueLoans_soloLosVencidos() {
        LoanResponse vigente = prestarOtroLibro();
        Loan vencido = loanRepository.save(new Loan(
                libro, usuario, LocalDate.now().minusDays(30), Loan.DEFAULT_LOAN_DAYS));
        entityManager.flush();

        List<LoanResponse> vencidos = loanService.getOverdueLoans();

        assertThat(vencidos).extracting(LoanResponse::id).containsExactly(vencido.getId());
        assertThat(vencidos).extracting(LoanResponse::daysOverdue).containsExactly(16L);
        assertThat(vencidos).extracting(LoanResponse::id).doesNotContain(vigente.id());
    }

    @Test
    @DisplayName("getOverdueLoans ordena del mas retrasado al mas reciente")
    void getOverdueLoans_ordenaPorRetraso() {
        User otro = userRepository.save(new User("Luis", "Soto", "Paz", UserStatus.ACTIVE));
        // Vencio hace 16 dias y vencio hace 2: el primero va delante.
        Loan muyVencido = loanRepository.save(new Loan(
                libro, usuario, LocalDate.now().minusDays(30), Loan.DEFAULT_LOAN_DAYS));
        Loan pocoVencido = loanRepository.save(new Loan(
                libro, otro, LocalDate.now().minusDays(16), Loan.DEFAULT_LOAN_DAYS));
        entityManager.flush();

        List<LoanResponse> vencidos = loanService.getOverdueLoans();

        assertThat(vencidos).extracting(LoanResponse::id)
                .containsExactly(muyVencido.getId(), pocoVencido.getId());
    }

    /**
     * Presta un segundo libro distinto al de setUp. Hace falta porque prestar dos
     * veces el mismo libro al mismo usuario esta prohibido y ensuciaria cualquier
     * aserto sobre el historial o el cupo.
     */
    private LoanResponse prestarOtroLibro() {
        bookRepository.save(new Book(ISBN_SEGUNDO_LIBRO, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 1));
        return loanService.lendBook(new LoanRequest(ISBN_SEGUNDO_LIBRO, usuario.getId()));
    }

    @Test
    @DisplayName("Hibernate impide guardar un prestamo de un libro no persistido")
    void hibernateImpideUnPrestamoDeLibroNoPersistido() {        // Con el modelo anterior (un String bookIsbn suelto) este insert se habria
        // guardado sin problema y habria dejado un prestamo huerfano apuntando a un
        // libro inexistente. Ahora hay DOS capas de proteccion y esta es la primera.
        Loan huerfano = new Loan(
                new Book(ISBN_INEXISTENTE, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 1),
                usuario, LocalDate.now(), Loan.DEFAULT_LOAN_DAYS);

        // Hibernate ni siquiera llega a enviar el INSERT: detecta que la referencia
        // es transient y falla antes. Por eso la excepcion NO es una
        // DataIntegrityViolationException, que es la del servidor de base de datos.
        assertThatThrownBy(() -> loanRepository.saveAndFlush(huerfano))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("transient");
    }

    @Test
    @DisplayName("La base de datos rechaza un prestamo de un libro inexistente")
    void laClaveForaneaImpideUnPrestamoDeLibroInexistente() {
        // Segunda capa: se salta a proposito la comprobacion de Hibernate con un
        // INSERT en SQL crudo, para verificar que la restriccion existe de verdad en
        // la tabla y no solo en el ORM.
        //
        // La excepcion que se espera aqui es una PersistenceException de Hibernate y no la
        // DataIntegrityViolationException de Spring: el traductor de excepciones solo
        // envuelve las operaciones que van por el repositorio, y una query nativa
        // creada a mano con EntityManager llega cruda. El mensaje del servidor es el
        // que prueba de forma inequivoca que fallo la FK y no otra restriccion.
        // renewal_count se incluye a proposito con valor 0. Al ser NOT NULL, omitirlo
        // haria fallar el INSERT por esa restriccion y el test pasaria por el motivo
        // equivocado: no estaria probando la clave foranea.
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        insert into loans (loan_date, loan_days, renewal_count, returned_date, book_isbn, user_id)
                        values (DATE '2026-03-01', 14, 0, null, '9788491050469', ?1)
                        """)
                .setParameter(1, usuario.getId())
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("FOREIGN KEY")
                .hasMessageContaining("BOOK_ISBN");

        assertThat(loanRepository.findAll()).isEmpty();
    }
}
