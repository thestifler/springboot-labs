package mexibank.domain.user;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Correo electronico de un usuario, validado e inmutable.
 *
 * <p><strong>Por que es un value object y no un {@code String}.strong> Un
 * {@code String} admitiria {@code "correo"} o {@code "a@b"}. Con este tipo,
 * construir un email invalido falla aqui, en el dominio, y no tres capas mas
 * arriba cuando alguien intenta persistirlo. El tipo lleva la garantia; si la
 * escrebiera cada caso de uso, bastaria con que uno se lo saltara.
 *
 * <p><strong>Normalizacion a minusculas.</strong> El email se guarda siempre en
 * minusculas porque no distingue mayusculas en la practica: {@code Ana@Correo.com}
 * y {@code ana@correo.com} son la misma cuenta. Si no se normalizara al
 * escribir, habria dos filas para la misma persona y el login dependeria de como
 * el usuario escribio su correo ese dia.
 *
 * <p><strong>Por que el regex esta aqui y no en el borde.</strong> Podria
 * parecer que validar el formato es tarea del adaptador REST, que es quien
 * recibe el dato crudo. Pero el mismo dato puede entrar por un comando de
 * consola, un evento de Kafka o una importacion masiva, y ninguno de esos caminos
 * pasa por el controlador. Si el formato se valida solo en REST, el resto de
 * entradas lo omiten. El adaptador puede validarlo antes (para dar un 400
 * legible), pero la garantia va en el dominio.
 */
public final class Email {

    /**
     * Formato pragmatico: algo antes de {@code @}, algo tras el punto del
     * dominio. No pretende cubrir la RFC 5322 completa, que permitiria
     * direcciones que ningun servidor de correo acepta hoy. Rechazar de mas es
     * preferible a aceptar de menos en un campo de identificacion.
     */
    private static final Pattern FORMATO = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /**
     * Limite de longitud. El RFC permite hasta 254 caracteres; se usa ese tope y
     * no otro porque el campo de la base de datos tiene que poder guardarlo.
     */
    private static final int MAX_LONGITUD = 254;

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    /**
     * Crea un email validandolo y normalizandolo a minusculas.
     *
     * @throws IllegalArgumentException si el formato no es valido o excede la
     *                                  longitud maxima
     */
    public static Email of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("El correo electronico no puede ser nulo ni vacio");
        }
        String normalizado = raw.trim().toLowerCase(Locale.ROOT);
        if (normalizado.length() > MAX_LONGITUD) {
            throw new IllegalArgumentException(
                    "El correo electronico no puede superar " + MAX_LONGITUD + " caracteres");
        }
        if (!FORMATO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("El correo electronico no tiene un formato valido");
        }
        return new Email(normalizado);
    }

    public String value() {
        return value;
    }

    /**
     * Compara por valor, no por identidad. Dos emails con el mismo texto son el
     * mismo email aunque sean instancias distintas: sin esto, un {@code Set} de
     * emails contendria duplicados del mismo valor.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Email that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * Enmascara el valor local. Un email es dato personal y estas clases acaban
     * en logs cuando algo falla, asi que no se imprime entero.
     *
     * <p>Se oculta solo la parte antes de la {@code @}: conservarla confirma que
     * el email pertenece a un dominio real, que ayuda a diagnosticar, sin
     * exponer la direccion.
     */
    @Override
    public String toString() {
        int arroba = value.indexOf('@');
        return "Email[***" + (arroba < 0 ? "" : value.substring(arroba)) + "]";
    }
}
