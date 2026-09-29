package library_api.controller;

import library_api.entity.Book;
import library_api.exception.BookNotFoundException;
import library_api.exception.NoAvailableCopiesException;
import library_api.repository.BookRepository;
import library_api.service.BookService;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integracion del recurso de libros de extremo a extremo.
 *
 * A diferencia de BookControllerTest, aqui no se simula nada: se levanta el
 * contexto completo y se comprueba el recorrido entero controller -> service ->
 * mapper -> repository -> H2. Es la unica forma de validar lo que los tests
 * unitarios no pueden ver: que MapStruct genera la implementacion que Spring
 * registra, que el isbn se persiste como clave primaria y que el esquema se crea
 * con los tipos correctos (por ejemplo, que publicationDate se guarda como
 * fecha y no como un java.util.Date).
 *
 * La anotacion @Transactional hace que cada test se ejecute en su propia
 * transaccion y se deshaga al terminar, dejando la base de datos como estaba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookControllerIntegrationTest {

    private static final String ISBN = "9780306406157";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookService bookService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Alta y posterior consulta de un libro: 201 y luego 200 con los mismos datos")
    void altaYConsulta_devuelveElLibroPersistido() throws Exception {
        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9780306406157",
                                  "author": "Ursula K. Le Guin",
                                  "title": "The Left Hand of Darkness",
                                  "publicationDate": "1997-03-03",
                                  "availableCopyNumber": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/library/books/9780306406157"))
                .andExpect(jsonPath("$.isbn").value(ISBN))
                .andExpect(jsonPath("$.publicationDate").value("1997-03-03"))
                .andExpect(jsonPath("$.availableCopyNumber").value(4));

        // El isbn es la clave primaria: debe haberse guardado como tal.
        assertThat(bookRepository.findById(ISBN)).isPresent();

        mockMvc.perform(get("/library/books/{isbn}", ISBN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(ISBN))
                .andExpect(jsonPath("$.author").value("Ursula K. Le Guin"))
                .andExpect(jsonPath("$.title").value("The Left Hand of Darkness"))
                .andExpect(jsonPath("$.publicationDate").value("1997-03-03"))
                .andExpect(jsonPath("$.availableCopyNumber").value(4));
    }

    @Test
    @DisplayName("El isbn se guarda tal cual, sin guiones ni espacios")
    void isbnSePersisteComoClavePrimaria() throws Exception {
        Book book = new Book("9788491050469", "Grupo Planeta", "El infinito en un junco", LocalDate.of(2020, 1, 15), 2);
        bookRepository.save(book);

        mockMvc.perform(get("/library/books/{isbn}", "9788491050469"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("9788491050469"))
                .andExpect(jsonPath("$.availableCopyNumber").value(2));
    }

    @Test
    @DisplayName("Un isbn ya dado de alta responde 409 y no se duplica la fila")
    void altaDeIsbnExistente_devuelve409() throws Exception {
        bookRepository.save(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 1));

        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9780306406157",
                                  "author": "Otro autor",
                                  "title": "Otro titulo",
                                  "availableCopyNumber": 9
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un libro con isbn: " + ISBN));

        // Sigue habiendo un unico libro y no se ha sobreescrito con el nuevo titulo.
        assertThat(bookRepository.count()).isEqualTo(1);
        assertThat(bookRepository.findById(ISBN).orElseThrow().getTitle()).isEqualTo("Titulo");
    }

    @Test
    @DisplayName("Consultar un isbn inexistente responde 404 con el cuerpo de error estandar")
    void consultaDeIsbnInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/library/books/{isbn}", "9788491050469"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No existe un libro con isbn: 9788491050469"));
    }

    // ---------------------------------------------------------------------
    // reserveCopy
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("La reserva de ejemplares se persiste de verdad en la base de datos")
    void reserveCopy_persisteElNuevoContador() {
        bookRepository.saveAndFlush(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 2));

        bookService.reserveCopy(ISBN);

        // flush y clear obligan a releer de la base de datos: si el contador no se
        // hubiera escrito, aqui seguiria viendo 2.
        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un libro agotado lanza NoAvailableCopiesException y conserva el contador en cero")
    void reserveCopy_sinEjemplares_lanzaNoAvailableCopiesException() {
        bookRepository.saveAndFlush(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 0));

        assertThatThrownBy(() -> bookService.reserveCopy(ISBN))
                .isInstanceOf(NoAvailableCopiesException.class)
                .hasMessageContaining(ISBN);

        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isZero();
    }

    @Test
    @DisplayName("Reservar un isbn inexistente propaga BookNotFoundException, no NoAvailableCopies")
    void reserveCopy_cuandoNoExiste_lanzaBookNotFoundException() {
        assertThatThrownBy(() -> bookService.reserveCopy("9788491050469"))
                .isInstanceOf(BookNotFoundException.class)
                .isNotInstanceOf(NoAvailableCopiesException.class)
                .hasMessageContaining("9788491050469");
    }

    // ---------------------------------------------------------------------
    // releaseCopy
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("La devolucion de ejemplares se persiste de verdad en la base de datos")
    void releaseCopy_persisteElNuevoContador() {
        bookRepository.saveAndFlush(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 1));

        bookService.releaseCopy(ISBN);

        // Sin el clear(), findById devolveria la entidad cacheada con el valor
        // viejo y el test pasaria aunque el UPDATE no se hubiera aplicado.
        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Devolver desde cero funciona: el contador llega a 1")
    void releaseCopy_desdeCero() {
        bookRepository.saveAndFlush(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 0));

        bookService.releaseCopy(ISBN);

        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Varias devoluciones consecutivas suman de una en una")
    void releaseCopy_variasVeces() {
        bookRepository.saveAndFlush(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 5));

        for (int i = 0; i < 3; i++) {
            bookService.releaseCopy(ISBN);
        }

        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(8);
    }

    @Test
    @DisplayName("Devolver un isbn inexistente lanza BookNotFoundException y no crea nada")
    void releaseCopy_cuandoNoExiste_lanzaBookNotFoundException() {
        assertThatThrownBy(() -> bookService.releaseCopy("9788491050469"))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessageContaining("9788491050469");

        // El UPDATE sobre un isbn inexistente no debe insertar una fila nueva.
        assertThat(bookRepository.existsById("9788491050469")).isFalse();
    }

    @Test
    @DisplayName("Reservar y devolver en alternancia deja el contador donde estaba")
    void reserveYRelease_enAlternancia_dejanElContadorIgual() {
        bookRepository.saveAndFlush(new Book(ISBN, "Autor", "Titulo", LocalDate.of(2000, 1, 1), 3));

        bookService.releaseCopy(ISBN); // 3 -> 4
        bookService.reserveCopy(ISBN);  // 4 -> 3

        entityManager.flush();
        entityManager.clear();

        assertThat(bookRepository.findById(ISBN).orElseThrow().getAvailableCopyNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("Un alta con datos invalidos no llega a la base de datos")
    void altaInvalida_noSePersiste() throws Exception {
        long antes = bookRepository.count();

        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "123",
                                  "author": "Autor",
                                  "title": "Titulo"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.isbn").exists());

        assertThat(bookRepository.count()).isEqualTo(antes);
    }
}
