package mexibank.domain.user;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Agregado de usuario: la identidad que puede autenticarse y operar en el banco.
 *
 * <p><strong>Por que los roles viven en el usuario y no aparte.</strong> Un rol
 * sin usuario no significa nada, y un usuario sin roles no puede hacer nada.
 * Modelarlos por separado obligaria a decidir, en cada operacion, si el conjunto
 * de roles esta completo y coherente, y a corregirlo despues. Al ser parte del
 * agregado, es imposible tener un usuario guardado sin roles o con roles
 * invalidos, porque no hay forma de construir uno asi.
 *
 * <p><strong>Inmutabilidad del correo y del hash.</strong> No hay setters para
 * ninguno de los dos. El correo identifica al usuario y cambiarlo obliga a
 * reidentificarse; el hash se cambia mediante {@link #changePassword}, que
 * ademas comprueba que la contrasena nueva no sea la anterior. Dejar el hash
 * mutable habria permitido que un mapper de un futuro refactor lo dejara a null.
 *
 * <p><strong>Por que el reloj entra por constructor.</strong> El dominio nunca
 * llama a {@code Instant.now()}: recibe el instante. Asi los tests pueden
 * congelar el tiempo con {@code Clock.fixed(...)} y comprobar la fecha de alta
 * sin depender del reloj del sistema, que es la fuente clasica de tests que
 * fallan a medianoche.
 *
 * <p><strong>Por que los accesores son {@code getX()} y no {@code x()}.</strong> Es
 * una inconsistencia deliberada con los value objects de este mismo paquete, que si
 * usan el estilo de record ({@code email.value()}, {@code id.value()}). MapStruct, que
 * genera el mapeo entre este agregado y la entidad JPA, solo reconoce propiedades
 * con el prefijo {@code get} o {@code is}; con accesores {@code id()} no encuentra
 * la propiedad y el proyecto no compila. Se prefirio romper la simetria de estilo
 * antes que abandonar el mapeo automatico: si alguien anade un campo al agregado y
 * olvida el mapper, el error aparece al compilar y no como un {@code null}
 * silencioso en produccion.
 */
public final class User {

    private final UserId id;
    private final Email email;
    private PasswordHash passwordHash;
    private Set<Role> roles;
    private final boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id,
                 Email email,
                 PasswordHash passwordHash,
                 Set<Role> roles,
                 boolean active,
                 Instant createdAt,
                 Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Da de alta un usuario. Es la unica via por la que se crea uno.
     *
     * <p><strong>Por que se exige un {@link PasswordHash} y no una
     * contrasena.</strong> El hasheo ocurre en el caso de uso, antes de llegar
     * aqui. Si este metodo aceptara el texto plano, bastaria un despiste para
     * que un usuario quedara guardado con la contrasena sin hashear y el sistema
     * aceptaria eso como valido.
     *
     * <p><strong>Por que hay que pasar el rol explicitamente.</strong> No se
     * asume {@link Role#defaultRoles()} aqui. Si el alta de cliente y la de
     * administrador comparten este metodo, la diferencia debe estar en el
     * argumento, no en un {@code if} dentro del metodo que adivine cual de los
     * dos es.
     *
     * @param id       identificador del usuario
     * @param email    correo ya validado por {@link Email}
     * @param passwordHash hash ya calculado por el puerto {@link PasswordHasher}
     * @param roles    roles iniciales; no puede estar vacio
     * @param now      instante de alta
     * @throws IllegalArgumentException si no se indica ningun rol
     */
    public static User register(UserId id,
                                Email email,
                                PasswordHash passwordHash,
                                Set<Role> roles,
                                Instant now) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Un usuario debe tener al menos un rol");
        }
        return new User(id, email, passwordHash, Set.copyOf(roles), true, now, now);
    }

    /**
     * Reconstruye un usuario desde la base de datos.
     *
     * <p><strong>Por que este metodo existe y por que es distinto de
     * {@link #register}.</strong> Al releer de la base de datos no se pueden
     * revalidar las reglas de alta: un usuario que se guardo cuando la politica
     * de contrasenas era mas laxa puede tener today una contrasena que ya no
     * cumple la regla, y denegar el acceso al leerlo dejaria al usuario
     * bloqueado sin explicacion. La validacion ocurre al entrar por el agregado,
     * no al releerlo.
     *
     * <p>Por eso los setters que tienen los adaptadores no son los mismos metodos
     * publicos: leer no pasa por el invariante, escribir si.
     *
     * @param updatedAt ultima modificacion registrada
     */
    public static User reconstitute(UserId id,
                                    Email email,
                                    PasswordHash passwordHash,
                                    Set<Role> roles,
                                    boolean active,
                                    Instant createdAt,
                                    Instant updatedAt) {
        return new User(id, email, passwordHash, Set.copyOf(roles), active, createdAt, updatedAt);
    }

    /**
     * Cambia la contrasena.
     *
     * @param newHash   hash de la contrasena nueva, ya calculado
     * @param currentHash hash de la contrasena que se cree correcta
     * @param now       instante del cambio
     * @throws IllegalArgumentException si el hash actual no coincide, o si la
     *                                  contrasena nueva es identica a la anterior
     */
    public void changePassword(PasswordHash newHash, PasswordHash currentHash, Instant now) {
        if (!Objects.equals(this.passwordHash, currentHash)) {
            throw new InvalidCurrentPasswordException();
        }
        if (Objects.equals(this.passwordHash, newHash)) {
            // BCrypt genera una salt distinta en cada hasheo, asi que dos
            // contrasenas iguales producen hashes distintos y esta comprobacion
            // NO detectaria una contrasena sin cambios. Se mantiene igualmente
            // porque el valor de PasswordHash.equals no va a cambiar: si en el
            // futuro se usara un hasher determinista, dejaria de ser cierto y
            // el usuario podria "cambiar" su contrasena a la misma sin que nada
            // lo dijera.
            throw new IllegalArgumentException("La contrasena nueva debe ser distinta de la actual");
        }
        this.passwordHash = newHash;
        this.updatedAt = now;
    }

    /**
     * Sustituye los roles del usuario.
     *
     * <p>Se valida que no quede sin roles por la misma razon que en el alta: un
     * usuario sin roles no puede hacer nada, y dejaria de ser evidente por que
     * su peticion devuelve 403.
     *
     * @param now instante del cambio
     * @throws IllegalArgumentException si el conjunto queda vacio
     */
    public void replaceRoles(Set<Role> newRoles, Instant now) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw new IllegalArgumentException("Un usuario debe conservar al menos un rol");
        }
        this.roles = Set.copyOf(newRoles);
        this.updatedAt = now;
    }

    /**
     * Indica si el usuario puede autenticarse ahora mismo.
     *
     * <p>Es la unica condicion que el login comprueba antes de validar la
     * contrasena. Un usuario desactivado que acierta la contrasena sigue sin
     * poder entrar.
     */
    public boolean canAuthenticate() {
        return active;
    }

    public UserId getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
