/**
 * Manejador de excepciones global para la API REST.
 *
 * <p>Traduce las excepciones de dominio a respuestas HTTP coherentes. No pasa
 * mensajes internos al cliente y mantiene un cuerpo uniforme ({@code ApiError})
 * con {@code timestamp}, {@code status}, {@code error}, {@code message} y
 * {@code fieldErrors} para los errores de validacion.
 *
 * <p>Las excepciones de dominio no saben de HTTP: ese conocimiento vive
 * unicamente aqui. Si el sistema deja de ser REST, este paquete se sustituye y
 * el dominio no cambia.
 */
package mexibank.infrastructure.rest.error;
