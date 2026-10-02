package mexibank.application.auth;

import java.time.Duration;
import java.time.Instant;

import mexibank.application.user.UserView;
import mexibank.domain.user.LoginResult;

/**
 * Respuesta del login que ve el cliente.
 *
 * <p><strong>Por que el tiempo de vida en segundos y no el instante.</strong> El
 * instante absoluto de caducidad solo lo necesita el servidor, que ya lo tiene.
 * El cliente necesita saber "cuanto me queda", y por eso recibe un numero de
 * segundos que puede restar a su reloj. Es tambien el dato que aparece en las
 * especificaciones de OAuth 2, asi que un cliente que ya hable con un
 * autorizador de OAuth reutiliza el mismo campo.
 *
 * <p><strong>Por que el formato del token lo decide el adaptador.</strong> Aqui
 * solo hay un {@code String}. Que sea un JWT, un token opaco o un valor
 * codificado es asunto del adaptador REST, que es quien lo serializa en la
 * cabecera {@code Authorization}.
 *
 * <p><strong>Por que existe y no se devuelve {@code LoginResult} tal cual.</strong>
 * {@code LoginResult} es un tipo interno y lleva el agregado {@code User}
 * completo, incluido su hash de contrasena. Serializarlo seria filtrar el hash
 * en cada login. Esta vista publica no tiene forma de contenerlo.
 *
 * @param accessToken el token, listo para enviar como {@code Bearer <token>}
 * @param tokenType   tipo de token. Siempre {@code Bearer}: es lo que el filtro
 *                    espera en la cabecera
 * @param expiresIn   segundos hasta que caduca
 * @param user        datos publicos del usuario autenticado
 */
public record AuthResponse(String accessToken,
                           String tokenType,
                           long expiresIn,
                           UserView user) {

    /**
     * Tipo de token.
     *
     * <p>Es una constante y no un literal escrito en el codigo porque el filtro de
     * autenticacion tiene que comprobar exactamente esta cadena. Si uno de los dos
     * la cambiara por su cuenta, el login devolveria un token que el propio sistema
     * rechazaria.
     */
    public static final String BEARER = "Bearer";

    public AuthResponse {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("La respuesta debe incluir el token");
        }
        if (user == null) {
            throw new IllegalArgumentException("La respuesta debe incluir los datos del usuario");
        }
    }

    /**
     * Construye la respuesta calculando los segundos restantes.
     *
     * @param result resultado del caso de uso
     * @param now    instante actual, inyectado para que el calculo sea
     *               determinista en los tests
     */
    public static AuthResponse from(LoginResult result, Instant now) {
        long seconds = Math.max(0, Duration.between(now, result.expiresAt()).getSeconds());
        return new AuthResponse(
                result.token(),
                BEARER,
                seconds,
                UserView.from(result.user()));
    }
}