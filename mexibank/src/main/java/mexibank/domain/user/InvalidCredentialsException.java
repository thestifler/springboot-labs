package mexibank.domain.user;

/**
 * El login no ha podido autenticarse.
 *
 * <p><strong>Por que una sola excepcion para las dos causas.</strong> Podria
 * haber una {@code UserNotFoundException} y una {@code WrongPasswordException},
 * y el login las distinguiria. Esa distincion es util para el servidor y
 * peligrosa para el cliente: si la API responde "usuario no encontrado" a un
 * correo y "contrasena incorrecta" a otro, un atacante puede recorrer una lista
 * de correos y descubrir quien tiene cuenta en el banco. Por eso el caso de uso
 * captura las dos y lanza siempre esta.
 *
 * <p>El detalle de que fallo realmente se registra en el log del servidor, que
 * si es interno. La respuesta al cliente es siempre la misma.
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Mensaje unico e indistinto. Cambiarlo por algo mas especifico rompe a
     * proposito la proteccion contra la enumeracion de cuentas.
     */
    public InvalidCredentialsException() {
        super("Las credenciales no son validas");
    }
}
