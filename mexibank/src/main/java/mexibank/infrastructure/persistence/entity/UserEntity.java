package mexibank.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Modelo de persistencia del usuario. Es el unico lugar donde existen anotaciones
 * JPA.
 *
 * <p><strong>Por que existe una entidad JPA separada y no se anota el
 * agregado.</strong> Es la decision que se documento en el readme y la que sostiene
 * el resto de la persistencia del proyecto. Anotar {@code User} con
 * {@code @Entity} obligaria al dominio a importar JPA, y la regla
 * {@code domainNoDependeDeAplicacionNiInfraestructura} lo prohibe. El precio es
 * este duplicado: dos clases que se parecen y ninguna que es la fuente de verdad.
 *
 * <p><strong>Quien manda.</strong> El agregado. La entidad es un contenedor de
 * datos que existe porque la base de datos exige una tabla con columnas y tipos
 * concretos; ninguna regla de negocio se escribe aqui. El mapeo entre ambas
 * formas lo hace {@code UserMapper}, y las pruebas de {@code ArchUnit} comprueban
 * que el dominio sigue sin anotaciones.
 *
 * <p><strong>Por que los roles son {@code @ManyToMany} y no una coleccion de
 * valores.</strong> Un rol es una fila de la tabla {@code roles}, con su id, y
 * varios usuarios lo comparten: eso es una relacion N:M entre dos entidades, no
 * una coleccion de valores. Guardarlo como texto dentro de {@code user_roles}
 * duplicaba el catalogo una vez por usuario y hacia imposible preguntar "quien
 * tiene el rol TELLER", porque el nombre no era una clave. Con el
 * ManyToMany la tabla intermedia {@code user_roles} guarda solo
 * {@code (user_id, role_id)} y el catalogo esta en un solo sitio.
 *
 * <p><strong>Por que el lado propietario es el usuario.</strong> En una
 * relacion N:M hay exactamente un lado que escribe la tabla intermedia, y si los
 * dos la escriben se duplican las filas. La propiedad esta aqui porque la
 * pregunta que se hace siempre es "que roles tiene este usuario": el camino de
 * lectura al autenticar parte del usuario, y la escritura de la tabla sale de
 * este lado. {@code RoleEntity} no guarda la lista de usuarios por eso.
 *
 * <p><strong>Por que {@code nullable = false} en las dos columnas del
 * {@code JoinTable}.</strong> Una fila de la tabla intermedia sin usuario o sin
 * rol no significa nada, y la migracion declara las dos columnas {@code NOT
 * NULL}. Que esten escritas aqui tambien es lo que hace que {@code
 * ddl-auto=validate} lo compruebe al arrancar.
 *
 * <p><strong>Por que el fetch de los roles es {@code EAGER}.</strong> Cada
 * peticion autenticada necesita los roles del usuario: sin ellos no hay
 * autoridades y la peticion cae en 403. Con {@code LAZY} habria que tocar la
 * sesion de Hibernate dentro de la transaccion, y el filtro de autenticacion
 * corre fuera de ella. El conjunto es de tres elementos y la tabla de roles
 * entera cabe en la cache de sesion, asi que el coste es despreciable.
 *
 * <p><strong>Lo que este campo no decide.</strong> Que roles se asignan y si
 * existen en el catalogo. Eso lo decide el agregado y lo comprueba el adaptador
 * de persistencia contra la tabla {@code roles}; aqui solo se declara la forma
 * de la relacion.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity {

    /**
     * Identificador del usuario.
     *
     * <p>Es {@code updatable = false} porque un identificador no cambia nunca.
     * Sin esa restriccion, un mapper que se olvidara de un campo podria
     * reescribir la clave primaria y, con ella, todas las filas que apuntan a
     * ella.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Correo electronico, ya normalizado a minusculas por el value object del
     * dominio.
     *
     * <p>Longitud 254, que es el maximo del RFC 5322. Se declara aqui y no solo
     * en la entidad JPA porque {@code ddl-auto=validate} comprueba el tipo y la
     * longitud, y un desajuste falla al arrancar en vez de truncar correos en
     * silencio.
     */
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    /**
     * Hash de la contrasena. Nunca la contrasena en claro: el agregado no la
     * conoce y el adaptador de persistencia solo recibe el hash.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Si el usuario puede autenticarse.
     *
     * <p>Se llama {@code enabled} y no {@code active} porque es la columna que
     * Spring Security espera por defecto en su modelo de usuario, y asi una
     * herramienta del ecosistema que asuma ese nombre seguira funcionando.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /**
     * Roles del usuario. Cada rol es una fila de {@code roles} y cada pareja
     * (usuario, rol) una fila de la tabla intermedia {@code user_roles}.
     *
     * <p>En el dominio el conjunto ya venia sin duplicados y sin roles
     * inventados; aqui lo que garantiza la base de datos es que no haya dos
     * veces el mismo rol para el mismo usuario (clave primaria
     * {@code (user_id, role_id)}) y que el {@code role_id} apunte a una fila del
     * catalogo (FK {@code fk_user_roles_role}).
     *
     * <p>Guardar un usuario NO da de alta el rol: lo que se escribe en
     * {@code user_roles} es el id de un rol que ya estaba en {@code roles}.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "role_id", nullable = false))
    private Set<RoleEntity> roles = new HashSet<>();

    /**
     * Instante del alta. No se actualiza nunca.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Instante de la ultima modificacion.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}