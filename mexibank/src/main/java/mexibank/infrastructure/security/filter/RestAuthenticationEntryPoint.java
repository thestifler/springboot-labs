package mexibank.infrastructure.security.filter;

import java.io.IOException;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import mexibank.infrastructure.rest.error.ApiError;

/**
 * Responde 401 cuando la peticion no esta autenticada.
 *
 * <p><strong>Por que hay que declararlo.</strong> Sin este bean, Spring Security
 * responde con una redireccion a una pagina de login (302 a {@code /login}) que en
 * una API no existe. El cliente recibe un 302, no un JSON, y su deserializador
 * falla con un error que dice "no se puede leer la respuesta" en lugar de "no estas
 * autenticado". El fallo aparece en el sitio equivocado.
 *
 * <p><strong>Por que el cuerpo es el mismo {@link ApiError} que el del
 * manejador de excepciones.</strong> Para que el cliente tenga un solo formato que
 * leer. Un 401 con un cuerpo distinto del resto obligaria a un segundo manejador
 * justo en el error mas frecuente.
 *
 * <p><strong>Por que la cabecera {@code WWW-Authenticate}.</strong> Es donde el
 * protocolo OAuth 2 y las librerias de cliente buscan que esquema de autenticacion
 * usar. Sin ella, un cliente OAuth no sabe que credenciales enviar y el 401 le
 * llega como un fallo generico de red.
 *
 * <p><strong>Por que el mensaje no dice por que se rechazo el token.</strong> Decir
 * "ha caducado" o "la firma no es valida" es informacion gratis para quien prueba
 * tokens. El detalle real va al log del servidor.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * @param objectMapper serializador de Spring Boot. No se instancia aqui a
     *                     proposito: el que se inyecta lleva la configuracion de
     *                     Jackson de la aplicacion, y uno nuevo escribiria las
     *                     fechas en otro formato
     * @param clock        reloj inyectado, para que la marca de tiempo del error sea
     *                     comprobable en los tests
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException {

        log.debug("Peticion no autenticada a {}: {}",
                request.getRequestURI(),
                authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer");

        objectMapper.writeValue(response.getOutputStream(), ApiError.de(
                clock,
                HttpStatus.UNAUTHORIZED,
                "No autenticado: falta un token valido en la cabecera Authorization"));
    }
}
