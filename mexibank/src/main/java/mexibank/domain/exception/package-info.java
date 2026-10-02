/**
 * Excepciones de dominio: reglas de negocio incumplidas.
 *
 * <p><strong>No conocen HTTP.</strong> Una excepcion de dominio no importa
 * {@code HttpStatus} ni menciona codigos 404 o 409. Traducirlas es trabajo del
 * adaptador de entrada ({@code infrastructure.rest.error}), y por eso las
 * mismas excepciones sirven intactas si manana el sistema se expone por Kafka o
 * por una cola.
 *
 * <p>Los mensajes se escriben paralogs y para el cliente, pero se construyen con
 * factorias semanticas ({@code X.faltaSaldo(...)} en vez de un
 * {@code String.format} en el caso de uso) para que el formato viva junto al
 * error que describe y no se disperse por los casos de uso.
 */
package mexibank.domain.exception;
