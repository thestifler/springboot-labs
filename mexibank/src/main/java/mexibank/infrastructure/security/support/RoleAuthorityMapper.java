package mexibank.infrastructure.security.support;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import mexibank.domain.user.Role;

/**
 * Convierte roles del dominio en autoridades que Spring Security entiende.
 *
 * <p><strong>Por que el prefijo {@code ROLE_} no esta en el dominio.</strong> Spring
 * espera que un rol se exprese como la autoridad {@code ROLE_ADMIN}, y su metodo
 * {@code hasRole("ADMIN")} anade el prefijo por dentro. Ese prefijo es una
 * convencion del framework, no un concepto de negocio: si viviera en el enum
 * {@link Role}, cambiarlo obligaria a recompilar el dominio, y el dominio no
 * deberia enterarse de que existe Spring Security.
 *
 * <p><strong>Por que una clase y no un metodo estatico.</strong> Podria ser
 * {@code RoleAuthorityMapper.toAuthorities(roles)} sin mas. Se declara como bean
 * en {@code SecurityConfig} para que tenga un unico lugar donde vive la regla y
 * para que un test pueda inyectarla. Cuando aparezcan mas fuentes de autoridad
 * (permisos finos, ambitos de API), la clase crece en lugar de repartirse en varios
 * sitios.
 *
 * <p><strong>Por que se ordena.</strong> El conjunto de autoridades va al
 * contexto de seguridad, que se compara e imprime en logs. Que dos peticiones del
 * mismo usuario produzcan el mismo texto, en el mismo orden, hace que un log
 * sea comparable con otro y que un test pueda afirmar sobre el contenido exacto.
 */
public class RoleAuthorityMapper {

    /**
     * Prefijo que Spring Security usa para distinguir un rol de un permiso.
     *
     * <p>Con el prefijo, {@code hasRole("ADMIN")} y {@code hasAuthority("ADMIN")}
     * dejan de ser lo mismo, y es lo que permite tener las dos cosas a la vez
     * (roles y permisos finos) sin que un permiso choca con un rol de igual
     * nombre.
     */
    private static final String PREFIX = "ROLE_";

    /**
     * Convierte un conjunto de roles en autoridades.
     *
     * @param roles roles del usuario. Puede ser {@code null} o vacio, y el
     *              resultado sera una lista vacia: un usuario sin roles es
     *              autentica pero no autorizado, y eso lo decide el paso de
     *              autorizacion, no el mapeo
     */
    public Collection<GrantedAuthority> toAuthorities(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(PREFIX + role.name()))
                // El orden de iteracion de un Set no esta garantizado. Ordenar
                // aqui hace que dos peticiones del mismo usuario produzcan la
                // misma coleccion de autoridades en el mismo orden, que es lo que
                // permite comparar dos logs o afirmar sobre el contenido exacto en
                // un test.
                .sorted(Comparator.comparing(GrantedAuthority::getAuthority))
                .toList();
    }
}