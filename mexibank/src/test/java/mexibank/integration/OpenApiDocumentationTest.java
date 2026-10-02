package mexibank.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Contrato OpenAPI que publica springdoc.
 *
 * <p><strong>Por que se prueba la documentacion.</strong> Una API sin contrato
 * obliga a cada cliente a leer el codigo para saber que enviar y que esperar. La
 * documentacion generada no es un adorno: es la interfaz publica. Si una anotacion
 * desaparece o el esquema de un DTO deja de generarse, el fallo debe salir aqui y no
 * en el cliente que se encuentra un contrato incompleto.
 *
 * <p><strong>Por que contra la aplicacion entera y no contra un fragmento.</strong>
 * Lo que se comprueba es que springdoc, Spring MVC y Spring Security conviven: que
 * la ruta del contrato es alcanzable sin token y que el esquema Bearer declarado en
 * {@code OpenApiConfig} llega hasta la operacion protegida. Un test de una sola
 * clase no veria el cableado.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenApiDocumentationTest {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        // Mismo motivo que en SecurityFlowTest: sin springSecurity() la cadena de
        // filtros no se instala y el test no probaria que el contrato es publico.
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Contrato OpenAPI")
    class Contrato {

        @Test
        @DisplayName("es publico, porque describe como conseguir el token")
        void esPublico() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("Mexibank API"))
                    .andExpect(jsonPath("$.openapi").isNotEmpty());
        }

        @Test
        @DisplayName("declara las rutas de login y de alta de usuario")
        void declaraLasRutas() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/users'].post").exists());
        }

        @Test
        @DisplayName("tipa la peticion y la respuesta con los DTO del borde")
        void tipaPeticionYRespuesta() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(
                            "$.paths['/api/v1/auth/login'].post.requestBody"
                                    + ".content['application/json'].schema.$ref")
                            .value(containsString("LoginRequest")))
                    .andExpect(jsonPath(
                            "$.paths['/api/v1/users'].post.requestBody"
                                    + ".content['application/json'].schema.$ref")
                            .value(containsString("CreateUserRequest")))
                    .andExpect(jsonPath("$.components.schemas.LoginResponse").exists())
                    .andExpect(jsonPath("$.components.schemas.UserResponse").exists())
                    .andExpect(jsonPath("$.components.schemas.ApiError").exists());
        }

        @Test
        @DisplayName("documenta los campos con los ejemplos de los DTO")
        void documentaCamposConEjemplos() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.email.example")
                            .value("ana@correo.com"))
                    .andExpect(jsonPath("$.components.schemas.UserResponse.properties.id.example")
                            .value("6f0a1c2e-7b3d-4a91-9f52-1c0e8a4d2b77"));
        }

        @Test
        @DisplayName("la ruta protegida declara el esquema bearer")
        void laRutaProtegidaDeclaraBearer() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andExpect(jsonPath("$.paths['/api/v1/users'].post.security[0].bearerAuth").isArray());
        }
    }

    @Nested
    @DisplayName("Swagger UI")
    class SwaggerUi {

        @Test
        @DisplayName("la pagina de la UI es publica")
        void laUiEsPublica() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }
    }
}
