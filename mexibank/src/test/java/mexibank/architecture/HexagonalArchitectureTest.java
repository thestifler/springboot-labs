package mexibank.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion explicita de que la estructura hexagonal esta montada.
 *
 * <p><strong>Por que este test y no solo las reglas de ArchUnit.</strong> Las
 * reglas de {@link ArchitectureTest} se activan solas segun haya clases que
 * comprobar, asi que hoy, con el proyecto vacio, pasan sin llegar a mirar nada.
 * Eso es correcto (una arquitectura vacia no viola ninguna regla), pero deja un
 * hueco: nada garantiza que los paquetes existan siquiera.
 *
 * <p>Este test cierra ese hueco. Comprueba que el esqueleto esta montado, de
 * forma que cuando borre un paquete o lo renombre por error, falla aqui y no
 * seis meses despues cuando se escriban las clases que lo necesitas.
 *
 * <p><strong>Que no hace.</strong> No comprueba reglas de dependencia (eso es
 * cosa de {@link ArchitectureTest}) ni comportamiento. Solo comprueba que la
 * estructura existe.
 */
class HexagonalArchitectureTest {

    /**
     * Paquetes que deben existir para que la arquitectura tenga sentido.
     *
     * <p>Se declaran como constantes y no en linea dentro del assert para que el
     * fallo de un test diga exactamente que paquete falta, en lugar de un
     * "expected true was false".
     */
    private static final List<String> PAQUETES_OBLIGATORIOS = List.of(
            "mexibank.domain",
            "mexibank.domain.account",
            "mexibank.domain.transfer",
            "mexibank.domain.shared",
            "mexibank.domain.exception",
            "mexibank.domain.user",
            "mexibank.application",
            "mexibank.application.account",
            "mexibank.application.transfer",
            "mexibank.application.auth",
            "mexibank.application.user",
            "mexibank.infrastructure",
            "mexibank.infrastructure.config",
            "mexibank.infrastructure.persistence",
            "mexibank.infrastructure.persistence.entity",
            "mexibank.infrastructure.persistence.springdata",
            "mexibank.infrastructure.persistence.adapter",
            "mexibank.infrastructure.persistence.mapper",
            "mexibank.infrastructure.rest",
            "mexibank.infrastructure.rest.dto",
            "mexibank.infrastructure.rest.error",
            "mexibank.infrastructure.security",
            "mexibank.infrastructure.security.config",
            "mexibank.infrastructure.security.filter",
            "mexibank.infrastructure.security.support"
    );

    private static JavaClasses importarClases() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("mexibank");
    }

    /**
     * Todos los paquetes de la arquitectura existen y tienen su
     * {@code package-info.java} con la documentacion.
     *
     * <p>El {@code package-info} no es cosmetico: es lo que hace que git rastree
     * un paquete vacio y lo que deja la regla de dependencia escrita en el sitio
     * donde se mira.
     */
    @Test
    void todosLosPaquetesDeLaArquitecturaExisten() {
        JavaClasses clases = importarClases();

        List<String> paquetesReales = clases.stream()
                .map(JavaClass::getPackageName)
                .distinct()
                .toList();

        assertThat(paquetesReales)
                .as("faltan paquetes de la arquitectura hexagonal. Un paquete vacio "
                        + "solo existe en git si tiene un package-info.java, asi que "
                        + "revisa tambien que no se hayan borrado esos archivos.")
                .containsAll(PAQUETES_OBLIGATORIOS);
    }

    /**
     * Toda clase del proyecto vive en domain, application o infrastructure.
     *
     * <p><strong>Por que hace falta.</strong> Las reglas de ArchUnit solo
     * <em>restringen</em> las tres capas: dicen "nada en domain depende de
     * infrastructure". No dicen "nada existe fuera de las tres capas". Por eso un
     * paquete suelto como {@code mexibank.util} o {@code mexibank.service}
     * pasaria todos los tests sin violar ninguna regla, y aun asi es
     * exactamente lo que esta arquitectura prohibe: un sitio donde meter codigo
     * sin capa clara, fuera del control de ninguna regla.
     *
     * <p>Comprobado de forma deliberada: al crear {@code mexibank.util.Colado} la
     * build seguia en verde sin este test.
     */
    @Test
    void todaClaseDelProyectoPerteneceAUnaDeLasTresCapas() {
        // Solo las clases que produce este proyecto. Importar "." recorre todo
        // lo que la JVM tiene cargado, que incluye Spring, JUnit y el JDK: unas
        // 3000 clases que viven fuera de 'mexibank' y no son culpa nuestra.
        List<String> clasesPropias = clasesDelPropioProyecto();

        // El punto de entrada vive en la raiz a proposito (si bajase a un
        // subpaquete, @ComponentScan dejaria de cubrir domain y application), asi
        // que es la unica excepcion a que todo este en una capa.
        List<String> fueraDeLasCapas = clasesPropias.stream()
                .filter(clase -> capaDe(clase) == null)
                .distinct()
                .toList();

        assertThat(fueraDeLasCapas)
                .as("hay clases que no pertenecen a domain, application ni "
                        + "infrastructure. Un paquete suelto como 'mexibank.util' o "
                        + "'mexibank.service' queda fuera de TODAS las reglas de "
                        + "ArchUnit (que solo restringen las tres capas) sin dar "
                        + "ningun error: es el agujero que este test tapa.")
                .isEmpty();
    }

    /**
     * Devuelve la capa a la que pertenece una clase, o {@code null} si esta fuera
     * de las tres capas.
     */
    private String capaDe(String nombreClase) {
        if (nombreClase.equals("mexibank.MexibankApplication")) {
            return "raiz";
        }
        if (nombreClase.startsWith("mexibank.domain.")) {
            return "domain";
        }
        if (nombreClase.startsWith("mexibank.application.")) {
            return "application";
        }
        if (nombreClase.startsWith("mexibank.infrastructure.")) {
            return "infrastructure";
        }
        return null;
    }

    /**
     * Clases que produce ESTE proyecto (target/classes y target/test-classes),
     * sin las dependencias externas.
     *
     * <p>Se filtran por el directorio de salida de Maven en lugar de usar
     * {@code importPackages(".")}, porque este ultimo recorre el classpath
     * completo. El coste no es solo ruido: en una version anterior de este test
     * importaba las ~3000 clases de Spring y JUnit y tardaba 30 segundos en
     * lugar de milisegundos.
     */
    private static List<String> clasesDelPropioProyecto() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                // Un unico ImportOption con un OR, no dos ImportOption con un AND
                // implicito: varias opciones se combinan con AND, asi que pedir
                // "/target/classes/" y "/target/test-classes/" a la vez no
                // encuentra ninguna clase (una ruta no puede estar en los dos
                // sitios a la vez) y el filtro acaba descartando todo.
                .withImportOption(location -> location.contains("/target/classes/")
                        || location.contains("/target/test-classes/"))
                .importPackages(".")
                .stream()
                .map(JavaClass::getName)
                .toList();
    }

    /**
     * El punto de entrada se queda en la raiz del proyecto.
     *
     * <p>No es una preferencia estetica. {@code @SpringBootApplication} escanea el
     * paquete en el que esta y todos sus descendientes: si la clase principal
     * bajase a {@code infrastructure}, Spring dejaria de encontrar los beans de
     * {@code domain} y {@code application} y el contexto arrancaria vacio, sin
     * dar ningun error claro.
     */
    @Test
    void elPuntoDeEntradaEstaEnLaRaizDelProyecto() {
        JavaClasses clases = importarClases();

        assertThat(clases.get("mexibank.MexibankApplication"))
                .as("MexibankApplication debe quedarse en el paquete raiz 'mexibank'. "
                        + "Si baja a un subpaquete, @ComponentScan dejara de cubrir "
                        + "domain y application.")
                .isNotNull();
    }
}