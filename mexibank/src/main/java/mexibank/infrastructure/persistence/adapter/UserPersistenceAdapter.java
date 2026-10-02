package mexibank.infrastructure.persistence.adapter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import mexibank.domain.user.Email;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;
import mexibank.domain.user.UserRepository;
import mexibank.infrastructure.persistence.entity.RoleEntity;
import mexibank.infrastructure.persistence.entity.UserEntity;
import mexibank.infrastructure.persistence.mapper.UserFactory;
import mexibank.infrastructure.persistence.mapper.UserMapper;
import mexibank.infrastructure.persistence.springdata.RoleJpaRepository;
import mexibank.infrastructure.persistence.springdata.UserJpaRepository;

/**
 * Adaptador de persistencia del puerto {@link UserRepository}.
 *
 * <p><strong>Por que esta clase existe.</strong> Es el puente entre el puerto que
 * pide el dominio y el repositorio de Spring Data que sabe hablar SQL. El caso de
 * de uso inyecta {@code UserRepository} y no tiene idea de que existen JPA,
 * Hibernate o la entidad {@code UserEntity}.
 *
 * <p><strong>Por que no lleva {@code @Transactional}.</strong> La transaccion la
 * declara el caso de uso, que es quien sabe si la operacion es atomica. Si el
 * adaptador la declarara, cada llamada publicaria la suya propia y el caso de uso
 * tendria que marcarse como REQUIRED para que no se encadenasen por
 * Delegar la transaccion es lo que permite que "comprobar que no existe y guardar"
 * sea una sola unidad de trabajo.
 *
 * <p><strong>Por que {@code @Repository} y no {@code @Service}.</strong> No es
 * cosmetico: Spring translates {@code @Repository} en una traduccion de
 * excepciones de persistencia a excepciones del dominio. Sin el, un
 * {@code DataIntegrityViolationException} por correo duplicado saldria al cliente
 * como un 500 en lugar de un 409, porque la traduccion vive asociada a este
 * Bean.
 *
 * <p><strong>Por que inyecta tambien el repositorio de roles.</strong> Es lo que
 * impide que un alta de usuario cree el rol. El agregado trae nombres y el modelo
 * de persistencia guarda filas del catalogo, asi que guardar exige resolver esos
 * nombres contra la tabla {@code roles}: el rol no se crea, se busca. Ese paso
 * ocurre aqui y no en el mapper porque el mapper no tiene repositorio, y no en el
 * caso de uso porque el caso de uso no sabe que existe una tabla de roles.
 */
@Repository
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final RoleJpaRepository roleRepository;
    private final UserMapper mapper;

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findById(UserId userId) {
        return jpaRepository.findById(userId.value())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.value());
    }

    /**
     * Guarda un usuario.
     *
     * <p><strong>Por que distingue entre insertar y actualizar.</strong> Si se
     * limitara a llamar a {@code save} con una entidad creada por el mapper,
     * Hibernate insertaria una fila nueva en cada modificacion, porque la entidad
     * resultante no seria la que el {@code persistence context} tiene registrada
     * como gestionada. El resultado seria un historial de usuarios en lugar de un
     * usuario con historial.
     *
     * <p>La comprobacion no es un detalle: preguntar a la base de datos antes de
     * escribir es lo que convierte "actualizar" en "actualizar de verdad".
     *
     * <p><strong>Por que los roles se resuelven antes de las dos ramas.</strong> Los
     * dos caminos escriben filas en {@code user_roles}, y en los dos la fila tiene
     * que apuntar a un rol del catalogo. Resolverlo una vez arriba ademas hace que
     * el fallo de un rol que no existe (una migracion que no lo ha sembrado) salga
     * antes de haber tocado nada, y no a mitad de un guardado.
     */
    @Override
    public void save(User user) {
        Set<RoleEntity> roles = rolesDelCatalogo(user.getRoles());

        Optional<UserEntity> existente = jpaRepository.findById(user.getId().value());

        if (existente.isEmpty()) {
            UserEntity nueva = mapper.toEntity(user);
            nueva.setRoles(new LinkedHashSet<>(roles));
            jpaRepository.save(nueva);
            return;
        }

        UserFactory.actualizarSobre(existente.get(), user, roles);
        // No hace falta llamar a save: la entidad sigue siendo la misma instancia
        // gestionada por el persistence context, y Hibernate la sincronizara al
        // cerrar la transaccion. Guardarla de nuevo no hace nada y solo confunde.
    }

    /**
     * Busca en el catalogo las filas que corresponden a unos roles del dominio.
     *
     * <p><strong>Por que no se crea el rol que falta.</strong> Seria la forma
     * corta de que el alta nunca fallara, y por eso es la equivocada: un rol que
     * el enum declara pero que la migracion no ha sembrado es un despliegue a
     * medias, y convertirlo en un INSERT silencioso lo esconderia hasta el
     * siguiente arranque. Aqui el error dice exactamente que falta.
     *
     * <p><strong>Por que las entidades que devuelve estan gestionadas.</strong> Se
     * leen dentro de la misma transaccion que escribe, asi que son las mismas
     * instancias que Hibernate tiene en su contexto. Es lo que permite ponerlas en
     * la coleccion de un usuario sin que Hibernate las trate como entidades
     * nuevas.
     *
     * @param roles roles del agregado, ya validados por el dominio
     * @return las filas del catalogo, en el mismo orden
     * @throws IllegalStateException si algun rol del dominio no esta en la tabla
     *                              {@code roles}
     */
    private Set<RoleEntity> rolesDelCatalogo(Set<Role> roles) {
        Map<Role, RoleEntity> porNombre = new LinkedHashMap<>();
        for (RoleEntity entidad : roleRepository.findAllByNameIn(roles)) {
            porNombre.put(entidad.getName(), entidad);
        }

        Set<RoleEntity> resueltos = new LinkedHashSet<>();
        for (Role rol : roles) {
            RoleEntity entidad = porNombre.get(rol);
            if (entidad == null) {
                throw new IllegalStateException(
                        "El rol " + rol.name() + " no existe en la tabla roles. "
                                + "Las migraciones siembran el catalogo de roles; anadir uno nuevo "
                                + "al enum Role exige una migracion que lo inserte.");
            }
            resueltos.add(entidad);
        }
        return resueltos;
    }
}