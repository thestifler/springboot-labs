package library_api.controller;

import library_api.dto.LoanRequest;
import library_api.entity.Book;
import library_api.entity.Loan;
import library_api.entity.User;
import library_api.entity.UserStatus;
import library_api.repository.BookRepository;
import library_api.repository.LoanRepository;
import library_api.repository.UserRepository;
import library_api.service.LoanService;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integracion del recurso de prestamos de extremo a extremo.
 *
 * Aqui no se simula nada: se levanta el contexto completo y se comprueba el
 * recorrido entero controller -> service -> mapper -> repository -> H2. Es la unica
 * forma de validar lo que los tests por capas no pueden ver:
 * <ul>
 *   <li>que el LoanMapper de MapStruct se registra como bean y serializa los campos
 *       DERIVADOS (dueDate, overdue, daysOverdue), que no son columnas;</li>
 *   <li>que las rutas /{id}/return, /{id}/renew y /overdue conviven con /{id} sin
 *       que Spring las confunda entre si;</li>
 *   <li>que los 404 y 409 llegan al cliente con el cuerpo de error estandar y no
 *       como un 500;</li>
 *   <li>que SUSPENDED, un estado al que solo se llega por el PATCH, bloquea de
 *       verdad un prestamo posterior: es la prueba de que el endpoint existe para
 *       algo y no es decorativo.</li>
 * </ul>
 *
 * La anotacion @Transactional hace que cada test se ejecute en su propia
 * transaccion y se deshaga al terminar, dejando la base de datos como estaba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoanControllerIntegrationTest {

    private static final String ISBN = "9780306406157";
    private static final String ISBN_SEGUNDO = "9788491050469";
    private static final Long ID_INEXISTENTE = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User usuario;
    private Book libro;

    @BeforeEach
    void setUp() {
        usuario = userRepository.save(new User("Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));
        libro = bookRepository.save(
                new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 2));
    }

    private String cuerpoDePrestamo() {
        return """
                {
                  "isbn": "%s",
                  "userId": %d
                }
                """.formatted(ISBN, usuario.getId());
    }

    private String cuerpoDePrestamo(Long userId) {
        return """
                {
                  "isbn": "%s",
                  "userId": %d
                }
                """.formatted(ISBN, userId);
    }

    private String cuerpoDeEstado(String status) {
        return """
                {
                  "status": "%s"
                }
                """.formatted(status);
    }

    private Long prestarLibro() {
        return loanService.lendBook(new LoanRequest(ISBN, usuario.getId())).id();
    }

    /**
     * Deshace la sesion de persistencia para forzar una relectura real de la base
     * de datos. Sin esto, findById devolveria la entidad cacheada con los valores
     * viejo y los tests de persistencia pasarian aunque el UPDATE no se hubiera
     * escrito.
     */
    private void releerDeLaBaseDeDatos() {
        entityManager.flush();
        entityManager.clear();
    }

    // ---------------------------------------------------------------------
    // POST /library/loans
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Prestar un libro responde 201 con Location y cuerpo")
    void lendBook_devuelve201ConLocationYBody() throws Exception {
        MvcResult result = mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value(ISBN))
                .andExpect(jsonPath("$.userId").value(usuario.getId()))
                .andExpect(jsonPath("$.loanDays").value(Loan.DEFAULT_LOAN_DAYS))
                .andExpect(jsonPath("$.renewalCount").value(0))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.daysOverdue").value(0))
                // Jackson serializa los null, asi que el campo esta presente con
                // valor null en vez de desaparecer.
                .andExpect(jsonPath("$.returnedDate").value(nullValue()))
                .andReturn();

        // La cabecera Location debe apuntar al prestamo que realmente se guardo, no
        // a un id inventado. Se comprueba contra la fila de la base de datos.
        Loan guardado = loanRepository.findAll().get(0);
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/library/loans/" + guardado.getId());
        assertThat(guardado.getBook().getIsbn()).isEqualTo(ISBN);
    }

    @Test
    @DisplayName("La respuesta incluye dueDate aunque no exista esa columna")
    void lendBook_incluyeElVencimientoDerivado() throws Exception {
        // dueDate, overdue y daysOverdue los calcula Loan, no la base de datos. Si
        // aparecen en el JSON con el valor correcto es que el mapper funciona sobre
        // la entidad de verdad, no que haya una columna escondida.
        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.loanDays").value(Loan.DEFAULT_LOAN_DAYS))
                .andExpect(jsonPath("$.dueDate").value(
                        LocalDate.now().plusDays(Loan.DEFAULT_LOAN_DAYS).toString()))
                .andExpect(jsonPath("$.renewalCount").value(0))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.daysOverdue").value(0));
    }

    @Test
    @DisplayName("Prestar descuenta un ejemplar de verdad en la base de datos")
    void lendBook_descuentaElEjemplarEnLaBaseDeDatos() throws Exception {
        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isCreated());

        releerDeLaBaseDeDatos();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sin ejemplares responde 409 y no descuenta de mas")
    void lendBook_sinEjemplares_devuelve409() throws Exception {
        bookRepository.findById(ISBN).orElseThrow().setAvailableCopyNumber(0);

        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString(ISBN)));

        releerDeLaBaseDeDatos();
        assertThat(loanRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Prestar a un usuario inexistente responde 404")
    void lendBook_usuarioInexistente_devuelve404() throws Exception {
        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo(ID_INEXISTENTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("999999")));
    }

    @Test
    @DisplayName("Prestar un isbn inexistente responde 404")
    void lendBook_isbnInexistente_devuelve404() throws Exception {
        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "%s",
                                  "userId": %d
                                }
                                """.formatted(ISBN_SEGUNDO, usuario.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString(ISBN_SEGUNDO)));
    }

    @Test
    @DisplayName("Un cuerpo sin isbn responde 400 con el detalle por campo")
    void lendBook_sinIsbn_devuelve400() throws Exception {
        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d
                                }
                                """.formatted(usuario.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.isbn").exists());

        assertThat(loanRepository.findAll()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // GET /library/loans/{id}
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Consultar un prestamo responde 200 con sus datos")
    void getLoan_devuelve200() throws Exception {
        Long loanId = prestarLibro();

        mockMvc.perform(get("/library/loans/{id}", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(loanId))
                .andExpect(jsonPath("$.isbn").value(ISBN))
                .andExpect(jsonPath("$.userId").value(usuario.getId()));
    }

    @Test
    @DisplayName("Consultar un prestamo inexistente responde 404")
    void getLoan_inexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/library/loans/{id}", ID_INEXISTENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("999999")));
    }

    @Test
    @DisplayName("Un id no numerico en la ruta NO se resuelve contra la ruta /overdue")
    void getLoan_conIdNoNumerico_devuelve400() throws Exception {
        // Comprueba que el enrutado prefiere el literal /overdue al comodin /{id}: si
        // no, un id invalido acabaria atendido por el listado de vencidos.
        mockMvc.perform(get("/library/loans/{id}", "no-es-un-numero"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------
    // POST /library/loans/{id}/return
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Devolver un libro responde 200 y reingresa el ejemplar")
    void returnBook_devuelve200YReingresaElEjemplar() throws Exception {
        Long loanId = prestarLibro();

        mockMvc.perform(post("/library/loans/{id}/return", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(loanId))
                .andExpect(jsonPath("$.returnedDate").value(LocalDate.now().toString()))
                // Un libro devuelto no es un libro vencido: overdue sigue en false
                // aunque su plazo haya pasado.
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.daysOverdue").value(0));

        releerDeLaBaseDeDatos();
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
        assertThat(loanRepository.findById(loanId).orElseThrow().isReturned()).isTrue();
    }

    @Test
    @DisplayName("Devolver dos veces el mismo prestamo responde 409 y no suma ejemplares")
    void returnBook_dosVeces_devuelve409() throws Exception {
        Long loanId = prestarLibro();
        mockMvc.perform(post("/library/loans/{id}/return", loanId)).andExpect(status().isOk());

        mockMvc.perform(post("/library/loans/{id}/return", loanId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString(loanId.toString())));

        // El inventario no se infla: el motivo de que la API no sea idempotente.
        releerDeLaBaseDeDatos();
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Devolver un prestamo inexistente responde 404")
    void returnBook_inexistente_devuelve404() throws Exception {
        mockMvc.perform(post("/library/loans/{id}/return", ID_INEXISTENTE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tras devolver, el usuario puede volver a llevarse el mismo libro")
    void returnBook_luegoSePuedePrestarDeNuevo() throws Exception {
        Long loanId = prestarLibro();
        mockMvc.perform(post("/library/loans/{id}/return", loanId)).andExpect(status().isOk());

        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isCreated());

        releerDeLaBaseDeDatos();
        assertThat(loanRepository.count()).isEqualTo(2);
        assertThat(loanRepository.countByUser_IdAndReturnedDateIsNull(usuario.getId())).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // POST /library/loans/{id}/renew
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Renovar responde 200 con el plazo ampliado y la cuenta de renovaciones")
    void renewLoan_devuelve200ConElPlazoAmpliado() throws Exception {
        Long loanId = prestarLibro();

        mockMvc.perform(post("/library/loans/{id}/renew", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanDays").value(Loan.DEFAULT_LOAN_DAYS * 2))
                .andExpect(jsonPath("$.renewalCount").value(1));
    }

    @Test
    @DisplayName("Agotadas las renovaciones, la siguiente responde 409")
    void renewLoan_agotadasLasRenovaciones_devuelve409() throws Exception {
        Long loanId = prestarLibro();
        for (int i = 0; i < Loan.MAX_RENEWALS; i++) {
            mockMvc.perform(post("/library/loans/{id}/renew", loanId)).andExpect(status().isOk());
        }

        mockMvc.perform(post("/library/loans/{id}/renew", loanId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("renovaciones")));

        releerDeLaBaseDeDatos();
        assertThat(loanRepository.findById(loanId).orElseThrow().getRenewalCount())
                .isEqualTo(Loan.MAX_RENEWALS);
    }

    @Test
    @DisplayName("Renovar un prestamo devuelto responde 409")
    void renewLoan_prestamoDevuelto_devuelve409() throws Exception {
        Long loanId = prestarLibro();
        mockMvc.perform(post("/library/loans/{id}/return", loanId)).andExpect(status().isOk());

        mockMvc.perform(post("/library/loans/{id}/renew", loanId))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Renovar un prestamo inexistente responde 404")
    void renewLoan_inexistente_devuelve404() throws Exception {
        mockMvc.perform(post("/library/loans/{id}/renew", ID_INEXISTENTE))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------
    // GET /library/loans/overdue
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("El listado de vencidos responde 200, del mas retrasado al menos")
    void getOverdueLoans_devuelve200Ordenado() throws Exception {
        User otro = userRepository.save(new User("Luis", "Soto", "Paz", UserStatus.ACTIVE));
        Loan muyVencido = loanRepository.save(new Loan(
                libro, usuario, LocalDate.now().minusDays(30), Loan.DEFAULT_LOAN_DAYS));
        Loan pocoVencido = loanRepository.save(new Loan(
                libro, otro, LocalDate.now().minusDays(16), Loan.DEFAULT_LOAN_DAYS));

        mockMvc.perform(get("/library/loans/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(muyVencido.getId()))
                .andExpect(jsonPath("$[0].overdue").value(true))
                .andExpect(jsonPath("$[0].daysOverdue").value(16))
                .andExpect(jsonPath("$[1].id").value(pocoVencido.getId()))
                .andExpect(jsonPath("$[1].daysOverdue").value(2));
    }

    @Test
    @DisplayName("Sin prestamos vencidos el listado responde 200 con lista vacia")
    void getOverdueLoans_sinVencidos_devuelveListaVacia() throws Exception {
        prestarLibro();

        mockMvc.perform(get("/library/loans/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------------
    // PATCH /library/users/{id}/status y su efecto sobre los prestamos
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Suspender a un usuario le impide volver a pedir prestamos")
    void suspenderAlUsuario_bloqueaNuevosPrestamos() throws Exception {
        mockMvc.perform(patch("/library/users/{id}/status", usuario.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeEstado("SUSPENDED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isConflict())
                // El mensaje nombra el estado real, no un "inactivo o suspendido" que
                // no permitiria al cliente saber cual de los dos se rechazo.
                .andExpect(jsonPath("$.message").value(containsString("SUSPENDED")));

        releerDeLaBaseDeDatos();
        assertThat(loanRepository.findAll()).isEmpty();
        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Reactivar a un usuario le devuelve el acceso a los prestamos")
    void reactivarAlUsuario_restauraElAcceso() throws Exception {
        usuario.setStatus(UserStatus.INACTIVE);

        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/library/users/{id}/status", usuario.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeEstado("ACTIVE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/library/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDePrestamo()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("El estado nuevo se persiste: una consulta posterior lo ve cambiado")
    void elCambioDeEstadoSePersiste() throws Exception {
        mockMvc.perform(patch("/library/users/{id}/status", usuario.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeEstado("INACTIVE")))
                .andExpect(status().isOk());

        releerDeLaBaseDeDatos();

        assertThat(userRepository.findById(usuario.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("El historial del usuario aparece en /library/users/{id}/loans")
    void elHistorialSeExponeEnUsersLoans() throws Exception {
        bookRepository.save(new Book(ISBN_SEGUNDO, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 1));
        Long primero = prestarLibro();
        Long segundo = loanService.lendBook(new LoanRequest(ISBN_SEGUNDO, usuario.getId())).id();
        mockMvc.perform(post("/library/loans/{id}/return", primero)).andExpect(status().isOk());

        mockMvc.perform(get("/library/users/{id}/loans", usuario.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(segundo))
                .andExpect(jsonPath("$[1].id").value(primero))
                .andExpect(jsonPath("$[1].returnedDate").value(LocalDate.now().toString()));
    }

    @Test
    @DisplayName("El historial de un usuario inexistente responde 404, no una lista vacia")
    void elHistorialDeUnUsuarioInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/library/users/{id}/loans", ID_INEXISTENTE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("999999")));
    }
}
