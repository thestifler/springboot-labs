/**
 * Filtro de autenticacion y respuestas 401/403.
 *
 * <p><strong>Por que hay que declarar la respuesta del 401.</strong> Sin un
 * {@code AuthenticationEntryPoint} propio, Spring Security responde con una
 * redireccion a una pagina de login (un 302 a {@code /login}) que en una API no
 * existe. El cliente recibe un 302 en vez de un JSON y su deserializador falla con
 * un error que dice "no se puede leer la respuesta" en lugar de "no estas
 * autenticado".
 *
 * <p><strong>Por que 401 y 403 estan separados.</strong> 401 significa "no se quien
 * eres, identificate". 403 significa "se quien eres y no te dejo". Confundirlos hace
 * que un cliente reintente un login que no va a funcionar nunca. Los dos cuerpos
 * tienen la misma forma para que el cliente tenga un solo formato que leer, pero el
 * codigo y el mensaje distinguen los casos.
 */
package mexibank.infrastructure.security.filter;