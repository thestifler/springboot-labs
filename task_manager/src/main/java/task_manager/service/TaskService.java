package task_manager.service;


import task_manager.controller.model.TaskEntity;

public interface TaskService {

    TaskEntity  getTaskById(Long taskId);
    TaskEntity createNewTask(TaskEntity task);
    TaskEntity updateTaskDescription(TaskEntity task);
}
