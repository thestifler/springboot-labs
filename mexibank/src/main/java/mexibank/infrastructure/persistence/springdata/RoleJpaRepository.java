package mexibank.infrastructure.persistence.springdata;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import mexibank.domain.user.Role;
import mexibank.infrastructure.persistence.entity.RoleEntity;

/**
 * Repositorio de Spring Data para el catalogo de roles.
 *
 * <p><strong>Por que existe.</strong> Con los roles como texto dentro de
 * {@code user_roles}, guardar un usuario no tenia que consultar nada: el nombre
 * bastaba. Con una tabla de roles, guardar exige resolver el nombre del dominio
 * a la fila del catalogo, y esa resolucion es una consulta. Este repositorio es
 * la unica forma de hacerla.
 *
 * <p><strong>Por que solo se lee.</strong> El catalogo se siembra con una
 * migracion y no se escribe desde la aplicacion. Un alta de usuario no crea el
 * rol: lo busca y escribe una fila en {@code user_roles} apuntando al que ya
 * existe. Por eso aqui no hay ningun {@code save} y por eso las filas que este
 * repositorio devuelve estan siempre gestionadas por el contexto de Hibernate:
 * son las mismas instancias que hay que poner en la coleccion del usuario, y no
 * copias sueltas que Hibernate tendria que resolver otra vez.
 *
 * <p><strong>Por que devuelve entidades y no el enum del dominio.</strong> La
 * regla de este paquete: aqui no se ve el dominio de negocio, solo el modelo de
 * persistencia. La traduccion entre {@code RoleEntity} y {@code Role} la hace el
 * adaptador ({@code UserPersistenceAdapter}).
 */
public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {

    /**
     * Busca los roles del catalogo con esos nombres.
     *
     * <p>Se pide una lista y no un rol cada vez porque el alta puede traer
     * varios: {@code findByName} en un bucle issued una consulta por rol, y con
     * el contexto de Hibernate abierto la segunda ya sale de la cache de sesion
     * pero sigue siendo una ida al repositorio. Ademas devuelve todos juntos, lo
     * que permite distinguir "no existe ese rol" de "todavia no se ha buscado".
     *
     * <p>El resultado puede ser menor que el conjunto pedido, y eso no es un
     * error del repositorio: significa que hay un rol del enum que la
     * migracion no ha sembrado. Lo detecta el adaptador, que es quien sabe
     * decidir si eso es tolerable.
     *
     * @param nombres nombres de rol, tal cual los escribe el enum
     * @return las filas del catalogo que coinciden, vacia si no hay ninguna
     */
    List<RoleEntity> findAllByNameIn(Set<Role> nombres);
}