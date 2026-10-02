/**
 * Adaptador de seguridad: emision y validacion de tokens.
 *
 * <p><strong>Por que la firma de tokens no la decide Spring Boot.</strong> El
 * starter de resource server autoconfigura el verificador a partir de
 * {@code spring.security.oauth2.resourceserver.jwt.*}, que espera una clave
 * publica o un emisor externo. Aqui el token lo emite esta misma aplicacion, y
 * esa configuracion choca con el secreto simetrico que se quiere usar. Por eso la
 * dependencia es {@code spring-security-oauth2-jose} y no el starter completo:
 * queda la libreria, y el cableado es explicito.
 *
 * <p><strong>Por que un filtro propio y no el de Spring.</strong> El
 * {@code BearerTokenAuthenticationFilter} de Spring acepta el token y da por
 * autenticada a la persona. Este filtro ademas consulta el usuario en la base de
 * datos, y esa consulta es lo que hace que desactivar una cuenta surta efecto en la
 * peticion siguiente y no cuando caduquen los tokens ya emitidos.
 */
package mexibank.infrastructure.security;