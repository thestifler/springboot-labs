package mexibank.domain.user;

/**
 * El usuario sabe que su contrasena, pero la contrasena que se le paso no es la
 * que tiene guardada.
 *
 * <p><strong>Por que es una excepcion y no un {@code boolean}.</strong> El caso
 * de uso que la captura tiene que poder distinguir tres situaciones: credenciales
 * correctas, contrasena incorrecta y usuario inexistente. Si el metodo devolviera
 * un {@code boolean}, las tres se fundirian en "no autenticado".
 *
 * <p><strong>Por que el mensaje es generico.</strong> En un login, decir
 * "ese correo no existe" o "la contrasena no coincide" permite a un atacante
 * enumerar las cuentas registradas probando correos y leyendo la respuesta. El
 * caso de uso recibe esta excepcion y responde siempre con el mismo mensaje. Por
 * eso el texto no distingue las dos causas: el detalle vive en el log del
 * servidor, no en la respuesta.
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    /**
     * Excepcion sin causa ni detalle: el mensaje es el mismo siempre, y el
     * servidor registra el motivo concreto.
     */
    public InvalidCurrentPasswordException() {
        super("La contrasena actual no es correcta");
    }
}
