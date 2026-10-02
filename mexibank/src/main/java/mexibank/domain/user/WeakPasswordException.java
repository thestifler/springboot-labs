package mexibank.domain.user;

/**
 * La contrasena no cumple la politica de seguridad.
 *
 * <p><strong>Por que la politica vive en el dominio.</strong> Longitud minima,
 * presencia de digitos o de mayusculas son reglas del banco, no del protocolo
 * HTTP. Si las comprobara el adaptador REST, un usuario creado por consola o por
 * una importacion se saltaria la regla, y existirian dos caminos con politicas
 * distintas.
 *
 * <p><strong>Por que el mensaje no dice que falta.</strong> Revelar que solo
 * falta "una mayuscula" ayuda a quien hace pruebas automatizadas a superar la
 * comprobacion letra a letra. El detalle va al log; la respuesta dice que la
 * contrasena no cumple la politica.
 */
public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException() {
        super("La contrasena no cumple la politica de seguridad");
    }
}
