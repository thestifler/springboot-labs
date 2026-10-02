package mexibank.domain.user;

import java.time.Duration;

/**
 * Puerto de salida: emite tokens de acceso y verifica los que llegan.
 *
 * <p><strong>Por que emitir un token es un puerto y no un detalle de la
 * aplicacion.</strong> El login necesita devolver algo que el cliente pueda
 * presentar en cada peticion posterior, y necesita comprobar que lo que llega es
 * genuino. Que ese algo sea un JWT firmado con HMAC, un JWT con RSA, o un token
 * opaco guardado en Redis es una decision de infraestructura. El caso de uso solo
 * sabe que pide "un token para este usuario" y que le devuelven "un token que
 * yo mismo emiti".
 *
 * <p><strong>Por que los metodos usan los tipos del dominio.</strong>
 * {@link #issue(User, Duration)} devuelve un {@link AccessToken} y no un
 * {@code String}. Asi el caso de uso entrega al cliente un valor con caducidad
 * asociada, y no un texto opaco cuyo vencimiento habria que repetir en el
 * controlador REST.
 *
 * <p><strong>Por que {@link #verify(String)} devuelve {@code UserId} y no
 * {@code User}.</strong> Verificar un token no debe dar acceso al usuario
 * completo: el filtro de autenticacion solo necesita saber quien es para decidir
 * si le deja pasar. Devolver el agregado entero invitaria a usarlo como fuente
 * de roles, y entonces un token.emitido con roles antiguos podria revalidarse
 * contra los roles actuales sin querer, mezcla que es justo la decision que se
 * documento en {@link AccessToken} (los roles viajan en el token).
 */
public interface TokenIssuer {

    /**
     * Emite un token para el usuario.
     *
     * @param user  usuario autenticado; se leen su identificador y sus roles
     * @param ttl   tiempo de vida. Lo decide el caso de uso, no el adaptador:
     *              el adaptador tiene la clave y el algoritmo, pero no sabe
     *              cuanto debe durar un token en este banco
     * @return el token emitido, con su caducidad
     */
    AccessToken issue(User user, Duration ttl);

    /**
     * Verifica la firma y la caducidad de un token.
     *
     * @param rawToken token tal cual llega en la cabecera {@code Authorization}
     * @return el identificador del usuario, o {@link java.util.Optional#empty()}
     *         si el token es ilegible, esta caducado o la firma no cuadra
     */
    java.util.Optional<UserId> verify(String rawToken);
}
