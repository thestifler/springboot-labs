package library_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import library_api.entity.User;

public interface UserRepository extends JpaRepository<User,Long> {

}
