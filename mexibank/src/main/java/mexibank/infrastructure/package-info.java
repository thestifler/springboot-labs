/**
 * Capa de infraestructura: adaptadores que conectan el dominio con el mundo.
 *
 * <p>Esta es la capa mas externa y la unica que tiene permiso para conocer
 * Spring, JPA, HTTP o cualquier otra tecnologia. Los adaptadores son
 * sustituibles: el repositorio puede ser Postgres, Mongo o en memoria, sin que
 * el dominio ni la aplicacion se enteren.
 *
 * <p><strong>Regla de dependencia:</strong> infrastructure depende de application
 * y de domain (lo que queda hacia dentro), pero <strong>ni application ni domain
 * dependen nunca de infrastructure</strong>. ArchUnit rechaza cualquier intento
 * de romper esta barrera, antes de que llegue a un pull request.
 *
 * <p><strong>Como cumple el hexagono:</strong> se divide en dos clases de
 * adaptadores:
 * <ul>
 *   <li><strong>De entrada</strong> (driving adapters): traducen peticiones
 *       externas al lenguaje de la aplicacion (casos de uso). Ej.: REST
 *       ({@code infrastructure.rest}).</li>
 *   <li><strong>De salida</strong> (driven adapters): traducen el lenguaje del
 *       dominio a llamadas a la tecnologia. Ej.: JPA
 *       ({@code infrastructure.persistence}).</li>
 * </ul>
 *
 * <p>La configuracion ({@code infrastructure.config}) es el pegamento que
 * cablea beans y hace que Spring construya el grafo respetando la inversion de
 * dependencias.
 */
package mexibank.infrastructure;
