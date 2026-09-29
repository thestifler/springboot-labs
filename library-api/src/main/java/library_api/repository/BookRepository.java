package library_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import library_api.entity.Book;

public interface BookRepository extends JpaRepository<Book,String> {

}
