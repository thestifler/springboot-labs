package mexibank.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de reloj para que el tiempo sea inyectable y determinista en
 * tests.
 *
 * <p>El dominio NUNCA llama a {@code Instant.now()} ni a {@code LocalDate.now()}
 * directamente. Recibe un {@code Clock} por inyeccion, de modo que los tests
 * pueden congelar el tiempo con {@code Clock.fixed(...)} y comprobar fechas y
 * vencimientos sin depender del reloj del sistema. Esto evita los fallos que
 * aparecen a medianoche y hace que el comportamiento sea reproducible.
 *
 * <p>Es un bean de Spring en infrastructure/config porque el cableado pertenece
 * a esa capa. El dominio sigue siendo 100% puro (java.time.Clock es JDK).
 */
@Configuration
public class ClockConfig {

    /**
     * Devuelve el reloj por defecto de la zona horaria del sistema.
     *
     * @return {@link Clock#systemDefaultZone()}
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
