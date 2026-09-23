package com.marketbridge.repository;

import com.marketbridge.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Spring automatically writes the SQL for these methods behind the scenes.
// We just declare what we want.
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRole(String role);
    Optional<User> findByToken(String token);
    Optional<User> findByResetToken(String resetToken);
}
