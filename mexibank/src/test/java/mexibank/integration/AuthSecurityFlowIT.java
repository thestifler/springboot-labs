package mexibank.integration;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.CreateUserUseCase;
import mexibank.domain.user.Email;
import mexibank.domain.user.Role;
import mexibank.domain.user.User;
import mexibank.domain.user.UserRepository;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;

/**
 * Flujo de seguridad completo contra PostgreSQL de verdad.
 *
 * <p><strong>Que anade este test sobre {@code SecurityFlowTest}.</strong> Aquel
 * corre contra H2 y comprueba el cableado: quien puede entrar, que codigo recibe
 * quien no, y que las respuestas tienen la forma esperada. Este corre contra el
 * motor de produccion y comprueba lo que H2 no puede: que un cambio de estado en
 * la base de datos (desactivar a alguien, cambiarle los roles) surte efecto en la
 * peticion siguiente. Es la propiedad que justifica que el filtro relea el usuario
 * en cada peticion, y sin PostgreSQL no se puede medir de verdad.
 *
 * <p><strong>Por que esta clase no es transaccional.</strong> El resto de tests de
 * integracion si lo son y revierten al terminar. Aqui no puede serlo: los cambios
 * tienen que ser visibles para las peticiones HTTP, que corren en su propia
 * transaccion. Un cambio sin cerrar no lo veria el filtro, y el test mediria el
 * aislamiento del test en lugar del codigo. La base la destruye el contenedor al
 * terminar la clase, asi que no hace falta limpiar.
 *
 * <p><strong>Por que cada test usa un correo distinto.</strong> Sin transaccion que
 * revierta, los datos se acumulan dentro de la clase. Un correo fijo chocaria con
 * la clave unica en el segundo test, y el fallo seria del test y no del codigo.
 */
class AuthSecurityFlowIT extends AbstractPostgresIT {

    private static final String CONTRASENA = "UnaContrasenaLarga";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Login contra la base de datos")
    class Login {

        @Test
        @DisplayName("devuelve un token con el que se puede llamar a un endpoint protegido")
        void elTokenSirveParaLlamar() throws Exception {
            String correo = correoUnico();
            crearUsuario(correo, Role.ADMIN);

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(correo))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", correoUnico(),
                                    "password", CONTRASENA))))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("la misma contrasena guardada en PostgreSQL verifica sin rehashear")
        void laContrasenaGuardadaVerifica() throws Exception {
            // El hash lo produce BCrypt y se guarda en una columna VARCHAR(255).
            // Este test comprueba que el valor sobrevive el viaje de ida y vuelta
            // por PostgreSQL y que el verificador lo acepta: si el motor recortara
            // o transformara la cadena, el login dejaria de funcionar y el fallo
            // estaria en la base, no en el codigo.
            String correo = correoUnico();
            crearUsuario(correo, Role.CUSTOMER);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", correo, "password", CONTRASENA))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Revocacion inmediata")
    class Revocacion {

        @Test
        @DisplayName("desactivar a un usuario invalida su token en la peticion siguiente")
        void desactivarInvalidaElToken() throws Exception {
            // Es la razon de existir del filtro propio. Si se confiara en los roles
            // del token, este usuario conservaria el acceso hasta que caducara, y
            // una cuenta bloqueada por fraude seguiria operando.
            String correo = correoUnico();
            crearUsuario(correo, Role.ADMIN);
            String token = loginDe(correo);

            desactivar(correo);

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", correoUnico(),
                                    "password", CONTRASENA))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("cambiar los roles invalida los permisos en la peticion siguiente")
        void cambiarLosRolesCambiaLosPermisos() throws Exception {
            // El token se emitio cuando el usuario era ADMIN. Al quitarle el rol, la
            // peticion siguiente tiene que fallar: los roles se leen del agregado,
            // no del token. Sin esto, retirar un permiso no serviria de nada hasta
            // que caducara el token.
            String correo = correoUnico();
            crearUsuario(correo, Role.ADMIN);
            String token = loginDe(correo);

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", correoUnico(),
                                    "password", CONTRASENA))))
                    .andExpect(status().isCreated());

            reemplazarRoles(correo, Set.of(Role.CUSTOMER));

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", correoUnico(),
                                    "password", CONTRASENA))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("volver a activar a un usuario devuelve la validez a sus tokens")
        void reactivarDevuelveElAcceso() throws Exception {
            // La contraprueba de la revocacion: si el filtro rechazara siempre a
            // los inactivos por haber sido desactivados alguna vez, una cuenta
            // reactivada quedaria inservible.
            String correo = correoUnico();
            crearUsuario(correo, Role.ADMIN);
            String token = loginDe(correo);

            desactivar(correo);
            activar(correo);

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", correoUnico(),
                                    "password", CONTRASENA))))
                    .andExpect(status().isCreated());
        }
    }

    /**
     * Desactiva a un usuario en su propia transaccion.
     *
     * <p><strong>Por que se envuelve en una transaccion.</strong> El adaptador de
     * persistencia no declara la suya: la unidad de trabajo la abre el caso de uso.
     * Fuera de una transaccion, el {@code findById} del adaptador cerraria la suya
     * y la entidad quedaria desconectada, con lo que la modificacion no llegaria a
     * la base de datos. Reproducir aqui esa transaccion es lo que hace un caso de
     * uso de desactivacion, y es la unica forma de que el cambio sea visible para
     * la peticion HTTP siguiente.
     *
     * @param correo correo del usuario
     */
    private void desactivar(String correo) {
        cambiarEstado(correo, false);
    }

    /**
     * Reactiva a un usuario en su propia transaccion.
     *
     * @param correo correo del usuario
     */
    private void activar(String correo) {
        cambiarEstado(correo, true);
    }

    private void cambiarEstado(String correo, boolean activo) {
        transactionTemplate.executeWithoutResult(estado -> {
            User actual = userRepository.findByEmail(Email.of(correo)).orElseThrow();
            userRepository.save(User.reconstitute(
                    actual.getId(),
                    actual.getEmail(),
                    actual.getPasswordHash(),
                    actual.getRoles(),
                    activo,
                    actual.getCreatedAt(),
                    actual.getUpdatedAt().plusSeconds(1)));
        });
    }

    /**
     * Reemplaza los roles de un usuario en su propia transaccion.
     *
     * @param correo correo del usuario
     * @param roles  roles nuevos
     */
    private void reemplazarRoles(String correo, Set<Role> roles) {
        transactionTemplate.executeWithoutResult(estado -> {
            User actual = userRepository.findByEmail(Email.of(correo)).orElseThrow();
            User nuevo = User.reconstitute(
                    actual.getId(),
                    actual.getEmail(),
                    actual.getPasswordHash(),
                    actual.getRoles(),
                    actual.isActive(),
                    actual.getCreatedAt(),
                    actual.getUpdatedAt());
            nuevo.replaceRoles(roles, actual.getUpdatedAt().plusSeconds(1));
            userRepository.save(nuevo);
        });
    }

    private void crearUsuario(String correo, Role rol) {
        createUserUseCase.createUser(new CreateUserCommand(correo, CONTRASENA, Set.of(rol)));
    }

    /**
     * Hace login y devuelve el token.
     *
     * @param correo correo del usuario
     * @return el token de acceso
     */
    private String loginDe(String correo) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", correo, "password", CONTRASENA))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(cuerpo).get("accessToken").asText();
    }

    /**
     * Genera un correo que no puede colisionar con el de otro test.
     *
     * @return correo unico dentro de la clase
     */
    private String correoUnico() {
        return "usuario-" + UUID.randomUUID() + "@correo.com";
    }

    private String json(Object valor) {
        return objectMapper.writeValueAsString(valor);
    }
}
