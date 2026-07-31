package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.user.internal.services.LocalLoginPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Answers the auth layer's local-login question from the stored auth settings (PRD-012). */
@Component
@RequiredArgsConstructor
public class SsoLocalLoginPolicy implements LocalLoginPolicy {

    private final SsoProviderService providerService;

    @Override
    public boolean isLocalLoginEnabled() {
        return providerService.localLoginEnabled();
    }
}
