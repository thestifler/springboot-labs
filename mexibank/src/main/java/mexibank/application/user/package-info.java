/**
 * Orquestacion de los casos de uso de usuario.
 *
 * <p>Cada clase implementa un puerto de entrada declarado en
 * {@code domain.user} y coordina las operaciones que el agregado no debe conocer:
 * consultar el repositorio, pedir el hash al puerto {@code PasswordHasher} y pedir
 * el token al puerto {@code TokenIssuer}. El agregado solo sabe invariantes.
 *
 * <p><strong>Por que los puertos de salida se declaran en el dominio.</strong>
 * Es lo que invierte la dependencia: el caso de uso depende de
 * {@code UserRepository}, y quien lo implementa (JPA) es el que depende del
 * dominio. Sin esa inversion, cambiar de base de datos tocaria esta capa.
 *
 * <p><strong>Por que la transaccion se declara aqui y no en el adaptador.</strong>
 * Porque solo esta capa sabe si una operacion es atomica. "Comprobar que el correo
 * no existe y luego guardarlo" es una unidad de trabajo, y el adaptador no puede
 * saber que esas dos llamadas van juntas.
 *
 * <p>{@link mexibank.application.user.UserView} es la proyeccion publica del
 * usuario. Existe para que el hash de contrasena no pueda colarse en una respuesta:
 * los controladores HTTP y el login construyen respuestas a partir de la vista,
 * nunca del agregado.
 */
package mexibank.application.user;