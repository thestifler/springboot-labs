/**
 * Agregado de cuenta, con sus puertos de salida.
 *
 * <p>La clase {@code Account} es el unico punto donde se puede cambiar el saldo
 * de una cuenta. No hay setters publicos: el saldo se mueve unicamente a traves
 * de operaciones que validan el invariante, de modo que es imposible dejar una
 * cuenta en un estado invalido por la via de los setters.
 *
 * <p>Las interfaces {@code AccountRepository} y {@code AccountNumberGenerator}
 * son <strong>puertos de salida</strong>: viven en el dominio porque el dominio
 * necesita recuperar y guardar cuentas, pero las implementa
 * {@code infrastructure.persistence}, porque el detalle de <em>como</em> se
 * guarda es technology, no negocio.
 */
package mexibank.domain.account;
