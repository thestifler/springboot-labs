package library_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * Requisito de entorno: JDK 17.0.10 o superior.
 *
 * Con JDK 17.0.2 sobre cgroup v2, el propio JDK no puede inicializar el MBean
 * server de la plataforma:
 *   "Cannot invoke jdk.internal.platform.CgroupInfo.getMountPoint() because anyController is null"
 * Lo dispara Micrometer (TomcatMetricsBinder) al iniciar y aborta el contexto.
 * Es un defecto del JDK, no del proyecto: no se puede resolver con configuracion.
 */
@SpringBootApplication
public class LibraryApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LibraryApiApplication.class, args);
	}

}
