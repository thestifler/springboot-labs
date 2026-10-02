package mexibank.integration;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.SET;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;

import mexibank.domain.user.Email;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserRepository;

/**
 * El arranque, de punta a punta: properties -&gt; administrador -&gt; login -&gt; alta.
 *
 * <p><strong>Por que este test existe.</strong> {@code POST /api/v1/users} exige el
 * rol {@code ADMIN}, y sin un administrador no hay forma de crear el primero: la
 * API se cierra sobre si misma. Este test comprueba que el bootstrap rompe ese
 * bucle de verdad, contra la base de datos y por HTTP, y no solo que el runner
 * llama al caso de uso.
 *
 * <p><strong>Por que se comprueba el login y no solo la fila.</strong> Porque el
 * fallo que este bootstrap tiene que evitar no es que el usuario no exista, sino
 * que exista con un hash que el login no reconoce. Un alta hecha por SQL, con un
 * hash generado fuera del sistema, dejaria la fila ahi y este login no pasaria
 * nunca. Que el administrador se autentique es lo que demuestra que su contrasena
 * ha pasado por {@code BCryptPasswordHasher}.
 *
 * <p><strong>Por que el ultimo test crea un usuario y no solo mira el token.</strong>
 * Porque tener un token no es lo mismo que poder usar la ruta que exige
 * {@code ADMIN}. Si el bootstrap creara el usuario sin el rol correcto, los dos
 * tests anteriores seguirian pasando y el bucle seguiria cerrado.
 *
 * <p><strong>Por que H2 y no un contenedor.</strong> Lo que se comprueba es el
 * arranque del bootstrap y la autenticacion contra lo que ha creado. El motor de
 * la base de datos es irrelevante para eso; los tests que si dependen del motor
 * usan Testcontainers y son los {@code *IT}.
 *
 * <p><strong>Por que las properties van aqui y no en un archivo.</strong> Porque el
 * bootstrap solo debe activarse cuando se pide. Un {@code application-test}
 * con las properties puestas las activaria para todos los tests del proyecto.
 */
@SpringBootTest(
        properties = {
                "mexibank.bootstrap.admin.email=arranque@correo.com",
                "mexibank.bootstrap.admin.password=UnaContrasenaLarga"
        })
@ActiveProfiles("test")
class BootstrapAdminIntegrationTest {

    private static final String CORREO = "arranque@correo.com";

    private static final String CONTRASENA = "UnaContrasenaLarga";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        // springSecurity() es lo que instala la cadena de filtros. Sin el, el
        // 403 del ultimo test sairia como 201 y el test pasaria probando lo
        // contrario de lo que dice.
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("el administrador existe y se creo con el rol ADMIN")
    void creaElAdministrador() {
        assertThat(userRepository.findByEmail(Email.of(CORREO)))
                .isPresent()
                .get()
                .extracting(User::getRoles, as(SET))
                .contains(Role.ADMIN);
    }

    @Test
    @DisplayName("el administrador puede iniciar sesion")
    void elAdministradorPuedeHacerLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", CORREO, "password", CONTRASENA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(CORREO))
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"));
    }

    @Test
    @DisplayName("con ese administrador se puede dar de alta un usuario nuevo")
    void elAdministradorRompeElBucle() throws Exception {
        // La comprobacion que justifica todo el mecanismo: el administrador creado
        // por bootstrap puede usar la ruta que exige ADMIN. Sin el,
        // POST /api/v1/users seria inalcanzable para siempre.
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token())
                        .content(json(Map.of(
                                "email", "creado.con.el.admin@correo.com",
                                "password", "OtraContrasenaLarga",
                                "roles", new String[]{"TELLER"}))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.roles[0]").value("TELLER"));
    }

    /**
     * Consigue un access token del administrador creado por el bootstrap.
     *
     * @return el token listo para enviar como {@code Bearer <token>}
     */
    private String token() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", CORREO, "password", CONTRASENA))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("accessToken")
                .asString();
    }

    private String json(Map<String, ?> datos) throws Exception {
        return objectMapper.writeValueAsString(datos);
    }
}