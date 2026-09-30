package library_api.dto;

import library_api.entity.UserStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Datos de entrada para cambiar el estado de un usuario.
 *
 * Va en un record propio y no dentro de UserRequest porque la intencion es otra:
 * UserRequest da de alta un usuario y por eso no lleva id ni estado (los decide la
 * aplicacion), mientras que este es una modificacion parcial sobre un usuario que
 * ya existe, donde el unico campo que cambia es el estado.
 *
 * El estado es un enum y no un String suelto: si llegara un valor que no existe,
 * Jackson fallaria al deserializar. Con @NotNull, un cuerpo sin estado llega null
 * y se rechaza con un 400 y el detalle por campo, que es lo que el cliente espera.
 */
public record UserStatusRequest(

        @NotNull(message = "el status es obligatorio")
        UserStatus status
) {
}
