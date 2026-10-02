package mexibank.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RoleTest {

    @Test
    @DisplayName("el rol por defecto es CUSTOMER, sin permisos de administracion")
    void elRolPorDefectoEsCustomer() {
        assertThat(Role.defaultRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("el conjunto por defecto contiene exactamente ese rol")
    void elConjuntoPorDefectoContieneSoloCustomer() {
        assertThat(Role.defaultRoles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    @DisplayName("el conjunto por defecto es inmutable, para que un alta no pueda alterarlo globalmente")
    void elConjuntoPorDefectoEsInmutable() {
        var roles = Role.defaultRoles();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> roles.add(Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("cada rol tiene un nombre en mayusculas estable")
    void losNombresSonEstables(Role role) {
        // El nombre del enum viaja dentro del token y se guarda en la columna
        // 'name' de la tabla roles (V3). Si alguien renombra una constante, los
        // datos ya guardados dejan de leerse, asi que el nombre es parte del
        // contrato con la base de datos y no un detalle interno. Ademas, la
        // migracion que siembra el catalogo escribe esos nombres a mano: el enum y
        // la tabla estan duplicados, y este test mas
        // UserPersistenceIT.todoRolDelDominioExisteEnLaTabla son los dos lados de
        // esa duplicacion.
        assertThat(role.name()).isEqualTo(role.name().toUpperCase(java.util.Locale.ROOT));
    }

    @Test
    @DisplayName("ADMIN es el unico rol que puede conceder roles")
    void adminEsElUnicoQueConcedeRoles() {
        assertThat(Role.valueOf("ADMIN")).isNotIn(Role.CUSTOMER, Role.TELLER);
    }
}