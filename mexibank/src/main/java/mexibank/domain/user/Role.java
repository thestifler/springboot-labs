package mexibank.domain.user;

import java.util.Set;

/**
 * Rol que se asigna a un usuario y que gobierna lo que puede hacer.
 *
 * <p><strong>Por que el dominio decide los roles.</strong> El conjunto de roles
 * es una decision de negocio: que exista un rol {@code ADMIN} y que ese rol
 * pueda dar de alta cuentas es conocimiento del banco, no de Spring Security.
 * Si los roles vivieran en el adaptador, el dominio no podria ni siquiera
 * expresar "este usuario es administrador".
 *
 * <p><strong>Por que un enum y no una coleccion en base de datos.</strong> Un
 * enum hace imposible tener un rol que el codigo no conoce: si alguien inserta
 * {@code "SUPERADMIN"} en la tabla, la aplicacion falla al leerla en lugar de
 * aceptarlo en silencio. Un rol que el dominio no reconoce es un error de
 * despliegue, y es mejor que falle al arrancar que en produccion con una
 * autorizacion que no hace lo que su nombre sugiere.
 *
 * <p>El coste es que anadir un rol exige desplegar. Es aceptable: los roles de un
 * banco son pocos, estables y subjects a control de cambios.
 *
 * <p><strong>Que NO vive aqui.</strong> El enum no conoce el prefijo
 * {@code ROLE_} ni el tipo {@code GrantedAuthority}. Ese prefijo es una
 * convencion de Spring Security, no un concepto de negocio, asi que el mapeo a
 * autoridades lo hace el adaptador ({@code RoleAuthorityMapper}). Si el
 * prefijo viviera aqui, cambiarlo seria tocar el nucleo, que es justo lo que la
 * arquitectura hexagonal evita.
 */
public enum Role {

    /**
     * Puede consultar y operar sobre sus propias cuentas y transferencias.
     *
     * <p>Es el rol por defecto de cualquier usuario registrado.
     */
    CUSTOMER,

    /**
     * Puede consultar y operar sobre <strong>cualquier</strong> cuenta.
     *
     * <p>Existe para el personal de sucursal, que necesita ver cuentas que no son
     * suyas. Por eso es un rol separado y no "permisos adicionales dentro de
     * CUSTOMER": poder tocar cuentas ajenas es una capacidad que tiene que poder
     * quitarse sin tocar la de los clientes.
     */
    TELLER,

    /**
     * Puede crear usuarios y conceder roles.
     *
     * <p>Es el unico rol que puede crear cuentas y conceder roles. Por diseno
     * no se concede desde la API: el primer ADMIN se crea directamente en la base
     * de datos, porque entregar ese poder tiene que ser una decision consciente.
     */
    ADMIN;

    /**
     * Rol que recibe un usuario recien registrado cuando no se indica otro.
     *
     * <p>Se centraliza aqui para que el "rol por defecto" no este repetido en
     * cada alta. Si ese valor cambia, este es el unico sitio que hay que tocar.
     */
    public static Role defaultRole() {
        return CUSTOMER;
    }

    /**
     * Conjunto con el rol por defecto.
     *
     * <p>Se encapsula en un metodo porque {@code Set.of(defaultRole())} repetido
     * en cada alta seria una decision duplicada.
     */
    public static Set<Role> defaultRoles() {
        return Set.of(defaultRole());
    }
}
