/**
 * Casos de uso de cuenta.
 *
 * <p>Cada archivo declara un caso de uso, de forma deliberada: una clase, un
 * metodo {@code execute(Command)} publico y una transaccion. Varias operaciones
 * en una misma clase obligan a decidir el limite de transaccion para cada
 * llamada, que es la forma habitual de que aparezcan escrituras fuera del sitio
 * donde se creia que estaban.
 *
 * <p>Los commands son records sin anotaciones: la validacion de forma ocurre en
 * el borde ({@code infrastructure.rest.dto}), antes de abrir la transaccion.
 * Aqui solo se comprobaran las reglas de negocio, que no se pueden expresar con
 * una anotacion.
 */
package mexibank.application.account;
