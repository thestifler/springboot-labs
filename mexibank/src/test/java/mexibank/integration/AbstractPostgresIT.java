package mexibank.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Clase base para los tests de integracion que necesitan PostgreSQL de verdad.
 *
 * <p><strong>Por que Testcontainers y no H2.</strong> H2 miente en las cosas que
 * en un banco importan: bloqueos pesimisticos ({@code SELECT ... FOR UPDATE}),
 * generacion de secuencias, precision de {@code numeric} y la semantica real de
 * las transacciones. Un test que pasa contra H2 puede fallar contra PostgreSQL,
 * que es donde corre el codigo. Por eso los tests de integracion levantan el
 * motor de verdad en un contenedor.
 *
 * <p><strong>Por que {@link ServiceConnection}.</strong> Spring Boot lee el
 * contenedor y sobreescribe url, usuario y contrasena de forma automatica. Es
 * preferible a un {@code @DynamicPropertySource} a mano por dos razones: no
 * repite nombres de propiedades (un error de tecleo en la propiedad no falla
 * al compilar, falla al ejecutar) y el contenedor queda declarado en un solo
 * sitio.
 *
 * <p><strong>El perfil "test"</strong> evita arrancar contra la base de datos de
 * desarrollo por error. Si el contenedor no levanta, el test falla en lugar de
 * operar sobre datos reales.
 *
 * <p><strong>Por que el contenedor se arranca a mano y no con
 * {@code @Testcontainers}.</strong> Spring cachea el contexto entre clases de
 * test, pero la extension de {@code @Testcontainers} arranca y <em>para</em> los
 * contenedores una vez por clase. Con dos subclases, la segunda arranca un
 * contenedor nuevo (con otro puerto) mientras Spring reutiliza el contexto
 * cacheado, que apunta al puerto del anterior: la conexion se rechaza con
 * {@code Connection refused} y el fallo aparece en el segundo test, no en el
 * primero, que es donde despista. Arrancarlo una sola vez en un inicializador
 * estatico de la base comun da un unico puerto estable durante toda la JVM, que
 * es lo que el contexto cacheado necesita. Testcontainers lo detiene al salir por
 * su propio recolector (Ryuk), asi que no queda nada huerfano.
 *
 * <p><strong>La imagen se fija a una version</strong> en lugar de {@code latest}:
 * una actualizacion no determinista de la imagen no puede romper la build un
 * martes cualquiera.
 *
 * <p><strong>Nota sobre Testcontainers 2.x.</strong> En esta version
 * {@code PostgreSQLContainer} ya no es generica (en 1.x lo era), asi que se
 * declara sin diamantes. La documentacion de 1.x sigue mostrando
 * {@code PostgreSQLContainer<?>} y no compila contra la 2.x. Como el resto del
 * proyecto, esta clase requiere Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIT {

    /**
     * Contenedor de PostgreSQL que Spring conecta al datasource automaticamente.
     *
     * <p>{@link ServiceConnection} no es decorativo: sin el, Spring ignora el
     * contenedor e intenta conectarse a la URL de {@code application.properties},
     * que apunta a la base de datos de desarrollo.
     *
     * <p>El arranque esta en el bloque estatico y no en un metodo para que ocurra
     * una sola vez, antes de que Spring construya el contexto.
     */
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16.9-alpine"))
                    .withDatabaseName("mexibank_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
    }
}
