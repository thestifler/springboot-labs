package mexibank.domain.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdefghijkl",                          // 12, el minimo
            "UnaContrasenaLarga",                     // 17
            "1234-5678-9012-3456"})                  // 19, solo digitos y guiones
    void aceptaContrasenasDentroDelRango(String contrasena) {
        assertThatCode(() -> PasswordPolicy.validate(contrasena)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acepta exactamente 12 caracteres, el minimo")
    void aceptaElMinimoExacto() {
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(PasswordPolicy.MIN_LENGTH)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza 11 caracteres, uno por debajo del minimo")
    void rechazaUnoMenosDelMinimo() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(PasswordPolicy.MIN_LENGTH - 1)))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    @DisplayName("acepta exactamente 72 caracteres, el limite de BCrypt")
    void aceptaElMaximoExacto() {
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(PasswordPolicy.MAX_LENGTH)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rechaza 73 caracteres, donde BCrypt truncaria y dos contrasenas distintas darian el mismo hash")
    void rechazaUnoMasDelMaximo() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(PasswordPolicy.MAX_LENGTH + 1)))
                .isInstanceOf(WeakPasswordException.class);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"   "})
    void rechazaNuloVacioYSoloEspacios(String contrasena) {
        assertThatThrownBy(() -> PasswordPolicy.validate(contrasena))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    @DisplayName("no exige simbolos ni mayusculas: la longitud es la regla, no la composicion")
    void noExigeComposicion() {
        assertThatCode(() -> PasswordPolicy.validate("abcdefghijkl"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el mensaje no dice que regla se incumplio, para no servir de oraculo")
    void elMensajeNoRevelaLaRegla() {
        assertThatThrownBy(() -> PasswordPolicy.validate("corta"))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageNotContaining("12")
                .hasMessageNotContaining("72")
                .hasMessageNotContaining("caracteres")
                .hasMessageNotContaining("minimo");
    }
}
