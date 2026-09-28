package library_api.dto;

import library_api.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de entrada para crear un usuario.
 *
 * No incluye id a proposito: el identificador lo genera la base de datos
 * (GenerationType.IDENTITY). Exponerlo en el request invitaba al cliente a
 * mandar un id falso, lo que llevaba a Spring Data por la ruta merge() en lugar
 * de persist() (un SELECT extra y un UPDATE en vez de un INSERT).
 */
public record UserRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 100, message = "el nombre no puede exceder 100 caracteres")
        String name,

        @NotBlank(message = "el primer apellido es obligatorio")
        @Size(max = 100, message = "el primer apellido no puede exceder 100 caracteres")
        String firstLastName,

        @Size(max = 100, message = "el segundo apellido no puede exceder 100 caracteres")
        String secondLastName,

        UserStatus status
) {
}
