package task_manager.exception;

public class ApiResponse<T> {

    private boolean success;
    private String message;
    private Integer status;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(String message, Integer status, T data) {
        this.success = status >= 200 && status < 300;
        this.message = message;
        this.status = status;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
