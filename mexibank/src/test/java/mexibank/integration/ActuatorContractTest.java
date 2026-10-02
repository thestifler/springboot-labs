package mexibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.CreateUserUseCase;
import mexibank.domain.user.Role;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lo que las sondas de actuator cuentan y a quien se lo cuentan.
 *
 * <p><strong>Por que estos contratos necesitan un test y no solo el readme.</strong>
 * El nivel de acceso de cada sonda no sale de una sola fuente: el starter decide
 * cuales existen y {@code SecurityConfig} decide cuales son publicas. Cambiar uno
 * de los dos sin tocar el otro deja una sonda que responde `404`, o peor, que
 * responde `200` con datos internos de la JVM. El readme lo cuenta; este test
 * hace que si deja de ser cierto, falle el build.
 *
 * <p><strong>Por que se comprueban las dos variantes de {@code health}.</strong>
 * Con {@code show-details=when-authorized} el mismo endpoint devuelve dos cuerpos
 * distintos segun haya token. El caso sin token es el que importa de verdad: es
 * el que ve el balanceador, y por eso no puede filtrar componentes internos. El
 * caso con token existe para comprobar que el filtro se ha invertido con
 * normalidad y no se ha roto.
 */
@SpringBootTest
@ActiveProfiles("test")
class ActuatorContractTest {

    private static final Logger log = LoggerFactory.getLogger(ActuatorContractTest.class);

    private static final String CONTRASENA = "UnaContrasenaLarga";

    private static final String CORREO = "sonda@correo.com";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        // springSecurity() es lo que instala la cadena de filtros. Sin el, las
        // sondas que deben pedir token responderian 200 y el test probaria lo
        // contrario de lo que dice.
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .build();

        try {
            createUserUseCase.createUser(
                    new CreateUserCommand(CORREO, CONTRASENA, Set.of(Role.CUSTOMER)));
        } catch (RuntimeException yaExiste) {
            log.debug("El usuario {} ya estaba sembrado: {}", CORREO, yaExiste.getMessage());
        }
    }

    @Nested
    @DisplayName("Sondas publicas")
    class Publicas {

        @Test
        @DisplayName("health responde 200 sin token y solo con el veredicto")
        void healthSinToken() throws Exception {
            // El balanceador no tiene token. Si al caer sin el apareciera un
            // "components" con el estado de la base de datos y del disco, la
            // sonda publica del banco estaria dando informacion de su
            // infraestructura a quien preguntara.
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }

        @Test
        @DisplayName("info responde 200 y sin token devuelve el objeto vacio")
        void infoSinToken() throws Exception {
            // Se afirma el vacio a proposito. En cuanto se anada build-info, este
            // test falla y obliga a decidir si lo que se publica dentro es
            // publicable.
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(objectMapper.readTree(
                            result.getResponse().getContentAsString()).isEmpty()).isTrue());
        }
    }

    @Nested
    @DisplayName("Sondas autenticadas")
    class Autenticadas {

        @Test
        @DisplayName("metrics responde 401 sin token")
        void metricsSinToken() throws Exception {
            mockMvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("metrics responde 200 con token y lista nombres de metricas")
        void metricsConToken() throws Exception {
            JsonNode cuerpo = objectMapper.readTree(
                    mockMvc.perform(get("/actuator/metrics")
                                    .header("Authorization", "Bearer " + token()))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString());

            // No se comprueba el valor de las metricas: cambian en cada ejecucion
            // y un test que las fije seria un test que falla sin que nada este
            // roto. Lo que importa es que la forma sea la documentada.
            assertThat(cuerpo.propertyNames()).containsExactly("names");
            assertThat(cuerpo.get("names").isArray()).isTrue();
            assertThat(cuerpo.get("names")).isNotEmpty();
        }

        @Test
        @DisplayName("health con token si anade los componentes")
        void healthConToken() throws Exception {
            // El caso inverso al publico. Comprueba que when-authorized filtra en
            // el sentido correcto: con token se ve mas, no menos.
            mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer " + token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components.db").exists());
        }

        @Test
        @DisplayName("la raiz /actuator pide token: no publica la lista de sondas")
        void laRaizExigeToken() throws Exception {
            // Descubrir que sondas existen solo es util para el que ya tiene
            // acceso. Por eso la raiz no se abre, aunque cada sonda decida por su
            // cuenta.
            mockMvc.perform(get("/actuator"))
                    .andExpect(status().isUnauthorized());
        }
    }

    /**
     * Consigue un token del usuario sembrado.
     *
     * @return access token JWT
     */
    private String token() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", CORREO,
                                "password", CONTRASENA))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("accessToken")
                .asString();
    }
}