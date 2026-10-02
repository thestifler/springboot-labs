package mexibank.domain.user;

/**
 * Puerto de salida: sabe hashear contrasenas y comprobar si una contrasena en
 * claro corresponde a un hash.
 *
 * <p><strong>Por que el dominio declara este puerto.</strong> Verificar una
 * contrasena es una regla de negocio ("este usuario se autentica con esta
 * contrasena"), pero <em>ccomo</em> se verifica es tecnologia. Si
 * {@link User} llamara a BCrypt directamente, el dominio importaria Spring
 * Security y habria que recompilarlo el dia que se migre a argon2. Con el
 * puerto, el dominio depende de esta interfaz y la implementacion concreta
 * cambia sin tocarlo.
 *
 * <p><strong>Por que hashear es un puerto y no un detalle interno del
 * adaptador.</strong> El alta de un usuario necesita hashear. Si el hash lo
 * hiciera el adaptador al guardar, el agregado tendria que aceptar una
 * contrasena en claro y la dejaria pasar por su estado, con el risque de que
 * alguien la registre en un log o la devuelva en una respuesta. Con este puerto,
 * el caso de uso hashea <em>antes</em> de construir el agregado, y el agregado
 * solo ve hashes.
 */
public interface PasswordHasher {

    /**
     * Hashea una contrasena en claro.
     *
     * <p>La implementacion debe generar una salt aleatoria distinta en cada
     * llamada. Si dos llamadas con la misma entrada devolvieran el mismo
     * resultado, dos usuarios con la misma contrasena seria identificables en la
     * base de datos, y un ataque con diccionario seria mas rapido.
     *
     * @param rawPassword contrasena en claro. Solo debe existir en memoria el
     *                    tiempo de hashearla
     * @return el hash, listo para persistir
     */
    PasswordHash hash(String rawPassword);

    /**
     * Comprueba si una contrasena en claro corresponde a un hash guardado.
     *
     * <p>El hash guarda su propia salt, asi que la verificacion no necesita
     * ningun dato adicional.
     *
     * @param rawPassword contrasena en claro a verificar
     * @param storedHash  hash previamente generado por {@link #hash(String)}
     * @return {@code true} si coinciden
     */
    boolean matches(String rawPassword, PasswordHash storedHash);
}
