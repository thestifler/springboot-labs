package library_api.exception;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

import library_api.controller.LoanController;
import library_api.dto.BookRequest;
import library_api.entity.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests del mapeo de excepciones de dominio a respuestas HTTP.
 *
 * Son el unico sitio donde se fija que cada excepcion produce el codigo
 * correcto: 404 para el recurso inexistente, 409 para los dos casos de
 * conflicto (isbn duplicado y sin ejemplares) y 400 para los errores de
 * validacion. Un codigo equivocado aqui solo se detectaria desde un cliente
 * real.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ---------------------------------------------------------------------
    // Not found
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("BookNotFoundException se traduce a 404 Not Found")
    void bookNotFound_devuelve404() {
        ResponseEntity<ApiError> response = handler.handleBookNotFound(new BookNotFoundException("9780306406157"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("Not Found");
        assertThat(response.getBody().message()).contains("9780306406157");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("UserNotFoundException se traduce a 404 Not Found")
    void userNotFound_devuelve404() {
        ResponseEntity<ApiError> response = handler.handleUserNotFound(new UserNotFoundException(42L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).contains("42");
    }

    // ---------------------------------------------------------------------
    // Conflict
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("BookAlreadyExistsException se traduce a 409 Conflict")
    void bookAlreadyExists_devuelve409() {
        ResponseEntity<ApiError> response =
                handler.handleBookAlreadyExists(new BookAlreadyExistsException("9780306406157"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("Conflict");
    }

    @Test
    @DisplayName("NoAvailableCopiesException se traduce a 409, no a 404")
    void noAvailableCopies_devuelve409YNo404() {
        ResponseEntity<ApiError> response =
                handler.handleNoAvailableCopies(new NoAvailableCopiesException("9780306406157"));

        // El libro existe: es un conflicto con su estado actual, no un 404.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("9780306406157");
    }

    @Test
    @DisplayName("La excepcion sin ejemplares expone el isbn para que el cliente lo pueda usar")
    void noAvailableCopies_exponeElIsbn() {
        NoAvailableCopiesException ex = new NoAvailableCopiesException("9780306406157");

        assertThat(ex.getIsbn()).isEqualTo("9780306406157");
    }

    @Test
    @DisplayName("UserNotActiveException se traduce a 409, no a 404")
    void userNotActive_devuelve409YNo404() {
        ResponseEntity<ApiError> response =
                handler.handleUserNotActive(new UserNotActiveException(42L, UserStatus.SUSPENDED));

        // El usuario existe: lo unico que choca es que esta en estado SUSPENDED.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("42").contains("SUSPENDED");
    }

    @Test
    @DisplayName("Un id no numerico en la ruta se traduce a 400, no a 500")
    void typeMismatch_devuelve400YNo500() throws Exception {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "no-es-un-numero", Long.class, "id", loanIdParameter(),
                new NumberFormatException("For input string: \"no-es-un-numero\""));

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(ex);

        // En Spring Framework 7 MethodArgumentTypeMismatchException hereda de
        // TypeMismatchException y ya NO implementa ErrorResponse. Sin este handler
        // caia en handleUnexpected y devolvia 500 por un fallo del cliente, que es
        // justo lo que un cliente no puede diagnosticar.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        // El mensaje nombra el parametro culpable para que el cliente sepa que
        // corregir, sin filtrar nombres de clases Java.
        assertThat(response.getBody().message()).contains("id");
    }

    @Test
    @DisplayName("LoanAlreadyLoanedException se traduce a 409, no a 404")
    void loanAlreadyLoaned_devuelve409YNo404() {
        ResponseEntity<ApiError> response =
                handler.handleLoanAlreadyLoaned(new LoanAlreadyLoanedException(42L, "9780306406157"));

        // Usuario y libro existen; el conflicto es que ya hay un prestamo abierto.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("9780306406157");
    }

    @Test
    @DisplayName("LoanNotFoundException se traduce a 404 Not Found")
    void loanNotFound_devuelve404() {
        ResponseEntity<ApiError> response = handler.handleLoanNotFound(new LoanNotFoundException(7L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).contains("7");
    }

    @Test
    @DisplayName("LoanAlreadyReturnedException se traduce a 409, no a 404 ni a un 200 silencioso")
    void loanAlreadyReturned_devuelve409() {
        ResponseEntity<ApiError> response =
                handler.handleLoanAlreadyReturned(new LoanAlreadyReturnedException(7L));

        // El prestamo existe y esta cerrado: es un conflicto con su estado, no un
        // recurso inexistente. Y el 409 es lo que evita que un reintento del cliente
        // se tome por un segundo ejemplar devuelto.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("7");
    }

    @Test
    @DisplayName("LoanLimitExceededException se traduce a 409 e informa del limite")
    void loanLimitExceeded_devuelve409() {
        ResponseEntity<ApiError> response =
                handler.handleLoanLimitExceeded(new LoanLimitExceededException(42L, 3));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("42").contains("3");
    }

    @Test
    @DisplayName("LoanNotRenewableException se traduce a 409 y explica el motivo")
    void loanNotRenewable_devuelve409() {
        ResponseEntity<ApiError> porVencimiento =
                handler.handleLoanNotRenewable(LoanNotRenewableException.porVencimiento(7L));
        ResponseEntity<ApiError> porLimite =
                handler.handleLoanNotRenewable(LoanNotRenewableException.porLimiteDeRenovaciones(7L, 2));

        assertThat(porVencimiento.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Los dos motivos comparten codigo pero no mensaje: el cliente necesita
        // distinguir "devuelvelo ya" de "no hay mas renovaciones".
        assertThat(porVencimiento.getBody().message()).contains("vencido");
        assertThat(porLimite.getBody().message()).contains("2");
    }

    @Test
    @DisplayName("Un cuerpo con un enum invalido se traduce a 400, no a 500")
    void cuerpoConEnumInvalido_devuelve400YNo500() {
        HttpMessageNotReadableException ex = cuerpoNoInterpretable();

        ResponseEntity<ApiError> response = handler.handleUnreadableBody(ex);

        // HttpMessageNotReadableException NO implementa ErrorResponse, asi que sin
        // este handler caia en handleUnexpected y devolvia 500 por un fallo del
        // cliente.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    @DisplayName("El error de deserializacion no filtra nombres de clases Java al cliente")
    void cuerpoConEnumInvalido_noFiltraNombresDeClases() {
        HttpMessageNotReadableException ex = cuerpoNoInterpretable();

        ResponseEntity<ApiError> response = handler.handleUnreadableBody(ex);

        assertThat(response.getBody().message())
                .doesNotContain("library_api")
                .doesNotContain("UserStatus")
                .doesNotContain("Cannot deserialize");
    }

    // ---------------------------------------------------------------------
    // Validacion (400)
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("Un cuerpo invalido se traduce a 400 con un error por campo")
    void cuerpoInvalido_devuelve400ConErroresPorCampo() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new BookRequest(null, null, null, null, null), "bookRequest");
        bindingResult.addError(new FieldError("bookRequest", "isbn", "no puede estar vacio"));
        bindingResult.addError(new FieldError("bookRequest", "title", "es obligatorio"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(bookRequestParameter(), bindingResult);

        ResponseEntity<ApiError> response = handler.handleInvalidBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("La peticion contiene datos invalidos");
        assertThat(response.getBody().fieldErrors())
                .containsEntry("isbn", "no puede estar vacio")
                .containsEntry("title", "es obligatorio");
    }

    @Test
    @DisplayName("Con dos fallos en el mismo campo se conserva el primero")
    void cuerpoInvalido_conVariosFallosPorCampo_conservaElPrimero() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new BookRequest(null, null, null, null, null), "bookRequest");
        // putIfAbsent: el primer mensaje registrado es el que sobrevive, de modo
        // que el mapa no se sobrescribe con cada anotacion que falle.
        bindingResult.addError(new FieldError("bookRequest", "isbn", "primero"));
        bindingResult.addError(new FieldError("bookRequest", "isbn", "segundo"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(bookRequestParameter(), bindingResult);

        ResponseEntity<ApiError> response = handler.handleInvalidBody(ex);

        assertThat(response.getBody().fieldErrors()).containsExactly(Map.entry("isbn", "primero"));
    }

    @Test
    @DisplayName("Un path variable invalido se traduce a 400 con el mensaje de Spring")
    void parametroDeMetodoInvalido_devuelve400() {
        // HandlerMethodValidationException solo aporta su getMessage() aqui, y
        // construirla de verdad exige un MethodParameter con anotaciones mas un
        // ParameterValidationResult de siete argumentos. El mock aísla lo que el
        // handler hace: copiar ese mensaje a un 400.
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getMessage()).thenReturn("Validation failed for argument 0");

        ResponseEntity<ApiError> response = handler.handleInvalidMethodArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Validation failed for argument 0");
    }

    @Test
    @DisplayName("Los errores de campo de un HandlerMethodValidationException llegan al cliente")
    void parametroDeMetodoInvalido_extraeLosErroresPorCampo() throws Exception {
        // Esta es la trampa de Spring 6.1+: cuando el metodo tiene AL MENOS UNA
        // restriccion propia (el @Positive del id de ruta en
        // UserController.updateUserStatus), la validacion del @Valid @RequestBody
        // tambien se canaliza por HandlerMethodValidationException en vez de por
        // MethodArgumentNotValidException. Si el handler solo copia ex.getMessage(),
        // el 400 llega con fieldErrors VACIO y el cliente no tiene ni idea de que
        // campo esta mal. Este test lo fija.
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                new BookRequest(null, null, null, null, null), "bookRequest");
        bindingResult.addError(new FieldError("bookRequest", "status", "el status es obligatorio"));

        ParameterErrors beanErrors = new ParameterErrors(
                bookRequestParameter(), new BookRequest(null, null, null, null, null),
                bindingResult, null, null, null);

        HandlerMethodValidationException ex = new HandlerMethodValidationException(
                MethodValidationResult.create(
                        this, endpointDeEjemploMethod(), List.of(beanErrors)));

        ResponseEntity<ApiError> response = handler.handleInvalidMethodArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors())
                .containsExactly(Map.entry("status", "el status es obligatorio"));
        // Y el mensaje deja de ser el criptico "400 BAD_REQUEST ...".
        assertThat(response.getBody().message()).isEqualTo("La peticion contiene datos invalidos");
    }

    @Test
    @DisplayName("Una ConstraintViolationException se traduce a 400")
    void constraintViolation_devuelve400() {
        ResponseEntity<ApiError> response =
                handler.handleConstraintViolation(new ConstraintViolationException("dato invalido", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("dato invalido");
    }

    // ---------------------------------------------------------------------
    // Red de seguridad (500)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Una excepcion inesperada no filtra su mensaje al cliente")
    void excepcionInesperada_noFiltraElMensaje() {
        ResponseEntity<ApiError> response =
                handler.handleUnexpected(new RuntimeException("password=secreto, jdbc:mysql://host:3306"));

        // El detalle va al log, nunca al cuerpo de la respuesta.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Ocurrio un error inesperado");
        assertThat(response.getBody().message()).doesNotContain("secreto", "jdbc");
    }

    @Test
    @DisplayName("Una excepcion de Spring conserva su status original en vez de volverse un 500")
    void excepcionDeSpring_conservaSuStatus() {
        ErrorResponseException ex = new ErrorResponseException(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<ApiError> response = handler.handleUnexpected(ex);

        // La red de seguridad no debe degradar los errores propios de Spring
        // (404 de ruta, 405, 415...) a un 500.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().status()).isEqualTo(405);
    }

    /**
     * Excepcion de Jackson con el mensaje que Spring produce al no poder convertir
     * un enum. El HttpInputMessage va a null porque el handler no lo consulta: solo
     * lo lee el mensaje. Spring Framework 7 ya no ofrece el constructor de un solo
     * argumento, asi que el segundo es obligatorio.
     */
    private static HttpMessageNotReadableException cuerpoNoInterpretable() {
        return new HttpMessageNotReadableException(
                "Cannot deserialize value of type `library_api.entity.UserStatus` "
                        + "from String \"PENDIENTE\"", null);
    }

    private static MethodParameter bookRequestParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("endpointDeEjemplo", BookRequest.class);
        return new MethodParameter(method, 0);
    }

    private static Method endpointDeEjemploMethod() throws NoSuchMethodException {
        return GlobalExceptionHandlerTest.class.getDeclaredMethod("endpointDeEjemplo", BookRequest.class);
    }

    /**
     * MethodParameter del id de ruta de LoanController.getLoan. El constructor de
     * MethodArgumentTypeMismatchException exige el MethodParameter, y el handler solo
     * usa ex.getName(), asi que puede ser cualquiera: lo unico que importa es que
     * getName() devuelva "id".
     */
    private static MethodParameter loanIdParameter() throws NoSuchMethodException {
        Method method = LoanController.class.getMethod("getLoan", Long.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void endpointDeEjemplo(BookRequest request) {
        // Su unico proposito es dar un MethodParameter con anotaciones al que
        // asociar los errores de binding en los tests.
    }
}
