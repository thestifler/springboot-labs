/**
 * Mappers entre dominio y entidad (MapStruct).
 *
 * <p>Los mappers son generados en tiempo de compilacion. Se registran con
 * {@code componentModel = SPRING} para poder inyectarlos en los adaptadores.
 *
 * <p>El reporte de JaCoCo excluye {@code *MapperImpl.class} de forma intencionada:
 * el codigo generado no se escribe a mano, no contiene logica de negocio y su
 * cobertura no aporta valor al analisis.
 */
package mexibank.infrastructure.persistence.mapper;
