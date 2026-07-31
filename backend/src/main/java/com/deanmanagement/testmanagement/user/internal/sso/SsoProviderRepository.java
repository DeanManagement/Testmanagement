package com.deanmanagement.testmanagement.user.internal.sso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SsoProviderRepository extends JpaRepository<SsoProvider, UUID> {

    Optional<SsoProvider> findBySlug(String slug);

    List<SsoProvider> findByActiveTrueOrderByDisplayNameAsc();

    List<SsoProvider> findAllByOrderByDisplayNameAsc();

    boolean existsBySlug(String slug);
}
