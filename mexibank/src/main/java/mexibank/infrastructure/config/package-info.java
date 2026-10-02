/**
 * Configuracion de Spring. Aqui se publican los beans que no se pueden
 * autodetectar con {@code @ComponentScan} o que es mas legible declararlos
 * explicitamente.
 *
 * <p>El objetivo es dejar el minimo imprescindible: el dominio sigue siendo
 * puro, la aplicacion solo usa {@code @Service} y {@code @Transactional}, y
 * todo lo que toca framework queda fuera de los dos nucleos. Un ejemplo tipico
 * es el {@code Clock} (JDK, no framework), que se inyecta para que el tiempo
 * sea determinista en los tests.
 *
 * <p>Nada de codigo de negocio pasa por aqui. Esto no es la capa de aplicacion:
 * es cableado.
 */
package mexibank.infrastructure.config;
