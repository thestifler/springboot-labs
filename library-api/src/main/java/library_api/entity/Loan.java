package library_api.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA de prestamo: una copia de un libro entregada a un usuario.
 *
 * El prestamo es una entidad propia y no un atributo de Book ni de User, porque
 * describe una relacion N:M con atributos (una misma pareja usuario/libro puede
 * tener varios prestamos a lo largo del tiempo) y porque tiene ciclo de vida
 * propio: nace en la entrega y se cierra en la devolucion.
 *
 * Modelado de los tres estados posibles:
 * <ul>
 *   <li>Salida: loanDate y loanDays fijan cuando salio el libro y por cuanto.</li>
 *   <li>Vencimiento: dueDate NO se guarda, se deriva (loanDate + loanDays).
 *       Almacenarlo seria duplicar un dato que depende de otros dos y que
 *       puede quedar desincronizado si alguno se corrige; el indice unico es que
 *       al no existir la columna es imposible que se desincronice.</li>
 *   <li>Devolucion: returnedDate es null mientras el prestamo sigue vigente y
 *       pasa a tener valor cuando el usuario devuelve el libro. Este unico campo
 *       sustituye a un enum de estados: "devuelto" es exactamente "returnedDate
 *       != null", de modo que no puede haber un Loan con estado RETURNED y
 *       returnedDate null. A diferencia de User, aqui el estado SI es derivable,
 *       asi que un LoanStatus guardado seria redundante.</li>
 * </ul>
 *
 * Las relaciones con Book y User son unidireccionales (solo Loan apunta) a
 * proposito. La inversa (Book.loans / User.loans) solo hace falta para navegar
 * del libro a sus prestamos, y anadirla obliga a cascadear la carga, a vigilar
 * la recursion en el equals/hashCode y a recordar de quitar la coleccion del
 * lado propietario. El historico de prestamos de un usuario se resuelve con una
 * consulta en LoanRepository, no con una coleccion en memoria.
 *
 * No se usa cascade: al borrar un libro o un usuario no debe arrastrarse su
 * historico de prestamos. Ademas, borrowedBook se declara LAZY para no cargar el
 * expediente del libro entero al listar prestamos.
 *
 * La validacion de negocio (que el usuario exista, que este activo, que no haya
 * agotado su cupo, que queden ejemplares, que no tenga ya este libro prestado, que
 * un prestamo se pueda renovar) NO vive aqui: corresponde al LoanService dentro de
 * su transaccion, igual que reserveCopy decide sobre el contador de ejemplares. En
 * la entidad solo quedan las restricciones de esquema y los cambios de estado
 * (markReturned y renew), que no deciden nada: solo aplican lo que el servicio ya
 * ha validado.
 */
@Entity
@Table(
        name = "loans",
        indexes = {
                // Ambas columnas son claves foraneas que se consultan de forma
                // habitual (prestamos de un usuario, prestamos de un libro), asi
                // que un indice evita un recorrido completo de la tabla en cada
                // consulta.
                @Index(name = "idx_loans_user_id", columnList = "user_id"),
                @Index(name = "idx_loans_book_isbn", columnList = "book_isbn"),
                // Indice compuesto para la consulta mas frecuente del sistema: los
                // prestamos ABIERTOS de un usuario. Con los indices sueltos de arriba
                // esa consulta tiene que descartar en memoria todas las filas del
                // usuario para quedarse con las que returned_date es null; el
                // compuesto filtra ya en el indice y ademas llega ordenado por
                // usuario, que es por donde se empieza a leer.
                @Index(name = "idx_loans_user_returned", columnList = "user_id, returned_date")
        })
public class Loan {

    /**
     * Dias de prestamo por defecto cuando no se indica una duracion concreta.
     * Se declara como constante (y no como un 14 suelto en el codigo) para que
     * el valor tenga nombre y no se repita en magic numbers.
     */
    public static final int DEFAULT_LOAN_DAYS = 14;

    /**
     * Numero maximo de renovaciones permitidas sobre un mismo prestamo.
     *
     * Sin este limite la renovacion seria infinita y un usuario podria retener un
     * libro indefinidamente sin llegar a devolverlo nunca. Es una politica de
     * negocio y por eso vive en una constante con nombre y no en un if suelto
     * dentro del service.
     */
    public static final int MAX_RENEWALS = 2;

    /**
     * Identificador generado por la base de datos con la misma estrategia que
     * User. El prestamo no admite id asignado por el cliente: es un hecho
     * interno de la biblioteca, no un dato que traiga el usuario del recurso.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Usuario que tiene el libro prestado. FK real y no un id suelto: la base de
     * datos garantiza que no exista un prestamo de un usuario que no existe, y
     * la entidad puede navegar al usuario sin una segunda consulta.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Libro prestado. El isbn es la clave primaria de Book, asi que la columna
     * referenciada se indica de forma explicita para no depender del nombre
     * deducido. Un prestamo siempre tiene libro y usuario: ambas columnas son
     * NOT NULL.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_isbn", referencedColumnName = "isbn", nullable = false)
    private Book book;

    /**
     * Fecha en la que el libro salio para el usuario. updatable = false porque es
     * un hecho historico: corregirlo despues invalidaria todos los calculos de
     * vencimiento y de retraso. Una devolucion tardia se registra en returnedDate.
     */
    @Column(name = "loan_date", nullable = false, updatable = false)
    private LocalDate loanDate;

    /**
     * Duracion acordada del prestamo, en dias.
     *
     * Es int y no long: un plazo se cuenta en dias y long no aportaria nada,
     * solo permitiria valores absurdos que nadie deberia poder guardar. Es
     * primitivo (y no Integer) porque siempre tiene valor, igual que
     * Book.availableCopyNumber.
     */
    @Column(name = "loan_days", nullable = false)
    private int loanDays;

    /**
     * Fecha en la que el usuario devolvio el libro. Null mientras el prestamo
     * sigue vigente. Es el unico campo que define si el prestamo esta abierto.
     */
    @Column(name = "returned_date")
    private LocalDate returnedDate;

    /**
     * Cuantas veces se ha prorrogado este prestamo.
     *
     * Es un contador, no un booleano, porque la politica es "hasta MAX_RENEWALS
     * veces" y no "renovable una vez". Se guarda en lugar de derivarse porque el
     * limite no puede deducirse de las fechas: dos prestamos de 14 dias con la
     * misma loanDate pueden haberse renovado cero o dos veces.
     */
    @Column(name = "renewal_count", nullable = false)
    private int renewalCount;

    protected Loan() {
        // constructor para JPA
    }

    /**
     * Prestamo de hoy por la duracion por defecto (DEFAULT_LOAN_DAYS). Es la via
     * habitual del alta: el caso comun es "este usuario se lleva el libro hoy".
     */
    public Loan(Book book, User user) {
        this(book, user, LocalDate.now(), DEFAULT_LOAN_DAYS);
    }

    /**
     * Prestamo con fecha y duracion explicitas. Existe para que el servicio
     * pueda registrar prestamos con otra duracion (biblioteca extendida) y para
     * que los tests puedan fijar las fechas en lugar de depender de LocalDate.now().
     *
     * Las fechas y la duracion se validan en el borde (LoanRequest) y no aqui,
     * igual que en Book.
     */
    public Loan(Book book, User user, LocalDate loanDate, int loanDays) {
        this.book = book;
        this.user = user;
        this.loanDate = loanDate;
        this.loanDays = loanDays;
    }

    /**
     * Fecha limite de devolucion, derivada de loanDate + loanDays.
     *
     * Deliberadamente NO es un campo ni tiene setter: como dato calculado no se
     * persiste ni se puede asignar, de modo que nunca queda desincronizado con
     * las columnas de las que depende. Los dias de margen suman exactamente
     * loanDays dias al calendario (no 24h por dia), que es lo que se espera de
     * una fecha civil.
     */
    public LocalDate getDueDate() {
        return loanDate.plusDays(loanDays);
    }

    /**
     * El prestamo sigue abierto: el libro aun no ha vuelto.
     * Derivado de returnedDate, no guardado como estado aparte.
     */
    public boolean isActive() {
        return returnedDate == null;
    }

    public boolean isReturned() {
        return returnedDate != null;
    }

    /**
     * El prestamo se paso de la fecha limite y el libro sigue sin devolver.
     *
     * Solo tiene sentido para un prestamo abierto: un libro devuelto tarde no
     * sigue "pendiente". La comparacion es estricta, asi que devolver el libro
     * el mismo dia del vencimiento NO lo marca como vencido.
     *
     * Delega en la variante con fecha para que esta sea una simple conveniencia
     * y el calculo real se pueda fijar en un test sin depender del reloj.
     */
    public boolean isOverdue() {
        return isOverdueOn(LocalDate.now());
    }

    /**
     * Version determinista de isOverdue(), con la fecha de referencia como
     * parametro. today es el "hoy" del prestamo, no la fecha de devolucion.
     */
    public boolean isOverdueOn(LocalDate today) {
        return isActive() && today.isAfter(getDueDate());
    }

    /**
     * Dias de retraso respecto a la fecha limite, o 0 si el prestamo no esta
     * vencido. Nunca negativo.
     *
     * Se calcula con ChronoUnit.DAYS entre dos LocalDate en vez de restar dias
     * porque entre fechas LocateDate la diferencia en dias no esta definida por
     * la aritmetica de los offsets horarios y restar dias no siempre daria el
     * numero de dias de calendario esperado.
     */
    public long getDaysOverdue() {
        return getDaysOverdueOn(LocalDate.now());
    }

    /**
     * Version determinista de getDaysOverdue().
     */
    public long getDaysOverdueOn(LocalDate today) {
        if (!isActive() || !today.isAfter(getDueDate())) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(getDueDate(), today);
    }

    /**
     * Registra la devolucion y cierra el prestamo.
     *
     * Es la unica via para pasar returnedDate de null a un valor, y por eso el
     * estado del prestamo no puede quedar incoherente por una asignacion
     * suelta. Si se invoca dos veces no lanza excepcion ni cambia la fecha: la
     * primera devolucion es la real y las siguientes son reintentos idempotentes
     * de un cliente que no sabe si ya habia funcionado la peticion anterior.
     * Devolver un libro por segunda vez no es un error de negocio, y el servicio
     * sigue siendo quien decide si tiene sentido (p. ej. incrementar el contador
     * de ejemplares).
     */
    public void markReturned(LocalDate returnedDate) {
        if (this.returnedDate == null) {
            this.returnedDate = returnedDate;
        }
    }

    /**
     * Cierre del prestamo con la fecha de hoy. Conveniencia sobre
     * markReturned(LocalDate.now()).
     */
    public void markReturned() {
        markReturned(LocalDate.now());
    }

    /**
     * Prorroga el prestamo otros extraDays dias a partir de la fecha limite
     * actual.
     *
     * Al prolongar loanDays en vez de recalcular una fecha, el vencimiento sigue
     * siendo coherente por construccion: getDueDate() devuelve
     * loanDate + loanDays, y loanDays ahora es 28, 42, etc. Si en cambio se
     * guardara una nueva fecha limite, habria que acordarse de recalcularla y el
     * riesgo de desincronizacion vuelve a aparecer.
     *
     * NO comprueba si el prestamo puede renovarse. Eso es una regla de negocio
     * (no renovar lo vencido, no pasar de MAX_RENEWALS) y pordecision del proyecto
     * vive en LoanService, que es quien tiene el contexto y lanza la excepcion de
     * dominio. Aqui solo se aplica el cambio de estado.
     */
    public void renew(int extraDays) {
        this.loanDays += extraDays;
        this.renewalCount++;
    }

    /**
     * Indica si el prestamo ya no admite mas renovaciones.
     */
    public boolean hasReachedRenewalLimit() {
        return renewalCount >= MAX_RENEWALS;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public void setLoanDate(LocalDate loanDate) {
        this.loanDate = loanDate;
    }

    public int getLoanDays() {
        return loanDays;
    }

    public void setLoanDays(int loanDays) {
        this.loanDays = loanDays;
    }

    public int getRenewalCount() {
        return renewalCount;
    }

    public LocalDate getReturnedDate() {
        return returnedDate;
    }

    /**
     * Asignacion directa de la fecha de devolucion. Se mantiene por simetria con
     * el resto de la entidad (JPA necesita poder escribirla al hidratar), pero
     * el camino normal para cerrar un prestamo es markReturned(), que evita
     * sobrescribir una devolucion ya registrada.
     */
    public void setReturnedDate(LocalDate returnedDate) {
        this.returnedDate = returnedDate;
    }

    /**
     * Identidad de negocio por id. Se evita el equals generado sobre todos los
     * campos porque en JPA una entidad puede cambiar de estado (managed ->
     * detached -> removed) y un hashCode basado en campos mutables rompe las
     * colecciones de sesion e impide su uso como clave en HashMap/HashSet.
     *
     * Cuando el id es null la entidad aun no ha sido persistida, por lo que solo
     * puede ser igual a si misma (ya cubierto por la comparacion referencial).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Loan other = (Loan) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Loan{id=" + id
                + ", user=" + (user == null ? null : user.getId())
                + ", bookIsbn='" + (book == null ? null : book.getIsbn()) + '\''
                + ", loanDate=" + loanDate
                + ", loanDays=" + loanDays
                + ", returnedDate=" + returnedDate
                + ", renewalCount=" + renewalCount
                + '}';
    }
}
