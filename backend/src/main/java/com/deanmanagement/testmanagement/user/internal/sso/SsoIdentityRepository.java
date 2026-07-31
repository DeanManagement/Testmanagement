package com.deanmanagement.testmanagement.user.internal.sso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SsoIdentityRepository extends JpaRepository<SsoIdentity, UUID> {

    Optional<SsoIdentity> findByProviderIdAndSubject(UUID providerId, String subject);

    Optional<SsoIdentity> findByProviderIdAndUserId(UUID providerId, UUID userId);

    List<SsoIdentity> findByUserId(UUID userId);
}
