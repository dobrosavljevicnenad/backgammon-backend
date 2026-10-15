package com.example.backend.repository;

import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User>findByUsername(String username);
    Optional<User> getUserById(Long id);
    void deleteUserById(Long id);
    boolean existsByEmail(String email);
    boolean existsByUsername(String email);
    Optional<User> getUserByEmail(String email);

}
