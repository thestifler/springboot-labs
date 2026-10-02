package mexibank.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de arquitectura. Fijan el hexagono en codigo.
 *
 * <p>Estos tests se ejecutan en la fase test y fallan la build si alguien rompe
 * la regla de dependencia. No se basan en convencion: se basan en bytecode
 * analizado por ArchUnit.
 *
 * <p>La primera regla es la que <strong>de verdad sostiene</strong> la arquitectura:
 * el dominio no puede conocer infrastructure ni application. El resto son barreras
 * de seguridad para evitar que el codigo se deslice hacia una arquitectura por
 * capas tradicional con el nombre de "hexagonal".
 */
@AnalyzeClasses(packages = "mexibank")
class ArchitectureTest {

    /**
     * El dominio es el nucleo mas interno: no conoce a nadie fuera de si mismo.
     */
    @ArchTest
    static final ArchRule domainNoDependeDeAplicacionNiInfraestructura = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "..application..",
                    "..infrastructure..",
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "jakarta.validation..",
                    "jakarta.servlet..",
                    "com.fasterxml.jackson.."
            );

    /**
     * La capa de aplicacion solo habla con el dominio (a traves de sus puertos).
     */
    @ArchTest
    static final ArchRule applicationNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    /**
     * Solo infrastructure.rest contiene @RestController.
     */
    @ArchTest
    static final ArchRule soloRestEsController = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..infrastructure.rest..")
            .allowEmptyShould(true);

    /**
     * Solo infrastructure.persistence.entity contiene @Entity.
     */
    @ArchTest
    static final ArchRule soloEntidadesSonJpaEntities = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..infrastructure.persistence.entity..")
            .allowEmptyShould(true);

    /**
     * Los casos de uso (implementaciones) se registran como Beans de Spring.
     */
    @ArchTest
    static final ArchRule casosDeUsoSonBeansSpring = classes()
            .that().resideInAPackage("..application..")
            .and().haveSimpleNameEndingWith("UseCaseImpl")
            .should().beAnnotatedWith(Service.class)
            .allowEmptyShould(true);

    /**
     * Toda interfaz que vive en domain.* debe tener su implementacion, y el sitio
     * donde vive depende de si el puerto es de entrada o de salida.
     *
     * <p><strong>Por que se comprueba con {@link ClassFileImporter} y no con el
     * DSL declarativo.</strong> La forma de expresar "esta interfaz la implementa
     * aquella clase" ha cambiado entre versiones de ArchUnit, mientras que el modelo
     * de clases importado es estable.
     *
     * <p><strong>Por que hay dos destinos.</strong> Un puerto de entrada (un caso de
     * uso) lo implementa la capa de aplicacion, porque es la que orquesta puertos de
     * salida. Un puerto de salida (un repositorio, un hasher, un emisor de tokens) lo
     * implementa infraestructura, que es la capa que sabe usar una tecnologia
     * concreta. Exigir que todo puerto tenga un adaptador en infraestructura
     * obligaria a meter los casos de uso en infraestructura y a abrir un agujero en
     * la direccion de las dependencias.
     *
     * <p><strong>Como se distingue uno de otro.</strong> Por el nombre, que es lo
     * unico que el compilador puede comprobar. Un puerto de entrada termina en
     * {@code UseCase}, y su implementacion en {@code UseCaseImpl}. Un puerto de
     * salida es cualquier otra interfaz del dominio, y su implementacion vive en
     * infraestructura.
     *
     * <p><strong>Que gana esto.</strong> Declarar un puerto en el dominio obliga a
     * escribir su implementacion en la capa correcta: si se declara
     * {@code AccountRepository} y no hay ninguna clase que lo implemente, el test
     * falla aqui y no en produccion.
     */
    @Test
    void todoPuertoDelDominioTieneSuImplementacionEnLaCapaCorrecta() {
        JavaClasses clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("mexibank");

        // package-info se importa como si fuera una interfaz (declarar un paquete
        // genera una "clase" synthetic en bytecode). No es un puerto, asi que se
        // excluye por nombre.
        List<JavaClass> puertos = clases.stream()
                .filter(c -> c.isInterface())
                .filter(c -> !c.getSimpleName().equals("package-info"))
                .filter(c -> c.getPackageName().startsWith("mexibank.domain"))
                .toList();

        for (JavaClass puerto : puertos) {
            boolean esDeEntrada = puerto.getSimpleName().endsWith("UseCase");
            String capaEsperada = capaDe(puerto);

            assertThat(implementacionesDe(clases, puerto, capaEsperada))
                    .as("el puerto de dominio %s no tiene ninguna implementacion en %s.*. "
                                    + (esDeEntrada
                                    ? "Un puerto de entrada (caso de uso) se implementa en application, "
                                    + "que es la capa que orquesta los puertos de salida."
                                    : "Un puerto de salida necesita un adaptador en infraestructura."),
                            puerto.getSimpleName(), capaEsperada)
                    .isNotEmpty();
        }
    }

    /**
     * Nombres de las clases de la capa indicada que implementan el puerto.
     *
     * @param clases  todas las clases del proyecto, sin tests
     * @param puerto  interfaz del dominio que se busca implementar
     * @param paquete prefijo del paquete donde debe estar la implementacion
     * @return nombres de las implementaciones, para que el fallo diga cual falta
     */
    private List<String> implementacionesDe(JavaClasses clases, JavaClass puerto, String paquete) {
        return clases.stream()
                .filter(c -> c.getPackageName().startsWith(paquete))
                .filter(c -> c.isAssignableTo(puerto.getName()))
                .map(JavaClass::getSimpleName)
                .toList();
    }

    /**
     * Capa donde debe estar la implementacion de un puerto del dominio.
     *
     * @param puerto interfaz del dominio
     * @return {@code mexibank.application} si es un caso de uso,
     *         {@code mexibank.infrastructure} en caso contrario
     */
    private String capaDe(JavaClass puerto) {
        return puerto.getSimpleName().endsWith("UseCase")
                ? "mexibank.application"
                : "mexibank.infrastructure";
    }

    /**
     * Los repositorios de dominio se llaman *Repository y no contienen
     * anotaciones de Spring (viven en domain). Es una salvaguarda para evitar
     * que alguien ponga @Repository en el centro por error.
     */
    @ArchTest
    static final ArchRule repositoriosDeDominioSonInterfacesPuras = classes()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .andShould().notBeAnnotatedWith("org.springframework.stereotype.Repository")
            .allowEmptyShould(true);
}
