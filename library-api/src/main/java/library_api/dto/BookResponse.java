package library_api.dto;

import java.time.LocalDate;

/**
 * Datos de salida de un libro.
 *
 * Expone todos los campos de la entidad, incluido el isbn (clave primaria) y el
 * numero de copias disponibles.
 */
public record BookResponse(
        String isbn,
        String author,
        String title,
        LocalDate publicationDate,
        long availableCopyNumber
) {
}
