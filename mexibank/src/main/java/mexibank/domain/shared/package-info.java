/**
 * Value objects y tipos compartidos por varios agregados.
 *
 * <p>Solo entra aqui lo que de verdad cruza fronteras de funcionalidad. Si un
 * tipo lo usa un unico agregado, pertenece a su paquete y no a este: un
 * {@code shared} que crece sin criterio acaba siendo un cajon de sastre donde
 * nadie encuentra nada.
 *
 * <p>Los value objects son inmutables, se comparan por valor (no por identidad)
 * y no tienen setters. Un importe que se pueda mutar despues de haber sido
 * validado es un importe que puede dejar de ser valido.
 */
package mexibank.domain.shared;
