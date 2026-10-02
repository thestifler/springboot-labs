/**
 * Agregado de usuario: identidad, credenciales y roles.
 *
 * <p><strong>Por que el usuario esta en el dominio y no solo en la
 * infraestructura.</strong> Que exista una identidad con credenciales, que la
 * identidad lleve roles, y que la cuenta desactivada no pueda entrar son reglas
 * del negocio. El adaptador REST necesita esas reglas para validar y el
 * adaptador de persistencia necesita el modelo para guardarlo, pero ninguno de
 * los dos las inventa: las aplica.
 *
 * <p><strong>Que hay aqui.</strong>
 * <ul>
 *   <li>{@code User}: el agregado, con sus invariantes y sin setters.</li>
 *   <li>{@code UserId}, {@code Email}, {@code PasswordHash}, {@code AccessToken}:
 *       value objects. Cada uno lleva su propia garantia, de modo que un dato
 *       invalido no puede existir.</li>
 *   <li>{@code Role}: los roles que el banco reconoce. Un enum, para que un rol
 *       desconocido en la base de datos falle en lugar de aceptarse.</li>
 *   <li>{@code UserRepository}, {@code PasswordHasher}, {@code TokenIssuer}:
 *       puertos de salida que implementa la infraestructura.</li>
 *   <li>{@code LoginUseCase}, {@code CreateUserUseCase}: puertos de entrada que
 *       implementa la capa de aplicacion.</li>
 *   <li>Excepciones de dominio, que no conocen HTTP.</li>
 * </ul>
 *
 * <p><strong>La regla de dependencia, aplicada a este paquete.</strong> Nada de
 * lo que hay aqui importa Spring, JPA ni servlet. Ni siquiera
 * {@code PasswordHasher}: el dominio dice "necesito que alguien sepa hashear" y
 * no dice con que algoritmo, que es exactamente la frontera que la arquitectura
 * hexagonal existe para dibujar.
 */
package mexibank.domain.user;
