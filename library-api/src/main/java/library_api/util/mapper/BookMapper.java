package library_api.util.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import library_api.dto.BookRequest;
import library_api.dto.BookResponse;
import library_api.entity.Book;

/**
 * Unica fuente de verdad para convertir entre entidad y DTO de libro.
 *
 * No se ignora ningun campo: el isbn es la clave primaria y llega en el request
 * (lo aporta el cliente), asi que BookRequest y Book tienen los mismos campos y
 * MapStruct los copia todos. publicationDate es un LocalDate en ambos lados, por
 * lo que tampoco necesita conversion.
 *
 * componentModel = SPRING es obligatorio: sin el, MapStruct genera la
 * implementacion pero no le anade @Component, asi que Spring nunca la registra
 * como bean y la inyeccion en BookServiceImpl falla al arrancar.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookMapper {

    BookResponse toResponse(Book book);

    Book toEntity(BookRequest request);
}
