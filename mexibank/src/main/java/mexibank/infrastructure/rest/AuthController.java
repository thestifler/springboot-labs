package mexibank.infrastructure.rest;

import java.time.Clock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import mexibank.application.auth.AuthResponse;
import mexibank.infrastructure.rest.dto.LoginRequest;
import mexibank.infrastructure.rest.dto.LoginResponse;
import mexibank.infrastructure.rest.error.ApiError;
import mexibank.domain.user.LoginCommand;
import mexibank.domain.user.LoginUseCase;

/**
 * Endpoints de autenticacion.
 *
 * <p><strong>Por que esta ruta es publica.</strong> El login no puede exigir un
 * token: el token es precisamente lo que se va a conseguir. La ruta queda declarada
 * en {@code SecurityConfig} como {@code permitAll}, que es la excepcion explicita a
 * la regla de "todo lo demas exige token".
 *
 * <p><strong>Por que el controlador no contiene logica.</strong> Solo traduce HTTP a
 * un comando del dominio y el resultado a una respuesta HTTP. No valida
 * credenciales, no genera el token, no toca la base de datos. Si este metodo hiciera
 * mas, la logica de autenticacion quedaria atada al protocolo HTTP y no podria
 * reutilizarse desde otro adaptador.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Emision del token de acceso")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final Clock clock;

    /**
     * Autentica a un usuario y devuelve un token de acceso.
     *
     * <p><strong>Por que devuelve 200 y no 201.</strong> No se creo ningun recurso:
     * el usuario ya existia, lo que se establece es una sesion. El codigo correcto
     * para "sesion iniciada" es 200.
     *
     * <p><strong>Por que se pasa el reloj.</strong> El calculo de los segundos
     * restantes necesita el instante actual, y tomarlo aqui (no dentro del caso de
     * uso) permite que en los tests el reloj este congelado y el valor sea exacto.
     *
     * @param request correo y contrasena
     * @return el token y los datos publicos del usuario
     */
    @Operation(
            summary = "Inicia sesion",
            description = "Valida el correo y la contrasena y devuelve un token JWT. "
                    + "Es el unico endpoint publico: el token es lo que aqui se obtiene.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales validas; se devuelve el token"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Peticion invalida: falta un campo o el correo no tiene formato de correo",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Credenciales incorrectas (mismo mensaje si el correo no existe o la contrasena no coincide)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "La cuenta esta desactivada",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "415",
                    description = "El Content-Type no es application/json",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var loginResult = loginUseCase.login(
                new LoginCommand(request.email(), request.password()));

        AuthResponse respuesta = AuthResponse.from(loginResult, clock.instant());
        return ResponseEntity.ok(LoginResponse.from(respuesta));
    }
}