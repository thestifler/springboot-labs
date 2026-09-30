package library_api.entity;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de la logica de Loan.
 *
 * No levantan el contexto de Spring ni tocan la base de datos: la entidad no
 * tiene dependencias, asi que se instancia directamente. Eso permite comprobar
 * el calculo de fechas fijando la fecha de referencia como parametro
 * (isOverdueOn / getDaysOverdueOn) en lugar de depender de LocalDate.now(), con
 * lo que el test no puede romperse al cambiar de dia.
 *
 * Los casos fijan el comportamiento que antes no existia en la entidad:
 * la fecha limite derivada, el vencimiento y la devolucion idempotente.
 */
class LoanTest {

    private static final LocalDate HOY = LocalDate.of(2026, 3, 1);

    private static Book libro() {
        return new Book("9780306406157", "García Márquez", "Cien años de soledad",
                LocalDate.of(1967, 5, 30), 3L);
    }

    private static Loan prestamo(LocalDate loanDate, int loanDays) {
        return new Loan(libro(), new User("Ana", "Gomez", "Ruiz", UserStatus.ACTIVE),
                loanDate, loanDays);
    }

    @Test
    @DisplayName("getDueDate suma los dias del prestamo a la fecha de salida")
    void getDueDate_sumaLoanDaysALaFechaDeSalida() {
        Loan loan = prestamo(HOY, 14);

        assertThat(loan.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    @DisplayName("un prestamo recien creado esta activo y no devuelto")
    void prestamoNuevo_estaActivoYNoDevuelto() {
        Loan loan = prestamo(HOY, 14);

        assertThat(loan.isActive()).isTrue();
        assertThat(loan.isReturned()).isFalse();
        assertThat(loan.getReturnedDate()).isNull();
    }

    @Test
    @DisplayName("devolver el prestamo el mismo dia del vencimiento no lo marca vencido")
    void devolverEnLaFechaLimite_noEstaVencido() {
        Loan loan = prestamo(HOY, 14);

        assertThat(loan.isOverdueOn(LocalDate.of(2026, 3, 15))).isFalse();
        assertThat(loan.getDaysOverdueOn(LocalDate.of(2026, 3, 15))).isZero();
    }

    @Test
    @DisplayName("un prestamo pasado de fecha se cuenta en dias de calendario")
    void prestamoVencido_cuentaLosDiasDeRetraso() {
        Loan loan = prestamo(HOY, 14);

        assertThat(loan.isOverdueOn(LocalDate.of(2026, 3, 18))).isTrue();
        assertThat(loan.getDaysOverdueOn(LocalDate.of(2026, 3, 18))).isEqualTo(3L);
    }

    @Test
    @DisplayName("marcar devuelto registra la fecha y cierra el prestamo")
    void markReturned_registraLaFechaYCierraElPrestamo() {
        Loan loan = prestamo(HOY, 14);
        LocalDate devolucion = LocalDate.of(2026, 3, 10);

        loan.markReturned(devolucion);

        assertThat(loan.getReturnedDate()).isEqualTo(devolucion);
        assertThat(loan.isReturned()).isTrue();
        assertThat(loan.isActive()).isFalse();
    }

    @Test
    @DisplayName("una devolucion repetida no pisa la fecha real de devolucion")
    void markReturned_porSegundaVezNoCambiaLaFecha() {
        Loan loan = prestamo(HOY, 14);
        LocalDate primeraDevolucion = LocalDate.of(2026, 3, 10);

        loan.markReturned(primeraDevolucion);
        loan.markReturned(LocalDate.of(2026, 4, 1));

        assertThat(loan.getReturnedDate()).isEqualTo(primeraDevolucion);
    }

    @Test
    @DisplayName("un libro devuelto tarde deja de contar como vencido")
    void devueltoTarde_noCuentaComoVencido() {
        Loan loan = prestamo(HOY, 14);

        loan.markReturned(LocalDate.of(2026, 3, 25));

        assertThat(loan.isOverdueOn(LocalDate.of(2026, 3, 26))).isFalse();
        assertThat(loan.getDaysOverdueOn(LocalDate.of(2026, 3, 26))).isZero();
    }

    @Test
    @DisplayName("el constructor corto aplica la duracion por defecto y la fecha de hoy")
    void constructorCorto_usaLaDuracionPorDefecto() {
        Loan loan = new Loan(libro(), new User("Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));

        assertThat(loan.getLoanDays()).isEqualTo(Loan.DEFAULT_LOAN_DAYS);
        assertThat(loan.getLoanDate()).isEqualTo(LocalDate.now());
    }

    // ---------------------------------------------------------------------
    // Renovacion
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("un prestamo nuevo no ha sido renovado nunca")
    void prestamoNuevo_noHaSidoRenovado() {
        Loan loan = prestamo(HOY, 14);

        assertThat(loan.getRenewalCount()).isZero();
        assertThat(loan.hasReachedRenewalLimit()).isFalse();
    }

    @Test
    @DisplayName("renew alarga el plazo y cuenta la renovacion")
    void renew_alargaElPlazoYCuentaLaRenovacion() {
        Loan loan = prestamo(HOY, 14);

        loan.renew(14);

        assertThat(loan.getLoanDays()).isEqualTo(28);
        assertThat(loan.getRenewalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("tras renovar, el vencimiento sigue siendo loanDate + loanDays")
    void renew_mantieneLaInvarianteDelVencimiento() {
        Loan loan = prestamo(HOY, 14);

        loan.renew(14);

        // Es LA garantia del diseno: prorregar no guarda una fecha nueva, suma dias
        // al plazo, asi que el vencimiento derivado no puede desincronizarse.
        assertThat(loan.getDueDate()).isEqualTo(HOY.plusDays(28));
        assertThat(loan.getDueDate())
                .isEqualTo(loan.getLoanDate().plusDays(loan.getLoanDays()));
    }

    @Test
    @DisplayName("el limite de renovaciones se alcanza pero no se sobrepasa solo")
    void hasReachedRenewalLimit_seActivaEnElUltimoPaso() {
        Loan loan = prestamo(HOY, 14);

        for (int i = 0; i < Loan.MAX_RENEWALS; i++) {
            assertThat(loan.hasReachedRenewalLimit())
                    .as("todavia quedan renovaciones en la iteracion %d", i)
                    .isFalse();
            loan.renew(14);
        }

        assertThat(loan.hasReachedRenewalLimit()).isTrue();
        assertThat(loan.getRenewalCount()).isEqualTo(Loan.MAX_RENEWALS);
    }

    @Test
    @DisplayName("renovar dos veces duplica el plazo inicial")
    void renew_variasVeces_acumulaLosDias() {
        Loan loan = prestamo(HOY, 14);

        loan.renew(14);
        loan.renew(14);

        assertThat(loan.getLoanDays()).isEqualTo(14 + 14 * 2);
        assertThat(loan.getDueDate()).isEqualTo(HOY.plusDays(42));
    }

    @Test
    @DisplayName("renovar un prestamo ya devuelto no cambia su fecha de devolucion")
    void renew_sobreUnPrestamoDevuelto_noAfectaALaDevolucion() {
        Loan loan = prestamo(HOY, 14);
        LocalDate devolucion = LocalDate.of(2026, 3, 5);

        loan.markReturned(devolucion);
        loan.renew(14);

        // La entidad no veta la renovacion: es la regla de negocio y vive en el
        // service. Lo que se comprueba aqui es que al menos la devolucion, que es
        // el hecho que ya ocurrio, no se altera.
        assertThat(loan.getReturnedDate()).isEqualTo(devolucion);
    }
}
