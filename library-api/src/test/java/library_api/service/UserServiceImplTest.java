package library_api.service;

import library_api.dto.UserRequest;
import library_api.dto.UserResponse;
import library_api.dto.UserStatusRequest;
import library_api.entity.User;
import library_api.entity.UserStatus;
import library_api.exception.UserNotFoundException;
import library_api.repository.UserRepository;
import library_api.util.mapper.UserMapper;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del service con las dependencias simuladas.
 *
 * El caso clave es getUser_cuandoNoExiste_lanzaUserNotFoundException: con el
 * codigo anterior (Optional.orElseThrow() sin supplier) se lanzaba
 * NoSuchElementException y la API respondia 500. Este test fija ese contrato.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("getUser devuelve el usuario cuando existe")
    void getUser_cuandoExiste_devuelveElUsuario() {
        User entity = userConId(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        UserResponse expected = new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity)).thenReturn(expected);

        UserResponse result = userService.getUser(1L);

        assertThat(result).isEqualTo(expected);
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("getUser lanza UserNotFoundException con el id cuando no existe")
    void getUser_cuandoNoExiste_lanzaUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");

        verify(userMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("createUser guarda la entidad y devuelve el id generado")
    void createUser_persisteYDevuelveElUsuarioCreado() {
        UserRequest request = new UserRequest("Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        User mapped = new User("Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        User saved = userConId(10L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        UserResponse expected = new UserResponse(10L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);

        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.save(mapped)).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse result = userService.createUser(request);

        assertThat(result).isEqualTo(expected);
        assertThat(mapped.getId())
                .as("el id debe generarlo la base de datos, no el mapper")
                .isNull();
        verify(userRepository).save(mapped);
    }

    @Test
    @DisplayName("createUser conserva todos los campos, incluido secondLastName y status")
    void createUser_mapeaTodosLosCampos() {
        UserRequest request = new UserRequest("Carlos", "Perez", "Lopez", UserStatus.SUSPENDED);
        User mapped = new User("Carlos", "Perez", "Lopez", UserStatus.SUSPENDED);
        User saved = userConId(11L, "Carlos", "Perez", "Lopez", UserStatus.SUSPENDED);

        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.save(mapped)).thenReturn(saved);
        when(userMapper.toResponse(saved))
                .thenReturn(new UserResponse(11L, "Carlos", "Perez", "Lopez", UserStatus.SUSPENDED));

        UserResponse result = userService.createUser(request);

        // Antes, el DTO compartido perdia secondLastname y status en silencio.
        assertThat(result.secondLastName()).isEqualTo("Lopez");
        assertThat(result.status()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("createUser aplica ACTIVE cuando el request no indica status")
    void createUser_sinStatus_aplicaActivePorDefecto() {
        UserRequest request = new UserRequest("Luis", "Soto", null, null);
        User mapped = new User("Luis", "Soto", null, null);
        User saved = userConId(12L, "Luis", "Soto", null, UserStatus.ACTIVE);

        when(userMapper.toEntity(request)).thenReturn(mapped);
        when(userRepository.save(mapped)).thenReturn(saved);
        when(userMapper.toResponse(saved))
                .thenReturn(new UserResponse(12L, "Luis", "Soto", null, UserStatus.ACTIVE));

        UserResponse result = userService.createUser(request);

        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
    }

    private User userConId(Long id, String name, String first, String second, UserStatus status) {
        User user = new User(name, first, second, status);
        user.setId(id);
        return user;
    }

    // ---------------------------------------------------------------------
    // updateUserStatus
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("updateUserStatus cambia el estado del usuario")
    void updateUserStatus_cambiaElEstado() {
        User entity = userConId(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        UserStatusRequest request = new UserStatusRequest(UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.SUSPENDED));

        UserResponse result = userService.updateUserStatus(1L, request);

        assertThat(result.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(entity.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("updateUserStatus no guarda la entidad: la sincroniza Hibernate")
    void updateUserStatus_noGuardaLaEntidad() {
        User entity = userConId(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.INACTIVE));

        userService.updateUserStatus(1L, new UserStatusRequest(UserStatus.INACTIVE));

        // Un save() explicito de una entidad ya gestionada seria un no-op dentro de
        // la transaccion: el dirty checking se encarga.
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUserStatus es idempotente: volver a poner el mismo estado no es un error")
    void updateUserStatus_mismoEstado_esIdempotente() {
        User entity = userConId(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));

        UserResponse result = userService.updateUserStatus(1L, new UserStatusRequest(UserStatus.ACTIVE));

        // Devolver 200 deja que el cliente reintente sin miedo, en vez de recibir un
        // 409 por algo que no es un conflicto real.
        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateUserStatus reactiva un usuario suspendido")
    void updateUserStatus_reactivaUnUsuarioSuspendido() {
        User entity = userConId(1L, "Ana", "Gomez", "Ruiz", UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));

        UserResponse result = userService.updateUserStatus(1L, new UserStatusRequest(UserStatus.ACTIVE));

        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateUserStatus lanza UserNotFoundException si el usuario no existe")
    void updateUserStatus_usuarioInexistente_lanzaUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.updateUserStatus(99L, new UserStatusRequest(UserStatus.SUSPENDED)))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");

        verify(userMapper, never()).toResponse(any());
    }

    @Test
    @DisplayName("updateUserStatus no toca el nombre ni los apellidos")
    void updateUserStatus_noAfectaLosDemasCampos() {
        User entity = userConId(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userMapper.toResponse(entity))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.INACTIVE));

        userService.updateUserStatus(1L, new UserStatusRequest(UserStatus.INACTIVE));

        // Es la razon de que el endpoint sea PATCH: cambiar el estado no obliga al
        // cliente a reenviar el resto del recurso y arriesgarse a perderlo.
        assertThat(entity.getName()).isEqualTo("Ana");
        assertThat(entity.getFirstLastName()).isEqualTo("Gomez");
        assertThat(entity.getSecondLastName()).isEqualTo("Ruiz");
    }
}
