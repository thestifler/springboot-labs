/**
 * Casos de uso de la autenticacion.
 *
 * <p>Por que vive en la capa de aplicacion y no en {@code domain.user}. El login
 * necesita dos cosas que el dominio no debe conocer: el formato en que el cliente
 * recibe el token (el nombre del campo {@code tokenType}, el numero de segundos) y
 * el instante actual para calcular lo que le queda. Son decisiones de presentacion,
 * y el adaptador REST las necesita sin arrastrar al dominio.
 *
 * <p>{@link mexibank.application.auth.AuthResponse} es el unico tipo de este
 * paquete y se construye desde el {@code LoginResult} del dominio, no desde el
 * agregado. Asi el hash de contrasena no tiene forma de aparecer en una respuesta:
 * {@code LoginResult} si lo lleva, porque es un tipo interno, pero la proyeccion
 * publica no tiene forma de contenerlo.
 */
package mexibank.application.auth;