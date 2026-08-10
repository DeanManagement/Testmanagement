package com.deanmanagement.testmanagement.user.internal.repository;

import com.deanmanagement.testmanagement.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Human accounts only — excludes the service accounts backing API keys (PRD-025 §3.2). */
    List<User> findByServiceAccountFalse();
}
