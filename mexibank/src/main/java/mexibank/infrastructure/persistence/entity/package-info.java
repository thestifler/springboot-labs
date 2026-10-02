/**
 * Entidades JPA. Modelan la tabla, no el negocio.
 *
 * <p>Estas clases existen para persistir: tienen anotaciones JPA, son mutables
 * por necesidad (Jackson/JPA necesitan setters o constructores), sus
 * {@code equals}/ {@code hashCode} siguen el patron recomendado para JPA
 * (identidad por clave primaria, hashCode estable) y no contienen reglas de
 * negocio.
 *
 * <p>El contrato de negocio no depende de ellas. El mapa entre entidad y
 * agregado es 1:N intencionado: el esquema puede cambiar (particiones, columnas
 * calculadas, vistas) sin que el dominio lo note.
 */
package mexibank.infrastructure.persistence.entity;
