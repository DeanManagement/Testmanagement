package com.deanmanagement.testmanagement.user.internal.controller;

import com.deanmanagement.testmanagement.user.internal.requests.ChangePasswordRequest;
import com.deanmanagement.testmanagement.user.internal.requests.UserResponse;
import com.deanmanagement.testmanagement.user.internal.services.AuthService;
import com.deanmanagement.testmanagement.user.internal.requests.LoginRequest;
import com.deanmanagement.testmanagement.user.internal.requests.LoginResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication endpoints")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        // With forward-headers-strategy=native + remoteip.remote-ip-header=X-Real-IP,
        // getRemoteAddr() resolves the real client behind the bundled nginx proxy while
        // ignoring the spoofable, appendable X-Forwarded-For chain.
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    /** Server-side logout (PRD-020): invalidates every outstanding token for the caller. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        authService.logout(UUID.fromString(authentication.getName()));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.getCurrentUser(authentication);
    }

    /**
     * Changes the password and invalidates all previous tokens; returns a fresh token so
     * the current session continues seamlessly (PRD-020).
     */
    @PostMapping("/change-password")
    public Map<String, String> changePassword(Authentication authentication,
                                              @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return Map.of("token", authService.changePassword(userId, request));
    }
}
