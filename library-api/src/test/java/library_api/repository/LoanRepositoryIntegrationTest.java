package library_api.repository;

import java.time.LocalDate;
import java.util.List;

import library_api.entity.Book;
import library_api.entity.Loan;
import library_api.entity.User;
import library_api.entity.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integracion de las consultas de Loan contra H2.
 *
 * A diferencia de LoanTest, aqui no se comprueba el calculo en memoria sino que
 * la consulta se traduce a SQL valido y filtra lo que debe. Es imprescindible
 * porque la forma de encontrar los prestamos vencidos depende de una expresion
 * HQL sobre loan_date + loan_days: si esa expresion no compiles o no respectara la
 * nulidad de returned_date, el filtro "no lo han devuelto" devolveria prestamos
 * ya cerrados y la validacioneria por completo.
 *
 * @Transactional deja cada test en su propia transaccion y la deshace al
 * terminar, por lo que los datos de un test no contaminan al siguiente.
 */
@SpringBootTest
@Transactional
class LoanRepositoryIntegrationTest {

    private static final String ISBN = "9780306406157";
    private static final LocalDate HOY = LocalDate.of(2026, 3, 1);

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    private User usuario;

    @BeforeEach
    void setUp() {
        usuario = userRepository.save(new User("Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));
        bookRepository.save(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 5));
    }

    private Loan crearPrestamo(LocalDate loanDate, int loanDays) {
        return loanRepository.save(new Loan(
                bookRepository.findById(ISBN).orElseThrow(),
                usuario, loanDate, loanDays));
    }

    @Test
    @DisplayName("un prestamo sin returned_date aparece como pendiente del usuario")
    void prestamoSinDevolver_apareceComoPendiente() {
        Loan loan = crearPrestamo(HOY.minusDays(30), 14);

        List<Loan> pendientes = loanRepository
                .findByUser_IdAndReturnedDateIsNull(usuario.getId());

        assertThat(pendientes).extracting(Loan::getId).containsExactly(loan.getId());
        assertThat(loanRepository.countByUser_IdAndReturnedDateIsNull(usuario.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("tras markReturned el prestamo deja de contar como pendiente")
    void prestamoDevuelto_dejaDeContarComoPendiente() {
        Loan loan = crearPrestamo(HOY.minusDays(30), 14);

        loan.markReturned(HOY.minusDays(5));
        loanRepository.flush();

        assertThat(loanRepository.findByUser_IdAndReturnedDateIsNull(usuario.getId())).isEmpty();
        assertThat(loanRepository.existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(
                usuario.getId(), ISBN)).isFalse();
    }

    @Test
    @DisplayName("existsBy... evita prestar dos veces el mismo libro al mismo usuario")
    void existsBy_detectaElLibroYaPrestado() {
        crearPrestamo(HOY, 14);

        boolean yaPrestado = loanRepository
                .existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(usuario.getId(), ISBN);

        assertThat(yaPrestado).isTrue();
    }

    @Test
    @DisplayName("un prestamo pasado de fecha y sin devolver sale como vencido")
    void prestamoVencidoYSinDevolver_apareceEnFindOverdueOn() {
        // Salida hace 30 dias con 14 de plazo: vencia hace 16 dias.
        Loan vencido = crearPrestamo(HOY.minusDays(30), 14);

        List<Loan> vencidos = loanRepository.findOverdueOn(HOY);

        assertThat(vencidos).extracting(Loan::getId).containsExactly(vencido.getId());
        // Y el calculo de la entidad coincide con el de la consulta.
        assertThat(vencido.getDaysOverdueOn(HOY)).isEqualTo(16L);
    }

    @Test
    @DisplayName("un prestamo vigente no sale como vencido")
    void prestamoVigente_noApareceEnFindOverdueOn() {
        crearPrestamo(HOY.minusDays(3), 14);

        assertThat(loanRepository.findOverdueOn(HOY)).isEmpty();
    }

    @Test
    @DisplayName("devolver el libro el dia del vencimiento no lo marca vencido")
    void devolverEnLaFechaLimite_noSaleComoVencido() {
        // Vence hoy: la comparacion es estricta, asi que no debe salir.
        crearPrestamo(HOY.minusDays(14), 14);

        assertThat(loanRepository.findOverdueOn(HOY)).isEmpty();
    }

    @Test
    @DisplayName("un prestamo devuelto tarde NO sale como vencido aunque su plazo haya pasado")
    void devueltoTarde_noApareceEnFindOverdueOn() {
        Loan loan = crearPrestamo(HOY.minusDays(30), 14);
        loan.markReturned(HOY.minusDays(2));
        loanRepository.flush();

        // El plazo venció hace 16 días, pero el libro ya esta devuelto: el filtro
        // por returned_date is null es lo que lo deja fuera.
        assertThat(loanRepository.findOverdueOn(HOY)).isEmpty();
        assertThat(loan.isOverdueOn(HOY)).isFalse();
    }

    @Test
    @DisplayName("findByIdAndReturnedDateIsNull no encuentra un prestamo ya devuelto")
    void buscarPrestadoPorId_devueltoDaVacio() {
        Loan loan = crearPrestamo(HOY.minusDays(30), 14);

        assertThat(loanRepository.findByIdAndReturnedDateIsNull(loan.getId())).isPresent();

        loan.markReturned(HOY.minusDays(1));
        loanRepository.flush();

        assertThat(loanRepository.findByIdAndReturnedDateIsNull(loan.getId())).isEmpty();
    }

    // ---------------------------------------------------------------------
    // Orden de findOverdueOn
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("findOverdueOn ordena del mas retrasado al mas reciente")
    void findOverdueOn_ordenaPorRetraso() {
        // Vencio hace 16 dias, hace 2 y hoy mismo vencia (no entra por el filtro
        // estricto). El orden lo pone la consulta, no un sort en Java.
        Loan muyVencido = crearPrestamo(HOY.minusDays(30), 14);
        Loan pocoVencido = crearPrestamo(HOY.minusDays(16), 14);
        crearPrestamo(HOY.minusDays(14), 14); // vence hoy: se queda fuera

        List<Loan> vencidos = loanRepository.findOverdueOn(HOY);

        // Si el ORDER BY con timestampadd no se aplicara, H2 devolveria las filas
        // en orden de insercion y este aserto fallaria.
        assertThat(vencidos).extracting(Loan::getId)
                .containsExactly(muyVencido.getId(), pocoVencido.getId());
    }

    // ---------------------------------------------------------------------
    // Listado del historial
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("findByUser_IdOrdena el historial del mas reciente al mas antiguo")
    void listarHistorial_ordenaDelRecienteAlAntiguo() {
        Loan antiguo = crearPrestamo(HOY.minusDays(20), 14);
        Loan medio = crearPrestamo(HOY.minusDays(10), 14);
        Loan reciente = crearPrestamo(HOY.minusDays(1), 14);

        List<Loan> historial = loanRepository.findByUser_IdOrderByLoanDateDescIdDesc(usuario.getId());

        assertThat(historial).extracting(Loan::getId)
                .containsExactly(reciente.getId(), medio.getId(), antiguo.getId());
    }

    @Test
    @DisplayName("Dos prestamos del mismo dia se ordenan de forma estable por id descendente")
    void listarHistorial_desempataPorId() {
        Loan primero = crearPrestamo(HOY, 14);
        Loan segundo = crearPrestamo(HOY, 14);

        // Sin el desempate por id, el orden de dos filas con la misma loanDate
        // dependeria del plan de ejecucion y podria cambiar entre llamadas.
        assertThat(loanRepository.findByUser_IdOrderByLoanDateDescIdDesc(usuario.getId()))
                .extracting(Loan::getId)
                .containsExactly(segundo.getId(), primero.getId());
    }

    @Test
    @DisplayName("El historial incluye los prestamos devueltos y los de otros usuarios no")
    void listarHistorial_incluyeDevueltosYNoMezclaUsuarios() {
        Loan mio = crearPrestamo(HOY.minusDays(5), 14);
        Loan mioDevuelto = crearPrestamo(HOY.minusDays(30), 14);
        mioDevuelto.markReturned(HOY.minusDays(1));

        User otro = userRepository.save(new User("Luis", "Soto", "Paz", UserStatus.ACTIVE));
        Loan suyo = loanRepository.save(new Loan(
                bookRepository.findById(ISBN).orElseThrow(), otro, HOY, 14));
        loanRepository.flush();

        List<Loan> historial = loanRepository.findByUser_IdOrderByLoanDateDescIdDesc(usuario.getId());

        assertThat(historial).extracting(Loan::getId)
                .containsExactly(mio.getId(), mioDevuelto.getId())
                .doesNotContain(suyo.getId());
    }

    @Test
    @DisplayName("El historial de un usuario sin prestamos sale vacio, no con datos de otro")
    void listarHistorial_usuarioSinPrestamos_saleVacio() {
        User sinPrestamos = userRepository.save(new User("Eva", "Mora", "Gil", UserStatus.ACTIVE));
        loanRepository.flush();

        crearPrestamo(HOY, 14);

        assertThat(loanRepository.findByUser_IdOrderByLoanDateDescIdDesc(sinPrestamos.getId()))
                .isEmpty();
    }
}
