package library_api.service;

import library_api.dto.UserRequest;
import library_api.dto.UserResponse;
import library_api.exception.UserNotFoundException;

/**
 * Contrato de la capa de servicio.
 *
 * getUser declara UserNotFoundException cuando el id no existe, en lugar de
 * devolver null. Con null, el controller era quien tenia que decidir el status
 * HTTP y ambos caminos de error quedaban indistinguibles.
 */
public interface UserService {

    /**
     * @throws UserNotFoundException si no existe un usuario con ese id
     */
    UserResponse getUser(Long id);

    UserResponse createUser(UserRequest userRequest);
}
