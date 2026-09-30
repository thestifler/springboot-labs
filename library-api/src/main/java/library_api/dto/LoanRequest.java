package library_api.dto;

import org.hibernate.validator.constraints.ISBN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Datos de entrada para prestar un libro concreto a un usuario.
 *
 * Se pide el isbn y el id del usuario, no las entidades Book y User. Por eso este
 * record NO se mapea a Loan con MapStruct (eso exigiria convertir un String en
 * una entidad dentro del mapper) y el servicio construye el Loan con las
 * entidades ya cargadas por el repositorio.
 *
 * El id del usuario va como Long wrapper y no como long primitivo por el motivo
 * ya documentado en BookRequest: si el campo no llega en el JSON, Jackson falla
 * con un 500 al deserializar, antes de que Bean Validation pueda actuar. Con el
 * wrapper llega null y lo rechaza el @NotNull con un 400 y el detalle por campo.
 *
 * loanDays no forma parte del request: el prestamo usa Loan.DEFAULT_LOAN_DAYS.
 * Anadir la duracion como campo opcional se dejaria para cuando exista el caso de
 * uso (prestamos extendidos), porque un parametro opcional que casi siempre vale
 * lo mismo solo anade ramas que probar.
 */
public record LoanRequest(

        @NotBlank(message = "el isbn es obligatorio")
        @ISBN(type = ISBN.Type.ISBN_13, message = "el ISBN-13 introducido no es valido")
        String isbn,

        @NotNull(message = "el userId es obligatorio")
        Long userId
) {
}
