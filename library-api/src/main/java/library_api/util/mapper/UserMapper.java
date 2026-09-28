package library_api.util.mapper;

import library_api.dto.UserRequest;
import library_api.dto.UserResponse;
import library_api.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * Unica fuente de verdad para convertir entre entidad y DTO.
 *
 * componentModel = SPRING es obligatorio: sin el, MapStruct genera la
 * implementacion pero no le anade @Component, asi que Spring nunca la registra
 * como bean y la inyeccion en UserServiceImpl falla al arrancar.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserResponse toResponse(User user);

    /**
     * El id se ignora de forma explicita: en un alta lo genera la base de datos.
     * MapStruct ya lo dejaria en null, pero declararlo evita el warning de
     * propiedad no mapeada y deja la intencion documentada.
     */
    @Mapping(target = "id", ignore = true)
    User toEntity(UserRequest request);
}
