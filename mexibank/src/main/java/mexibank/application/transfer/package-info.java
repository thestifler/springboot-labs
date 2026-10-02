/**
 * Casos de uso de transferencia.
 *
 * <p>Una transferencia coordina dos cuentas, asi que es el caso de uso mas
 * complejo del sistema y el unico donde el <em>orden</em> importa por motivos
 * que no son de negocio sino de concurrencia: dos transferencias inversas
 * simultaneas pueden bloquearse mutuamente si cada una coge las cuentas en
 * orden distinto. El orden de bloqueo se fija aqui, en la capa de aplicacion,
 * porque es una decision de orquestacion; el bloqueo en si (PESSIMISTIC_WRITE)
 * es del adaptador.
 */
package mexibank.application.transfer;
