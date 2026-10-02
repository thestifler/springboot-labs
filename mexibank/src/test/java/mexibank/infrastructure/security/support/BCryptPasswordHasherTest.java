package mexibank.infrastructure.security.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mexibank.domain.user.PasswordHash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class BCryptPasswordHasherTest {

    private static final String CONTRASENA = "UnaContrasenaLarga";

    private BCryptPasswordHasher hasher;

    @BeforeEach
    void preparar() {
        hasher = new BCryptPasswordHasher(PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    @Nested
    @DisplayName("Hasheo")
    class Hasheo {

        @Test
        @DisplayName("no guarda la contrasena: el hash no la contiene")
        void elHashNoContieneLaContrasena() {
            PasswordHash hash = hasher.hash(CONTRASENA);

            assertThat(hash.value()).doesNotContain(CONTRASENA);
        }

        @Test
        @DisplayName("antepone el prefijo del algoritmo, que es lo que permite migrar a argon2 despues")
        void llevaElPrefijoDelAlgoritmo() {
            // Sin "{bcrypt}" no habria forma de saber con que algoritmo se
            // guardo cada contrasena, y cambiar de algoritmo obligaria a
            // rehashear todas de golpe.
            assertThat(hasher.hash(CONTRASENA).value()).startsWith("{bcrypt}");
        }

        @Test
        @DisplayName("dos hasheos de la misma contrasena dan hashes distintos, porque la sal es distinta")
        void cadaHasheoUsaUnaSalDistinta() {
            PasswordHash uno = hasher.hash(CONTRASENA);
            PasswordHash otro = hasher.hash(CONTRASENA);

            // Si dos usuarios con la misma contrasena tuvieran el mismo hash en
            // la tabla, con leerla bastaria para saber que comparten contrasena.
            // La sal es lo que lo evita.
            assertThat(uno).isNotEqualTo(otro);
        }

        @Test
        @DisplayName("hashea una cadena vacia sin fallar: la politica decide si es valida, no el hasher")
        void hasheaLaCadenaVacia() {
            assertThat(hasher.hash("").value()).startsWith("{bcrypt}");
        }
    }

    @Nested
    @DisplayName("Verificacion")
    class Verificacion {

        @Test
        @DisplayName("acepta la contrasena que produjo el hash")
        void aceptaLaContrasenaCorrecta() {
            assertThat(hasher.matches(CONTRASENA, hasher.hash(CONTRASENA))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "unacontrsenalarga",      // una letra menos
                "UnaContrasenaLarg",     // una letra menos al final
                "UNA CONTRASENA LARGA",  // mayusculas y espacios
                "OtraContrasenaLarga"})
        void rechazaContrasenasDistintas(String otra) {
            assertThat(hasher.matches(otra, hasher.hash(CONTRASENA))).isFalse();
        }

        @Test
        @DisplayName("acepta el hash de otro hasher con el mismo prefijo, porque el formato es el estandar")
        void aceptaUnHashExterno() {
            PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

            // Es lo que haria una migracion: un hash escrito por otra
            // instancia, con otra sal, se verifica igual.
            assertThat(hasher.matches(CONTRASENA, PasswordHash.of(encoder.encode(CONTRASENA))))
                    .isTrue();
        }

        @Test
        @DisplayName("propaga el error si el hash guardado no lleva prefijo de algoritmo")
        void fallaConUnHashSinPrefijo() {
            // Un hash sin prefijo no se puede verificar porque no se sabe con que
            // algoritmo se produjo. Fallar es mejor que devolver false: false
            // significaria "contrasena incorrecta" y el usuario no podria hacer
            // nada para arreglarlo.
            assertThatThrownBy(() -> hasher.matches(CONTRASENA, PasswordHash.of("sin-prefijo")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
