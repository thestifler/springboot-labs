package library_api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import library_api.entity.Book;

public interface BookRepository extends JpaRepository<Book,String> {

    /**
     * Busca el libro bloqueando la fila para escritura (SELECT ... FOR UPDATE).
     *
     * Es lo que hace segura una operacion leer-modificar-escribir sobre el
     * contador de copias. Sin el bloqueo, dos transacciones concurrentes pueden
     * leer el mismo valor y escribir encima la una de la otra (lost update): si
     * queda un unico ejemplar disponible, dos reservas simultaneas lo consumen
     * ambas y el contador acaba en 0 cuando solo se entrego una copia.
     *
     * El bloqueo se mantiene hasta que termine la transaccion del service que lo
     * invoca, por eso el metodo que lo usa debe ser @Transactional. Spring Data
     * no acepta este metodo fuera de transaccion y falla con
     * TransactionRequiredException.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.isbn = :isbn")
    Optional<Book> findByIsbnForUpdate(@Param("isbn") String isbn);

    /**
     * Incrementa en uno el contador de ejemplares mediante un unico UPDATE.
     *
     * Devolver un ejemplar no exige ninguna decision de negocio (a diferencia de
     * reservarlo, que debe comprobar si queda alguno), asi que no hace falta leer
     * el valor antes: sumar sobre el valor guardado en la base de datos es atomico
     * por construccion. Por eso NO se usa el findByIsbnForUpdate del descuento:
     * aqui un UPDATE basta y es mas eficiente.
     *
     * @return filas afectadas: 1 si el libro existe, 0 si no existe
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Book b set b.avaliableCopyNumber = b.avaliableCopyNumber + 1 where b.isbn = :isbn")
    int increaseAvaliableCopyNumber(@Param("isbn") String isbn);
}
