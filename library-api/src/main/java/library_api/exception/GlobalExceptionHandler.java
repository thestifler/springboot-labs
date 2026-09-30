package library_api.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/**
 * Traduce excepciones a respuestas HTTP con un cuerpo uniforme.
 *
 * Sin esta clase, el Optional.orElseThrow() del service terminaba en un 500
 * con el cuerpo de error por defecto de Spring, sin indicar que faltaba el
 * recurso. Aqui el not-found es un 404 explicito y los errores de validacion
 * un 400 con el detalle por campo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ApiError> handleBookNotFound(BookNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage()));
    }

    /** El isbn ya esta dado de alta: conflicto con el estado actual del recurso. */
    @ExceptionHandler(BookAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleBookAlreadyExists(BookAlreadyExistsException ex) {
        log.warn("Conflicto de recurso existente: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    /**
     * El libro existe pero no quedan ejemplares. 409 y no 404 porque la peticion
     * es valida: choca con el estado actual del recurso.
     */
    @ExceptionHandler(NoAvailableCopiesException.class)
    public ResponseEntity<ApiError> handleNoAvailableCopies(NoAvailableCopiesException ex) {
        log.warn("Sin ejemplares disponibles: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    /**
     * El usuario existe pero esta INACTIVE o SUSPENDED. 409 y no 404 porque la
     * peticion es valida y el recurso existe: lo que choca es su estado actual.
     */
    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<ApiError> handleUserNotActive(UserNotActiveException ex) {
        log.warn("Usuario no activo para prestar: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    /**
     * El usuario ya tiene ese libro prestado y sin devolver. Mismo 409 que el
     * resto de conflictos de estado: usuario y libro existen y la peticion es
     * valida, pero choca con un prestamo abierto.
     */
    @ExceptionHandler(LoanAlreadyLoanedException.class)
    public ResponseEntity<ApiError> handleLoanAlreadyLoaned(LoanAlreadyLoanedException ex) {
        log.warn("Prestamo duplicado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    /** El prestamo no existe: 404, igual que un libro o un usuario inexistente. */
    @ExceptionHandler(LoanNotFoundException.class)
    public ResponseEntity<ApiError> handleLoanNotFound(LoanNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage()));
    }

    /**
     * El usuario ha superado el numero maximo de prestamos abiertos. 409 y no 404:
     * el usuario existe, lo que choca es su estado.
     */
    @ExceptionHandler(LoanLimitExceededException.class)
    public ResponseEntity<ApiError> handleLoanLimitExceeded(LoanLimitExceededException ex) {
        log.warn("Limite de prestamos superado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    /**
     * Devolver dos veces el mismo prestamo. Se responde 409 y no 200 idempotente
     * porque sumaria un segundo ejemplar al inventario: el cliente necesita saber
     * que su reintento no surtio efecto.
     */
    @ExceptionHandler(LoanAlreadyReturnedException.class)
    public ResponseEntity<ApiError> handleLoanAlreadyReturned(LoanAlreadyReturnedException ex) {
        log.warn("Prestamo ya devuelto: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    /**
     * El prestamo no admite renovacion: ya venció o agoto sus renovaciones.
     */
    @ExceptionHandler(LoanNotRenewableException.class)
    public ResponseEntity<ApiError> handleLoanNotRenewable(LoanNotRenewableException ex) {
        log.warn("Prestamo no renovable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Conflict", ex.getMessage()));
    }

    /** Fallo de validacion del cuerpo de la peticion (@Valid @RequestBody). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "La peticion contiene datos invalidos", fieldErrors));
    }

    /**
     * Fallo de validacion de parametros de metodo (path variables, query params).
     *
     * ATENCION, esta es una trampa de Spring Framework 6.1+ y la razon de que este
     * handler sea mas largo de lo que parece. Segun como este decorado el metodo,
     * un {@code @Valid @RequestBody} puede fallar de dos maneras distintas:
     * <ul>
     *   <li>{@code MethodArgumentNotValidException} -> lo captura handleInvalidBody.
     *       Es lo que pasa en {@code POST /library/users}, que no tiene ninguna
     *       restriccion en los parametros.</li>
     *   <li>{@code HandlerMethodValidationException} -> lo captura este handler. Es
     *       lo que pasa en cuanto el metodo tiene AL MENOS UNA restriccion propia,
     *       como el {@code @Positive} del id de ruta. Al existir validacion de
     *       metodo, Spring la aplica tambien al cuerpo y mete ahi los errores de
     *       los campos del DTO.</li>
     * </ul>
     *
     * Sin extrapolar el detalle, el cliente recibia un 400 con
     * {@code fieldErrors} vacio y un mensaje generico del tipo
     * {@code 400 BAD_REQUEST "Validation failure"}, sin ninguna pista de que campo
     * estaba mal. Y el mismo DTO validado desde otro endpoint si daba el detalle,
     * lo que hace el fallo practicamente imposible de diagnosticar desde el lado
     * del cliente.
     *
     * {@code getBeanResults()} devuelve solo los resultados que son
     * {@code ParameterErrors}, que es la clase que implementa {@code Errors} y por
     * tanto la unica que sabe devolver los errores POR CAMPO.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleInvalidMethodArgument(HandlerMethodValidationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ParameterErrors errors : ex.getBeanResults()) {
            for (FieldError fieldError : errors.getFieldErrors()) {
                fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
            }
        }

        // Si no hay errores por campo (por ejemplo, una restriccion de clase) se
        // devuelve el mensaje de Spring, que en ese caso si es util.
        String message = fieldErrors.isEmpty()
                ? ex.getMessage()
                : "La peticion contiene datos invalidos";

        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", message, fieldErrors));
    }

    /**
     * Un parametro de la peticion no se ha podido convertir al tipo declarado.
     *
     * Ocurre con GET /library/loans/no-es-un-numero, donde el id es un Long y el
     * cliente manda texto. El problema no es del servidor, es de la peticion, asi
     * que la respuesta es 400 y no 500.
     *
     * Necesita handler propio porque en Spring Framework 7
     * MethodArgumentTypeMismatchException ya NO implementa ErrorResponse: hereda de
     * org.springframework.beans.TypeMismatchException. Antes de Spring 7 caia en
     * handleUnexpected y respondia 500 con "Ocurrio un error inesperado", que
     * ademas ocultaba que el problema era el id de la URL.
     *
     * El mensaje crudo ("Failed to convert value of type String to required type
     * Long") se registra en el log pero no se devuelve: el cliente ya sabe que ha
     * mandado algo que no es un numero, y el detalle tecnico no le aporta nada.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parametro '{}' con valor no convertible: {}",
                ex.getName(), ex.getValue(), ex);
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "El valor del parametro '" + ex.getName() + "' no tiene un formato valido"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage()));
    }

    /**
     * El cuerpo no se ha podido convertir al DTO: JSON mal formado, tipo de campo
     * equivocado o un enum con un valor que no existe.
     *
     * Sin este handler, un "status": "PENDIENTE_DE_PAGO" caeria en
     * handleUnexpected y responderia 500, porque HttpMessageNotReadableException no
     * implementa ErrorResponse. Sin embargo el problema no es del servidor: la
     * peticion del cliente es invalida y por eso es un 400. Es el error mas comun
     * al escribir a mano un PATCH de estado.
     *
     * El mensaje que devuelve Spring ("Cannot deserialize value of type
     * `UserStatus`...") NO se reenvia al cliente: revela nombres de clases Java.
     * El detalle va al log y el cuerpo se queda con un mensaje estable.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo de la peticion no interpretable: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
                        "El cuerpo de la peticion no tiene un formato valido"));
    }

    /**
     * Red de seguridad para que una excepcion inesperada no exponga detalles
     * internos al cliente. Las excepciones propias de Spring que implementan
     * ErrorResponse (404 de ruta, 405 de metodo no permitido) conservan su status
     * original.
     *
     * OJO: que sea una excepcion de Spring NO implica que implemente ErrorResponse.
     * MethodArgumentTypeMismatchException es de Spring y en Framework 7 ha dejado de
     * implementarla, asi que necesita su propio handler (handleTypeMismatch) o
     * acabaria aqui y responderia 500 por un 400 del cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(ApiError.of(status, HttpStatus.valueOf(status).getReasonPhrase(),
                            errorResponse.getBody().getDetail()));
        }
        log.error("Error inesperado no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "Ocurrio un error inesperado"));
    }
}
