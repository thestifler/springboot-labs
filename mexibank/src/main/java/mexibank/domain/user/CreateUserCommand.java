package mexibank.domain.user;

import java.util.Set;

/**
 * Datos para dar de alta un usuario.
 *
 * <p><strong>Por que la contrasena viaja como {@code String}.</strong> Es texto
 * plano todavia sin hashear. El caso de uso lo pasa por {@link PasswordHasher} y
 * construye el agregado con el resultado, de modo que la contrasena en claro no
 * llega a estar guardada en ningun objeto del dominio mas alla de esta llamada.
 *
 * <p><strong>Por que los roles vienen en el comando.</strong> Quien da de alta
 * decide que permisos tiene el usuario nuevo. Si el caso de uso los impusiera,
 * no habria forma de crear un TELLER desde la aplicacion.
 *
 * <p><strong>Por que no se valida la politica de contrasena aqui.</strong> Un
 * record de comando transporta datos; las reglas viven en el dominio y en el
 * caso de uso. Validar aqui haria que un usuario creado por consola (que no pasa
 * por aqui) se saltase la comprobacion.
 *
 * @param email       correo del nuevo usuario
 * @param rawPassword contrasena en claro
 * @param roles       roles a asignar; si esta vacio se aplica
 *                    {@link Role#defaultRoles()}
 */
public record CreateUserCommand(String email, String rawPassword, Set<Role> roles) {

    public CreateUserCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El correo no puede ser nulo ni vacio");
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("La contrasena no puede ser nula ni vacia");
        }
        roles = (roles == null || roles.isEmpty()) ? Role.defaultRoles() : Set.copyOf(roles);
    }
}
