package mexibank;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de carga del contexto.
 *
 * <p><strong>Por que NO extiende {@code AbstractPostgresIT}.strong> Este test
 * comprueba que el contexto de Spring se construye: el cableado de beans, las
 * clases mapeadas y los puertos. No necesita base de datos real, asi que
 * arrancarlo con un contenedor de PostgreSQL solo anadiria el coste de levantar
 * Docker al build de todos los dias. Los tests que si necesitan la base de datos
 * de verdad extienden {@code AbstractPostgresIT} y ahi si se levanta el
 * contenedor.
 *
 * <p>Es una distincion util porque separa dos preguntas distintas: "el contexto
 * es coherente" y "el SQL funciona contra PostgreSQL". Un solo test que
 * comprobara las dos cosas obligaria a tener Docker para todo.
 *
 * <p>Usa el perfil "test", que apunta a una base H2 en memoria. La base en
 * memoria basta para construir el contexto y es instantanea; los tests que
 * necesitan PostgreSQL de verdad lo overridean con Testcontainers.
 */
@SpringBootTest
@ActiveProfiles("test")
class MexibankApplicationTests {

    /**
     * Verifica que el contexto de aplicacion arranca sin errores.
     */
    @Test
    void contextLoads() {
    }
}