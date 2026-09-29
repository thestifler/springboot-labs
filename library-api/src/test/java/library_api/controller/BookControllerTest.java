package library_api.controller;

import library_api.dto.BookResponse;
import library_api.exception.BookAlreadyExistsException;
import library_api.exception.BookNotFoundException;
import library_api.service.BookService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de slice web: solo se carga la capa web, el service va simulado.
 *
 * Verifican el contrato HTTP de /library/books: 200 al leer, 404 si el isbn no
 * existe, 409 al dar de alta un isbn repetido y 400 con el detalle por campo en
 * las peticiones invalidas.
 */
@WebMvcTest(BookController.class)
class BookControllerTest {

    private static final String ISBN = "9780306406157";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @Test
    @DisplayName("GET /library/books/{isbn} responde 200 con el libro")
    void getBookByIsbn_devuelve200ConElLibro() throws Exception {
        when(bookService.getBookByIsbn(ISBN)).thenReturn(
                new BookResponse(ISBN, "Ursula K. Le Guin", "The Left Hand of Darkness",
                        java.time.LocalDate.of(1997, 3, 3), 4));

        mockMvc.perform(get("/library/books/{isbn}", ISBN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(ISBN))
                .andExpect(jsonPath("$.author").value("Ursula K. Le Guin"))
                .andExpect(jsonPath("$.title").value("The Left Hand of Darkness"))
                .andExpect(jsonPath("$.publicationDate").value("1997-03-03"))
                .andExpect(jsonPath("$.avaliableCopyNumber").value(4));
    }

    @Test
    @DisplayName("GET responde 404 cuando el isbn no existe")
    void getBookByIsbn_cuandoNoExiste_devuelve404() throws Exception {
        when(bookService.getBookByIsbn(ISBN)).thenThrow(new BookNotFoundException(ISBN));

        mockMvc.perform(get("/library/books/{isbn}", ISBN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No existe un libro con isbn: " + ISBN));
    }

    @Test
    @DisplayName("GET con un isbn que no son 13 digitos responde 400 sin llegar al service")
    void getBookByIsbn_conFormatoInvalido_devuelve400() throws Exception {
        mockMvc.perform(get("/library/books/{isbn}", "no-es-un-isbn"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // El formato se descarta en el borde: no se lanza una consulta imposible.
        verifyNoInteractions(bookService);
    }

    @Test
    @DisplayName("POST /library/books responde 201 con Location y cuerpo")
    void addBook_devuelve201ConLocationYBody() throws Exception {
        when(bookService.addBook(any())).thenReturn(
                new BookResponse(ISBN, "Robert C. Martin", "Clean Code", java.time.LocalDate.of(2008, 8, 1), 7));

        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9780132350884",
                                  "author": "Robert C. Martin",
                                  "title": "Clean Code",
                                  "publicationDate": "2008-08-01",
                                  "avaliableCopyNumber": 7
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/library/books/" + ISBN))
                .andExpect(jsonPath("$.isbn").value(ISBN))
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.publicationDate").value("2008-08-01"))
                .andExpect(jsonPath("$.avaliableCopyNumber").value(7));
    }

    @Test
    @DisplayName("POST con un ISBN-13 de digito de control incorrecto responde 400")
    void addBook_conIsbnInvalido_devuelve400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9780306406158",
                                  "author": "Ursula K. Le Guin",
                                  "title": "The Left Hand of Darkness"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.isbn").exists());

        verifyNoInteractions(bookService);
    }

    @Test
    @DisplayName("POST con campos obligatorios vacios responde 400 con el detalle por campo")
    void addBook_conCamposInvalidos_devuelve400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "  ",
                                  "author": "",
                                  "title": "   ",
                                  "avaliableCopyNumber": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.isbn").exists())
                .andExpect(jsonPath("$.fieldErrors.author").exists())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.avaliableCopyNumber").exists());

        verifyNoInteractions(bookService);
    }

    @Test
    @DisplayName("POST con fecha de publicacion futura responde 400")
    void addBook_conFechaFutura_devuelve400() throws Exception {
        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9780132350884",
                                  "author": "Robert C. Martin",
                                  "title": "Clean Code",
                                  "publicationDate": "2999-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.publicationDate").exists());

        verifyNoInteractions(bookService);
    }

    @Test
    @DisplayName("POST sin avaliableCopyNumber responde 400 y no un 500 de deserializacion")
    void addBook_sinNumeroDeCopias_devuelve400() throws Exception {
        // Regresion: con un long primitivo en el DTO, Jackson lanzaba
        // MismatchedInputException al deserializar y la peticion terminaba en un
        // 500 antes de llegar a la validacion.
        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9780132350884",
                                  "author": "Robert C. Martin",
                                  "title": "Clean Code"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.avaliableCopyNumber").exists());

        verifyNoInteractions(bookService);
    }

    @Test
    @DisplayName("POST de un isbn ya registrado responde 409")
    void addBook_cuandoElIsbnYaExiste_devuelve409() throws Exception {
        when(bookService.addBook(any())).thenThrow(new BookAlreadyExistsException(ISBN));

        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isbn": "9780132350884",
                                  "author": "Robert C. Martin",
                                  "title": "Clean Code",
                                  "avaliableCopyNumber": 7
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Ya existe un libro con isbn: " + ISBN));
    }

    @Test
    @DisplayName("GET no propaga al service el isbn con formato invalido")
    void getBookByIsbn_noLlamaAlServiceConFormatoInvalido() throws Exception {
        mockMvc.perform(get("/library/books/{isbn}", "123"))
                .andExpect(status().isBadRequest());

        verify(bookService, never()).getBookByIsbn(eq("123"));
    }
}
