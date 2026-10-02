package mexibank.infrastructure.security.config;

import java.time.Clock;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import mexibank.domain.user.PasswordHasher;
import mexibank.domain.user.TokenIssuer;
import mexibank.domain.user.UserRepository;
import mexibank.infrastructure.security.filter.BearerTokenAuthenticationFilter;
import mexibank.infrastructure.security.filter.RestAccessDeniedHandler;
import mexibank.infrastructure.security.filter.RestAuthenticationEntryPoint;
import mexibank.infrastructure.security.support.BCryptPasswordHasher;
import mexibank.infrastructure.security.support.JwtTokenIssuer;
import mexibank.infrastructure.security.support.RoleAuthorityMapper;

/**
 * Configuracion de seguridad: quien puede entrar, como se emite y valida su token y
 * que se responde cuando algo falla.
 *
 * <p><strong>Por que todo el cableado vive aqui y no en {@code @Component} sueltos
 * por el paquete.</strong> Los adaptadores de seguridad dependen unos de otros: el
 * emisor necesita el encoder, el hasher necesita el codificador de contrasenas y el
 * filtro necesita el emisor. Repartidos por el codigo, saber que clase recibe cada
 * dependencia obliga a abrir cinco ficheros; aqui se lee entero de un vistazo. El
 * precio es que hay que venir aqui cuando se anade un adaptador, y esa es la
 * compensacion que compensa.
 *
 * <p><strong>Por que se desactivan csrf, httpBasic y formLogin.</strong> Es una
 * API REST sin estado. CSRF no aplica con tokens Bearer: el riesgo delata es que el
 * navegador anada credenciales a una peticion que el usuario no pidio, y con un
 * token que el sitio no tiene, no hay nada que anadir automaticamente. httpBasic y
 * formLogin sirven para navegadores y dejarlos puestos haria que una peticion sin
 * token recibiera un 401 con una ventana de login incrustada en el JSON.
 *
 * <p><strong>Por que la sesion es STATELESS.</strong> Cada peticion lleva su token.
 * Guardar sesion en el servidor crea estado, obliga a compartir almacenamiento entre
 * instancias y rompe el escalado horizontal. El coste es la consulta a base de datos
 * del filtro, ya documentada en {@code BearerTokenAuthenticationFilter}.
 *
 * <p><strong>Por que no hay {@code @EnableWebSecurity}.</strong> Spring Boot activa
 * la seguridad por el hecho de que exista un {@code SecurityFilterChain} en el
 * contexto. Anadir la anotacion no aporta nada y supondria una segunda via para
 * activar la misma configuracion.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * Nombre del algoritmo simetrico con el que se firma.
     *
     * <p>Lo entiende la JCA de la JDK ({@code javax.crypto}), no es un
     * invento de Spring ni de Nimbus. Se declara aqui para que el nombre viva en
     * un unico sitio: si el encoder y el decoder usaran una cadena distinta, la
     * firma se produciria con un algoritmo y se verificaria con otro, y el fallo
     * apareceria como "todos los tokens son invalidos" en vez de como un error de
     * configuracion.
     */
    private static final String ALGORITMO_FIRMA = "HmacSHA256";

    /**
     * Emisor que debe declarar todo token aceptado.
     *
     * <p>Debe coincidir con el que escribe {@code JwtTokenIssuer}. Si divergen,
     * el sintoma es que todos los tokens se rechazan, que es un fallo ruidoso; lo
     * contrario, que se acepte el token equivocado, seria silencioso.
     */
    private static final String EMISOR = "mexibank";

    /**
     * Define la cadena de filtros.
     *
     * <p><strong>Por que el filtro propio va antes de
     * {@code UsernamePasswordAuthenticationFilter}.</strong> Ese filtro atiende un
     * {@code /login} con formulario HTML, que aqui no se usa. Colocarse antes
     * significa que el token se intenta leer antes de que ningun otro filtro decida
     * que la peticion necesita credenciales.
     *
     * <p><strong>Por que las rutas se declaran aqui y no con anotaciones.</strong>
     * Para que exista un unico sitio donde se lee que es publico y que no. Con
     * anotaciones repartidas por los controladores, contestar a la pregunta mas
     * basica de una API (que puedo llamar y quien) obliga a revisar todos los
     * controladores.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BearerTokenAuthenticationFilter bearerFilter,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // La documentacion OpenAPI y Swagger UI tienen que ser
                        // alcanzables sin token: describen como conseguir uno. Si
                        // exigieran autenticacion, el contrato solo lo leeria quien
                        // ya lo conoce. No exponen datos de negocio, solo la forma
                        // de la API; en un despliegue real se apagan con
                        // springdoc.api-docs.enabled=false sin tocar codigo.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Dar de alta un usuario es la unica operacion que crea
                        // cuentas y concede roles, asi que se restringe a ADMIN.
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Codificador de contrasenas.
     *
     * <p><strong>Por que el delegante y no {@code BCryptPasswordEncoder} a
     * secas.</strong> El delegante antepone {@code {bcrypt}} al hash. Sin ese
     * prefijo no hay forma de saber con que algoritmo se guardo cada contrasena, y
     * migrar a argon2 obligaria a rehashear todas de golpe. Con el prefijo, la
     * migracion es usuario a usuario: el verificador sabe por el prefijo que
     * comprobar y puede rehashear en el siguiente login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Adaptador del puerto {@link PasswordHasher}.
     *
     * <p>Se declara por el puerto, no por la clase, porque el caso de uso pide el
     * puerto. En un test se puede sustituir por otro bean del mismo tipo sin tocar
     * nada mas.
     */
    @Bean
    public PasswordHasher passwordHasher(PasswordEncoder encoder) {
        return new BCryptPasswordHasher(encoder);
    }

    /**
     * Verificador de tokens.
     *
     * <p><strong>Por que se declara a mano.</strong> El starter de resource server
     * de Spring solo autoconfigura este bean si esta el modulo
     * {@code spring-boot-starter-oauth2-resource-server}. Aqui se declara la
     * dependencia {@code spring-security-oauth2-jose} a proposito, sin el starter,
     * precisamente porque se quiere el control de la firma: una clave simetrica
     * rehidratada desde configuracion, no la que leeria el starter de la propiedad
     * {@code spring.security.oauth2.resourceserver.jwt.*}.
     *
     * <p><strong>Por que se declara el validador en vez de dejar el de por
     * defecto.</strong> El de por defecto comprueba la vigencia y nada mas. Sin
     * comprobar el emisor, cualquier servicio que comparta el secreto (un
     * entorno de pruebas, un proceso hermano) podria emitir tokens que esta
     * aplicacion aceptaria, y acabaria con permisos que no le corresponden. La
     * comprobacion cuesta una linea y hace que el claim {@code iss} que escribe
     * {@code JwtTokenIssuer} signifique algo.
     *
     * <p>El instante contra el que se mide la vigencia es el reloj del sistema.
     * Es lo unico razonable en produccion: la caducidad del token tiene que
     * medirse contra la hora que ven todos, no contra la de una instancia
     * cualquiera.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKeySpec secretKey = claveSecreta(properties);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISOR));
        return decoder;
    }

    /**
     * Emisor de tokens.
     *
     * <p><strong>Por que {@code ImmutableSecret} y no una clave publica.</strong>
     * {@code ImmutableSecret} es la forma que Nimbus ofrece para material simetrico:
     * envuelve los bytes en un JWK de tipo {@code oct} y funciona con el mismo
     * {@code JWKSource} que usaria una clave RSA. El dia que se migre a RSA solo
     * cambia esta linea: el resto del encoder sigue igual.
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        byte[] clave = Base64.getDecoder().decode(properties.secret().value());
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(clave);
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Adaptador del puerto {@link TokenIssuer} sobre los beans de arriba.
     */
    @Bean
    public TokenIssuer tokenIssuer(JwtEncoder encoder, JwtDecoder decoder, Clock clock) {
        return new JwtTokenIssuer(encoder, decoder, clock);
    }

    /**
     * Filtro de autenticacion por cabecera {@code Authorization}.
     *
     * <p>Se construye a mano y no con anotacion porque el filtro se registra en la
     * cadena por posicion, no por autodeteccion de beans: Spring no lo insertaria
     * solo aunque fuera un bean.
     */
    @Bean
    public BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter(
            TokenIssuer tokenIssuer,
            UserRepository userRepository,
            RoleAuthorityMapper roleAuthorityMapper) {
        return new BearerTokenAuthenticationFilter(tokenIssuer, userRepository, roleAuthorityMapper);
    }

    /**
     * Traduccion de roles del dominio a autoridades de Spring.
     */
    @Bean
    public RoleAuthorityMapper roleAuthorityMapper() {
        return new RoleAuthorityMapper();
    }

    /**
     * Traduce la clave de configuracion a bytes y a una clave utilizable por los
     * dos extremos de la firma.
     *
     * <p><strong>Por que Base64 y no la cadena tal cual.</strong> El secreto es
     * material binario disfrazado de texto. Codificandolo, un valor generado con
     * {@code openssl rand -base64 32} entra en el archivo de configuraciones sin
     * caracteres especiales que rompan un {@code .properties}, y el mismo valor
     * produce siempre los mismos bytes.
     *
     * @param properties propiedades de seguridad ya validadas
     * @return la clave simetrica lista para firmar y verificar
     */
    private SecretKeySpec claveSecreta(JwtProperties properties) {
        byte[] bytes = Base64.getDecoder().decode(properties.secret().value());
        return new SecretKeySpec(bytes, ALGORITMO_FIRMA);
    }
}