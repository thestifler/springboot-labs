/**
 * Interfaces de Spring Data JPA. Solo trabajan con {@code *Entity}.
 *
 * <p>Prohibido devolver agregados del dominio desde aqui: eso mezclaria el
 * modelo de persistencia con el de negocio. La traduccion la hace el
 * {@code *PersistenceAdapter}.
 */
package mexibank.infrastructure.persistence.springdata;
