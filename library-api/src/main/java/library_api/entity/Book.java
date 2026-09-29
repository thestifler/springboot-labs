package library_api.entity;

import java.time.LocalDate;

import org.hibernate.validator.constraints.ISBN;
import org.hibernate.validator.constraints.ISBN.Type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA de libro.
 *
 * La clave primaria es el ISBN-13, que es la clave natural del recurso: lo
 * aporta el cliente al dar de alta el libro y no se genera en la base de datos.
 * Al ser un id asignado, Spring Data resuelve la insercion con persist()
 * (no hace falta ningun GenerationType).
 *
 * publicationDate es un java.time.LocalDate y no un java.util.Date: Date es
 * mutable, no es thread-safe y su API de aritmetica esta obsoleta desde Java 8.
 * Para una fecha civil (sin hora ni zona) LocalDate es el tipo correcto y lo
 * serializa Jackson directamente en ISO-8601 ("1997-03-03").
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @ISBN(type = Type.ISBN_13, message = "El ISBN-13 introducido no es valido")
    @Column(name = "isbn", nullable = false, length = 13)
    private String isbn;

    @Column(name = "author", nullable = false, length = 100)
    private String author;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "avaliable_copy_number", nullable = false)
    private long avaliableCopyNumber;

    protected Book() {
        // constructor para JPA
    }

    public Book(String isbn, String author, String title, LocalDate publicationDate, long avaliableCopyNumber) {
        this.isbn = isbn;
        this.author = author;
        this.title = title;
        this.publicationDate = publicationDate;
        this.avaliableCopyNumber = avaliableCopyNumber;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public long getAvaliableCopyNumber() {
        return avaliableCopyNumber;
    }

    public void setAvaliableCopyNumber(long avaliableCopyNumber) {
        this.avaliableCopyNumber = avaliableCopyNumber;
    }

    /**
     * Identidad de negocio por isbn. Se evita el equals generado sobre todos los
     * campos porque en JPA una entidad puede cambiar de estado (managed ->
     * detached -> removed) y un hashCode basado en campos mutables rompe las
     * colecciones de sesion e impide su uso como clave en HashMap/HashSet.
     *
     * Cuando el isbn es null la entidad aun no ha sido persistida, por lo que solo
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
        Book other = (Book) o;
        return isbn != null && isbn.equals(other.isbn);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Book{isbn='" + isbn + '\''
                + ", author='" + author + '\''
                + ", title='" + title + '\''
                + ", publicationDate=" + publicationDate
                + ", avaliableCopyNumber=" + avaliableCopyNumber
                + '}';
    }
}
