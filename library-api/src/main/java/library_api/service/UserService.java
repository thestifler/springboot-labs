package library_api.service;

import library_api.dto.UserRequest;
import library_api.dto.UserResponse;
import library_api.dto.UserStatusRequest;
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

    /**
     * Cambia el estado de un usuario (ACTIVE / INACTIVE / SUSPENDED).
     *
     * Sin esta operacion el enum UserStatus es decorativo: todos los usuarios se
     * crean ACTIVE y nada en la aplicacion puede pasar a SUSPENDED, con lo que
     * UserNotActiveException no se dispararia nunca. Es la pieza que hace
     * funcional la regla de no prestar a un usuario suspendido.
     *
     * @throws UserNotFoundException si no existe un usuario con ese id
     */
    UserResponse updateUserStatus(Long id, UserStatusRequest statusRequest);
}
