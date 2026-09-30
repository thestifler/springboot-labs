package library_api.dto;

import java.time.LocalDate;

/**
 * Datos de salida de un prestamo.
 *
 * Expone el estado completo del prestamo para que el cliente no tenga que
 * recomputarlo: incluye dueDate, que en la entidad es un valor DERIVADO
 * (loanDate + loanDays) y unicamente se materializa aqui. returnedDate a null
 * significa que el libro sigue prestado.
 *
 * Se responden isbn y userId en lugar de la entidad Book o User anidada: son las
 * referencias que el cliente ya envio y asi el DTO no arrastra el expediente
 * completo del libro o del usuario ni carga de mas las relaciones LAZY.
 */
public record LoanResponse(
        Long id,
        String isbn,
        Long userId,
        LocalDate loanDate,
        int loanDays,
        LocalDate dueDate,
        LocalDate returnedDate,
        int renewalCount,
        boolean overdue,
        long daysOverdue
) {
}
