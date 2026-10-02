package mexibank.infrastructure.security.filter;

import java.security.Principal;

import mexibank.domain.user.UserId;

/**
 * Identifica al usuario autenticado dentro del contexto de seguridad.
 *
 * <p><strong>Por que no se guarda el agregado {@code User} como principal.</strong>
 * El filtro ya consulto el usuario para conocer sus roles y comprobar que sigue
 * activo, y la tentacion es dejar el objeto a mano. Es un error por dos motivos
 * distintos, y los dos importan:
 *
 * <ul>
 *   <li>El agregado lleva dentro el hash de la contrasena. Cualquier cosa que
 *       lea el contexto de seguridad, como un registrador de auditoria o el
 *       {@code toString()} de la propia autenticacion que se escribe en los logs
 *       cuando hay un acceso denegado, alcanzaria el hash sin haber pasado por
 *       ningun filtro.</li>
 *   <li>Es un objeto mutable y su estado puede quedar obsoleto. Si alguien lo
 *       modificara, las autoridades de la peticion y los datos del principal ya
 *       no describirian lo mismo.</li>
 * </ul>
 *
 * <p>Lo unico que hace falta en el contexto es <em>quien</em> es. Los permisos ya
 * estan en las autoridades, que es donde Spring Security los consulta, y el resto
 * de datos se releen de la base de datos cuando hacen falta.
 *
 * <p><strong>Por que implementa {@link Principal} y no {@code UserDetails}.</strong>
 * Porque {@code UserDetails} obliga a declarar {@code getPassword()}, que aqui
 * no existe: en autenticacion por token no hay contrasena en la peticion, y un
 * metodo que devuelve {@code null} para cumplir una interfaz seria un comprobador
 * de que algo no aplica. {@code Principal} exige solo un nombre, que es
 * exactamente lo que se necesita.
 *
 * <p><strong>Por que el nombre es el identificador y no el correo.</strong> El
 * correo se puede cambiar; el identificador no. Un log de seguridad debe
 * apuntar a la misma persona antes y despues del cambio.
 *
 * @param id identificador del usuario autenticado
 */
public record AuthenticatedUser(UserId id) implements Principal {

    /**
     * @throws IllegalArgumentException si el identificador es nulo, que seria un
     *                                  principal sin nombre
     */
    public AuthenticatedUser {
        if (id == null) {
            throw new IllegalArgumentException("Un principal necesita un identificador");
        }
    }

    /**
     * Nombre con el que Spring identifica a este principal.
     *
     * <p>Sin este metodo, {@code Authentication.getName()} devolveria el
     * {@code toString()} del objeto, que en un record es
     * {@code AuthenticatedUser[id=...]} y en cualquier otra clase seria la
     * identidad del objeto en memoria. Ninguna de las dos cosas sirve para
     * auditar: la primera es ruidosa y la segunda cambia en cada ejecucion.
     *
     * @return el identificador del usuario en texto
     */
    @Override
    public String getName() {
        return id.value().toString();
    }
}
