/**
 * Adaptadores de los puertos de seguridad del dominio.
 *
 * <p>Cada clase implementa un puerto declarado en {@code domain.user} sobre una
 * tecnologia concreta: BCrypt para el hash de contrasenas, HMAC-SHA256 para los
 * tokens, y el prefijo {@code ROLE_} para las autoridades de Spring.
 *
 * <p><strong>Por que la tecnologia no aparece en el dominio.</strong> El puerto
 * {@code PasswordHasher} no dice BCrypt en ningun sitio y el puerto
 * {@code TokenIssuer} no dice JWT. Si el banco decidiera argon2 o pasara a tokens
 * firmados por un tercero, cambiarian estas clases y el dominio no se enteraria.
 * Esa es la prueba de que la inversion de dependencias esta bien puesta.
 */
package mexibank.infrastructure.security.support;