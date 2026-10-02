/**
 * Agregado de transferencia, con sus puertos de salida.
 *
 * <p>Una transferencia mueve saldo entre dos cuentas que no se conocen entre si:
 * ninguna de las dos es dueña de la operacion. Por eso la regla que decide si la
 * transferencia es valida no cabe en {@code Account} y vive en un servicio de
 * dominio de este paquete, no en uno de los dos agregados.
 *
 * <p>El paquete aloja ademas los puertos declarados desde el dominio que
 * implementa la capa de entrada (casos de uso). No hay ningun {@code @Service}
 * aqui: el servicio de dominio se instancia desde el caso de uso que lo usa.
 */
package mexibank.domain.transfer;
