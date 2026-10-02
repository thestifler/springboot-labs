package mexibank.infrastructure.persistence.springdata;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import mexibank.infrastructure.persistence.entity.UserEntity;

/**
 * Repositorio de Spring Data para {@link UserEntity}.
 *
 * <p><strong>Por que no es el puerto del dominio.</strong> Este tipo depende de
 * Spring Data, y el puerto {@code UserRepository} del dominio no puede. Lo que si
 * hace es conectarse al adaptador ({@code UserPersistenceAdapter}), que es quien depende
 * de los dos: conoce la entidad JPA y expone el puerto.
 *
 * <p><strong>Por que los metodos se derivan del nombre y no se escriben
 * {@code @Query}.</strong> {@code findByEmail} se convierte en un
 * {@code SELECT} sobre la columna {@code email} y ya. Escribir la consulta a mano
 * solo tiene sentido cuando el nombre no describe lo que hace, y entonces el
 * {@code @Query} documenta algo que el nombre no dice.
 *
 * <p><strong>Por que {@code existsByEmail} devuelve {@code boolean} y no
 * {@code Optional<User>}.</strong> Porque el caso de uso que lo llama solo quiere
 * saber si el correo esta libre, no quiere la fila. Devolver la entidad obligaria
 * a traerla para descartar la mitad de sus columnas.
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Busca por correo.
     *
     * <p>El correo llega ya normalizado a minusculas por el value object del
     * dominio, asi que la comparacion es exacta y puede usar el indice. Por eso no
 * hay {@code IgnoreCase} aqui: impediria usar el indice unico de la columna.
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Indica si el correo ya esta registrado.
     *
     * <p>Devuelve un booleano y no un {@code Optional} porque el caso de uso solo
     * necesita el si o el no. Spring Data lo traduce a un
     * {@code SELECT COUNT(*)}, que no trae ninguna columna.
     */
    boolean existsByEmail(String email);
}