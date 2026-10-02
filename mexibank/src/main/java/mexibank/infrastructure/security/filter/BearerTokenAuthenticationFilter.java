package mexibank.infrastructure.security.filter;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import mexibank.domain.user.TokenIssuer;
import mexibank.domain.user.User;
import mexibank.domain.user.UserId;
import mexibank.domain.user.UserRepository;
import mexibank.infrastructure.security.support.RoleAuthorityMapper;

/**
 * Filtro de autenticacion por token Bearer.
 *
 * <p><strong>Por que un filtro propio y no el
 * {@code BearerTokenAuthenticationFilter} de Spring.</strong> Ese filtro existe
 * (viene en {@code spring-security-oauth2-resource-server}) y es la eleccion
 * habitual cuando el token lo emite un servidor de autorizacion externo. Aqui el
 * token lo emite esta misma aplicacion, y hay una comprobacion que el filtro de
 * Spring no hace: antes de dar por autenticada a alguien, se relee el usuario de la
 * base de datos para confirmar que sigue existiendo y que no esta desactivado.
 * Sin eso, desactivar un usuario no surte efecto hasta que su token caduca.
 *
 * <p><strong>Por que se relee el usuario en cada peticion.</strong> Es el precio
 * de que la revocacion sea inmediata. Una alternativa es confiar en los roles que
 * viajan en el token y no consultar nada: es mas rapida, pero un usuario
 * desactivado conserva el acceso hasta 15 minutos. Para un banco, 15 minutos de
 * acceso a una cuenta bloqueada es un incidente. El coste es una consulta por
 * peticion autenticada, que es el precio de la revocabilidad y se puede.cachear
 * mas adelante si el volumen lo exige.
 *
 * <p><strong>Por que un token invalido no corta la cadena.</strong> Si el filtro
 * respondiera 401 el mismo, un cliente que llega a un endpoint publico con un token
 * caducado en la cabecera (por ejemplo, un portal con una sesion caducada
 * navegando por una pagina de precios) no podria cargar ni esa pagina. Lo que se
 * hace es dejar pasar la peticion sin autenticar y que sea el paso de
 * autorizacion el que decida: como la cadena continua sin credenciales, acabara
 * dando 403 por falta de permisos en vez de 401, que es la respuesta correcta para
 * "no puedes ver esto".
 *
 * <p><strong>Por que se limpia el contexto antes de escribir.</strong> Spring
 * Security acumula el contexto de la peticion en un hilo de un pool. Si esta
 * peticion no deja nada escrito, el contexto anterior de ese hilo podria
 * traspasarse a la siguiente peticion que use el mismo hilo, y un usuario
 * autenticado se veria en la peticion de otro. Es un fallo de seguridad real, no
 * una hipotesis: por eso se limpia siempre al terminar.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthenticationFilter.class);

    private static final String PREFIJO_BEARER = "Bearer ";

    /**
     * Cabecera donde viaja el token. Es una constante de la especificacion HTTP,
     * no un nombre elegido aqui.
     */
    private static final String CABECERA_AUTHORIZATION = "Authorization";

    private final TokenIssuer tokenIssuer;
    private final UserRepository userRepository;
    private final RoleAuthorityMapper authorityMapper;

    public BearerTokenAuthenticationFilter(TokenIssuer tokenIssuer,
                                           UserRepository userRepository,
                                           RoleAuthorityMapper authorityMapper) {
        this.tokenIssuer = tokenIssuer;
        this.userRepository = userRepository;
        this.authorityMapper = authorityMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // El contexto se limpia al terminar, no antes. Si un filtro anterior ya
        // habia autenticado a alguien (por ejemplo, un test con @WithMockUser), no se
        // pisa lo que hizo.
        try {
            autenticar(request);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Intenta autenticar la peticion a partir del token, si lo hay.
     *
     * <p>Este metodo no falla nunca: si no hay token, si es invalido, o si el
     * usuario ya no existe, la peticion sigue sin autenticar. Quien decide si eso
     * es un error es el paso de autorizacion.
     */
    private void autenticar(HttpServletRequest request) {
        Optional<String> token = extraerToken(request);
        if (token.isEmpty()) {
            return;
        }

        Optional<UserId> userId = tokenIssuer.verify(token.get());
        if (userId.isEmpty()) {
            // No se registra el token: es una credencial y acabaria en los logs de
            // cualquier sitio que copie la peticion.
            log.debug("Peticion con token que no se pudo validar. Se sigue como anonima.");
            return;
        }

        Optional<User> usuario = userRepository.findById(userId.get());
        if (usuario.isEmpty()) {
            log.debug("Token valido cuyo usuario ya no existe. Se sigue como anonima.");
            return;
        }

        // Los roles se leen del agregado recien consultado, no del claim del
        // token. El token identifica; el usuario autoriza. Por eso desactivar a
        // alguien surte efecto en la peticion siguiente y no en 15 minutos.
        User user = usuario.get();
        if (!user.canAuthenticate()) {
            // No se dice que el usuario esta desactivado en la respuesta: se trata
            // como si no hubiera token. Informar de ello confirmaria que el token es
            // genuino, y con eso un atacante sabe que ha dado con una cuenta real.
            log.debug("Peticion con token de usuario desactivado. Se sigue como anonima.");
            return;
        }

        Collection<GrantedAuthority> authorities = authorityMapper.toAuthorities(user.getRoles());

        // Se mete un principal reducedo (el identificador) y no el agregado
        // User. El motivo esta en AuthenticatedUser: el agregado lleva el hash de
        // la contrasena dentro y eso no debe quedar accesible desde el contexto de
        // seguridad.
        //
        // authenticated(...) en vez del constructor de tres argumentos: el
        // constructor marca el token como NO autenticado todavia, y Spring exige
        // que el flag de autenticado sea true para aceptar el contexto.
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(user.getId()), null, authorities);

        // Guarda IP y sesion en la peticion, para que aparezcan en los logs de
        // seguridad sin tener que leerlas a mano del request.
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Usuario {} autenticado con roles {}", user.getId(), user.getRoles());
    }

    /**
     * Extrae el token de la cabecera {@code Authorization}.
     *
     * <p><strong>Por que se compara el prefijo entero y con mayuscula.</strong> El
     * esquema de autenticacion Bearer define el token como un valor precedido de
     * la palabra "Bearer". Comparar solo el principio de la palabra seria ambiguo
     * con otro esquema que empezara igual, y comparar en minusculas haria que
     * "bearer " tambien valiera. Ninguna de las dos flexiones aporta nada y las dos
     * amplian lo que se acepta.
     *
     * <p>Un token con el prefijo pero sin contenido se considera ausente, no
     * invalido: es el caso de un cliente que manda la cabecera vacia por error, y
     * tratarlo como invalido daria un 403 en vez del comportamiento normal de
     * peticion anonima.
     */
    private Optional<String> extraerToken(HttpServletRequest request) {
        String cabecera = request.getHeader(CABECERA_AUTHORIZATION);
        if (cabecera == null || !cabecera.startsWith(PREFIJO_BEARER)) {
            return Optional.empty();
        }
        String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /**
     * Este filtro no se aplica a las peticiones que no pueden llevar token.
     *
     * <p><strong>Por que se filtra por extension.</strong> Las peticiones a
     * ficheros estaticos (CSS, imagenes del logo) no llevan cabecera de
     * autorizacion. Sin este filtro, cada una abriria una transaccion de base de
     * datos para nada. No es una optimizacion menor: son peticiones que el navegador
     * hace en cada pagina y que, sumadas, son la mayor parte del trafico de un
     * navegador real.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.startsWith("/webjars/")
                || uri.equals("/favicon.ico");
    }
}