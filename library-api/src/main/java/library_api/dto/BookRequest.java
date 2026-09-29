package library_api.dto;

import java.time.LocalDate;

import org.hibernate.validator.constraints.ISBN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada para dar de alta un libro.
 *
 * El isbn si forma parte del request porque es la clave primaria asignada por el
 * cliente, a diferencia de User, donde el id lo genera la base de datos.
 *
 * Los campos ausentes se validan en el borde (aqui), no en la entidad: una
 * peticion invalida se rechaza con un 400 y el detalle por campo sin llegar a
 * tocar la base de datos.
 */
public record BookRequest(

        @NotBlank(message = "el isbn es obligatorio")
        @ISBN(type = ISBN.Type.ISBN_13, message = "el ISBN-13 introducido no es valido")
        String isbn,

        @NotBlank(message = "el author es obligatorio")
        @Size(max = 100, message = "el author no puede exceder 100 caracteres")
        String author,

        @NotBlank(message = "el title es obligatorio")
        @Size(max = 100, message = "el title no puede exceder 100 caracteres")
        String title,

        @PastOrPresent(message = "la fecha de publicacion no puede ser futura")
        LocalDate publicationDate,

        /*
         * Wrapper y no primitivo a proposito. Jackson falla con un 500
         * ("Cannot map null into type long") cuando un long primitivo no llega en
         * el JSON, y ese fallo ocurre al deserializar, antes de que Bean Validation
         * pueda actuar. Con Long el campo ausente llega como null y lo rechaza el
         * @NotNull de abajo, con un 400 y el detalle por campo, que es el
         * contrato que el cliente espera. La entidad si lo declara como long
         * primitivo: ahi el valor siempre llega ya validado.
         */
        @NotNull(message = "el avaliableCopyNumber es obligatorio")
        @PositiveOrZero(message = "el avaliableCopyNumber no puede ser negativo")
        Long avaliableCopyNumber
) {
}
