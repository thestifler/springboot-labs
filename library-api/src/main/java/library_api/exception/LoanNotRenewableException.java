package library_api.exception;

/**
 * Excepcion de dominio para "este prestamo no se puede renovar".
 *
 * Hay dos motivos distintos y se reporta el que aplica en cada caso porque el
 * mensaje es lo que el cliente muestra:
 * <ul>
 *   <li>el prestamo ya venció: renewing un libro que se debía hace días solo
 *       alarga la deuda, asi que la politica es que primero se devuelva;</li>
 *   <li>se alcanzo Loan.MAX_RENEWALS.</li>
 * </ul>
 *
 * GlobalExceptionHandler la traduce a 409 Conflict: el prestamo existe y la
 * peticion es valida, pero choca con el estado en que se encuentra.
 */
public class LoanNotRenewableException extends RuntimeException {

    private final Long id;

    public LoanNotRenewableException(Long id, String motivo) {
        super("El prestamo con id: " + id + " no se puede renovar: " + motivo);
        this.id = id;
    }

    /**
     * El prestamo ya habia pasado su fecha limite.
     */
    public static LoanNotRenewableException porVencimiento(Long id) {
        return new LoanNotRenewableException(id, "el prestamo ya esta vencido");
    }

    /**
     * El prestamo ya consumio todas sus renovaciones.
     */
    public static LoanNotRenewableException porLimiteDeRenovaciones(Long id, int maxRenewals) {
        return new LoanNotRenewableException(id, "ha agotado sus " + maxRenewals + " renovaciones");
    }

    public Long getId() {
        return id;
    }
}
