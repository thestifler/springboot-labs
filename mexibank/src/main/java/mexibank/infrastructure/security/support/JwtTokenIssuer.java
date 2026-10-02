package mexibank.infrastructure.security.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import mexibank.domain.user.AccessToken;
import mexibank.domain.user.Role;
import mexibank.domain.user.TokenIssuer;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;

/**
 * Adaptador del puerto {@link TokenIssuer} sobre JWT firmado con HMAC-SHA256.
 *
 * <p><strong>Por que HMAC y no RSA.</strong> Con HMAC la misma clave firma y
 * verifica, asi que solo hay que proteger un secreto. Con RSA hay dos claves: una
 * publica repartible y otra privada que solo tiene el servidor. La eleccion depende
 * de quien verifica. Si un tercero debe comprobar tokens sin poder confiar en
 * nosotros (otra aplicacion, un movil), hace falta RSA para no darle la clave de
 * firma. En un monolito, donde solo nosotros emitimos y verificamos, HMAC es mas
 * simple y no se puede usar mal por error.
 *
 * <p><strong>Por que HS256 y no el algoritmo por defecto.</strong> Con HMAC la
 * seguridad la aporta la longitud de la clave, no la del resumen. Una clave de 256
 * bits con HS256 ya es segura. Se declara de forma explicita para que el valor no
 * dependa de lo que elija la libreria.
 *
 * <p><strong>Por que los roles viajan dentro del token.</strong> Es una decision
 * con coste explicito. A cambio de no consultar la base de datos en cada peticion,
 * que es lo que permite escalar sin cache, un cambio de rol no surte efecto hasta
 * que caducan los tokens ya emitidos. Se compensa con una caducidad corta, 15
 * minutos en {@code LoginUseCaseImpl}.
 *
 * <p><strong>Por que el reloj se inyecta.</strong> La caducidad se calcula con
 * {@code clock.instant()} y no con {@code Instant.now()}. Con un reloj fijo, un
 * test puede comprobar que el token caduca exactamente cuando dice, sin que el
 * resultado dependa de lo rapido que se ejecute el test.
 */
public class JwtTokenIssuer implements TokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenIssuer.class);

    /**
     * Identificador del emisor.
     *
     * <p>Viaja como claim {@code iss} y el decoder lo comprueba, en
     * {@code SecurityConfig}. Sirve para que un token emitido por otra aplicacion
     * que comparta la clave (un servicio hermano, un entorno de pruebas) no sea
     * aceptado por error.
     */
    private static final String ISSUER = "mexibank";

    /**
     * Claim donde viajan los roles.
     *
     * <p>El nombre es el contrato entre este adaptador y el que los lea. Si el
     * filtro busca {@code roles} y aqui se escribiera {@code authorities}, el
     * token se validaria correctamente y el usuario acabaria sin permisos: un
     * fallo que no se manifiesta como error sino como un 403 desconcertante.
     */
    private static final String CLAIM_ROLES = "roles";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public JwtTokenIssuer(JwtEncoder encoder, JwtDecoder decoder, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.clock = clock;
    }

    /**
     * Emite un token para el usuario.
     *
     * <p><strong>Por que los roles se ordenan antes de escribirlos.</strong> Un
     * {@code Set} no tiene orden de iteracion garantizado, asi que dos emisiones
     * del mismo usuario con los mismos roles podrian producir tokens con bytes
     * distintos. No rompe nada, pero hace imposible comparar dos tokens para
     * comprobar si son el mismo, que es lo que haria un test.
     */
    @Override
    public AccessToken issue(User user, Duration ttl) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);

        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().value().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(CLAIM_ROLES, roles)
                .build();

        // El algoritmo se declara aqui y no se deja por defecto porque
        // NimbusJwtEncoder usa RS256 cuando no se le indica otro, y una clave
        // simetrica no puede firmar con RSA. Sin esta linea, el encoder falla con
        // "Failed to select a JWK signing key" en el primer login, no al arrancar:
        // el bean se construye sin problema y el error aparece cuando ya hay un
        // usuario intentando entrar.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        String token = encoder.encode(
                JwtEncoderParameters.from(header, claims)).getTokenValue();

        // La cadena firmada es la que vuelve al cliente. Las fechas tambien
        // viajan en el value object porque son lo que el caso de uso necesita
        // para responder sin tener que volver a verificar el token.
        //
        // El identificador unico del token lo genera la libreria. Anadirlo como
        // claim {@code jti} permitiria montar un dia una lista de revocados, pero
        // no se hace ahora porque no hay lista que consultar: la revocacion es
        // por caducidad.
        return new AccessToken(token, user.getId(), user.getRoles(), expiresAt, issuedAt);
    }

    /**
     * Verifica el token y devuelve el identificador del usuario.
     *
     * <p><strong>Por que devuelve {@code Optional} y no propaga la
     * excepcion.</strong> Un token invalido no es un fallo del sistema: es un
     * cliente que manda basura, o un atacante. Si aqui se propagara la excepcion,
     * el filtro de autenticacion tendria que distinguirlas para responder siempre
     * 401, y ese try-catch alrededor de cada llamada es justo el sitio donde se
     * cuela un 500 sin querer. Devolver vacio hace que el filtro ignore el token y
     * sea el paso de autorizacion el que responda.
     *
     * <p><strong>Por que se distingue el token caducado en el log.</strong> Solo
     * para el log. Un token caducado es el caso normal de un cliente que no ha
     * renovado a tiempo; uno con firma invalida es un intento de manipulacion. La
     * respuesta al cliente es la misma en ambos casos (401), porque distinguirlos
     * ayudaria a probar claves.
     */
    @Override
    public Optional<UserId> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(rawToken);
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                log.warn("Token con firma valida pero sin claim 'sub'. Se rechaza.");
                return Optional.empty();
            }
            return Optional.of(UserId.from(UUID.fromString(subject)));
        } catch (JwtValidationException ex) {
            log.debug("Token caducado o aun no valido: {}", ex.getMessage());
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException ex) {
            // JwtException cubre firma invalida, formato roto y todo lo demas.
            // IllegalArgumentException cubre el caso de un 'sub' que no es un UUID,
            // que llega aqui porque el token es genuino pero lo emitio otro servicio
            // con la misma clave.
            log.debug("Token rechazado: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}