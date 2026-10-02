/**
 * Adaptadores que implementan los puertos de repositorio del dominio.
 *
 * <p>Aqui ocurre la inversion de dependencias: el dominio declara la interfaz,
 * este paquete la implementa. Por eso {@code application} puede inyectar
 * {@code AccountRepository} (dominio) sin saber que detras hay JPA.
 *
 * <p>Las operaciones con bloqueo se llaman explicitamente desde el caso de uso
 * (nombre del metodo del puerto deja clara la intencion: {@code findByIdForUpdate}).
 */
package mexibank.infrastructure.persistence.adapter;
