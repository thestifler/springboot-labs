package mexibank.infrastructure.security.support;

import org.springframework.security.crypto.password.PasswordEncoder;

import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.PasswordHasher;

/**
 * Adaptador del puerto {@link PasswordHasher} sobre BCrypt.
 *
 * <p><strong>Por que BCrypt y no SHA-256 o MD5.</strong> Son funciones de digest:
 * son rapidas y estan disenadas para resumir datos, no para guardar contrasenas.
 * Precisamente por ser rapidas son el objetivo de una tabla arcoiris: con SHA-256
 * se prueban miles de millones de contrasenas por segundo con una GPU. BCrypt es
 * lento a proposito y lleva una sal por hash, asi que el coste se paga en el
 * atacante y las tablas precalculadas no sirven.
 *
 * <p><strong>Por que la configuracion usa el codificador delegante.</strong> Spring
 * ofrece {@code BCryptPasswordEncoder} suelto y
 * {@code PasswordEncoderFactories.createDelegatingPasswordEncoder()}. El segundo
 * es el que se usa aqui porque antepone {@code {bcrypt}} al hash y remembers que
 * algoritmo lo produjo. Sin ese prefijo, cambiar a argon2 en el futuro obligaria a
 * rehashear todas las contrasenas de golpe, porque no habria forma de saber con que
 * algoritmo se guardo cada una. Con el prefijo, cada hash dice como se verifico y
 * se puede migrar usuario a usuario.
 *
 * <p><strong>Por que no se fijan los parametros de coste.</strong> El coste por
 * defecto (10 en Spring Security 7) es un termino medio razonable. Subirlo aumenta
 * la seguridad y tambien el tiempo de login de cada usuario; bajarlo hace lo
 * contrario. Es una decision que depende de la capacidad del servidor y de si el
 * resultado se Ira a permitir en mas de un intento por segundo, asi que se deja en
 * el valor por defecto en lugar de fijarlo sin criterio.
 *
 * <p><strong>Por que el algoritmo no aparece en el dominio.</strong> El puerto
 * {@link PasswordHasher} no dice BCrypt en ningun sitio. Si el banco decidiera
 * argon2, cambiaria esta clase y el dominio no se enteraria.
 */
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    /**
     * @param encoder el codificador delegante, declarado en {@code SecurityConfig}.
     *                Inyectar la interfaz de Spring y no la implementacion
     *                concreta es lo que permite cambiarla en los tests sin tocar
     *                esta clase
     */
    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * {@inheritDoc}
     *
     * <p>La sal la genera el propio BCrypt en cada llamada, asi que dos contrasenas
     * iguales producen hashes distintos. Es lo que impide que un atacante que
     * consiga leer la tabla sepa que dos usuarios comparten contrasena.
     */
    @Override
    public PasswordHash hash(String rawPassword) {
        return PasswordHash.of(encoder.encode(rawPassword));
    }

    /**
     * {@inheritDoc}
     *
     * <p>No se compara el hash almacenado con el recien generado, porque jamas
     * serian iguales: la salt es distinta en cada invocacion. {@code matches} lo
     * que hace es reextraer la salt del hash guardado y hashear con ella, que es
     * el unico modo de que dos hashes de la misma contrasena coincidan.
     */
    @Override
    public boolean matches(String rawPassword, PasswordHash storedHash) {
        return encoder.matches(rawPassword, storedHash.value());
    }
}