package mexibank.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import mexibank.application.auth.AuthResponse;

/**
 * Respuesta HTTP del login.
 *
 * <p><strong>Por que existe y no se serializa {@link AuthResponse}.</strong>
 * {@code AuthResponse} es una respuesta de la capa de aplicacion y su campo
 * {@code user} es un {@code UserView}, cuyos identificadores son objetos de
 * dominio: al serializarlo, el id del usuario salia como {@code {"value": "..."}}
 * mientras que el alta de usuario devolvia {@code "id": "..."}. Dos endpoints que
 * devuelven el mismo usuario no pueden tener dos formas distintas: el cliente
 * tendria que escribir dos deserializadores para el mismo dato. Aqui el usuario se
 * proyecta con {@link UserResponse}, que es la forma publica unica.
 *
 * <p><strong>Por que se mantiene {@link AuthResponse} en aplicacion.</strong>
 * Porque es la vista de la capa de aplicacion, reutilizable desde otro adaptador
 * (un futuro endpoint gRPC, por ejemplo) que no serialice a JSON. Traducirla a la
 * forma HTTP concreta es trabajo de este borde, no suyo.
 *
 * @param accessToken token listo para enviar como {@code Bearer <token>}
 * @param tokenType   tipo de token, siempre {@code Bearer}
 * @param expiresIn   segundos hasta la caducidad
 * @param user        datos publicos del usuario autenticado
 */
@Schema(description = "Sesion iniciada")
public record LoginResponse(
        @Schema(description = "Token JWT, listo para enviar como 'Authorization: Bearer <token>'",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2ZjBh...")
        String accessToken,

        @Schema(description = "Tipo de token; siempre Bearer", example = "Bearer")
        String tokenType,

        @Schema(description = "Segundos hasta la caducidad del token", example = "900")
        long expiresIn,

        @Schema(description = "Datos publicos del usuario autenticado")
        UserResponse user) {

    /**
     * Proyecta la respuesta de aplicacion a la respuesta HTTP.
     *
     * @param auth respuesta del caso de uso de login
     * @return la respuesta con la forma publica de usuario
     */
    public static LoginResponse from(AuthResponse auth) {
        return new LoginResponse(
                auth.accessToken(),
                auth.tokenType(),
                auth.expiresIn(),
                UserResponse.from(auth.user()));
    }
}
