package com.deanmanagement.testmanagement.user.internal.sso;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the login screen needs before anyone has authenticated (PRD-012 §3.3). Public by necessity:
 * the browser has to know which buttons to draw before it has a token.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public authentication configuration")
@RequiredArgsConstructor
public class AuthConfigController {

    private final SsoProviderService providerService;

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return providerService.authConfig();
    }
}
