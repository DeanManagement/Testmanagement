package com.deanmanagement.testmanagement.user.internal.sso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthSettingsRepository extends JpaRepository<AuthSettings, UUID> {
}
