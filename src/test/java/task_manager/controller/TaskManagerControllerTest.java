package task_manager.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import task_manager.controller.model.TaskEntity;
import task_manager.exception.ApiResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TaskManagerControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper; // Inyecta ObjectMapper para convertir JSON a Java

        @Test
        void getTaskById_ShouldReturnNotFound_WhenTaskDoesNotExist() throws Exception {

                MvcResult result = mockMvc.perform(get("/task/999"))
                                .andExpect(status().isNotFound())
                                .andReturn();

                assertNotNull(result);

        }

        @Test
        @Sql(scripts = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        void ShouldReturnTaskWhenTaskExists() throws Exception {

                // 1. Ejecutar la petición y capturar el resultado
                MvcResult result = mockMvc.perform(get("/task/1"))
                                .andExpect(status().isOk()) // Mantenemos la aserción HTTP aquí por simplicidad
                                .andReturn();

                // 2. Extraer el cuerpo de la respuesta como String (JSON)
                String jsonResponse = result.getResponse().getContentAsString();

                // 3. Convertir el JSON a tu entidad TaskEntity
                ApiResponse<TaskEntity> response = objectMapper.readValue(jsonResponse,
                                new TypeReference<ApiResponse<TaskEntity>>() {
                                });
                TaskEntity task = response.getData();

                // 4. Usar asserts tradicionales
                assertNotNull(task);
                assertEquals(1L, task.getId());
                assertEquals("Create the Controller Test", task.getDescription());
                assertEquals("INPROGRESS", task.getStatus());
                assertNotNull(task.getCreateAt());
        }

        @Test
        @Sql(scripts = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
        void createNewTask_WhenBodyIsValid_ReturnsCreatedTask() throws Exception {

                // 1. Arrange
                TaskEntity newTask = new TaskEntity();
                newTask.setDescription("Create the POST endpoint");
                newTask.setStatus("INPROGRESS");

                String jsonBody = objectMapper.writeValueAsString(newTask);

                // 2. Act
                MvcResult result = mockMvc.perform(post("/task")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody))
                                .andExpect(status().isCreated())
                                .andExpect(header().exists("Location"))
                                .andReturn();

                ApiResponse<TaskEntity> response = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<TaskEntity>>() {
                                });
                TaskEntity created = response.getData();

                assertNotNull(created);
                assertNotNull(created.getId());
                assertEquals(6L, created.getId());
                assertEquals("Create the POST endpoint", created.getDescription());
                assertEquals("INPROGRESS", created.getStatus());
                assertNotNull(created.getCreateAt());
                assertNotNull(created.getUpdateAt());

                // 4. Verificar que realmente se persistió
                MvcResult check = mockMvc.perform(get("/task/6"))
                                .andExpect(status().isOk())
                                .andReturn();

                ApiResponse<TaskEntity> checkResponse = objectMapper.readValue(
                                check.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<TaskEntity>>() {
                                });

                TaskEntity persisted = checkResponse.getData();

                assertNotNull(persisted);

                assertEquals("Create the POST endpoint", persisted.getDescription());
        }

        @Test
        void createNewTask_WhenJsonIsMalformed_ReturnsBadRequest() throws Exception {

                mockMvc.perform(post("/task")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"description\": "))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createNewTask_WhenBodyIsEmpty_ReturnsBadRequest() throws Exception {

                mockMvc.perform(post("/task")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void createNewTask_WhenDescriptionIsMissing_ReturnsBadRequest() throws Exception {

                mockMvc.perform(post("/task")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"INPROGRESS\"}"))
                                .andExpect(status().isBadRequest());
        }
}