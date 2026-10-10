package com.issuetracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data generates the SQL from the method names, e.g.
 * findByEmailIgnoreCase -> SELECT * FROM users WHERE upper(email) = upper(?)
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
