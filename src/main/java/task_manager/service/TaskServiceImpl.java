package task_manager.service;


import org.springframework.stereotype.Service;

import task_manager.controller.model.TaskEntity;
import task_manager.exception.InvalidTaskException;
import task_manager.exception.TaskNotFoundException;
import task_manager.exception.TaskPersistenceException;
import task_manager.repository.TaskRepository;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public TaskEntity getTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task Not Found with ID: " + taskId));
    }

    @Override
    public TaskEntity createNewTask(TaskEntity task) {

        if (task == null) {
            throw new InvalidTaskException("The new Task can not be null, enter a valid task");
        }

        TaskEntity taskDb = taskRepository.save(task);
        if (taskDb == null) {
            throw new TaskPersistenceException("Task could not be created");
        }
        return taskDb;
    }

}
