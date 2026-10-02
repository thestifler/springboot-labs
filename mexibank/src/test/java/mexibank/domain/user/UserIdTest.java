package mexibank.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserIdTest {

    @Nested
    @DisplayName("Generacion")
    class Generacion {

        @Test
        @DisplayName("newId genera un identificador distinto en cada llamada")
        void generaIdentificadoresDistintos() {
            // Si newId devolviera siempre lo mismo, todos los usuarios menos el
            // primero chocarian con la clave primaria. Se comprueba con un
            // conjunto en vez de con dos valores: dos llamadas podrian coincidir
            // por azar y el test no lo veria.
            Set<UserId> generados = new java.util.HashSet<>();
            for (int i = 0; i < 100; i++) {
                generados.add(UserId.newId());
            }

            assertThat(generados).hasSize(100);
        }

        @Test
        @DisplayName("from no genera un identificador nuevo: conserva el que recibe")
        void fromConservaElIdentificador() {
            // Es la diferencia que importa al rehidratar. Si from generara un id
            // nuevo, cada lectura de la base de datos inventaria una fila distinta
            // y los datos quedarian huerfanos.
            UUID original = UUID.randomUUID();

            assertThat(UserId.from(original).value()).isEqualTo(original);
            assertThat(UserId.from(original)).isEqualTo(new UserId(original));
        }
    }

    @Nested
    @DisplayName("Igualdad")
    class Igualdad {

        @Test
        @DisplayName("dos identificadores con el mismo UUID son iguales")
        void igualdadPorValor() {
            UUID uuid = UUID.randomUUID();

            assertThat(UserId.from(uuid)).isEqualTo(UserId.from(uuid));
            assertThat(UserId.from(uuid)).hasSameHashCodeAs(UserId.from(uuid));
        }

        @Test
        @DisplayName("dos identificadores con UUID distinto no son iguales")
        void distintoValorNoSonIguales() {
            assertThat(UserId.from(UUID.randomUUID()))
                    .isNotEqualTo(UserId.from(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("Validacion")
    class Validacion {

        @Test
        @DisplayName("no admite un UUID nulo")
        void rechazaNulo() {
            // Un id nulo no es un id: dejaria al agregado sin forma de
            // identificarse y el fallo apareceria mas tarde, al persistir.
            assertThatThrownBy(() -> UserId.from(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no puede ser nulo");
        }
    }
}
