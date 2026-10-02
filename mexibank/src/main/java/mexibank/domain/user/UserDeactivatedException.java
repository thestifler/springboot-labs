package mexibank.domain.user;

/**
 * El usuario esta desactivado y por tanto no puede autenticarse.
 *
 * <p><strong>Por que NO se extiende de {@link InvalidCredentialsException}.</strong>
 * Es tentador reutilizar esa excepcion para que el login no distinga entre
 * credenciales malas y usuario bloqueado. Se ha rechazado porque son cosas
 * distintas para el usuario: un usuario desactivado que intenta entrar merece
 * saber que su cuenta esta bloqueada, y un soporte que lo recibe necesita esa
 * informacion para distinguirlo de un robo de credenciales.
 *
 * <p>El filtro de autenticacion, en cambio, no distingue: cuando un token
 * pertenece a un usuario desactivado lo rechaza como si no tuviera token, y
 * ahi si conviene que el mensaje sea indistinto.
 */
public class UserDeactivatedException extends RuntimeException {

    public UserDeactivatedException() {
        super("La cuenta esta desactivada. Contacta con el banco para reactivarla");
    }
}
