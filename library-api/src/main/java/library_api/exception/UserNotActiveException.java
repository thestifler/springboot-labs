package library_api.exception;

import library_api.entity.UserStatus;

/**
 * Excepcion de dominio para "el usuario no puede llevarse prestamos ahora mismo".
 *
 * El usuario existe, asi que no es un 404, pero su UserStatus es SUSPENDED o
 * INACTIVE. La comprobacion se hace en el servicio y no con un @PrePersist en la
 * entidad porque es una regla de negocio que depende de otro agregado (User) y
 * porque el readme del proyecto deja la validacion de negocio en el service,
 * dentro de su transaccion, y en la entidad solo las restricciones de esquema.
 *
 * GlobalExceptionHandler la traduce a 409 Conflict: la peticion es valida, pero
 * choca con el estado actual del usuario.
 *
 * El mensaje nombra el estado REAL en vez de decir "esta inactivo o suspendido".
 * No es decorativo: son dos estados distintos que el cliente puede diferenciar y
 * solo uno de ellos es un bloqueo disciplinario. "Inactivo o suspendido" obligaba
 * al cliente a ir a consultar el usuario para saber cual de los dos era, cuando la
 * excepcion ya lo tiene en la mano.
 */
public class UserNotActiveException extends RuntimeException {

    private final Long userId;
    private final UserStatus status;

    public UserNotActiveException(Long userId, UserStatus status) {
        super("El usuario con id: " + userId + " esta en estado " + status
                + " y no puede llevarse prestamos");
        this.userId = userId;
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    /**
     * El estado que provoca la excepcion. Permite comprobar el motivo sin
     * parsear el mensaje.
     */
    public UserStatus getStatus() {
        return status;
    }
}