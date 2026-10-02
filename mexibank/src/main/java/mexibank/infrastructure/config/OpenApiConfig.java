package mexibank.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Metadatos del contrato OpenAPI de la API.
 *
 * <p><strong>Por que esta clase es infraestructura y no dominio ni aplicacion.</strong>
 * OpenAPI es un formato de descripcion de HTTP. Ni el agregado ni el caso de uso
 * saben que existe una API REST, y la prueba es que este proyecto podria exponerse
 * por otro protocolo sin tocar ninguna de las dos capas. El contrato, por tanto,
 * pertenece al borde.
 *
 * <p><strong>Por que se declara el esquema de seguridad a mano.</strong> springdoc
 * deduce el contrato de los controladores, pero no puede adivinar que el token viaja
 * en {@code Authorization: Bearer <token>}: eso no se lee del codigo, se decide en
 * {@code SecurityConfig}. Sin declararlo aqui, Swagger UI tendria un boton
 * "Authorize" vacio y el usuario no podria probar ningun endpoint protegido sin
 * pegar la cabecera a mano. La constante {@link #ESQUEMA_BEARER} es el nombre que
 * los controladores referencian con {@code @SecurityRequirement}.
 *
 * <p><strong>Por que la version del contrato no coincide con la version de la
 * aplicacion.</strong> La ruta esta versionada ({@code /api/v1}) y es esa version,
 * no la del {@code pom.xml}, la que el cliente contrata. Un cambio incompatible
 * obliga a subir a {@code /api/v2}; mientras tanto, decir "1.0-SNAPSHOT" no aporta
 * nada al consumidor.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Nombre del esquema de seguridad que los controladores referencian.
     *
     * <p>Es una constante y no un literal repetido porque debe coincidir en dos
     * sitios: el nombre que se registra aqui y el que usan las anotaciones
     * {@code @SecurityRequirement}. Si divergen, el candado de la operacion no
     * encuentra el esquema y Swagger UI la muestra como publica aunque no lo sea.
     */
    public static final String ESQUEMA_BEARER = "bearerAuth";

    /**
     * Contrato OpenAPI que sirve springdoc.
     *
     * @return informacion general de la API y el esquema de autenticacion Bearer
     */
    @Bean
    public OpenAPI mexibankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mexibank API")
                        .version("v1")
                        .description("""
                                API de Mexibank. La mayoria de las rutas exigen un token \
                                obtenido en POST /api/v1/auth/login y enviado como \
                                'Authorization: Bearer <token>'. El endpoint de login es \
                                el unico publico: el token es precisamente lo que alli se \
                                consigue."""))
                .components(new Components().addSecuritySchemes(
                        ESQUEMA_BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT devuelto por POST /api/v1/auth/login")));
    }
}
