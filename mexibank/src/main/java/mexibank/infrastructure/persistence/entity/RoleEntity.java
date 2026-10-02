package mexibank.infrastructure.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import mexibank.domain.user.Role;

/**
 * Modelo de persistencia del catalogo de roles. Una fila de la tabla
 * {@code roles}: un rol, su identificador y su nombre.
 *
 * <p><strong>Por que existe una tabla de roles y no solo el enum del
 * dominio.</strong> Antes el rol viajaba escrito dentro de {@code user_roles},
 * junto al id del usuario. Eso no era una relacion, era texto repetido: el
 * catalogo de roles vivia duplicado, una vez por cada usuario, y no existia
 * ninguna clave por la que preguntar "quien tiene el rol TELLER". Con
 * {@code roles} + {@code user_roles} el catalogo es un dato y la pertenencia es
 * una relacion, que es la unica forma de que la consulta sea posible.
 *
 * <p><strong>Por que el nombre sigue siendo el enum del dominio.</strong> La
 * tabla existe por la relacion, no para sustituir al enum: la regla
 * {@code domainNoDependeDeAplicacionNiInfraestructura} no impide que
 * infraestructura use el vocabulario del negocio, y aqui no hay conflicto. Con
 * {@code @Enumerated(EnumType.STRING)} un nombre desconocido en la base de datos
 * revienta al leer la fila, que es justo el diagnostico que se quiere; guardar el
 * {@code String} y traducirlo en el mapper daria un error mas tarde, en la
 * autorizacion, donde ya no se puede saber de donde salio el rol equivocado.
 *
 * <p><strong>Por que no tiene la lista de usuarios.</strong> La relacion se
 * declara en {@code UserEntity} y no aqui. Declararla en los dos lados obliga a
 * que Hibernate mantenga las dos mitades sincronizadas sin que ninguna apporte
 * nada, y ademas obliga a decidir quien escribe la tabla intermedia: una
 * relacion ManyToMany tiene un unico lado propietario, y tenerlos los dos es
 * una fuente clasica de filas duplicadas.
 *
 * <p><strong>Por que no define {@code equals} ni {@code hashCode}.</strong> La
 * identidad por referencia es la de JPA, y aqui es la correcta: dos
 * {@code RoleEntity} con el mismo nombre son la MISMA fila, y lo que se
 * compara es si Hibernate las trae del contexto o de una consulta. Reescribir
 * la igualdad por el nombre haria que Hibernate creyera que la coleccion de
 * roles ha cambiado cuando en realidad es la misma fila.
 *
 * <p><strong>Quien escribe aqui.</strong> Nadie. Esta entidad se crea sola: la
 * migracion {@code V3__roles_table.sql} siembra el catalogo y el adaptador de
 * persistencia se limita a buscar las filas por su nombre. Un alta de usuario
 * no inserta un rol, solo una fila en {@code user_roles} apuntando a una fila
 * que ya existe. Por eso {@link UserEntity} la referencia en vez de copiarla.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class RoleEntity {

    /**
     * Identificador del rol.
     *
     * <p>Lo escribe la migracion, no el codigo. Por eso {@code updatable =
     * false}: es la identidad de la fila del catalogo y un cambio de id dejaria
     * colgando todas las filas de {@code user_roles} que apuntan a ella.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Nombre del rol, con el vocabulario del dominio.
     *
     * <p>{@code unique} en la entidad y {@code uk_roles_name} en la migracion son
     * la misma restriccion escrita en los dos sitios porque los dos existen: la
     * base de datos la impone aunque alguien escriba en la tabla con SQL, y
     * {@code ddl-auto=validate} la comprueba al arrancar. Sin la restriccion dos
     * filas podrian llamarse CUSTOMER y el catalogo dejaria de ser un
     * catalogo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private Role name;

    /**
     * Construye la entidad de un rol ya existente en el catalogo.
     *
     * <p>Existe para que se vea, al leer el codigo, que un {@code RoleEntity}
     * con id NO se inserta: si la fila ya esta en {@code roles}, lo que se hace
     * con ella es buscarla y reutilizarla. El alta de un usuario no pasa por
     * aqui.
     *
     * @param id   identificador que le dio la migracion
     * @param name rol del dominio
     * @return entidad del catalogo
     */
    public static RoleEntity delCatalogo(UUID id, Role name) {
        RoleEntity entidad = new RoleEntity();
        entidad.id = id;
        entidad.name = name;
        return entidad;
    }
}