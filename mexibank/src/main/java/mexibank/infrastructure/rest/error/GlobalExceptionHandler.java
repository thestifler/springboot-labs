package mexibank.infrastructure.rest.error;

import java.time.Clock;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import mexibank.domain.user.EmailAlreadyRegisteredException;
import mexibank.domain.user.InvalidCredentialsException;
import mexibank.domain.user.UserDeactivatedException;
import mexibank.domain.user.WeakPasswordException;

/**
 * Traduce las excepciones a respuestas HTTP con un unico formato.
 *
 * <p><strong>Por que esta clase es la unica que conoce los codigos de
 * estado.</strong> Las excepciones del dominio no saben que es un 401 ni un 409: si
 * lo supieran, el dominio dependeria de HTTP y habria que reescribirlas para
 * exponerlos por otro protocolo (gRPC, un consumidor de eventos). Aqui ocurre esa
 * traduccion, asi que hay un unico sitio donde leer que codigo devuelve cada fallo.
 *
 * <p><strong>Por que tambien se capturan aqui los fallos de los validadores
 * del borde.</strong> Spring lanza {@code MethodArgumentNotValidException} cuando un
 * {@code @Valid} falla, y responde con su propio JSON de error, de estructura
 * distinta. Sin capturarla aqui, el cliente tendria que aprender dos formatos
 * distintos segun el origen del fallo: uno para las validaciones y otro para todo
 * lo demas. Un solo formato es lo que permite un unico manejador en el cliente.
 *
 * <p><strong>Por que el detalle va al log y no a la respuesta.</strong> El cliente
 * recibe un mensaje util para corregir su peticion. El motivo exacto se queda en el
 * log del servidor. En un login fallido eso es ademas lo que impide enumerar
 * cuentas: ver {@link InvalidCredentialsException}.
 *
 * <p><strong>Por que el reloj se inyecta.</strong> La marca de tiempo del error la
 * leen los logs del cliente para correlacionar con los del servidor. Si se calculara
 * con {@code Instant.now()}, un test no podria comprobar su valor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Credenciales incorrectas: correo desconocido o contrasena que no coincide.
     *
     * <p>El motivo real va al log y la respuesta es siempre la misma. Un cliente que
     * pudiera distinguir "ese correo no existe" de "la contrasena no es la correcta"
     * podria recorrer una lista de correos y descubrir quien tiene cuenta en el
     * banco. El mismo camino de calculo en los dos casos (el hash se verifica
     * siempre) evita ademas que la diferencia se mida por reloj.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException ex) {
        log.info("Login fallido: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Usuario desactivado que ha acertado la contrasena.
     *
     * <p>403 y no 401 porque aqui el cliente ya ha demostrado que se identifica
     * bien: el problema es el estado de la cuenta, no las credenciales. Un 401
     * obligaria a reintentar un login que no va a funcionar nunca, y el usuario
     * acabaria pensando que su contrasena es incorrecta.
     */
    @ExceptionHandler(UserDeactivatedException.class)
    public ResponseEntity<ApiError> deactivated(UserDeactivatedException ex) {
        log.info("Login rechazado por cuenta desactivada");
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Correo ya registrado.
     *
     * <p>409 y no 400: el formato del correo es valido, el problema es que entra en
     * conflicto con el estado actual del recurso. Aqui no hay riesgo de enumeracion
     * porque el cliente conoce el correo que ha enviado.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> duplicateEmail(EmailAlreadyRegisteredException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Contrasena que no cumple la politica.
     *
     * <p>El mensaje es el generico de la excepcion, no el detalle de que regla
     * incumple. Un endpoint de alta de usuarios es un lugar razonable para buscar y un
     * lugar inutil para proteger.
     */
    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ApiError> weakPassword(WeakPasswordException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Rol desconocido en la peticion.
     *
     * <p>Es un error de la peticion, no del servidor: el cliente ha enviado un valor
     * que no existe. Distinguirlo de otros fallos del servidor es lo que permite
     * responder 400 y no un 500, que haria pensar que hay que reintentar.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Content-Type que la API no sabe leer.
     *
     * <p><strong>Por que esto no puede quedarse en el 500 del manejador
     * generico.</strong> Spring lanza {@code HttpMediaTypeNotSupportedException}
     * cuando el cliente manda, por ejemplo, un formulario en lugar de JSON. Es un
     * fallo del cliente y su respuesta correcta es 415, no 500: un 500 dice "el
     * servidor se ha roto" e invita a reintentar, mientras que la peticion tal
     * cual jamas va a funcionar porque el problema esta en la cabecera. Lo que se
     * responde es el mismo {@link ApiError} de siempre, para que el cliente no
     * tenga un segundo formato de error que interpretar.
     *
     * <p>El motivo llega al log y no a la respuesta porque Spring lo compone con
     * las clases de converter que hay en el classpath, que el cliente no puede
     * actuar sobre ellas. Se le dice que formato se espera.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMediaType(
            HttpMediaTypeNotSupportedException ex) {
        log.debug("Content-Type no soportado: {}", ex.getMessage());
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "El Content-Type no es soportado; se espera application/json");
    }

    /**
     * Cuerpo que no se puede deserializar: JSON mal formado o un tipo inesperado.
     *
     * <p><strong>Por que se captura y no sube.</strong> El mensaje de Jackson
     * incluye la clase de la excepcion y la posicion del caracter, que no le sirve de
     * nada a un cliente y en algunos despliegues incluye informacion interna del
     * servidor. Se registra en modo debug, que es donde sirve para diagnosticar, y
     * al cliente se le dice solo que el cuerpo no es valido.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Cuerpo de peticion ilegible: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "El cuerpo de la peticion no es un JSON valido");
    }

    /**
     * Fallo de las anotaciones de validacion del borde.
     *
     * <p>Se traduce campo a campo para que el cliente sepa <em>que</em> corregir. Un
     * mensaje unico del tipo "la peticion no es valida" obliga a probar
     * combinaciones hasta acertar.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        // TreeMap ordena los campos: el mensaje resulta legible y, sobre todo,
        // reproducible entre ejecuciones, que es lo que permite comparar dos
        // respuestas en un test.
        Map<String, String> errores = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> errores.putIfAbsent(
                fe.getField(),
                fe.getDefaultMessage() == null ? "valor no valido" : fe.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.de(clock, HttpStatus.BAD_REQUEST,
                        "Hay campos con formato o valor incorrecto", errores));
    }

    /**
     * Cualquier otra excepcion no prevista.
     *
     * <p><strong>Por que el mensaje es generico.</strong> El texto de una excepcion
     * inesperada suele contener detalles internos: nombres de tabla, consultas SQL,
     * rutas del servidor. Devolverlo al cliente es una fuga de informacion. Se
     * registra completo en el log y se responde con un mensaje neutro.
     *
     * <p>Se registra en nivel {@code error} y no en {@code warn} porque una
     * excepcion no controlada casi siempre es un fallo real que alguien tiene que
     * mirar.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    /**
     * Construye la respuesta de error sin detalle por campo.
     *
     * @param status  codigo HTTP
     * @param mensaje descripcion legible del problema
     * @return respuesta con el cuerpo {@link ApiError} y el codigo correspondiente
     */
    private ResponseEntity<ApiError> error(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status)
                .body(ApiError.de(clock, status, mensaje));
    }
}
