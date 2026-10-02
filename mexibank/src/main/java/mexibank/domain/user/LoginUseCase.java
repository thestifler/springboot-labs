package mexibank.domain.user;

/**
 * Puerta de entrada de la autenticacion: alguien presenta credenciales y espera
 * un token.
 *
 * <p><strong>Por que esta interfaz vive en el dominio y no en
 * {@code application}.</strong> El caso de uso de login es la definicion de la
 * operacion de negocio "entrar al banco", y esa definicion pertenece al dominio
 * de la identidad. La interfaz es, ademas, el contrato que el adaptador REST
 * consume sin conocer los detalles de como se implementa.
 *
 * <p><strong>Por que el comando no lleva el resultado dentro.</strong>
 * {@link LoginCommand} solo contiene lo que el usuario escribe. El token sale
 * como {@link LoginResult}, porque son dos cosas distintas: una es entrada, la
 * otra es salida, y mezclarlas permite cambiar una sin romper la otra.
 */
public interface LoginUseCase {

    /**
     * Autentica a un usuario y le devuelve un token de acceso.
     *
     * @param command correo y contrasena tal como los escribio el usuario
     * @return el token emitido y los datos publicos del usuario
     * @throws InvalidCredentialsException si el correo no existe o la contrasena
     *                                    no coincide. Las dos causas comparten
     *                                    excepcion a proposito, para no permitir
     *                                    enumerar cuentas
     * @throws UserDeactivatedException   si el usuario esta desactivado
     */
    LoginResult login(LoginCommand command);
}
