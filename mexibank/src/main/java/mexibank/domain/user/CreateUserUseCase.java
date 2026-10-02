package mexibank.domain.user;

/**
 * Puerta de entrada del alta de usuarios.
 *
 * <p><strong>Por que el alta necesita un rol mas que el login.</strong> Un
 * ADMIN necesita crear usuarios con permisos que el propio ADMIN no tendria por
 * defecto. Por eso {@link CreateUserCommand} lleva los roles a asignar y el caso
 * de uso comprueba quien lo pide. La autorizacion en si la resuelve el adaptador
 * REST (que exige el rol ADMIN para llegar aqui); lo que este caso de uso
 * comprueba son las <em>reglas</em> del alta, que son reglas de negocio.
 *
 * <p><strong>Por que es una interfaz aparte y no un metodo mas en
 * {@link LoginUseCase}.</strong> Cada operacion de negocio tiene su puerta de
 * entrada. Una interfaz que agrupa "crear usuario", "cambiar contrasena" y
 * "cambiar roles" seria una interfaz con tres razones distintas para cambiar, y
 * cada una obliga a tocar a todos los que la usan.
 */
public interface CreateUserUseCase {

    /**
     * Da de alta un usuario con los roles indicados.
     *
     * @param command datos del nuevo usuario
     * @return el usuario creado
     * @throws EmailAlreadyRegisteredException si el correo ya esta en uso
     * @throws IllegalArgumentException        si la contrasena no cumple la
     *                                         politica de seguridad
     */
    User createUser(CreateUserCommand command);
}
