package mexibank.domain.user;

import java.util.Objects;

/**
 * Hash de una contrasena. Envuelve el resultado de hashear y no admite el texto
 * plano.
 *
 * <p><strong>Por que existe esta clase.</strong> Sin ella, el hash seria un
 * {@code String} mas en el agregado, indistinguible de una contrasena en claro.
 * Con ella, la firma de cada metodo dice si espera un hash o una contrasena, y
 * compilar es la unica forma de pasar la equivocacion. Es el equivalente en
 * contrasenas de lo que hace {@link Email} con el formato: mover el error de una
 * lista de campos a un error de compilacion.
 *
 * <p><strong>Lo que esta clase NO hace.</strong> No hashea. Hashear es trabajo de
 * la infraestructura, porque el algoritmo (BCrypt, argon2) es una tecnologia y
 * el dominio no deberia conocerla ni cambiar cuando cambie. El puerto
 * {@link PasswordHasher} vive en el dominio; su implementacion, en el adaptador.
 *
 * <p><strong>Por que {@link #toString()} y {@link #hashCode()} ocultan el
 * valor.</strong> Un hash de contrasena es material reutilizable: quien lo lea en
 * un log puede hacer un ataque de diccionario offline contra el, y las
 * contrasenas que la gente reutiliza suelen estar ya filtradas en otro sitio.
 * Como ambos metodos se pueden invocar sin querer (al depurar, al agrupar en un
 * mapa, al concatenar en un mensaje), se neutralizan en origen.
 */
public final class PasswordHash {

    private final String value;

    private PasswordHash(String value) {
        this.value = value;
    }

    /**
     * Envuelve un hash ya calculado.
     *
     * @throws IllegalArgumentException si el hash es nulo o vacio
     */
    public static PasswordHash of(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("El hash de la contrasena no puede ser nulo ni vacio");
        }
        return new PasswordHash(hash);
    }

    /**
     * Expone el hash para que el adaptador pueda persistirlo.
     *
     * <p>Es el unico punto por el que el hash sale del value object, y por eso
     * no aparece en logs ni en las respuestas de la API.
     */
    public String value() {
        return value;
    }

    /**
     * Comparacion por valor.
     *
     * <p>Dos usuarios con la misma contrasena tienen hashes <em>distintos</em>,
     * porque BCrypt genera una salt distinta en cada invocacion. Por eso este
     * metodo casi nunca devuelve {@code true} entre usuarios distintos, y no
     * sirve para comprobar contrasenas: para eso esta
     * {@link PasswordHasher#matches(String, PasswordHash)}, que re-hashea con la
     * salt guardada.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PasswordHash that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    /**
     * Constante a proposito.
     *
     * <p>Un {@code hashCode} derivado del valor es de lectura publica y podria
     * filtrar informacion del hash. Ademas, como los {@code PasswordHash} casi
     * nunca van en un {@code HashSet} ni en un {@code HashMap}, una constante no
     * cuesta nada: en el improbable caso de colision solo degrada el rendimiento,
     * nunca la correccion.
     */
    @Override
    public int hashCode() {
        return 31;
    }

    @Override
    public String toString() {
        return "PasswordHash[oculto]";
    }
}
