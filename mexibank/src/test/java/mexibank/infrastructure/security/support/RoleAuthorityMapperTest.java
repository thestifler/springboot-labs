package mexibank.infrastructure.security.support;

import static org.assertj.core.api.Assertions.assertThat;

import mexibank.domain.user.Role;

import java.util.Collection;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.GrantedAuthority;

class RoleAuthorityMapperTest {

    private final RoleAuthorityMapper mapper = new RoleAuthorityMapper();

    @Test
    @DisplayName("antepone ROLE_, que es la convencion que entiende hasRole()")
    void anteponeElPrefijo() {
        Collection<GrantedAuthority> autoridades = mapper.toAuthorities(Set.of(Role.ADMIN));

        assertThat(autoridades)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("ordena el resultado, para que dos peticiones del mismo usuario produzcan el mismo texto")
    void ordenaElResultado() {
        Collection<GrantedAuthority> autoridades = mapper.toAuthorities(
                Set.of(Role.TELLER, Role.ADMIN, Role.CUSTOMER));

        // El orden de iteracion de un Set no esta garantizado. Sin ordenar, un
        // test que comparase el contenido del contexto de seguridad pasaria o
        // fallaria segun el hash de los objetos, y dos logs de la misma
        // peticion no serian comparables.
        assertThat(autoridades)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_CUSTOMER", "ROLE_TELLER");
    }

    @Test
    @DisplayName("la misma entrada produce siempre la misma salida, incluso si el Set cambia de orden")
    void esDeterminista() {
        Set<Role> a = Set.of(Role.ADMIN, Role.CUSTOMER);
        Set<Role> b = Set.of(Role.CUSTOMER, Role.ADMIN);

        assertThat(mapper.toAuthorities(a))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyElementsOf(mapper.toAuthorities(b).stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("cada rol produce una autoridad con su prefijo")
    void cadaRolSeTraduce(Role role) {
        assertThat(mapper.toAuthorities(Set.of(role)))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_" + role.name());
    }

    @Test
    @DisplayName("un conjunto vacio produce una lista vacia, no un error")
    void conjuntoVacio() {
        // Un usuario sin roles es una situacion que el paso de autorizacion
        // tiene que poder tratar. Si aqui se lanzara excepcion, el fallo
        // apareceria como un 500 en lugar de como un 403, que es lo que de
        // verdad esta pasando: el usuario esta autenticado y no puede hacer
        // nada.
        assertThat(mapper.toAuthorities(Set.of())).isEmpty();
    }

    @Test
    @DisplayName("un conjunto nulo produce una lista vacia, por si el token llega sin roles")
    void conjuntoNulo() {
        assertThat(mapper.toAuthorities(null)).isEmpty();
    }
}
