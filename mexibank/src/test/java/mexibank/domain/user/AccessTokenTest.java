package mexibank.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccessTokenTest {

    private static final UserId SUJETO = UserId.from(UUID.randomUUID());

    private static final Instant EMISION = Instant.parse("2026-03-01T10:00:00Z");

    @Nested
    @DisplayName("Invariantes")
    class Invariantes {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("no admite un token vacio: un token en blanco no identifica a nadie")
        void exigeToken(String valor) {
            assertThatThrownBy(() -> new AccessToken(valor, SUJETO, Set.of(Role.CUSTOMER),
                    EMISION.plusSeconds(900), EMISION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vacio");
        }

        @Test
        @DisplayName("no admite un token sin sujeto, porque no se sabria a quien pertenece")
        void exigeSujeto() {
            assertThatThrownBy(() -> new AccessToken("abc", null, Set.of(Role.CUSTOMER),
                    EMISION.plusSeconds(900), EMISION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sujeto");
        }

        @Test
        @DisplayName("no admite un token sin roles, porque no podria autorizar nada")
        void exigeRoles() {
            assertThatThrownBy(() -> new AccessToken("abc", SUJETO, Set.of(),
                    EMISION.plusSeconds(900), EMISION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("al menos un rol");
        }

        @Test
        @DisplayName("no admite un token que caduca antes de emitirse")
        void exigeCaducidadPosterior() {
            assertThatThrownBy(() -> new AccessToken("abc", SUJETO, Set.of(Role.CUSTOMER),
                    EMISION.minusSeconds(1), EMISION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("caducar despues");
        }

        @Test
        @DisplayName("no admite un token que caduca en el mismo instante en que se emite")
        void exigeCaducidadEstrictamentePosterior() {
            assertThatThrownBy(() -> new AccessToken("abc", SUJETO, Set.of(Role.CUSTOMER),
                    EMISION, EMISION))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("no admite fechas nulas")
        void exigeFechas() {
            assertThatThrownBy(() -> new AccessToken("abc", SUJETO, Set.of(Role.CUSTOMER), null, EMISION))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AccessToken("abc", SUJETO, Set.of(Role.CUSTOMER),
                    EMISION.plusSeconds(900), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Inmutabilidad de los roles")
    class Inmutabilidad {

        @Test
        @DisplayName("copia el conjunto recibido: quien loQuite despues no cambia el token emitido")
        void copiaLosRoles() {
            Set<Role> roles = new HashSet<>(Set.of(Role.CUSTOMER));
            AccessToken token = new AccessToken("abc", SUJETO, roles,
                    EMISION.plusSeconds(900), EMISION);

            roles.add(Role.ADMIN);

            assertThat(token.roles()).containsExactly(Role.CUSTOMER);
        }

        @Test
        @DisplayName("el conjunto de roles no admite cambios una vez construido")
        void elConjuntoNoEsModificable() {
            AccessToken token = new AccessToken("abc", SUJETO, Set.of(Role.CUSTOMER),
                    EMISION.plusSeconds(900), EMISION);

            assertThatThrownBy(() -> token.roles().add(Role.ADMIN))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("acepta un conjunto inmutable, que es lo que le pasa Set.copyOf")
        void aceptaConjuntoInmutable() {
            assertThat(Collections.unmodifiableSet(Set.of(Role.TELLER)))
                    .isNotNull();

            AccessToken token = new AccessToken("abc", SUJETO,
                    Collections.unmodifiableSet(Set.of(Role.TELLER)),
                    EMISION.plusSeconds(900), EMISION);

            assertThat(token.roles()).containsExactly(Role.TELLER);
        }
    }

    @Nested
    @DisplayName("Accesores")
    class Accesores {

        @Test
        @DisplayName("expone el token tal cual lo emitio el adaptador, sin transformarlo")
        void elTokenEsOpaco() {
            String opaco = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.firma";

            AccessToken token = new AccessToken(opaco, SUJETO, Set.of(Role.CUSTOMER),
                    EMISION.plusSeconds(900), EMISION);

            assertThat(token.value()).isEqualTo(opaco);
        }

        @Test
        @DisplayName("permite calcular lo que le queda de vigencia a partir de las fechas")
        void lasFechasPermitenCalcularLaVigencia() {
            AccessToken token = new AccessToken("abc", SUJETO, Set.of(Role.CUSTOMER),
                    EMISION.plus(Duration.ofMinutes(15)), EMISION);

            assertThat(Duration.between(token.issuedAt(), token.expiresAt()))
                    .isEqualTo(Duration.ofMinutes(15));
        }
    }
}
