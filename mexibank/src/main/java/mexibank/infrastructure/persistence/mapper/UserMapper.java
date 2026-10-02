package mexibank.infrastructure.persistence.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import mexibank.domain.user.Email;
import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;
import mexibank.infrastructure.persistence.entity.UserEntity;

/**
 * Traduce entre el agregado {@link User} y la entidad {@link UserEntity}.
 *
 * <p><strong>Por que una direccion es generada y la otra escrita.</strong> El
 * mapeo de la entidad hacia el agregado se declara a mano porque el agregado tiene
 * constructor privado, y MapStruct 1.6.3 no es capaz de construir un tipo sin
 * constructor accesible: se comprobaron las cuatro vias que ofrece (una clase en
 * {@code uses} con metodo estatico, la misma clase anotada con
 * {@code @ObjectFactory}, un metodo {@code default} en el mapper y un metodo
 * {@code static} en el mapper) y en las cuatro falla con "does not have an
 * accessible constructor". El mapeo del agregado hacia la entidad si se genera,
 * porque ahi el destino tiene constructor sin argumentos y setters.
 *
 * <p>El resultado practico es el que se queria: anadir un campo al agregado obliga
 * a tocar {@link #toEntity}, porque MapStruct lo exige al compilar, y no puede
 * olvidarse sin que la build falle.
 *
 * <p><strong>Por que la conversion de los value objects es explicita.</strong> El
 * agregado expone {@link UserId} y la entidad expone {@link UUID}. MapStruct
 * empareja por tipo, no sabe que uno envuelve al otro y no inventaria la
 * conversion. Los metodos {@code default} con {@code @Named} la declaran una vez y
 * son la unica fuente de esa traduccion.
 *
 * <p><strong>Lo que este mapper no hace.</strong> No hashea contrasenas, no
 * normaliza correos y no valida nada: solo copia valores ya calculados. Si
 * apareciera una contrasena en claro aqui, seria porque el caso de uso se la ha
 * pasado, y eso ya es un fallo antes de llegar a esta capa.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * De entidad a agregado.
     *
     * <p><strong>Por que se construye con {@code reconstitute} y no con
     * {@code register}.}</strong> Son las dos fabricas del agregado y hacen cosas
     * distintas: {@code register} valida las reglas del alta, {@code reconstitute}
     * solo rehidrata. Al releer de la base de datos tiene que usarse la segunda. Un
     * usuario dado de alta cuando la politica de contrasenas era mas laxa debe
     * poder seguir entrando; reconstruir con {@code register} lo bloquearia en
     * silencio el dia que la politica se endureciera.
     *
     * <p>Los roles llegan como {@code Set<RoleEntity>} (filas de la tabla
     * {@code roles}) y se traducen dentro de {@link UserFactory#construir}: esta es
     * la direccion de la lectura, donde la entidad ya trae las filas del catalogo.
     */
    default User toDomain(UserEntity entity) {
        return UserFactory.construir(
                uuidAUserId(entity.getId()),
                stringAEmail(entity.getEmail()),
                stringAPasswordHash(entity.getPasswordHash()),
                entity.getRoles(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * De agregado a entidad en una entidad nueva.
     *
     * <p><strong>Por que esta direccion si la genera MapStruct.</strong> El destino
     * tiene constructor sin argumentos y setters, que es justo el caso que
     * MapStruct resuelve sin ayuda. El coste de usar la biblioteca esta aqui: si se
     * anade un campo a {@code User}, hay que declararlo con
     * {@link Mapping}, porque los nombres no coinciden
     * ({@code enabled} frente a {@code active}). Ese desajuste es la garantia, no un
     * defecto: obliga a que alguien mire de que trata el campo.
     *
     * <p><strong>Por que los roles se ignoran.</strong> Porque no hay nada que
     * copiar: el agregado trae nombres ({@code Set<Role>}) y la entidad quiere
     * filas del catalogo ({@code Set<RoleEntity>}). Si MapStruct lo intentara,
     * generaria entidades {@code RoleEntity} sueltas con el id a null, y la
     * relacion con {@code roles} dejaria de ser una referencia a un rol ya
     * existente para ser el alta de un rol nuevo: exactamente el alta indebida que
     * el modelo normalizado elimina. Enlazarlos exige consultar la tabla
     * {@code roles}, y un mapper no tiene repositorio; de eso se encarga el
     * adaptador, que si lo tiene.
     *
     * <p><strong>Por que el adaptador no la usa para actualizar.</strong> Su retorno
     * es una entidad nueva. Si el adaptador la guardara durante una modificacion,
     * Hibernate no la reconoceria como la fila que ya tiene gestionada e insertaria
     * una en lugar de actualizar. Para eso esta
     * {@link UserFactory#actualizarSobre}.
     */
    @Mapping(target = "id", source = "id", qualifiedByName = "userIdAUuid")
    @Mapping(target = "email", source = "email", qualifiedByName = "emailAString")
    @Mapping(target = "passwordHash", source = "passwordHash", qualifiedByName = "passwordHashAString")
    @Mapping(target = "enabled", source = "active")
    @Mapping(target = "roles", ignore = true)
    UserEntity toEntity(User user);

    @Named("stringAEmail")
    default Email stringAEmail(String value) {
        return Email.of(value);
    }

    @Named("stringAPasswordHash")
    default PasswordHash stringAPasswordHash(String value) {
        return PasswordHash.of(value);
    }

    @Named("userIdAUuid")
    default UUID userIdAUuid(UserId value) {
        return value.value();
    }

    @Named("emailAString")
    default String emailAString(Email value) {
        return value.value();
    }

    @Named("passwordHashAString")
    default String passwordHashAString(PasswordHash value) {
        return value.value();
    }

    /**
     * De cadena de la entidad a {@link UserId}.
     *
     * <p>No lleva {@code @Named} porque solo se usa una vez, en un unico sitio. Las
     * conversiones que tienen dos sentidos posibles si lo llevan, para que quede
     * escrito cual de los dos se usa.
     */
    default UserId uuidAUserId(UUID value) {
        return UserId.from(value);
    }
}