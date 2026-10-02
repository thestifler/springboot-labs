/**
 * Capa de dominio: el centro del hexagono.
 *
 * <p>Aqui vive el modelo de negocio y <strong>nada mas</strong>. Es la unica capa
 * que se escribe pensando en el negocio, no en el framework ni en la base de
 * datos.
 *
 * <p><strong>Regla de dependencia:</strong> el dominio no depende de
 * {@code application} ni de {@code infrastructure}, ni de Spring, ni de
 * {@code jakarta.persistence}, ni de nada que no sea el JDK. Las dependencias
 * apuntan hacia dentro y este es el punto mas interno, asi que no admite ninguna
 * hacia fuera.
 *
 * <p><strong>Como se comprueba:</strong> {@code ArchitectureTest} (ArchUnit)
 * falla la build si aparece una dependencia prohibida. No es una convencion, es
 * una puerta.
 *
 * <p><strong>Que hay aqui:</strong>
 * <ul>
 *   <li>Agregados, con su comportamiento y sus invariantes.</li>
 *   <li>Value objects (identidades, importes).</li>
 *   <li>Servicios de dominio, para reglas que abarcan mas de un agregado.</li>
 *   <li>Puertos de salida: las interfaces de repositorio. Se <em>declaran</em>
 *       aqui y se <em>implementan</em> en {@code infrastructure.persistence}.</li>
 *   <li>Excepciones de dominio, que no conocen HTTP.</li>
 * </ul>
 *
 * <p><strong>Prohibido aqui:</strong> anotaciones de Spring, anotaciones JPA,
 * {@code @Transactional}, tipos de servlet, DTOs de entrada o salida, y
 * logica de orquestacion.
 */
package mexibank.domain;
