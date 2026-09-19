package com.deanmanagement.testmanagement.shared.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("securityAuditorAware")
public class SecurityAuditorAware implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        // The name, as every controller reads it: the principal is a String for a JWT or an API key,
        // but a UserDetails for other authentication types, which used to leave createdBy empty.
        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException notAUserId) {
            return Optional.empty();
        }
    }
}
