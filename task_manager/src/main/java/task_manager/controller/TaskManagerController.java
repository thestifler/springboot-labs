package task_manager.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.validation.Valid;
import task_manager.controller.model.TaskEntity;
import task_manager.exception.ApiResponse;
import task_manager.service.TaskService;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/task")
public class TaskManagerController {

    private final TaskService taskService;

    public TaskManagerController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskEntity>> getTaskById(@PathVariable Long taskId) {
        TaskEntity task = taskService.getTaskById(taskId);
        return ResponseEntity.ok(new ApiResponse<>("Task found", 200, task));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskEntity>> createNewTask(@Valid  @RequestBody TaskEntity task,
            UriComponentsBuilder ucb) {
        TaskEntity created = taskService.createNewTask(task);

        URI location = ucb.path("/task/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location)
                .body(new ApiResponse<>("Task created", 201, created));
    }
}
