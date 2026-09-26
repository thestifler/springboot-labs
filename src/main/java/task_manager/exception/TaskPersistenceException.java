package task_manager.exception;

public class TaskPersistenceException extends RuntimeException {
    public TaskPersistenceException(String message) {
        super(message);
    }

    public TaskPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
