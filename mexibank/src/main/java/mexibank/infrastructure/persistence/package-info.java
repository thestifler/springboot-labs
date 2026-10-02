/**
 * Adaptador de salida: persistencia con JPA.
 *
 * <p>Implementa los puertos de repositorio declarados en {@code domain.*}. La
 * separacion es deliberada entre:
 * <ul>
 *   <li>{@code entity}: modelo de <strong>persistencia</strong> con
 *       {@code @Entity}, restricciones de esquema ({@code @Column(nullable=...)}),
 *       indices y relaciones.</li>
 *   <li>{@code springdata}: interfaces que extienden {@code JpaRepository} y
 *       metodos con {@code @Query} o {@code @Lock}. Solo conocen entidades.</li>
 *   <li>{@code adapter}: implementa el puerto del dominio. Es quien traduce
 *       entre el modelo del dominio (agregados, value objects) y el modelo de
 *       persistencia (entidades). Aqui es donde ocurre la inversion.</li>
 *   <li>{@code mapper}: mapeo entre dominio y entidad. Se usa MapStruct con
 *       {@code componentModel = SPRING} para que el adaptador lo pueda inyectar.
 *       El mapper es codigo generado, y los tests lo tratan como caja negra.</li>
 * </ul>
 *
 * <p><strong>Principio clave:</strong> el dominio NO conoce estas clases. El
 * adaptador devuelve agregados del dominio (ya mapeados) y recibe agregados del
 * dominio (para salvarlos). Nunca devuelve {@code *Entity} hacia dentro.
 *
 * <p><strong>Bloqueo:</strong> {@code @Lock(PESSIMISTIC_WRITE)} vive en la
 * interface de Spring Data y solo se usa cuando el puerto declara una operacion
 * con bloqueo (p. ej. {@code findByIdForUpdate}). El orden de bloqueo no se
 * decide aqui: lo decide el caso de uso.
 */
package mexibank.infrastructure.persistence;
