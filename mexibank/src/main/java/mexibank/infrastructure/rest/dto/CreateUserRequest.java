package mexibank.infrastructure.rest.dto;

import java.time.Instant;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de la peticion de alta de usuario.
 *
 * <p><strong>Por que los roles llegan como cadenas y no como el enum.</strong> El
 * enum {@code Role} es un tipo Java y no existe en el mundo exterior del proceso.
 * Si el borde usara el enum, Jackson deserializaria el rol por nombre y, ante un
 * rol desconocido, fallaria con un error de deserializacion que ni el cliente ni
 * el servidor entendieron. Con cadenas, la validacion es explicita y el mensaje
 * puede decir que roles existen, que es justo lo que un administrador necesita
 * para corregir la peticion.
 *
 * <p><strong>Por que la contrasena lleva {@code @Size}.</strong> No suplanta a
 * {@code PasswordPolicy}, que es la garantia real. Solo evita mandar 10 MB de
 * contrasena para que el servidor la rechace. El limite superior coincide con el
 * del dominio (72, el tope de BCrypt) para que un cliente que se pasa reciba un
 * error de validacion y no el error opaco del hasher.
 *
 * @param email    correo del nuevo usuario
 * @param password contrasena en claro
 * @param roles    roles a asignar. Vacio significa el rol por defecto
 */
@Schema(description = "Peticion de alta de usuario")
public record CreateUserRequest(

        @NotBlank(message = "El correo electronico es obligatorio")
        @Schema(description = "Correo del nuevo usuario", example = "nuevo@correo.com")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(
                min = mexibank.domain.user.PasswordPolicy.MIN_LENGTH,
                max = mexibank.domain.user.PasswordPolicy.MAX_LENGTH,
                message = "La contrasena debe tener entre "
                        + mexibank.domain.user.PasswordPolicy.MIN_LENGTH + " y "
                        + mexibank.domain.user.PasswordPolicy.MAX_LENGTH + " caracteres")
        @Schema(
                description = "Contrasena en claro",
                example = "OtraContrasenaLarga",
                minLength = mexibank.domain.user.PasswordPolicy.MIN_LENGTH,
                maxLength = mexibank.domain.user.PasswordPolicy.MAX_LENGTH)
        String password,

        @Schema(
                description = "Roles a asignar. Ausente o vacio significa el rol por defecto CUSTOMER",
                example = "[\"TELLER\"]")
        Set<String> roles) {
}