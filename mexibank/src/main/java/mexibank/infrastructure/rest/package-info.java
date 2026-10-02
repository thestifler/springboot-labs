/**
 * Adaptador de entrada: REST.
 *
 * <p>Controladores que exponen los casos de uso. No contienen logica de negocio:
 * validan el formato en el borde, delegan al caso de uso correspondiente y
 * traducen el resultado a {@code ResponseEntity}. Los errores de dominio los
 * resuelve {@code infrastructure.rest.error.GlobalExceptionHandler}.
 *
 * <p>Los DTOs ({@code infrastructure.rest.dto}) separan la API externa del
 * modelo de dominio. Esto protege el contrato HTTP de cambios internos y
 * permite evolucionarlos por separado (versionado, renombrados, campos
 * derivados). El id generado por la base de datos nunca se acepta en un request
 * de alta.
 */
package mexibank.infrastructure.rest;
