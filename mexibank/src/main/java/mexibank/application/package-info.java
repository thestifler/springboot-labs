/**
 * Capa de aplicacion: los casos de uso.
 *
 * <p>Un caso de uso es una operacion completa desde el punto de vista de quien
 * la pide. Responde a "abrir una cuenta", no a "persistir una cuenta": el
 * primero tiene nombre de negocio, el segundo es un paso tecnico.
 *
 * <p><strong>Regla de dependencia:</strong> esta capa no conoce
 * {@code infrastructure}. Habla con el exterior unicamente a traves de los
 * puertos del dominio, que son interfaces. Si un caso de uso necesita algo que
 * aun no tiene puerto, la respuesta es <em>crear el puerto en el dominio</em>,
 * no importar el adaptador.
 *
 * <p><strong>Que hay aqui:</strong>
 * <ul>
 *   <li>Puertos de entrada: las interfaces {@code *UseCase}.</li>
 *   <li>Sus implementaciones {@code *UseCaseImpl}, unicas por caso de uso.</li>
 *   <li>Los commands (records) de entrada, sin anotaciones de validacion.</li>
 * </ul>
 *
 * <p><strong>Que hace y que no hace un caso de uso.</strong> Orquesta: carga
 * agregados por el puerto, se los pasa al dominio para que decida, guarda el
 * resultado y traduce una excepcion de dominio en otra de su mismo ambito si
 * hace falta. <strong>No decide</strong>: las reglas ("no se puede retirar de
 * una cuenta sin saldo") viven en el dominio. Un caso de uso con un
 * {@code if} de regla de negocio es dominio disfrazado, y el error sale caro:
 * la regla no se puede reutilizar ni probar sin Spring.
 *
 * <p><strong>Transacciones:</strong> {@code @Transactional} vive aqui y solo
 * aqui. Va en el metodo de escritura y en las lecturas. Es la unica pieza de
 * Spring que cruza hacia dentro, y es el precio consciente de cablear con
 * Spring en vez de con una configuracion de 40 metodos {@code @Bean}.
 */
package mexibank.application;
