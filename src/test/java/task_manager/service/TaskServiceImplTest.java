package task_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import task_manager.controller.model.TaskEntity;
import task_manager.exception.InvalidTaskException;
import task_manager.exception.TaskNotFoundException;
import task_manager.exception.TaskPersistenceException;
import task_manager.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
public class TaskServiceImplTest {

    private TaskServiceImpl taskService;
    @Mock
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(taskRepository);
    }

    @Test
    @DisplayName("Debe retornar un TaskEntity válido con los datos simulados cuando se consulta por ID")
    void ShouldReturnTaskEntityWithCorrectData() {
        // 1. Arrange (Preparar los datos simulados y el comportamiento del mock)
        Long taskId = 10L;
        TaskEntity mockTask = new TaskEntity();
        mockTask.setId(taskId);
        mockTask.setDescription("Create the project Controller");
        mockTask.setStatus("INPROGRESS");

        // Le decimos al mock qué devolver cuando se llame a findById
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(mockTask));

        // 2. Act
        TaskEntity result = taskService.getTaskById(taskId);

        // 3. Assert
        assertEquals(taskId, result.getId(), "El ID debe coincidir con el solicitado");
        assertEquals("Create the project Controller", result.getDescription());
        assertEquals("INPROGRESS", result.getStatus());
    }

    @Test
    @DisplayName("Fallo: Lanza ResourceNotFoundException cuando el ID no existe en la BD")
    void getTaskById_WhenIdDoesNotExist_ThrowsException() {
        // Arrange
        Long nonExistentId = 999L;
        // Simulamos que la BD no encontró nada
        when(taskRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            taskService.getTaskById(nonExistentId);
        });
        verify(taskRepository, times(1)).findById(nonExistentId);
    }

    @Test
    @DisplayName("Debe retornar el ID generado cuando la tarea se crea correctamente")
    void createNewTask_WhenTaskIsValid_ReturnsGeneratedId() {
        // 1. Arrange
        TaskEntity newTask = new TaskEntity();
        newTask.setDescription("Create the Service Test");
        newTask.setStatus("INPROGRESS");

        TaskEntity savedTask = new TaskEntity();
        savedTask.setId(6L);
        savedTask.setDescription("Create the Service Test");
        savedTask.setStatus("INPROGRESS");

        when(taskRepository.save(newTask)).thenReturn(savedTask);

        // 2. Act
        TaskEntity result = taskService.createNewTask(newTask);

        // 3. Assert
        assertNotNull(result, "El ID no debe ser null");
        assertEquals(6L, result.getId(), "Debe retornar el ID generado por la BD");
        verify(taskRepository, times(1)).save(newTask);
    }

    @Test
    @DisplayName("Fallo: Lanza InvalidTaskException y NO persiste cuando la tarea es null")
    void createNewTask_WhenTaskIsNull_ThrowsExceptionAndDoesNotPersist() {
        // Arrange
        TaskEntity nullTask = null;

        // Act & Assert
        assertThrows(InvalidTaskException.class, () -> {
            taskService.createNewTask(nullTask);
        });

        // Verificación clave: no debe haberse intentado guardar
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fallo: Lanza TaskPersistenceException cuando el repositorio no devuelve la tarea guardada")
    void createNewTask_WhenRepositoryReturnsNull_ThrowsException() {
        // Arrange
        TaskEntity newTask = new TaskEntity();
        newTask.setDescription("Create the Service Test");

        when(taskRepository.save(newTask)).thenReturn(null);

        // Act & Assert
        assertThrows(TaskPersistenceException.class, () -> {
            taskService.createNewTask(newTask);
        });
    }
}
