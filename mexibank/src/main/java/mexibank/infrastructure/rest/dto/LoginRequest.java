package mexibank.infrastructure.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de la peticion de login.
 *
 * <p><strong>Por que {@code @Email} de Bean Validation y no solo el value object
 * {@code Email}.</strong> Son dos cosas distintas. El value object es la garantia:
 * ningun camino de entrada puede crear un usuario con un correo invalido. El
 * validador del borde es la experiencia de usuario: sin el, un correo mal escrito
 * devolveria 401 ("credenciales no validas"), que es una respuesta desconcertante
 * para alguien que solo se ha equivocado al escribir su propia direccion.
 *
 * <p><strong>Por que el controlador valida y el dominio tambien.</strong> No es
 * redundancia gratuita. El borde acepta mas entradas de las que el dominio suele
 * ver (formularios, scripts, importaciones), y su trabajo es dar un error claro y
 * temprano. El dominio mantiene su garantia porque el mismo dato puede entrar por
 * una consola o por un evento que no pasan por aqui. Si el borde no validara,
 * esos otros caminos seguirian protegidos; si el dominio no validara, este
 * controllable dejaria de ser opcional.
 *
 * <p><strong>Por que el campo se llama {@code email} y no {@code correo}.</strong>
 * El JSON que viaja por la red es un contrato publico: es lo que escriben los
 * clientes y lo que leen otras personas. Los nombres de campo de una API deben ser
 * los del ecosistema al que pertenece, no los del idioma del equipo que lo escribe.
 *
 * @param email    correo electronico del usuario
 * @param password contrasena en claro. Viaja por HTTPS y no se registra en ningun log
 */
public record LoginRequest(

        @NotBlank(message = "El correo electronico es obligatorio")
        @Email(message = "El correo electronico no tiene un formato valido")
        @Schema(description = "Correo electronico del usuario", example = "ana@correo.com")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        @Schema(description = "Contrasena en claro", example = "UnaContrasenaLarga")
        String password) {
}