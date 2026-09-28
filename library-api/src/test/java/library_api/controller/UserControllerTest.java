package library_api.controller;

import library_api.dto.UserResponse;
import library_api.entity.UserStatus;
import library_api.exception.UserNotFoundException;
import library_api.service.UserService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
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
 * Verifican el contrato HTTP, en especial que un usuario inexistente devuelve
 * 404 y no 500, que era el fallo del orElseThrow() sin supplier.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

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
}
