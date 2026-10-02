package mexibank.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordHashTest {

    private static final String HASH = "{bcrypt}$2a$10$abcdefghijklmnopqrstuvwxyz012345";

    @Nested
    @DisplayName("Construccion")
    class Construccion {

        @Test
        @DisplayName("envuelve el hash sin transformarlo")
        void envuelveElHashTalCual() {
            // of no hashea ni valida el formato: hashear es trabajo del adaptador,
            // que es quien conoce el algoritmo. Si of transformara el valor, el
            // hash guardado no corresponderia al que el verificador espera.
            assertThat(PasswordHash.of(HASH).value()).isEqualTo(HASH);
        }

        @Test
        @DisplayName("no admite un hash nulo ni vacio")
        void rechazaNuloYVacio() {
            // Un hash vacio permitiria guardar un usuario sin contrasena util y el
            // fallo aparecerian en el login, lejos del punto que lo origino.
            assertThatThrownBy(() -> PasswordHash.of(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nulo ni vacio");
            assertThatThrownBy(() -> PasswordHash.of("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nulo ni vacio");
        }
    }

    @Nested
    @DisplayName("Ocultacion del valor")
    class Ocultacion {

        @Test
        @DisplayName("toString no revela el hash")
        void toStringNoRevelaElHash() {
            // Un hash es material reutilizable: quien lo lea en un log puede
            // atacarlo sin conexion. Como toString se invoca sin querer al depurar
            // o al concatenar mensajes, se neutraliza en origen.
            assertThat(PasswordHash.of(HASH)).hasToString("PasswordHash[oculto]");
            assertThat(PasswordHash.of(HASH).toString()).doesNotContain(HASH);
        }
    }

    @Nested
    @DisplayName("Igualdad")
    class Igualdad {

        @Test
        @DisplayName("dos envoltorios del mismo hash son iguales")
        void igualdadPorValor() {
            assertThat(PasswordHash.of(HASH)).isEqualTo(PasswordHash.of(HASH));
            assertThat(PasswordHash.of(HASH)).hasSameHashCodeAs(PasswordHash.of(HASH));
        }

        @Test
        @DisplayName("hashes distintos no son iguales")
        void distintoValorNoSonIguales() {
            assertThat(PasswordHash.of(HASH))
                    .isNotEqualTo(PasswordHash.of("{bcrypt}otro"));
        }

        @Test
        @DisplayName("el hashCode es constante y no revela el valor")
        void elHashCodeNoRevelaElValor() {
            // No se deriva del valor a proposito: un hashCode de lectura publica
            // filtraria informacion del hash. Como estos objetos casi nunca van en
            // un conjunto, una constante no cuesta nada.
            assertThat(PasswordHash.of(HASH)).hasSameHashCodeAs(PasswordHash.of("{bcrypt}otro"));
        }
    }
}
