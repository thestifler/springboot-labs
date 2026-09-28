package task_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import task_manager.controller.model.TaskEntity;

public interface TaskRepository extends JpaRepository<TaskEntity,Long> {

}
