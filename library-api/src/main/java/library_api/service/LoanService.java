package library_api.service;

import java.util.List;

import library_api.dto.LoanRequest;
import library_api.dto.LoanResponse;

/**
 * Contrato de la capa de negocio de prestamos.
 *
 * Igual que en el resto de servicios, no devuelve null ni un boolean: lanza una
 * excepcion de dominio que GlobalExceptionHandler traduce a un codigo HTTP con
 * sentido, de modo que el llamante nunca tiene que interpretar un resultado.
 */
public interface LoanService {

    /**
     * Presta un ejemplar del libro indicado a un usuario y descuenta una unidad
     * del contador de disponibles.
     *
     * Todas las validaciones ocurren antes de escribir nada y dentro de la misma
     * transaccion: si alguna falla, el contador de ejemplares no se toca.
     *
     * @throws library_api.exception.UserNotFoundException si no existe el usuario
     *         (404)
     * @throws library_api.exception.UserNotActiveException si el usuario esta
     *         INACTIVE o SUSPENDED (409)
     * @throws library_api.exception.LoanLimitExceededException si el usuario ya
     *         tiene demasiados prestamos abiertos (409)
     * @throws library_api.exception.BookNotFoundException si no existe el isbn
     *         (404)
     * @throws library_api.exception.NoAvailableCopiesException si el libro existe
     *         pero no queda ningun ejemplar disponible (409)
     * @throws library_api.exception.LoanAlreadyLoanedException si el usuario ya
     *         tiene ese libro prestado y sin devolver (409)
     */
    LoanResponse lendBook(LoanRequest request);

    /**
     * Registra la devolucion de un prestamo y devuelve el ejemplar al contador de
     * disponibles.
     *
     * @throws library_api.exception.LoanNotFoundException si no existe el prestamo
     *         (404)
     * @throws library_api.exception.LoanAlreadyReturnedException si el prestamo ya
     *         estaba devuelto (409). No se responde 200 porque un reintento del
     *         cliente no debe volver a sumar un ejemplar al contador
     */
    LoanResponse returnBook(Long loanId);

    /**
     * Prorroga el prestamo DEFAULT_LOAN_DAYS mas, salvo que ya venció o se
     * agotaron las renovaciones.
     *
     * @throws library_api.exception.LoanNotFoundException si no existe el prestamo
     *         (404)
     * @throws library_api.exception.LoanAlreadyReturnedException si el prestamo ya
     *         estaba devuelto (409)
     * @throws library_api.exception.LoanNotRenewableException si esta vencido o ha
     *         agotado Loan.MAX_RENEWALS renovaciones (409)
     */
    LoanResponse renewLoan(Long loanId);

    /**
     * @throws library_api.exception.LoanNotFoundException si no existe el prestamo
     */
    LoanResponse getLoan(Long loanId);

    /**
     * Historial completo de prestamos de un usuario, del mas reciente al mas
     * antiguo, incluidos los ya devueltos.
     *
     * @throws library_api.exception.UserNotFoundException si no existe el usuario
     *         (404). Se comprueba para que un id equivocado no se traduzca en un
     *         historial vacio que el cliente interpretaria como "no tiene prestamos"
     */
    List<LoanResponse> getUserLoans(Long userId);

    /**
     * Prestamos sin devolver que han pasado su fecha limite, del que mas retrasado
     * al mas reciente. Es la vista de trabajo del bibliotecario.
     */
    List<LoanResponse> getOverdueLoans();
}
