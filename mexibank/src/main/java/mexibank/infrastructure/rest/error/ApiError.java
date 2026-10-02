package mexibank.infrastructure.rest.error;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Cuerpo de error de la API. Unico para todos los fallos.
 *
 * <p><strong>Por que un solo formato y no uno por origen.</strong> Hay tres sitios
 * que producen errores: el manejador de excepciones de los controladores, el punto
 * de entrada de Spring Security (401) y el manejador de acceso denegado (403). Los
 * tres escriben este mismo record. Si cada uno compusiera su propio JSON, el
 * cliente necesitaria un manejador distinto segun quien hubiera fallado, y el
 * fallo mas frecuente (el 401) seria justamente el que no tiene.
 *
 * <p><strong>Por que no se reutiliza el cuerpo de error de Spring.</strong> El de
 * Spring cambia entre versiones y sus campos varian. Un contrato de error estable
 * es lo que permite al cliente escribir un unico manejador y no tocarlo cada vez
 * que se actualiza el servidor.
 *
 * <p><strong>Por que el codigo va tambien en el cuerpo.</strong> Un cliente que
 * solo lee el cuerpo (por ejemplo, un proceso por lotes que no expone el estado de
 * la respuesta) tiene el dato sin depender de la cabecera.
 *
 * @param timestamp   instante en que se produjo el error, en ISO-8601
 * @param status      codigo HTTP
 * @param error       nombre del estado, por ejemplo {@code Unauthorized}
 * @param message     descripcion legible
 * @param fieldErrors errores por campo; vacio si el problema no es de validacion
 */
@Schema(description = "Cuerpo de error unico de la API")
public record ApiError(
        @Schema(description = "Instante en que se produjo el error, en ISO-8601",
                example = "2026-03-01T10:15:30Z")
        Instant timestamp,

        @Schema(description = "Codigo HTTP", example = "400")
        int status,

        @Schema(description = "Nombre del estado HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Descripcion legible del problema",
                example = "Hay campos con formato o valor incorrecto")
        String message,

        @Schema(description = "Errores por campo; vacio si el fallo no es de validacion")
        Map<String, String> fieldErrors) {

    /**
     * Copia defensiva.
     *
     * <p>Sin ella, el mapa que llega se devolveria tal cual a todos los llamantes
     * y cualquiera podria alterarlo. Copiar ademas lo deja inmutable, que es lo que
     * conviene en una respuesta que ya no se va a tocar.
     */
    public ApiError {
        fieldErrors = Map.copyOf(new LinkedHashMap<>(fieldErrors));
    }

    /**
     * Error sin detalle por campo.
     *
     * <p>Es el caso mayoritario: solo los fallos de validacion tienen algo que
     * decir de un campo concreto. Para el resto, un mapa vacio es mas claro que
     * un {@code null}, porque el cliente puede iteratearlo siempre.
     *
     * @param clock    reloj inyectado, para que la marca de tiempo sea
     *                 comprobable en los tests
     * @param status   codigo HTTP
     * @param mensaje  descripcion legible
     * @return cuerpo de error listo para serializar
     */
    public static ApiError de(Clock clock, HttpStatusCode status, String mensaje) {
        return new ApiError(
                clock.instant(),
                status.value(),
                nombreDelEstado(status),
                mensaje,
                Map.of());
    }

    /**
     * Error con detalle por campo.
     *
     * @param clock           reloj inyectado
     * @param status          codigo HTTP
     * @param mensaje         descripcion legible
     * @param erroresPorCampo errores de validacion
     * @return cuerpo de error listo para serializar
     */
    public static ApiError de(Clock clock,
                              HttpStatusCode status,
                              String mensaje,
                              Map<String, String> erroresPorCampo) {
        return new ApiError(
                clock.instant(),
                status.value(),
                nombreDelEstado(status),
                mensaje,
                erroresPorCampo);
    }

    /**
     * Nombre legible del estado, por ejemplo {@code Unauthorized}.
     *
     * <p>Un valor desconocido devuelve el numero en lugar de lanzar: un codigo
     * que Spring no conoce todavia no debe impedir que se describa el error.
     *
     * @param status codigo HTTP
     * @return el nombre del estado, o su valor numerico
     */
    private static String nombreDelEstado(HttpStatusCode status) {
        HttpStatus conocido = HttpStatus.resolve(status.value());
        return conocido == null ? Integer.toString(status.value()) : conocido.getReasonPhrase();
    }
}
