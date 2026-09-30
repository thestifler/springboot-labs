package library_api.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import library_api.entity.Loan;

/**
 * Consultas sobre prestamos.
 *
 * La idea central de este repositorio es que "el usuario todavia no ha devuelto
 * el libro" NO es un estado guardado, sino la ausencia de fecha de devolucion:
 * returned_date is null. Por eso las consultas que buscan prestamos pendientes
 * filtran por ese campo y no por un enum de estados, que ademas puede quedar
 * desincronizado.
 *
 * Todos los metodos que necesitan "hoy" reciben la fecha como parametro en lugar
 * de llamar a LocalDate.now() dentro del query. Asi la consulta es determinista
 * (testeable sin depender del reloj) y, mas importante, el service puede
 * aplicar la misma fecha a todo el calculo de una peticion: si el prestamo
 * vence a las 00:00 no puede quedar a medias vencido en una consulta y sin
 * vencer en la siguiente.
 *
 * Las propiedades anidadas se escriben con guion bajo (User_Id, Book_Isbn) y no
 * pegadas (UserId, BookIsbn). Spring Data admite las dos formas, pero la pegada
 * obliga a adivinar por donde parte el nombre: UserId podria ser la propiedad
 * userId o user.id. Con el guion bajo no hay ambiguedad, y aqui es relevante
 * porque el modelo original de Loan TENIA un campo suelto llamado bookIsbn; con
 * la forma pegada, BookIsbn podria acabar resolviendo a ese campo en lugar de a
 * book.isbn y la consulta filtraria por la columna equivocada sin fallar nunca.
 */
public interface LoanRepository extends JpaRepository<Loan, Long> {

    /**
     * Prestamos que el usuario tiene pendientes de devolver.
     *
     * Es la consulta que responde "este usuario aun no me ha devuelto esto": todo
     * lo que aparezca aqui tiene returnedDate a null, sea o no este ya vencido.
     */
    List<Loan> findByUser_IdAndReturnedDateIsNull(Long userId);

    /**
     * Indica si el usuario tiene pendiente ese libro concreto.
     *
     * Es la comprobacion que evita prestar dos veces el mismo ejemplar del mismo
     * usuario: si devuelve true, el servicio debe rechazar el nuevo prestamo.
     * exists... genera un SELECT 1 con LIMIT 1, no trae la entidad, y por tanto
     * no necesita cargar las relaciones LAZY de un Loan.
     */
    boolean existsByUser_IdAndBook_IsbnAndReturnedDateIsNull(Long userId, String bookIsbn);

    /**
     * Prestamos sin devolver que ya pasaron su fecha limite en la fecha dada.
     *
     * Aqui es donde se ve el coste de no guardar dueDate: la fecha limite no
     * existe como columna, asi que el calculo loan_date + loan_days < :today se
     * resuelve en el propio SQL y no puede apoyarse en un indice sobre la
     * fecha limite. Para el volumen de una biblioteca es irrelevante; si
     * llegara a haber cientos de miles de prestamos historicos, la solucion es
     * anadir una columna generated (calculada por la base de datos, que por eso
     * no puede desincronizarse) e indexarla, sin volver a duplicar el dato en
     * Java.
     *
     * El filtro por returnedDate is null es imprescindible: un libro devuelto
     * tarde tiene loan_date + loan_days pasado, pero ya no esta pendiente de
     * nada y no debe aparecer como vencido.
     *
     * La comparacion es estricta (<) para que devolver el libro el mismo dia del
     * vencimiento no lo marque como vencido, igual que isOverdue() en la entidad.
     */
    @Query("""
            select l from Loan l
            where l.returnedDate is null
              and timestampadd(day, l.loanDays, l.loanDate) < :today
            order by timestampadd(day, l.loanDays, l.loanDate) asc
            """)
    List<Loan> findOverdueOn(@Param("today") LocalDate today);

    /**
     * Un prestamo concreto y pendiente, para poder devolverlo sabiendo que sigue
     * abierto. Devolverlo sobre un Optional vacio significa que ya se devolvio
     * antes, que es un caso de negocio distinto de "no existe".
     */
    Optional<Loan> findByIdAndReturnedDateIsNull(Long id);

    /**
     * Prestamos de un usuario en orden inverso de salida, para el historial.
     *
     * El orden viene de la consulta en lugar de ordenarse en Java con un sort:
     * la base de datos ya tiene que recorrer las filas yordenarlas alli es gratis,
     * mientras que cargarlas todas en memoria para ordenarlas despues es lo
     * contrario. Se ordena por loanDate DESC porque lo que se mira primero es lo
     * mas reciente, y por id DESC para desempatar dos prestamos del mismo dia y
     * que el resultado sea estable entre llamadas.
     *
     * Devuelve prestamos abiertos y ya devueltos: el historial completo es lo que
     * necesita el usuario para saber que ha tenido y que aun tiene.
     */
    List<Loan> findByUser_IdOrderByLoanDateDescIdDesc(Long userId);

    /**
     * Numero de prestamos pendientes de un usuario. Es la forma barata de
     * responder "cuantos libros debe" sin cargar las entidades ni sus
     * relaciones para despues solo contarlas.
     */
    long countByUser_IdAndReturnedDateIsNull(Long userId);
}
