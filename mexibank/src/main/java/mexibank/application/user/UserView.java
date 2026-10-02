package mexibank.application.user;

import java.util.Set;

import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;

/**
 * Vista publica de un usuario. Es lo que sale del sistema hacia el cliente.
 *
 * <p><strong>Por que existe y no se devuelve el {@link User} directamente.</strong>
 * El agregado tiene un campo que es el hash de la contrasena. Si el caso de uso
 * lo devolviera tal cual, el adaptador REST tendria que acordarse de no
 * serializarlo, y ese "acordarse" es una linea que se puede borrar sin que nada
 * falle. Con esta vista, el hash no tiene donde aparecer: el problema se hace
 * imposible en lugar de detectable.
 *
 * <p><strong>Por que un record y no el agregado.</strong> Un record es
 * inmutable por construccion y su {@code toString()} no invoca nada del
 * agregado. Ademas, si anadiera un campo nuevo al agregado, este record no
 * cambiara, y habra que decidir explicitamente si sale o no. Ese es el
 * comportamiento que se quiere en una vista publica.
 *
 * @param id        identificador del usuario
 * @param email     correo electronico
 * @param roles     roles asignados
 * @param active    si la cuenta esta activa
 * @param createdAt fecha de alta
 */
public record UserView(UserId id,
                       String email,
                       Set<Role> roles,
                       boolean active,
                       java.time.Instant createdAt) {

    public UserView {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * Proyecta el agregado a la vista publica.
     */
    public static UserView from(User user) {
        return new UserView(
                user.getId(),
                user.getEmail().value(),
                user.getRoles(),
                user.isActive(),
                user.getCreatedAt());
    }
}
