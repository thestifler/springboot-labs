package mexibank.domain.user;

/**
 * Reglas que debe cumplir una contrasena para aceptarse en el banco.
 *
 * <p><strong>Por que es una clase y no un metodo suelto en el caso de uso.</strong>
 * La politica se aplica en el alta, en el cambio de contrasena y probablemente en
 * el restablecimiento. Si estuviera escrita dentro del caso de uso de alta, el
 * cambio de contrasena tendria su propia copia, y las dos divergirian sin que
 * nadie se entere hasta que un usuario note que una via acepta mas que la otra.
 *
 * <p><strong>Por que las reglas viven en el dominio y no en un validador
 * Bean.</strong> Bean Validation ({@code @Size}, {@code @Pattern}) solo puede
 * declarar restricciones sobre el dato tal cual, y su mensaje acaba en la
 * respuesta HTTP, lo que revela la regla exacta. Ademas, un
 * {@code @Pattern} en un record solo se dispara si el dato pasa por un
 * {@code @Valid}, y no todos los caminos de entrada lo hacen.
 */
public final class PasswordPolicy {

    /**
     * Longitud minima. 12 caracteres es el minimo razonable en 2026 para una
     * cuenta bancaria: por debajo, casi todas las contrasenas aparecen en tablas
     * de credenciales filtradas que estan disponibles para cualquiera.
     */
    public static final int MIN_LENGTH = 12;

    /**
     * Longitud maxima. 72 es el limite de BCrypt: a partir de ahi, las
     * sobreposiciones se truncan y dos contrasenas distintas darian el mismo
     * hash. Cortar aqui y documentarlo es preferible a fingir que se admiten
     * contrasenas mas largas que en realidad se truncan.
     */
    public static final int MAX_LENGTH = 72;

    /**
     * Clase de utilidad: no se instancia.
     */
    private PasswordPolicy() {
    }

    /**
     * Comprueba la contrasena.
     *
     * @throws WeakPasswordException si no cumple la politica
     */
    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH
                || rawPassword.length() > MAX_LENGTH) {
            throw new WeakPasswordException();
        }
    }
}
