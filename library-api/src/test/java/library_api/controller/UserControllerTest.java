package library_api.controller;

import library_api.dto.LoanResponse;
import library_api.dto.UserResponse;
import library_api.dto.UserStatusRequest;
import library_api.entity.Loan;
import library_api.entity.UserStatus;
import library_api.exception.UserNotFoundException;
import library_api.service.LoanService;
import library_api.service.UserService;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de slice web: solo se carga la capa web, el service va simulado.
 *
 * Verifican el contrato HTTP, en especial que un usuario inexistente devuelve
 * 404 y no 500, que era el fallo del orElseThrow() sin supplier.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    /**
     * UserController expone tambien el historial de prestamos, asi que necesita
     * LoanService. Spring exige el bean o el contexto del slice no arranca, y el
     * fallo apareceria como un error de arranque en lugar de un test rojo claro.
     */
    @MockitoBean
    private LoanService loanService;

    @Test
    @DisplayName("GET /library/users/{id} responde 200 con el usuario")
    void getUser_devuelve200ConElUsuario() throws Exception {
        when(userService.getUser(1L))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));

        mockMvc.perform(get("/library/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.firstLastName").value("Gomez"))
                .andExpect(jsonPath("$.secondLastName").value("Ruiz"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /library/users/{id} responde 404 cuando no existe")
    void getUser_cuandoNoExiste_devuelve404() throws Exception {
        when(userService.getUser(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/library/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No existe un usuario con id: 99"));
    }

    @Test
    @DisplayName("GET con id no positivo responde 400")
    void getUser_conIdNoPositivo_devuelve400() throws Exception {
        mockMvc.perform(get("/library/users/0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("POST /library/users responde 201 con Location y cuerpo")
    void createUser_devuelve201ConLocationYBody() throws Exception {
        when(userService.createUser(any()))
                .thenReturn(new UserResponse(10L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));

        mockMvc.perform(post("/library/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ana",
                                  "firstLastName": "Gomez",
                                  "secondLastName": "Ruiz"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/library/users/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST con campos invalidos responde 400 con el detalle por campo")
    void createUser_conCamposInvalidos_devuelve400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/library/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "firstLastName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.firstLastName").exists());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("POST ignora un id enviado por el cliente")
    void createUser_conIdEnElBody_loDescarta() throws Exception {
        when(userService.createUser(any()))
                .thenReturn(new UserResponse(10L, "Ana", "Gomez", null, UserStatus.ACTIVE));

        mockMvc.perform(post("/library/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 999,
                                  "name": "Ana",
                                  "firstLastName": "Gomez"
                                }
                                """))
                .andExpect(status().isCreated());

        // El DTO de entrada ya no tiene componente id, asi que Jackson lo ignora
        // por defecto y el service nunca recibe un id del cliente.
        verify(userService).createUser(any());
    }

    // ---------------------------------------------------------------------
    // PATCH /library/users/{id}/status
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("PATCH del estado responde 200 con el usuario ya actualizado")
    void updateStatus_devuelve200ConElNuevoEstado() throws Exception {
        when(userService.updateUserStatus(eq(1L), any()))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.SUSPENDED));

        mockMvc.perform(patch("/library/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SUSPENDED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("PATCH del estado propaga el id de la ruta, no el del cuerpo")
    void updateStatus_usaElIdDeLaRuta() throws Exception {
        when(userService.updateUserStatus(eq(1L), any()))
                .thenReturn(new UserResponse(1L, "Ana", "Gomez", "Ruiz", UserStatus.ACTIVE));

        mockMvc.perform(patch("/library/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 999,
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UserStatusRequest> captor = ArgumentCaptor.forClass(UserStatusRequest.class);
        verify(userService).updateUserStatus(eq(1L), captor.capture());
        // El cuerpo no tiene componente id, asi que el unico id posible es el de la
        // ruta: un attacker no puede cambiar el estado de otro usuario.
        assertThat(captor.getValue().status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    @DisplayName("PATCH sin estado responde 400 con el detalle por campo")
    void updateStatus_sinEstado_devuelve400() throws Exception {
        mockMvc.perform(patch("/library/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH con un estado inexistente responde 400, no 500")
    void updateStatus_conEstadoInexistente_devuelve400() throws Exception {
        // El enum es la barrera: Jackson falla al deserializar un valor que no
        // existe, y Spring lo traduce a 400 en lugar de dejarlo escapar como 500.
        mockMvc.perform(patch("/library/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PENDIENTE_DE_PAGO"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH del estado de un usuario inexistente responde 404")
    void updateStatus_usuarioInexistente_devuelve404() throws Exception {
        when(userService.updateUserStatus(eq(99L), any()))
                .thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(patch("/library/users/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe un usuario con id: 99"));
    }

    @Test
    @DisplayName("PATCH con id no positivo responde 400 y no llega al service")
    void updateStatus_conIdNoPositivo_devuelve400() throws Exception {
        mockMvc.perform(patch("/library/users/0/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    // ---------------------------------------------------------------------
    // GET /library/users/{id}/loans
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET del historial responde 200 con la lista de prestamos")
    void getUserLoans_devuelve200ConLaLista() throws Exception {
        when(loanService.getUserLoans(1L)).thenReturn(List.of(
                prestamo(2L, false),
                prestamo(1L, true)));

        mockMvc.perform(get("/library/users/1/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].isbn").value("9780306406157"))
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].overdue").value(false))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[1].returnedDate").value("2026-03-10"));
    }

    @Test
    @DisplayName("GET del historial de un usuario sin prestamos responde 200 con lista vacia")
    void getUserLoans_sinPrestamos_devuelveListaVacia() throws Exception {
        when(loanService.getUserLoans(1L)).thenReturn(List.of());

        mockMvc.perform(get("/library/users/1/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET del historial de un usuario inexistente responde 404, no una lista vacia")
    void getUserLoans_usuarioInexistente_devuelve404() throws Exception {
        when(loanService.getUserLoans(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/library/users/99/loans"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe un usuario con id: 99"));
    }

    /**
     * LoanResponse de ejemplo. Los campos derivados (dueDate, overdue, daysOverdue)
     * van fijados a mano porque aqui no hay entidad de la que derivarlos.
     */
    private static LoanResponse prestamo(Long id, boolean devuelto) {
        return new LoanResponse(
                id,
                "9780306406157",
                1L,
                LocalDate.of(2026, 3, 1),
                Loan.DEFAULT_LOAN_DAYS,
                LocalDate.of(2026, 3, 15),
                devuelto ? LocalDate.of(2026, 3, 10) : null,
                0,
                false,
                0L);
    }
}
