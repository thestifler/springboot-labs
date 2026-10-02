package mexibank.infrastructure.security.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import mexibank.domain.user.AccessToken;
import mexibank.domain.user.Email;
import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;

/**
 * Tests del emisor de tokens.
 *
 * <p><strong>Por que se construye el encoder y el decoder reales.</strong> Lo que
 * hay que comprobar es que el token emitido se puede volver a leer con la misma
 * clave y que uno manipulado no. Con el encoder y el decoder simulados, la
 * prueba comprobaria que el codigo llama a los collaborators, que es exactamente
 * lo que podria romperse sin que nadie se entere.
 *
 * <p><strong>Por que la clave se deriva de una constante y no de configuracion.</strong>
 * El test no necesita la clave de produccion, y escribirla aqui evita que un
 * cambio de secreto rompa la suite. Lo que si importa es que tenga 32 bytes,
 * que es el minimo que acepta HS256.
 */
class JwtTokenIssuerTest {

    private static final String CLAVE_TXT = "clave-de-prueba-de-32-bytes-12345";

    private static final byte[] CLAVE = CLAVE_TXT.getBytes(StandardCharsets.UTF_8);

    private static final Instant AHORA = Instant.parse("2026-03-01T10:15:30Z");

    private static final Duration TTL = Duration.ofMinutes(15);

    /**
     * Instante al que se le da al verificador: un minuto despues de emitir.
     *
     * <p>Un minuto esta dentro de la vigencia de 15 minutos, de modo que un token
     * recien emitido es valido; y esta dos horas y un minuto despues de la
     * caducidad del token emitido hace dos horas, de modo que ese si esta
     * caducado. Con el reloj del sistema en su lugar, estos dos casos solo se
     * podrian distinguir si la suite se ejecutara dentro de la ventana de 15
     * minutos posterior a {@link #AHORA}.
     */
    private static final Instant VERIFICACION = AHORA.plus(Duration.ofMinutes(1));

    private JwtEncoder encoder;

    private JwtDecoder decoder;

    private JwtTokenIssuer issuer;

    @BeforeEach
    void preparar() {
        encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(CLAVE));
        decoder = decoderCon(CLAVE);
        issuer = new JwtTokenIssuer(encoder, decoder, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    /**
     * Decoder con la clave dada y con la caducidad medida contra
     * {@link #VERIFICACION}.
     *
     * <p><strong>Por que hay que fijarle el reloj.</strong> Spring valida el
     * claim {@code exp} con el reloj del sistema y el builder no expone el reloj
     * de Nimbus. Los tokens de estos tests se emiten con una fecha fija, asi que
     * con el reloj real todos habrian caducado y el test mediria la fecha de
     * ejecion en lugar de la logica. Reconstruir el validador con
     * {@code createDefaultWithValidators} mantiene las dos comprobaciones que si
     * importan, la del emisor y la de la vigencia, y solo cambia de donde sale el
     * instante.
     *
     * <p><strong>Lo que no se toca.</strong> La verificacion de la firma la hace
     * Nimbus, que es la que estos tests miden de verdad: un token firmado con
     * otra clave tiene que ser rechazado.
     */
    private JwtDecoder decoderCon(byte[] clave) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(clave, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decodificador.setJwtValidator(jwt -> {
            // getClaimAsString y no getIssuer: este ultimo convierte el claim a
            // URL, y "mexibank" no lo es, de modo que la llamada lanzaria una
            // excepcion en lugar de devolver el valor.
            if (!"mexibank".equals(jwt.getClaimAsString("iss"))) {
                return fallo("el token lo emitio otro servicio");
            }
            Instant exp = jwt.getExpiresAt();
            if (exp == null) {
                return fallo("el token no lleva caducidad");
            }
            if (!exp.isAfter(VERIFICACION)) {
                return fallo("el token ha caducado");
            }
            Instant nbf = jwt.getNotBefore();
            if (nbf != null && nbf.isAfter(VERIFICACION)) {
                return fallo("el token todavia no es valido");
            }
            return OAuth2TokenValidatorResult.success();
        });

        return decodificador;
    }

    @Nested
    @DisplayName("Emision")
    class Emision {

        @Test
        @DisplayName("produce un token que se puede volver a leer")
        void elTokenSeVerifica() {
            User user = usuario(Role.CUSTOMER);

            AccessToken token = issuer.issue(user, TTL);

            assertThat(issuer.verify(token.value())).contains(user.getId());
        }

        @Test
        @DisplayName("el token se firma con HS256, que es lo que permite una clave simetrica")
        void elAlgoritmoEsHs256() {
            // Si el algoritmo no se declara, Nimbus usa RS256 por defecto y una
            // clave simetrica no puede firmar. El fallo aparece en el primer
            // login, no al arrancar, asi que el unico sitio donde se detecta es
            // un test que emita un token de verdad.
            Jwt jwt = leer(issuer.issue(usuario(Role.CUSTOMER), TTL).value());

            assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        }

        @Test
        @DisplayName("el token lleva al usuario como sujeto, y no el correo")
        void elSujetoEsElIdentificador() {
            User user = usuario(Role.CUSTOMER);

            Jwt jwt = leer(issuer.issue(user, TTL).value());

            assertThat(jwt.getSubject()).isEqualTo(user.getId().value().toString());
        }

        @Test
        @DisplayName("el token lleva los roles, ordenados, para que dos emisiones del mismo usuario coincidan")
        void losRolesVanOrdenados() {
            User user = usuario(Role.TELLER, Role.ADMIN, Role.CUSTOMER);

            Jwt jwt = leer(issuer.issue(user, TTL).value());

            assertThat(jwt.getClaimAsStringList("roles"))
                    .containsExactly("ADMIN", "CUSTOMER", "TELLER");
        }

        @Test
        @DisplayName("dos emisiones del mismo usuario producen exactamente el mismo token")
        void laEmisionEsDeterminista() {
            User user = usuario(Role.CUSTOMER, Role.TELLER);

            // El orden de iteracion de un Set no esta garantizado, asi que sin
            // ordenar los roles dos emisiones con los mismos roles podrian dar
            // tokens distintos y este test no podria afirmar nada.
            assertThat(issuer.issue(user, TTL).value())
                    .isEqualTo(issuer.issue(user, TTL).value());
        }

        @Test
        @DisplayName("la caducidad sale del reloj inyectado mas el TTL, no del reloj del sistema")
        void laCaducidadVieneDelRelojInyectado() {
            AccessToken token = issuer.issue(usuario(Role.CUSTOMER), TTL);

            assertThat(token.issuedAt()).isEqualTo(AHORA);
            assertThat(token.expiresAt()).isEqualTo(AHORA.plus(TTL));
        }

        @Test
        @DisplayName("el value object devuelto lleva el token, el sujeto y las fechas")
        void elValueObjectLoTieneTodo() {
            User user = usuario(Role.CUSTOMER);

            AccessToken token = issuer.issue(user, TTL);

            assertThat(token.subject()).isEqualTo(user.getId());
            assertThat(token.roles()).containsExactly(Role.CUSTOMER);
            assertThat(Duration.between(token.issuedAt(), token.expiresAt())).isEqualTo(TTL);
        }
    }

    @Nested
    @DisplayName("Verificacion")
    class Verificacion {

        @Test
        @DisplayName("rechaza una cadena vacia o nula sin lanzar excepcion")
        void rechazaVacio() {
            assertThat(issuer.verify(null)).isEmpty();
            assertThat(issuer.verify("")).isEmpty();
            assertThat(issuer.verify("   ")).isEmpty();
        }

        @Test
        @DisplayName("rechaza una cadena que no es un token")
        void rechazaBasura() {
            assertThat(issuer.verify("esto-no-es-un-jwt")).isEmpty();
        }

        @Test
        @DisplayName("rechaza un token con la firma alterada")
        void rechazaFirmaManipulada() {
            String original = issuer.issue(usuario(Role.CUSTOMER), TTL).value();

            // Se altera el PRIMER caracter de la firma, no el ultimo. Es
            // deliberado: en Base64 el ultimo caracter de un grupo incompleto
            // solo aporta 4 de sus 6 bits, los otros 2 son relleno. Dos
            // caracteres distintos pueden por tanto decodificar a los mismos
            // bytes ("A" y "B" se diferencian solo en un bit de relleno), de
            // modo que cambiar el ultimo caracter no siempre cambia la firma y
            // el test fallaba de forma intermitente. El primer caracter aporta
            // 6 bits significativos, asi que alterarlo cambia siempre los
            // bytes y la verificacion tiene que fallar.
            int inicioFirma = original.lastIndexOf('.') + 1;
            char primero = original.charAt(inicioFirma);
            char alterado = primero == 'A' ? 'B' : 'A';
            String manipulado = original.substring(0, inicioFirma)
                    + alterado
                    + original.substring(inicioFirma + 1);

            assertThat(issuer.verify(manipulado)).isEmpty();
        }

        @Test
        @DisplayName("rechaza un token firmado con otra clave")
        void rechazaFirmaDeOtraClave() {
            byte[] otraClave = "otra-clave-distinta-de-32-bytes-ok!".getBytes(StandardCharsets.UTF_8);
            JwtTokenIssuer emisorExterno = new JwtTokenIssuer(
                    new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(otraClave)),
                    decoderCon(otraClave),
                    Clock.fixed(AHORA, ZoneOffset.UTC));

            // El mismo contenido, firmado por alguien a quien no se le ha dado
            // la clave del banco. Es el ataque mas simple y el que el filtro
            // tiene que cortar.
            assertThat(issuer.verify(emisorExterno.issue(usuario(Role.ADMIN), TTL).value()))
                    .isEmpty();
        }

        @Test
        @DisplayName("rechaza un token caducado, aunque la firma sea correcta")
        void rechazaTokenCaducado() {
            JwtTokenIssuer emisorPasado = new JwtTokenIssuer(encoder, decoder,
                    Clock.fixed(AHORA.minus(Duration.ofHours(2)), ZoneOffset.UTC));

            // El token se emitio hace dos horas con 15 minutos de vigencia, asi
            // que su 'exp' ya paso. Nimbus lo rechaza al decodificar, y por eso
            // verify devuelve vacio en vez de propagar la excepcion.
            AccessToken viejo = emisorPasado.issue(usuario(Role.CUSTOMER), TTL);

            assertThat(issuer.verify(viejo.value())).isEmpty();
        }

        @Test
        @DisplayName("el token identifica al usuario para el que se emitio, y a nadie mas")
        void elTokenIdentificaASuPropioUsuario() {
            User emisor = usuarioConId(UUID.randomUUID(), Role.CUSTOMER);
            User otro = usuarioConId(UUID.randomUUID(), Role.CUSTOMER);

            // El filtro carga el usuario por el identificador que sale de aqui.
            // Si el token no lo llevara, el filtro no tendria contra quien
            // comprobar la contrasena ni si la cuenta sigue activa, que es
            // justamente lo que hace que desactivar una cuenta surta efecto
            // inmediato.
            UserId identificado = issuer.verify(issuer.issue(emisor, TTL).value()).orElseThrow();

            // No basta con que el token lleve un identificador cualquiera: tiene
            // que ser el del emisor y no el de otro usuario. Un emisor que
            // escribiera un id fijo pasaria la primera comprobacion y fallaria
            // esta.
            assertThat(identificado).isEqualTo(emisor.getId());
            assertThat(identificado).isNotEqualTo(otro.getId());
        }
    }

    /**
     * Resultado de una comprobacion fallida.
     *
     * @param descripcion motivo, que solo aparece en el log de los tests
     * @return resultado con el error
     */
    private OAuth2TokenValidatorResult fallo(String descripcion) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", descripcion, null));
    }

    /**
     * Lee un token con el decoder bajo prueba.
     *
     * <p>Es el mismo decoder que usa {@code verify}, de modo que el token que
     * este metodo descompone es exactamente el que el filtro de autenticacion
     * vera. Comprobar los claims aqui no con otro decoder distinto es lo que
     * hace que el test cubra el camino real y no una reconstruccion del formato.
     */
    private Jwt leer(String token) {
        return decoder.decode(token);
    }

    private User usuario(Role... roles) {
        return usuarioConId(UUID.randomUUID(), roles);
    }

    private User usuarioConId(UUID id, Role... roles) {
        return User.reconstitute(
                UserId.from(id),
                Email.of("ana@correo.com"),
                PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv"),
                java.util.Set.of(roles),
                true,
                AHORA.minusSeconds(86_400),
                AHORA);
    }

}
