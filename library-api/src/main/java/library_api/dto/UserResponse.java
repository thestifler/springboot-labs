package library_api.dto;

import library_api.entity.UserStatus;

/**
 * Datos de salida de un usuario. Incluye el id generado por la base de datos.
 *
 * Antes existia un unico UserDto usado tanto para entrada como para salida, lo
 * que obligaba a duplicar el mapeo a mano (UserDto.fromEntity) ademas de usar
 * el mapper: dos fuentes de verdad que ademas se desincronizaban.
 */
public record UserResponse(
        Long id,
        String name,
        String firstLastName,
        String secondLastName,
        UserStatus status
) {
}
