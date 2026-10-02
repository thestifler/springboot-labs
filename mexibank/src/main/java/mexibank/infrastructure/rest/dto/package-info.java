/**
 * DTOs de entrada/salida de la API REST.
 *
 * <p>Separados por direccion (Request/Response) a proposito. La validacion
 * ({@code @Valid}, {@code @NotBlank}, {@code @Positive}) vive aqui, en el borde,
 * antes de abrir ninguna transaccion. Los DTOs no tienen anotaciones JPA y no
 * conocen el dominio mas alla de lo estrictamente necesario para mapear.
 */
package mexibank.infrastructure.rest.dto;
