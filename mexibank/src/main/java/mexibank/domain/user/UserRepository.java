package mexibank.domain.user;

import java.util.Optional;

/**
 * Puerto de salida: recupera y guarda usuarios.
 *
 * <p>Vive en el dominio porque el dominio necesita poder consultar y persistir
 * usuarios, pero lo implementa {@code infrastructure.persistence.adapter}: el
 * detalle de <em>como</em> se guarda (JPA, SQL, fichero) es tecnologia, no
 * negocio. Gracias a la inversion de dependencias, un caso de uso puede inyectar
 * esta interfaz sin saber que detras hay Hibernate.
 *
 * <p><strong>Nota sobre los metodos de bloqueo.</strong> No hay un
 * {@code findByIdForUpdate}. Se decidio a proposito: la unica operacion que hoy
 * necesita bloqueo es la transferencia, que bloquea <em>cuentas</em>, no
 * usuarios. Anadir metodos de bloqueo aqui "por si acaso" crea puertos que nadie
 * implementa todavia y que obligan a cada adaptador nuevo a decidirse por ellos.
 * Si surge la necesidad, se anade entonces, con el caso de uso que la motiva.
 */
public interface UserRepository {

    /**
     * Busca por correo electronico.
     *
     * <p>El correo llega ya normalizado a minusculas por {@link Email}, asi que la
     * comparacion en base de datos es exacta y no necesita funciones de
     * insensibilidad a mayusculas, que impedirian usar el indice.
     *
     * @param email correo ya normalizado
     * @return el usuario, o vacio si no existe
     */
    Optional<User> findByEmail(Email email);

    /**
     * Busca por identificador.
     *
     * @param userId identificador del usuario
     * @return el usuario, o vacio si no existe
     */
    Optional<User> findById(UserId userId);

    /**
     * Indica si ya existe un usuario con ese correo.
     *
     * <p>Existe aparte de {@code findByEmail(...).isPresent()} para poder
     * responder con una consulta que no trae la fila entera. En el alta de un
     * usuario el caso mas frecuente es que el correo este libre, y en ese caso
     * esta consulta no lee nada.
     */
    boolean existsByEmail(Email email);

    /**
     * Guarda o actualiza el usuario.
     *
     * <p>El agregado llega aqui ya validado: los invariantes se comprueban en
     * {@link User}, no en la base de datos. La base de datos mantiene sus propias
     * restricciones (unicidad, tipos, claves foraneas) como ultima linea de
     * defensa, no como la primera.
     */
    void save(User user);
}
