package library_api.exception;

/**
 * Excepcion de dominio para "este prestamo ya estaba devuelto".
 *
 * Existe para que devolver dos veces el mismo libro NO sea silencioso. Como
 * markReturned() es idempotente, la entidad no falla por si sola; el servicio es
 * quien consulta el estado antes de actuar y lanza esto. La diferencia con el 404
 * de LoanNotFoundException es la que le importa al cliente: el prestamo existe,
 * de hecho esta cerrado (409), o no existe nunca (404).
 *
 * GlobalExceptionHandler la traduce a 409 Conflict. Es especialmente importante
 * que NO sea idempotente a nivel de API: si el cliente reintenta una devolucion
 * porque la respuesta anterior se perdio, no debe quedar guardado el mismo
 * ejemplar dos veces en el contador de disponibles. Por eso el servicio devuelve
 * un 409 en vez de un 200 silencioso, aunque la entidad si lo tolere.
 */
public class LoanAlreadyReturnedException extends RuntimeException {

    private final Long id;

    public LoanAlreadyReturnedException(Long id) {
        super("El prestamo con id: " + id + " ya estaba devuelto");
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
