package com.lastmilebanking.backend.repository;

import com.lastmilebanking.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUserId(String userId);
    boolean existsByUsername(String username);
}
