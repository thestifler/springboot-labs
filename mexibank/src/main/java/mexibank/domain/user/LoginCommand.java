package mexibank.domain.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Credenciales tal como las escribe el usuario en el formulario de login.
 *
 * <p><strong>Por que el correo y la contrasena son {@code String} y no los value
 * objects del dominio.</strong> Este record representa lo que entra <em>sin
 * procesar</em>. Si aqui se usara {@link Email}, la exception de formato se
 * tiraria antes de que el caso de uso pueda distinguir "el usuario escribio mal
 * su correo" de "ese correo no existe", y el mensaje que veria seria el de un
 * error de validacion en lugar de credenciales incorrectas. Normalizar y validar
 * el correo es responsabilidad del caso de uso, que es quien sabe que
 * respuesta darle.
 *
 * <p>La contrasena tampoco se envuelve en ningun tipo: {@link PasswordHash}
 * representa un hash, y aqui lo que hay es texto plano que todavia no se ha
 * hasheado. Son cosas distintas y no se mezclan.
 *
 * @param email    correo introducido, sin normalizar
 * @param password contrasena en claro. No se registra en ningun log
 */
public record LoginCommand(String email, String password) {

    public LoginCommand {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El correo no puede ser nulo ni vacio");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("La contrasena no puede ser nula ni vacia");
        }
    }
}
