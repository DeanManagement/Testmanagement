package com.deanmanagement.testmanagement.user.internal.services;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.requests.ChangePasswordRequest;
import com.deanmanagement.testmanagement.user.internal.requests.UserResponse;
import com.deanmanagement.testmanagement.user.internal.requests.LoginRequest;
import com.deanmanagement.testmanagement.user.internal.requests.LoginResponse;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.deanmanagement.testmanagement.user.internal.config.JwtConfig;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtEncoder jwtEncoder;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;
    private final LoginThrottleService loginThrottle;

    /**
     * Read through a supplier rather than injecting the SSO service: authentication must not
     * depend on the SSO module being present, and this keeps the wiring one-directional.
     */
    private final java.util.function.BooleanSupplier localLoginEnabled;

    /**
     * PRD-020: verified against when the email is unknown, so "unknown user" and "wrong
     * password" take the same BCrypt time — no user-enumeration timing oracle.
     */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, JwtEncoder jwtEncoder,
                       BCryptPasswordEncoder passwordEncoder, JwtConfig jwtConfig,
                       LoginThrottleService loginThrottle,
                       LocalLoginPolicy localLoginPolicy) {
        this.userRepository = userRepository;
        this.jwtEncoder = jwtEncoder;
        this.passwordEncoder = passwordEncoder;
        this.jwtConfig = jwtConfig;
        this.loginThrottle = loginThrottle;
        this.localLoginEnabled = localLoginPolicy::isLocalLoginEnabled;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request, String remoteIp) {
        loginThrottle.checkAllowed(request.email(), remoteIp);

        User user = userRepository.findByEmail(request.email().toLowerCase()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !matches) {
            loginThrottle.recordFailure(request.email(), remoteIp);
            throw new BadCredentialsException("Invalid email or password");
        }

        // PRD-025 §3.2: service accounts back API keys and must never hold a session. Their null
        // password hash already makes `matches` false, so this is belt and braces — but it is the
        // check that documents the intent, and it is asserted by test.
        if (user.isServiceAccount()) {
            loginThrottle.recordFailure(request.email(), remoteIp);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Break-glass (PRD-012 §4.2): when local login is switched off, system admins may still
        // use a password. The check happens after credential verification so it cannot be used to
        // discover which accounts are admins.
        if (!localLoginAllowed(user)) {
            loginThrottle.recordFailure(request.email(), remoteIp);
            throw new BadCredentialsException(
                    "Password sign-in is disabled. Use one of the single sign-on options.");
        }

        loginThrottle.recordSuccess(request.email(), remoteIp);
        String token = generateToken(user);
        UserResponse userResponse = toUserResponse(user);
        return new LoginResponse(token, userResponse, user.isForcePasswordChange());
    }

    private boolean localLoginAllowed(User user) {
        return user.isSystemAdmin() || localLoginEnabled.getAsBoolean();
    }

    /**
     * Server-side logout (PRD-020): bumping {@code token_version} invalidates every
     * outstanding token for this user — the JWT filter rejects stale versions.
     */
    @Transactional
    public void logout(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        });
    }

    /**
     * Changes the password and invalidates all existing tokens (PRD-020). Returns a fresh
     * token (minted with the new version) so the current session survives the change.
     */
    @Transactional
    public String changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setForcePasswordChange(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        return generateToken(user);
    }

    public UserResponse getCurrentUser(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return toUserResponse(user);
    }

    /**
     * Mints a session token for a user who has already proved their identity some other way —
     * currently an SSO login (PRD-012). Deliberately package-visible in spirit but public because
     * the SSO handler lives in a sibling package: it must never be reachable without prior
     * authentication, so nothing calls it from a controller.
     */
    public String issueToken(User user) {
        return generateToken(user);
    }

    private String generateToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("systemAdmin", user.isSystemAdmin())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(now)
                .expiresAt(now.plusMillis(jwtConfig.getExpirationMs()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isSystemAdmin(),
                user.getCreatedAt()
        );
    }
}
