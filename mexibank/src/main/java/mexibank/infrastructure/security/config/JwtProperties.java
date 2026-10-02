package mexibank.infrastructure.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Propiedades de la firma de tokens, leidas de {@code mexibank.security.jwt.*}.
 *
 * <p><strong>Por que no se leen con {@code @Value}.</strong> {@code @Value}
 * resuelve una propiedad cada vez y el resultado es un {@code String} que hay que
 * convertir a mano, con el fallo en tiempo de ejecucion si la conversion no cabe.
 * Con esta clase, Spring enlaza y valida los valores al arrancar: si la clave no
 * cumple el minimo de longitud o falta, la aplicacion no llega a aceptar
 * peticiones.
 *
 * <p><strong>Por que se valida la longitud de la clave.</strong> HS256 con una
 * clave corta se firma igual y no da error: simplemente es debil, porque el
 * atacante prueba todas las claves posibles en vez de todas las contrasenas
 * posibles. Un fallo de seguridad silencioso es el peor tipo, y la unica defensa es
 * no arrancar.
 *
 * <p><strong>Por que el validador es {@code @Validated} y no un if en el
 * constructor.</strong> El if solo se ejecutaria al crear el bean, y los beans de
 * propiedades se crean de forma perezosa en algunos contextos, con lo que el
 * fallo apareceria en el primer login en vez de al arrancar. Con
 * {@code @Validated}, Spring comprueba las restricciones al inyectar el bean.
 */
@Validated
@ConfigurationProperties(prefix = "mexibank.security.jwt")
public record JwtProperties(
        @NotNull
        SecretKey secret,
        long expirationMinutes) {

    /**
     * Longitud minima de la clave en bytes.
     *
     * <p>256 bits, que es el tamano que recomienda la especificacion JOSE para
     * HS256. Con una clave mas corta, la seguridad de la firma baja al tamano de la
     * clave, no al de la funcion de resumen.
     */
    public static final int MIN_KEY_BYTES = 32;

    public JwtProperties {
        if (secret != null && secret.value() != null
                && secret.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "La clave de firma de JWT debe tener al menos " + MIN_KEY_BYTES
                            + " bytes. Con una clave mas corta, HS256 sigue firmando pero la "
                            + "seguridad de la firma pasa a depender de la clave y no del resumen, "
                            + "y un atacante puede probar claves mas rapido que contrasenas. "
                            + "Genera una con: openssl rand -base64 32");
        }
    }

    /**
     * Clave secreta de firma, codificada en Base64.
     *
     * @param value clave en Base64
     */
    public record SecretKey(@NotBlank String value) {
    }
}