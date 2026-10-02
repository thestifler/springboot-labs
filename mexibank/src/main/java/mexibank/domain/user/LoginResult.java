package mexibank.domain.user;

import java.time.Instant;

/**
 * Resultado de un login correcto: el token y el usuario autenticado.
 *
 * <p><strong>Por que lleva el {@link User} entero y no campos sueltos.</strong>
 * Una version anterior de este record llevaba por separado el id, el correo y
 * los roles, y no el {@code createdAt}. El resultado era un {@code null} en un
 * campo que jamas se llenaba: un dato que no se sabe si falta porque no importa
 * o porque se olvido. Llevar el agregado entero hace imposible esa situacion.
 *
 * <p><strong>Por que el token es un {@code String} y no un
 * {@link AccessToken}.</strong> El token ya se emitio dentro de este mismo caso
 * de uso, asi que aqui no hay nada que el servidor no sepa. Lo que sale hacia
 * fuera es exactamente el texto que va en la cabecera {@code Authorization}, y la
 * caducidad tambien, porque el cliente necesita saber cuando renovar.
 *
 * <p><strong>Recordatorio de seguridad.</strong> Este record nunca se serializa
 * directamente. Lo serializa {@code AuthResponse}, que es una vista publica y no
 * incluye el hash de la contrasena.
 *
 * @param token     token emitido, tal cual ira en la cabecera
 * @param user      usuario autenticado
 * @param expiresAt caducidad del token
 */
public record LoginResult(String token, User user, Instant expiresAt) {

    public LoginResult {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token no puede ser nulo ni vacio");
        }
        if (user == null) {
            throw new IllegalArgumentException("El resultado debe incluir el usuario");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("El resultado debe incluir la caducidad del token");
        }
    }
}
