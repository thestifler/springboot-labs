package mexibank.infrastructure.rest.dto;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;

import mexibank.application.user.UserView;

/**
 * Respuesta de la API para un usuario.
 *
 * <p><strong>Por que no se serializa el agregado directamente.</strong> El agregado
 * {@code User} tiene un hash de contrasena. Si se serializara, cada respuesta
 * incluiria ese hash, y con el se puede montar un ataque de diccionario offline. Es
 * un fallo grave que no se manifiesta como excepcion ni como test rojo: la API
 * funciona perfectamente y devuelve un secreto.
 *
 * <p><strong>Por que se proyecta desde {@link UserView} y no desde
 * {@code User}.</strong> Es la tercera proyeccion del mismo dato (agregado, vista de
 * aplicacion, respuesta HTTP) y se ve redundante. La cadena tiene un motivo:
 * cada capa expone solo lo que su consumidor necesita, y anadir un campo a la vista
 * de aplicacion no lo hace aparecer en la API sin que alguien lo decida
 * explicitamente. Con una sola proyeccion, cualquier campo nuevo sale por
 * defecto.
 *
 * <p><strong>Por que los roles viajan como cadenas.</strong> Es lo que espera el
 * JSON. El enum se convierte con {@code name()} y el cliente recibe
 * {@code "ADMIN"}.
 *
 * @param id        identificador del usuario, en texto
 * @param email     correo electronico
 * @param roles     roles asignados, como nombres de enum
 * @param active    si la cuenta esta activa
 * @param createdAt fecha de alta, en ISO-8601
 */
@Schema(description = "Datos publicos de un usuario")
public record UserResponse(
        @Schema(description = "Identificador del usuario", example = "6f0a1c2e-7b3d-4a91-9f52-1c0e8a4d2b77")
        String id,

        @Schema(description = "Correo electronico", example = "ana@correo.com")
        String email,

        @Schema(description = "Roles asignados, como nombres de enum", example = "[\"CUSTOMER\"]")
        Set<String> roles,

        @Schema(description = "Si la cuenta esta activa", example = "true")
        boolean active,

        @Schema(description = "Fecha de alta en ISO-8601", example = "2026-03-01T10:15:30Z")
        Instant createdAt) {

    /**
     * Proyecta la vista de aplicacion a la respuesta HTTP.
     *
     * @param view vista publica del usuario
     */
    public static UserResponse from(UserView view) {
        return new UserResponse(
                view.id().value().toString(),
                view.email(),
                view.roles().stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet()),
                view.active(),
                view.createdAt());
    }
}