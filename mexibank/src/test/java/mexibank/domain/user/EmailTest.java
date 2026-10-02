package mexibank.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Nested
    @DisplayName("Validacion del formato")
    class Validacion {

        @ParameterizedTest
        @ValueSource(strings = {
                "ana@correo.com",
                "ana.garcia@correo.es",
                "ana+banco@correo.com",
                "ana_garcia@correo.com",
                "ana-garcia@correo.com",
                "a@b.co"})
        void aceptaCorreosConFormatoValido(String raw) {
            assertThat(Email.of(raw).value()).isEqualTo(raw);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "correo",
                "ana@",
                "@correo.com",
                "ana@correo",
                "ana@.com",
                "ana@@correo.com",
                "ana garcia@correo.com",
                "ana@correo.c"})
        void rechazaCorreosConFormatoInvalido(String raw) {
            assertThatThrownBy(() -> Email.of(raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rechazaNuloVacioYSoloEspacios(String raw) {
            assertThatThrownBy(() -> Email.of(raw))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nulo ni vacio");
        }

        @Test
        @DisplayName("rechaza un correo mas largo de 254 caracteres, el maximo del RFC")
        void rechazaSobredimensionado() {
            String dominio = "@correo.com";
            String local = "a".repeat(254 - dominio.length() + 1);

            assertThatThrownBy(() -> Email.of(local + dominio))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("254");
        }

        @Test
        @DisplayName("acepta un correo de exactamente 254 caracteres")
        void aceptaElMaximoExacto() {
            String dominio = "@correo.com";
            String local = "a".repeat(254 - dominio.length());

            assertThat(Email.of(local + dominio).value()).hasSize(254);
        }
    }

    @Nested
    @DisplayName("Normalizacion")
    class Normalizacion {

        @Test
        @DisplayName("pasa a minusculas, porque el correo no distingue mayusculas")
        void normalizaAMinusculas() {
            assertThat(Email.of("Ana.Garcia@Correo.COM").value()).isEqualTo("ana.garcia@correo.com");
        }

        @Test
        @DisplayName("recorta los espacios de los extremos")
        void recortaEspacios() {
            assertThat(Email.of("  ana@correo.com  ").value()).isEqualTo("ana@correo.com");
        }

        @Test
        @DisplayName("dos escrituras del mismo correo dan el mismo valor")
        void dosEscriturasCoinciden() {
            assertThat(Email.of("Ana@Correo.com"))
                    .isEqualTo(Email.of("ana@correo.com"));
        }
    }

    @Nested
    @DisplayName("Igualdad y representacion")
    class IgualdadYRepresentacion {

        @Test
        @DisplayName("compara por valor, no por identidad")
        void comparaPorValor() {
            assertThat(Email.of("ana@correo.com"))
                    .isEqualTo(Email.of("ana@correo.com"))
                    .isNotEqualTo(Email.of("otro@correo.com"))
                    .isNotEqualTo(null)
                .isNotEqualTo("ana@correo.com");
        }

        @Test
        @DisplayName("dos correos iguales produce el mismo hashCode")
        void hashCodeCoincideConIgualdad() {
            assertThat(Email.of("ana@correo.com").hashCode())
                    .isEqualTo(Email.of("ana@correo.com").hashCode());
        }

        @Test
        @DisplayName("un conjunto de correos no guarda duplicados del mismo valor")
        void noGeneraDuplicadosEnUnConjunto() {
            // Un HashSet, no un Set.of: Set.of lanza excepcion ante duplicados en
            // vez de descartarlos, asi que no serviria para comprobar que la
            // normalizacion hace que dos escrituras sean la misma clave.
            Set<Email> correos = new HashSet<>(List.of(
                    Email.of("Ana@correo.com"),
                    Email.of("ana@correo.com"),
                    Email.of("OTRO@correo.com")));

            assertThat(correos).hasSize(2);
        }

        @Test
        @DisplayName("toString enmascara la parte local del correo")
        void toStringEnmascara() {
            String texto = Email.of("ana.garcia@correo.com").toString();

            assertThat(texto)
                    .doesNotContain("ana.garcia")
                    .contains("@correo.com");
        }
    }
}