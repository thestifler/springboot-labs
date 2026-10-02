package mexibank.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import mexibank.domain.user.Email;
import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;
import mexibank.infrastructure.persistence.entity.RoleEntity;
import mexibank.infrastructure.persistence.entity.UserEntity;

/**
 * Fabricas de {@link User} y {@link UserEntity}.
 *
 * <p><strong>Por que es una clase y no metodos dentro de la interfaz
 * {@code UserMapper}.</strong> La interfaz declara {@code toDomain} como metodo
 * {@code default} justamente para que este codigo no aparezca en ella. Si la
 * construccion viviera en la interfaz, se leeria como parte del mapeo automatico
 * cuando en realidad es la parte que MapStruct no sabe hacer, y habria que
 * mantener el fichero para saber que es cual.
 *
 * <p><strong>Por que {@code construir} delega en
 * {@link User#reconstitute}.}</strong> El agregado tiene dos fabricas. Al releer de
 * la base de datos hay que usar la que no revalida. Un usuario guardado cuando la
 * politica de contrasenas era mas laxa debe poder seguir entrando; reconstruir con
 * {@code register} lo bloquearia en silencio el dia que se endurezca la politica.
 *
 * <p><strong>Por que {@code actualizarSobre} existe.</strong> El mapeo inverso que
 * genera MapStruct devuelve una entidad nueva. Si el adaptador la guardara, Hibernate
 * no reconoceria que es la misma fila que ya tiene gestionada y, al no haber id
 * gestionado, insertaria una fila nueva en vez de actualizar la existente. Este
 * metodo vuelca el estado sobre la entidad que Hibernate ya tiene en su contexto.
 *
 * <p><strong>Por que aqui hay traduccion de roles y no en el mapper
 * automatico.</strong> En la entidad un rol es un {@link RoleEntity} (la fila del
 * catalogo) y en el agregado es un {@link Role} (el enum). Son dos tipos distintos,
 * asi que el cambio no es un renombrado: hay que pasar de una fila a su nombre y
 * del nombre a su fila. La direccion de lectura se hace aqui, en
 * {@link #construir}, porque acaba de llegar una entidad; la de escritura se hace
 * en el adaptador ({@code UserPersistenceAdapter}), porque resolver el nombre a una
 * fila del catalogo exige consultar {@code roles}.
 */
public final class UserFactory {

    private UserFactory() {
    }

    /**
     * Construye el agregado desde los valores de una entidad.
     *
     * <p><strong>Por que los roles se traducen y no se pasan tal cual.</strong> La
     * entidad trae {@code Set<RoleEntity>}, que son filas del catalogo; el
     * agregado guarda {@code Set<Role>}, que son nombres. La copia la hace
     * {@link User#reconstitute}, que ya congela el conjunto: a partir de ahi el
     * agregado no depende de la sesion de Hibernate, y por eso no hace falta
     * conservar el {@code PersistentSet} que traia la entidad.
     *
     * @param roles filas del catalogo asociadas al usuario
     */
    public static User construir(UserId id,
                                 Email email,
                                 PasswordHash passwordHash,
                                 Set<RoleEntity> roles,
                                 boolean active,
                                 Instant createdAt,
                                 Instant updatedAt) {
        return User.reconstitute(id, email, passwordHash, aRoles(roles), active, createdAt, updatedAt);
    }

    /**
     * Vuelca un agregado sobre una entidad ya gestionada por Hibernate.
     *
     * <p><strong>Por que {@code createdAt} no se toca.</strong> Es
     * {@code updatable = false}: la fecha de alta es inmutable y el dominio no tiene
     * forma de cambiarla. Reasignarla no tendria efecto, y dejarla a null (si el
     * agregado viniera sin ella) dejaria la fila con la fecha perdida.
     *
     * <p><strong>Por que los roles se reciben en vez de traducirse aqui.</strong> A
     * diferencia de la lectura, la escritura necesita las <em>entidades</em> del
     * catalogo, y sacarlas de la base de datos no es cosa de una clase sin
     * repositorio. El adaptador se las pasa ya resueltas y este metodo solo las
     * vuelca.
     *
     * <p><strong>Por que los roles se copian a un conjunto nuevo.</strong> La
     * entidad gestionada tiene un {@code PersistentSet} que Hibernate compara con
     * la tabla para decidir que filas borrar y que filas insertar. Si se le
     * asignara el mismo conjunto que ya tiene gestionado, Hibernate no tendria nada
     * que comparar y podria no detectar el cambio. Con un conjunto nuevo, el
     * cambio queda visible y se traduce en el INSERT o DELETE correcto sobre
     * {@code user_roles}.
     *
     * @param target entidad gestionada, con id y fecha de alta ya fijados
     * @param source agregado con el estado nuevo
     * @param roles entidades del catalogo que corresponden a los roles del agregado
     */
    public static void actualizarSobre(UserEntity target, User source, Set<RoleEntity> roles) {
        target.setEmail(source.getEmail().value());
        target.setPasswordHash(source.getPasswordHash().value());
        target.setEnabled(source.isActive());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setRoles(new HashSet<>(roles));
    }

    /**
     * Traduce filas del catalogo a los roles del dominio.
     *
     * <p>Es la unica fuente de esa conversion en el sentido de lectura. Vive aqui y
     * no en {@code UserMapper} porque es uno de los mapas que MapStruct no sabe
     * escribir, y no es un mapper mas porque solo hay un sitio que lo necesita: una
     * traduccion en un unico lugar no puede quedarse vieja.
     *
     * @param roles entidades del catalogo, tal cual las trae la entidad
     * @return conjunto de roles del dominio
     */
    private static Set<Role> aRoles(Set<RoleEntity> roles) {
        return roles.stream()
                .map(RoleEntity::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}