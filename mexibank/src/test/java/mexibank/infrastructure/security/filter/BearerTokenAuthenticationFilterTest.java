package mexibank.infrastructure.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import mexibank.domain.user.Email;
import mexibank.domain.user.PasswordHash;
import mexibank.domain.user.Role;
import mexibank.domain.user.TokenIssuer;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;
import mexibank.domain.user.UserRepository;
import mexibank.infrastructure.security.support.RoleAuthorityMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tests del filtro de autenticacion, por separado de la cadena de seguridad.
 *
 * <p><strong>Por que se llama al filtro directamente y no se levanta Spring
 * MVC.</strong> Lo que hay que comprobar aqui es una sola cosa: que el filtro
 * decide correctamente a quien autentica y a quien deja pasar. Montarlo con la
 * aplicacion entera mezclaria el comportamiento del filtro con el de los matchers
 * de autorizacion, y un fallo no diria en que capa esta. {@code doFilter} de
 * {@code OncePerRequestFilter} es la plantilla que ejecuta las mismas
 * comprobaciones que en produccion, incluido {@code shouldNotFilter}, de modo que
 * llamarlo a mano no deja nada sin cubrir.
 *
 * <p><strong>Por que los collaborators estan simulados.</strong> La firma del token
 * ya la comprueba {@code JwtTokenIssuerTest} contra la libreria de verdad. Aqui
 * interesa que el filtro consulte al usuario y respete lo que le digan, y eso se
 * expresa mejor con un doble que con una base de datos.
 */
@ExtendWith(MockitoExtension.class)
class BearerTokenAuthenticationFilterTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.cuerpo.firma";

    private static final Instant AHORA = Instant.parse("2026-03-01T10:15:30Z");

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private UserRepository userRepository;

    private BearerTokenAuthenticationFilter filtro;

    @BeforeEach
    void preparar() {
        filtro = new BearerTokenAuthenticationFilter(
                tokenIssuer,
                userRepository,
                new RoleAuthorityMapper());
    }

    @Nested
    @DisplayName("Token valido")
    class TokenValido {

        @Test
        @DisplayName("autentica al usuario con sus roles traducidos a autoridades")
        void autenticaConSusRoles() throws Exception {
            User user = usuario(Role.ADMIN, Role.CUSTOMER);
            prepararTokenValidoDe(user);

            Authentication autenticacion = filtrarCon("Bearer " + TOKEN);

            // El orden viene del comparador de RoleAuthorityMapper, no del del Set.
            // Afirmar sobre el orden exacto detectaria un cambio en ese comparador.
            assertThat(nombresDe(autenticacion)).containsExactly("ROLE_ADMIN", "ROLE_CUSTOMER");
        }

        @Test
        @DisplayName("los roles salen del agregado, no del token")
        void losRolesVienenDelAgregado() throws Exception {
            // El token se firmo cuando el usuario era CUSTOMER, pero en la base de
            // datos ya es ADMIN. Gana la base de datos: por eso cambiar un rol
            // surte efecto en la peticion siguiente y no cuando caduquen los
            // tokens.
            User enBaseDeDatos = usuario(Role.ADMIN);
            when(tokenIssuer.verify(TOKEN)).thenReturn(Optional.of(enBaseDeDatos.getId()));
            when(userRepository.findById(enBaseDeDatos.getId()))
                    .thenReturn(Optional.of(enBaseDeDatos));

            Authentication autenticacion = filtrarCon("Bearer " + TOKEN);

            assertThat(nombresDe(autenticacion)).containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("el principal lleva el identificador del usuario, para que se pueda auditar")
        void elPrincipalLlevaElIdentificador() throws Exception {
            User user = usuario(Role.CUSTOMER);
            prepararTokenValidoDe(user);

            Authentication autenticacion = filtrarCon("Bearer " + TOKEN);

            assertThat(autenticacion.getName()).isEqualTo(user.getId().value().toString());
        }

        @Test
        @DisplayName("el principal no es el agregado, para que el hash quede fuera del contexto")
        void elPrincipalNoEsElAgregado() throws Exception {
            User user = usuario(Role.CUSTOMER);
            prepararTokenValidoDe(user);

            Authentication autenticacion = filtrarCon("Bearer " + TOKEN);

            // El principal es lo primero que se escribe en un log de acceso
            // denegado. Si fuera el agregado, el hash de la contrasena acabaria en
            // los logs del servidor sin que nadie lo pidiera.
            assertThat(autenticacion.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
            assertThat(autenticacion.getPrincipal()).isNotInstanceOf(User.class);
            assertThat(autenticacion.getPrincipal().toString())
                    .doesNotContain("bcrypt")
                    .doesNotContain("2a$");
            assertThat(autenticacion.getCredentials()).isNull();
        }
    }

    @Nested
    @DisplayName("Token ausente o invalido")
    class TokenAusenteOInvalido {

        @Test
        @DisplayName("sin cabecera Authorization, la peticion sigue sin autenticar")
        void sinCabecera() throws Exception {
            Authentication autenticacion = filtrarSinCabecera();

            assertThat(autenticacion).isNull();
            // Ni la base de datos ni la verificacion del token se tocan. Sin
            // cabecera no hay nada que comprobar, y una consulta por peticion
            // anonima seria trabajo para todos los que no estan autenticados.
            verify(tokenIssuer, never()).verify(any());
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("un token que no se puede verificar deja la peticion sin autenticar")
        void tokenInvalido() throws Exception {
            when(tokenIssuer.verify(TOKEN)).thenReturn(Optional.empty());

            Authentication autenticacion = filtrarCon("Bearer " + TOKEN);

            assertThat(autenticacion).isNull();
            // Un token que no se puede verificar no llega a consultar la base de
            // datos: no hay a quien buscar.
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("un token invalido no corta la cadena: la peticion sigue su curso")
        void noCortaLaCadena() throws Exception {
            // Si el filtro respondiera 401 el mismo, una peticion a un endpoint
            // publico con un token caducado no podria cargar. Lo que se hace es
            // dejar pasar la peticion sin autenticar, y que sea el paso de
            // autorizacion el que decida.
            when(tokenIssuer.verify(TOKEN)).thenReturn(Optional.empty());

            MockHttpServletResponse respuesta = respuestaDe(
                    peticionCon("Bearer " + TOKEN));
            assertThat(respuesta.getStatus()).isEqualTo(200);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "bearer ",      // esquema en minusculas
                "BEARER ",      // esquema en mayusculas
                "Basic ",       // otro esquema
                ""})            // sin esquema
        @DisplayName("solo acepta el esquema Bearer con esa capitalizacion")
        void soloAceptaElEsquemaCorrecto(String prefijo) throws Exception {
            Authentication autenticacion = filtrarCon(prefijo + TOKEN);

            assertThat(autenticacion).isNull();
            verify(tokenIssuer, never()).verify(any());
        }

        @Test
        @DisplayName("una cabecera con el esquema pero sin token cuenta como ausente")
        void esquemaSinToken() throws Exception {
            // Es el caso de un cliente que manda la cabecera vacia por error.
            // Tratarlo como token invalido daria un 403 en vez del comportamiento
            // normal de peticion anonima.
            Authentication autenticacion = filtrarCon("Bearer    ");

            assertThat(autenticacion).isNull();
            verify(tokenIssuer, never()).verify(any());
        }
    }

    @Nested
    @DisplayName("Token valido de alguien que ya no puede entrar")
    class TokenDeUsuarioNoAutorizable {

        @Test
        @DisplayName("un usuario desactivado deja la peticion sin autenticar, aunque su token sea valido")
        void elDesactivadoNoSeAutentica() throws Exception {
            // Esta es la razon de releer el usuario en cada peticion. Sin la
            // consulta, desactivar una cuenta no surtiria efecto hasta que
            // caducasen los 15 minutos del token.
            User desactivado = usuarioInactivo();
            when(tokenIssuer.verify(TOKEN)).thenReturn(Optional.of(desactivado.getId()));
            when(userRepository.findById(desactivado.getId()))
                    .thenReturn(Optional.of(desactivado));

            Authentication autenticacion = filtrarCon("Bearer " + TOKEN);

            assertThat(autenticacion).isNull();
        }

        @Test
        @DisplayName("un token valido cuyo usuario ya no existe deja la peticion sin autenticar")
        void elBorradoNoSeAutentica() throws Exception {
            UserId id = UserId.from(UUID.randomUUID());
            when(tokenIssuer.verify(TOKEN)).thenReturn(Optional.of(id));
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            Authentication autenticacion = filtrarCon("Bearer " + TOKEN);

            assertThat(autenticacion).isNull();
        }

        @Test
        @DisplayName("el usuario se consulta una sola vez por peticion")
        void unaSolaConsultaPorPeticion() throws Exception {
            // Si se consultara mas de una vez, cada peticion costaria mas y un
            // fallo de base de datos en la segunda consulta dejaria al usuario sin
            // autenticar a pesar de tener un token valido.
            User desactivado = usuarioInactivo();
            when(tokenIssuer.verify(TOKEN)).thenReturn(Optional.of(desactivado.getId()));
            when(userRepository.findById(desactivado.getId()))
                    .thenReturn(Optional.of(desactivado));

            filtrarCon("Bearer " + TOKEN);

            verify(userRepository).findById(desactivado.getId());
        }
    }

    @Nested
    @DisplayName("Limpieza del contexto entre peticiones")
    class LimpiezaDelContexto {

        @Test
        @DisplayName("el contexto queda vacio despues de la peticion")
        void elContextoSeLimpia() throws Exception {
            // Spring Security guarda el contexto de la peticion en un hilo de un
            // pool. Si esta peticion no lo limpiara, el contexto de la anterior
            // pasaria a la siguiente que use el mismo hilo, y un usuario
            // autenticado se veria en la peticion de otro.
            User user = usuario(Role.CUSTOMER);
            prepararTokenValidoDe(user);

            filtrarCon("Bearer " + TOKEN);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("una peticion sin token no hereda la autenticacion de la anterior")
        void noSeHeredaLaAutenticacion() throws Exception {
            // Es el caso real: dos peticiones del mismo hilo, la primera
            // con token y la segunda sin. Si el contexto no se limpiara, la
            // segunda responderia como el usuario de la primera.
            User user = usuario(Role.CUSTOMER);
            prepararTokenValidoDe(user);

            assertThat(nombresDe(filtrarCon("Bearer " + TOKEN))).containsExactly("ROLE_CUSTOMER");
            assertThat(filtrarSinCabecera()).isNull();
        }
    }

    @Nested
    @DisplayName("Peticiones que no pueden llevar token")
    class PeticionesSinTokenPosible {

        @ParameterizedTest
        @ValueSource(strings = {
                "/css/estilos.css",
                "/js/app.js",
                "/images/logo.png",
                "/webjars/jquery.min.js",
                "/favicon.ico"})
        @DisplayName("no se ejecuta el filtro para ficheros estaticos")
        void noSeFiltraLoEstatico(String ruta) throws Exception {
            // Se manda un token valido a proposito: si el filtro se ejecutara, se
            // veria en las llamadas a los collaborators. Comprobar con una
            // peticion sin token pasaria aunque el filtro se ejecutara.
            MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
            peticion.addHeader("Authorization", "Bearer " + TOKEN);

            MockHttpServletResponse respuesta = respuestaDe(peticion);

            assertThat(respuesta.getStatus()).isEqualTo(200);
            // Cada peticion a un fichero estatico abriria una transaccion de base
            // de datos para nada. Sumadas, son la mayor parte del trafico de un
            // navegador real.
            verify(tokenIssuer, never()).verify(any());
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("una peticion normal si se filtra, y con token llega a consultar al usuario")
        void siSeFiltraLoNormal() throws Exception {
            User user = usuario(Role.CUSTOMER);
            prepararTokenValidoDe(user);

            filtrarCon("Bearer " + TOKEN);

            // La contraprueba del caso anterior: si shouldNotFilter devolviera
            // siempre true, el test de los estaticos pasaria por el motivo
            // equivocado y el filtro no se estaria ejecutando nunca.
            verify(tokenIssuer).verify(TOKEN);
            verify(userRepository).findById(user.getId());
        }
    }

    /**
     * Pasa una peticion GET por el filtro y devuelve la autenticacion que deja.
     *
     * <p>La autenticacion se captura dentro de la cadena, que es donde el contexto
     * esta escrito. Leerla despues de {@code doFilter} devolveria siempre
     * {@code null}, porque el filtro limpia el contexto al terminar, y ese
     * {@code null} no distinguiria "no autentico" de "limpio".
     *
     * @param cabecera valor de la cabecera {@code Authorization}, o {@code null}
     * @return la autenticacion que el filtro dejo en el contexto, o {@code null}
     * @throws Exception si la cadena falla
     */
    private Authentication filtrarCon(String cabecera) throws Exception {
        return autenticacionDe(peticionCon(cabecera));
    }

    /**
     * Construye una peticion a un endpoint normal con la cabecera indicada.
     *
     * @param cabecera valor de la cabecera {@code Authorization}, o {@code null}
     * @return la peticion lista para pasar por el filtro
     */
    private MockHttpServletRequest peticionCon(String cabecera) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/users");
        if (cabecera != null) {
            peticion.addHeader("Authorization", cabecera);
        }
        return peticion;
    }

    private Authentication filtrarSinCabecera() throws Exception {
        return autenticacionDe(new MockHttpServletRequest("GET", "/api/v1/users"));
    }

    private Authentication autenticacionDe(MockHttpServletRequest peticion) throws Exception {
        Authentication[] capturada = new Authentication[1];
        filtro.doFilter(peticion, new MockHttpServletResponse(),
                (request, response) -> capturada[0] =
                        SecurityContextHolder.getContext().getAuthentication());
        return capturada[0];
    }

    private MockHttpServletResponse respuestaDe(MockHttpServletRequest peticion) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, (request, response) -> {
        });
        return respuesta;
    }

    private java.util.List<String> nombresDe(Authentication autenticacion) {
        assertThat(autenticacion).isNotNull();
        return autenticacion.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .collect(Collectors.toList());
    }

    private void prepararTokenValidoDe(User user) {
        when(tokenIssuer.verify(TOKEN)).thenReturn(Optional.of(user.getId()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private User usuario(Role... roles) {
        return User.reconstitute(
                UserId.newId(),
                Email.of("ana@correo.com"),
                PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv"),
                Set.of(roles),
                true,
                AHORA.minusSeconds(86_400),
                AHORA);
    }

    private User usuarioInactivo() {
        return User.reconstitute(
                UserId.newId(),
                Email.of("ana@correo.com"),
                PasswordHash.of("{bcrypt}$2a$10$abcdefghijklmnopqrstuv"),
                Set.of(Role.CUSTOMER),
                false,
                AHORA.minusSeconds(86_400),
                AHORA);
    }
}
