package mexibank.infrastructure.security.filter;

import java.io.IOException;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import mexibank.infrastructure.rest.error.ApiError;

/**
 * Responde 403 cuando el usuario esta autenticado pero no tiene permiso.
 *
 * <p><strong>Por que 403 y no 401.</strong> Es la diferencia que mas confunde a
 * quien consume una API. 401 significa "no se quien eres, identificate". 403
 * significa "se quien eres y no te dejo". Confundirlos hace que el cliente reintente
 * un login que no va a funcionar nunca.
 *
 * <p><strong>Por que se registra quien fallo y no que permisos necesitaba.</strong>
 * Saber que permisos concretos hacen falta solo ayuda a hacer pruebas sistematicas
 * a alguien que ya esta autenticado. El log lo guarda el servidor, pero la
 * respuesta no lleva nada de eso.
 *
 * <p><strong>Por que el cuerpo es el mismo {@link ApiError} que el del 401.</strong>
 * El cliente no necesita dos formatos para leer un error, y mucho menos en el caso
 * en que se va a repetir.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException {

        Authentication auth = (Authentication) request.getAttribute("jakarta.servlet.security.user");

        log.warn("Acceso denegado en {} para {}", request.getRequestURI(), auth);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getOutputStream(), ApiError.de(
                clock,
                HttpStatus.FORBIDDEN,
                "Acceso denegado: tu usuario no tiene permisos para esta operacion"));
    }
}
