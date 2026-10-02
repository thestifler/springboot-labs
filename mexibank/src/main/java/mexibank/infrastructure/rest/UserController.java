package mexibank.infrastructure.rest;

import java.net.URI;
import java.util.EnumSet;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import mexibank.application.user.UserView;
import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.CreateUserUseCase;
import mexibank.domain.user.Role;
import mexibank.infrastructure.config.OpenApiConfig;
import mexibank.infrastructure.rest.dto.CreateUserRequest;
import mexibank.infrastructure.rest.dto.UserResponse;
import mexibank.infrastructure.rest.error.ApiError;

/**
 * Endpoints de gestion de usuarios.
 *
 * <p><strong>Por que la autorizacion no va aqui.</strong> Ni en el controlador ni
 * en el caso de uso. Que esta ruta exija el rol ADMIN se declara en
 * {@code SecurityConfig}, junto con las demas rutas, para que exista un unico
 * sitio donde se lee que es publico y que no. Repartirlo por anotaciones en cada
 * metodo obliga a revisar N ficheros para responder a la pregunta mas basica de
 * una API: que puedo llamar. Y el caso de uso no puede declararlo porque no sabe
 * que existe el concepto de peticion HTTP.
 *
 * <p>Lo que si vive en el dominio son las <em>reglas</em> del alta (politica de
 * contrasena, correo unico), y se comprueban igual aunque el alta venga por otro
 * camino.
 *
 * <p><strong>Por que se traduce {@code Set<String>} a {@code Set<Role>} aqui.</strong>
 * Porque el enum no existe fuera del proceso. Si el rol solicitado no existe, la
 * respuesta debe decir "estos son los roles validos" y no dejar que Jackson falle
 * con un error de deserializacion que nadie entiende.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestion de cuentas")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class UserController {

    private final CreateUserUseCase createUserUseCase;

    /**
     * Da de alta un usuario.
     *
     * <p><strong>Por que devuelve 201 y con {@code Location}.</strong> Se ha creado
     * un recurso con identificador propio, y la convencion HTTP es responder 201 con
     * la URI donde se puede consultar o modificar. El cliente no tiene que construir
     * esa URL por su cuenta.
     *
     * @param request datos del nuevo usuario
     * @return la URI del recurso creado y su representacion
     */
    @Operation(
            summary = "Da de alta un usuario",
            description = "Crea una cuenta con los roles indicados. Requiere el rol ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado; la cabecera Location apunta al recurso"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Peticion invalida: falta un campo, la contrasena no cumple la politica o el rol no existe",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Falta el token o no es valido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "El token no tiene el rol ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "El correo ya esta registrado",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "415",
                    description = "El Content-Type no es application/json",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        var user = createUserUseCase.createUser(new CreateUserCommand(
                request.email(),
                request.password(),
                traducirRoles(request.roles())));

        UserView view = UserView.from(user);
        UserResponse body = UserResponse.from(view);

        URI location = URI.create("/api/v1/users/" + body.id());
        return ResponseEntity.created(location).body(body);
    }

    /**
     * Traduce los roles del mundo exterior al enum del dominio.
     *
     * <p><strong>Por que falla aqui y no en el caso de uso.</strong> Porque el
     * caso de uso solo conoce {@code Set<Role>}: si le llegaran cadenas tendria que
     * saber tambien como se llaman los roles en JSON. Al hacerlo aqui, el nombre
     * del enum vive en un solo sitio y el dominio queda con su vocabulario.
     *
     * <p>Un rol desconocido produce un 400 con la lista de roles validos. Se
     * responde asi y no con un 500: es un error de la peticion, no del servidor, y
     * el cliente puede corregirlo si sabe cuales son las opciones.
     *
     * @param roles nombres de rol tal como llegan en el JSON
     * @return el conjunto de roles del dominio; vacio significa "usa el rol por
     *         defecto", que es lo que decide {@code CreateUserCommand}
     * @throws IllegalArgumentException si algun rol no existe
     */
    private Set<Role> traducirRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        EnumSet<Role> traducidos = EnumSet.noneOf(Role.class);
        for (String nombre : roles) {
            try {
                traducidos.add(Role.valueOf(nombre.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Rol desconocido: '" + nombre + "'. Roles validos: " + rolesValidos());
            }
        }
        return traducidos;
    }

    /**
     * Nombres de los roles que el sistema acepta.
     */
    private Set<String> rolesValidos() {
        return EnumSet.allOf(Role.class).stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}