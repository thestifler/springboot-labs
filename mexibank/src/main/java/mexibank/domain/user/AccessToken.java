package mexibank.domain.user;

import java.time.Instant;
import java.util.Set;

/**
 * Token de acceso emitido para un usuario: su identificador, sus roles y cuando
 * caduca.
 *
 * <p><strong>Por que el token es un value object del dominio y no un
 * {@code String}.strong> El caso de uso de login devuelve "un token que caduca
 * el dia tal", y el adaptador REST lo serializa a un header. Si el caso de uso
 * devolviera un {@code String}, no habria forma de que el caducado viajara hasta
 * el cliente, y acabaria escrito a mano en el controlador.
 *
 * <p><strong>Que NO lleva dentro.</strong> No lleva la contrasena, ni el hash, ni
 * ningun dato que no sea necesario para autorizar. Un token viaja en cada
 * peticion y suele acabar en logs de proxies y de APM, asi que su contenido es
 * asumido como visible.
 *
 * <p><strong>Por que los roles estan aqui y no se releen en cada peticion.</strong>
 * Es una decision con coste, y conviene que sea explicita: el token lleva los
 * roles en el momento de emitirse, asi que un cambio de rol no surte efecto hasta
 * que el token caduca. A cambio, autorizar una peticion no requiere ir a la base
 * de datos, que es lo que permite que el sistema escale sin cache.
 *
 * <p>La alternativa es un token corto (por ejemplo 15 minutos) para que la
 * ventana de desfase sea pequena, y es la que se aplica por defecto. Si el banco
 * necesita que el cambio de rol sea inmediato, la opcion es verificar el usuario
 * contra la base de datos en cada peticion, que es una consulta mas por
 * request.
 *
 * @param value     el token ya firmado. El dominio lo trata como una cadena opaca:
 *                  no sabe si es un JWT, un valor aleatorio guardado en una tabla o
 *                  una referencia cifrada. Lo que sea, es responsabilidad del
 *                  adaptador que lo emitio
 * @param subject   identificador del usuario portador
 * @param roles     roles en el momento de la emision
 * @param expiresAt instante de caducidad
 * @param issuedAt  instante de emision
 */
public record AccessToken(String value,
                          UserId subject,
                          Set<Role> roles,
                          Instant expiresAt,
                          Instant issuedAt) {

    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El token no puede estar vacio");
        }
        if (subject == null) {
            throw new IllegalArgumentException("El token debe tener un sujeto");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("El token debe llevar al menos un rol");
        }
        if (expiresAt == null || issuedAt == null) {
            throw new IllegalArgumentException("El token debe tener instante de emision y caducidad");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("El token debe caducar despues de emitirse");
        }
        // Set.copyOf evita que quien lo reciba modifique el conjunto de roles y
        // cambie los permisos de un token ya emitido.
        roles = Set.copyOf(roles);
    }
}
