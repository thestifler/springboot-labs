package mexibank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import mexibank.domain.user.CreateUserCommand;
import mexibank.domain.user.CreateUserUseCase;
import mexibank.domain.user.Role;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Flujo de seguridad completo contra la base de datos en memoria.
 *
 * <p><strong>Por que H2 y no un contenedor.</strong> Lo que se comprueba aqui es
 * el cableado de Spring Security, el filtro, los codigos de estado y el formato
 * de las respuestas. El SQL entra en juego solo para leer y escribir una fila de
 * usuario, y H2 lo hace igual. Un contenedor de PostgreSQL costaria Docker en cada
 * build para no anadir cobertura a lo que ya se mide; lo que si depende del motor
 * esta en los tests {@code *IT}.
 *
 * <p><strong>Por que se construye {@code MockMvc} a mano y no con
 * {@code @AutoConfigureMockMvc}.</strong> Porque hace falta aplicar
 * {@code springSecurity()}, que es lo que instala la cadena de filtros en el
 * {@code MockMvc}. Con la anotacion, la instalacion es automatica pero implicita;
 * aqui queda a la vista, y el fallo mas probable de este test (creer que hay
 * seguridad y no la hay) se ve en la linea que la activa.
 *
 * <p><strong>Por que se siembran usuarios llamando al caso de uso.</strong> El
 * endpoint de alta exige el rol ADMIN, asi que no puede ser la via para crear al
 * primer ADMIN. Llamar al caso de uso es lo que hace un administrador desde la
 * consola, y ademas comprueba que el caso de uso funciona con los mismos datos que
 * usara un cliente.
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityFlowTest {

    private static final String CONTRASENA = "UnaContrasenaLarga";

    private static final String CORREO_ADMIN = "admin@correo.com";

    private static final String CORREO_CLIENTE = "cliente@correo.com";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        // springSecurity() es imprescindible: sin el, MockMvc no instala la
        // cadena de filtros y todas las peticiones llegan al controlador sin
        // pasar por autorizacion. Los 401 y 403 de estos tests saldrian entonces
        // como 201, y el test pasaria probando lo contrario de lo que dice.
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .build();
        crearSiNoExiste(CORREO_ADMIN, Role.ADMIN);
        crearSiNoExiste(CORREO_CLIENTE, Role.CUSTOMER);
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("devuelve el token, el tipo y los segundos de vigencia")
        void loginCorrecto() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", CORREO_CLIENTE,
                                    "password", CONTRASENA))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").isNumber())
                    .andExpect(jsonPath("$.user.email").value(CORREO_CLIENTE))
                    .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                    // El usuario sale con la misma forma que en el alta de
                    // usuario. Sin esta comprobacion, el id volveria a salir como
                    // un objeto y el cliente necesitaria dos deserializadores
                    // para el mismo dato.
                    .andExpect(jsonPath("$.user.id").isString())
                    .andExpect(jsonPath("$.user.active").isBoolean())
                    .andExpect(jsonPath("$.user.createdAt").isNotEmpty());
        }

        @Test
        @DisplayName("nunca devuelve el hash de la contrasena")
        void elLoginNoDevuelveElHash() throws Exception {
            // Es el fallo mas grave posible en este endpoint y el unico que no se
            // manifiesta como excepcion: la API funciona y entrega un secreto con
            // el que se puede montar un ataque de diccionario sin conexion.
            String cuerpo = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", CORREO_CLIENTE,
                                    "password", CONTRASENA))))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(cuerpo)
                    .doesNotContain("bcrypt")
                    .doesNotContain("passwordHash")
                    .doesNotContain(CONTRASENA);
        }

        @Test
        @DisplayName("no exige token: es la unica ruta que se puede llamar sin credenciales")
        void elLoginEsPublico() throws Exception {
            // Si el login exigiera token, no habria forma de obtenerlo. Que la
            // ruta este en permitAll es la excepcion explicita a la regla de
            // "todo lo demas exige token", y este test es lo que lo comprueba.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "nadie@correo.com", "password", CONTRASENA))))
                    .andExpect(status().isUnauthorized());
        }

        @ParameterizedTest
        @MethodSource("credencialesIncorrectas")
        @DisplayName("responde 401 con el mismo mensaje, haya quien haya fallado")
        void credencialesIncorrectas(String email, String password) throws Exception {
            // Dos fallos distintos con una sola respuesta. Un cliente que
            // distinguiera "ese correo no existe" de "la contrasena no es la
            // buena" podria recorrer una lista de correos y descubrir quien
            // tiene cuenta en el banco.
            //
            // Un correo con formato invalido NO aparece aqui a proposito: el
            // validador del borde lo rechaza con un 400 antes de que la peticion
            // llegue al caso de uso, y el test siguiente comprueba ese camino. En
            // el caso de uso si se unifican las dos respuestas, pero aqui esa
            // proteccion no llega.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", password))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message")
                            .value("Las credenciales no son validas"));
        }

        static Stream<Arguments> credencialesIncorrectas() {
            return Stream.of(
                    Arguments.of("desconocido@correo.com", CONTRASENA),
                    Arguments.of(CORREO_CLIENTE, "ContrasenaEquivocada"));
        }

        @Test
        @DisplayName("valida el formato del correo antes de tocar la base de datos")
        void validaElCorreoEnElBorde() throws Exception {
            // Un correo mal escrito por el usuario merece un 400 legible, no un
            // 401 de credenciales. La diferencia con el caso anterior es
            // deliberada: aqui el cliente ya sabe que correo ha enviado, asi que
            // no hay nada que proteger.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "esto-no-es-un-correo", "password", "x"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        }

        @Test
        @DisplayName("un JSON mal formado produce 400, no 500")
        void cuerpoMalFormado() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{esto-no-es-json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("un Content-Type que no es JSON produce 415, no 500")
        void contentTypeNoSoportado() throws Exception {
            // El motivo del test: sin un manejador para
            // HttpMediaTypeNotSupportedException, Spring deja la excepcion fuera
            // del advice y cae en el manejador generico, que responde 500. Un 500
            // dice "vuelve a intentarlo" cuando reintentar es imposible, porque el
            // problema esta en una cabecera que el cliente controla.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content("email=ana@correo.com&password=Una"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(415))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Acceso sin credenciales")
    class SinCredenciales {

        @Test
        @DisplayName("responde 401, con WWW-Authenticate para que el cliente sepa que esquema usar")
        void sinTokenDa401() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "nuevo@correo.com",
                                    "password", CONTRASENA))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("un token invalido produce el mismo 401 que no enviar ninguno")
        void tokenInvalidoDa401() throws Exception {
            // Si un token manipulado y la ausencia de token dieran respuestas
            // distintas, un atacante probaria tokens y aprenderia de las
            // diferencias. Con un solo caso, el 401 no dice nada sobre por que se
            // rechazo.
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer esto-no-es-un-jwt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "nuevo@correo.com",
                                    "password", CONTRASENA))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("un token con la firma alterada tambien produce 401")
        void tokenManipuladoDa401() throws Exception {
            String token = loginDe(CORREO_ADMIN);
            char ultimo = token.charAt(token.length() - 1);
            String manipulado = token.substring(0, token.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + manipulado)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "nuevo@correo.com",
                                    "password", CONTRASENA))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el esquema debe ser Bearer: en minusculas no vale")
        void elEsquemaEsSensibleAMayusculas() throws Exception {
            // Aceptar "bearer " seria ampliar lo que se acepta sin aportar nada:
            // el esquema lo define la especificacion HTTP con esa capitalizacion.
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "bearer " + loginDe(CORREO_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "nuevo@correo.com",
                                    "password", CONTRASENA))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Autorizacion por rol")
    class AutorizacionPorRol {

        @Test
        @DisplayName("un ADMIN puede dar de alta usuarios")
        void adminPuedeCrearUsuarios() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(CORREO_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "nuevo@correo.com",
                                    "password", CONTRASENA,
                                    "roles", Set.of("TELLER")))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/users/")))
                    .andExpect(jsonPath("$.email").value("nuevo@correo.com"))
                    .andExpect(jsonPath("$.roles[0]").value("TELLER"));
        }

        @Test
        @DisplayName("un CUSTOMER recibe 403, no 401: se sabe quien es, no le dejan")
        void customerNoPuedeCrearUsuarios() throws Exception {
            // La diferencia entre 401 y 403 es la que mas confunde a quien
            // consume una API. Aqui el token es valido, de modo que un 401
            // obligaria al cliente a reintentar un login que ya funciono.
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(CORREO_CLIENTE))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "otro@correo.com",
                                    "password", CONTRASENA))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("la respuesta 403 no dice que roles habrian permitido la operacion")
        void el403NoDiceQueRolesFaltan() throws Exception {
            // Saber que roles hacen falta solo ayuda a hacer pruebas sistematicas
            // a alguien que ya esta autenticado. La respuesta no lo dice; el log
            // del servidor si.
            String cuerpo = mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(CORREO_CLIENTE))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "otro@correo.com",
                                    "password", CONTRASENA))))
                    .andExpect(status().isForbidden())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(cuerpo).doesNotContain("ROLE_ADMIN").doesNotContain("ADMIN");
        }
    }

    @Nested
    @DisplayName("Alta de usuarios")
    class AltaDeUsuarios {

        @Test
        @DisplayName("un correo repetido produce 409")
        void correoRepetidoDa409() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(CORREO_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", CORREO_CLIENTE,
                                    "password", CONTRASENA))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"));
        }

        @Test
        @DisplayName("un rol desconocido produce 400 con la lista de roles validos")
        void rolDesconocidoDa400() throws Exception {
            // Y no un 500 por fallo de deserializacion: es un error de la peticion,
            // y el cliente puede corregirlo si sabe cuales son las opciones.
            String cuerpo = mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(CORREO_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "otro@correo.com",
                                    "password", CONTRASENA,
                                    "roles", Set.of("SUPERADMIN")))))
                    .andExpect(status().isBadRequest())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(cuerpo).contains("CUSTOMER").contains("TELLER").contains("ADMIN");
        }

        @Test
        @DisplayName("una contrasena corta se rechaza en el borde, antes de hashear")
        void contrasenaCortaDa400() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(CORREO_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "otro@correo.com",
                                    "password", "corta"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
        }

        @Test
        @DisplayName("la respuesta del alta nunca incluye el hash")
        void elAltaNoDevuelveElHash() throws Exception {
            String cuerpo = mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + loginDe(CORREO_ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "limpio@correo.com",
                                    "password", CONTRASENA))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(cuerpo)
                    .doesNotContain("bcrypt")
                    .doesNotContain("passwordHash")
                    .doesNotContain(CONTRASENA);
        }
    }

    /**
     * Hace login y devuelve el token.
     *
     * <p><strong>Por que cada test pide su propio token.</strong> Un token
     * dura 15 minutos y estos tests tardan milisegundos, asi que podrian
     * reutilizar uno. Pedirlo en cada test deja escrito que cada uno ejercita el
     * camino completo desde el login, y no un estado compartido que un test
     * anterior dejo preparado.
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

        // Se relee con el mismo ObjectMapper de la aplicacion y no a mano: si el
        // nombre del campo cambiara, este test fallaria al no encontrarlo, que es
        // justo lo que debe pasar si se cambia el contrato del login.
        return objectMapper.readTree(cuerpo).get("accessToken").asText();
    }

    private String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    /**
     * Crea un usuario si todavia no existe.
     *
     * <p><strong>Por que se siembra en cada test y no una sola vez.</strong> La
     * base en memoria vive mientras viva el contexto de Spring, que se reutiliza
     * entre clases. Sembrar una sola vez exigiria un {@code @BeforeAll} con
     * inyeccion, que no existe de forma limpia, y sembrar sin comprobar nada
     * haria fallar el segundo test con un 409 que no dice nada del codigo que se
     * quiere probar. La excepcion se ignora a proposito: lo unico que importa
     * para el resto del test es que al final haya un usuario con ese correo.
     *
     * @param correo correo del usuario
     * @param rol    rol que se le asigna
     */
    private void crearSiNoExiste(String correo, Role rol) {
        try {
            createUserUseCase.createUser(
                    new CreateUserCommand(correo, CONTRASENA, Set.of(rol)));
        } catch (RuntimeException yaExiste) {
            log.debug("El usuario {} ya estaba sembrado: {}", correo, yaExiste.getMessage());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SecurityFlowTest.class);
}
