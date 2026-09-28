package library_api.entity;

/**
 * Estados posibles de un usuario de la biblioteca.
 * Se persiste como String (@Enumerated(EnumType.STRING)) para que la base de
 * datos siga siendo legible y no dependa del orden de las constantes del enum.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
