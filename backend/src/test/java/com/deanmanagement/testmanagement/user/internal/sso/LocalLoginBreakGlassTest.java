package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.deanmanagement.testmanagement.user.internal.requests.LoginRequest;
import com.deanmanagement.testmanagement.user.internal.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The break-glass rule (PRD-012 §4.2): turning off password sign-in must not be able to lock an
 * instance out. System admins keep it; everyone else is pushed to SSO.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class LocalLoginBreakGlassTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private SsoProviderService providerService;
    @Autowired
    private SsoProviderRepository providerRepository;

    private String adminEmail;
    private String regularEmail;

    @BeforeEach
    void setUp() {
        adminEmail = saveUser(true).getEmail();
        regularEmail = saveUser(false).getEmail();

        // Disabling local login requires an active provider to exist.
        SsoProvider provider = new SsoProvider();
        provider.setSlug("break-glass-idp");
        provider.setDisplayName("IdP");
        provider.setIssuerUri("https://idp.example.com");
        provider.setClientId("c");
        provider.setClientSecretEncrypted("encrypted");
        provider.setScopes("openid");
        provider.setEmailClaim("email");
        provider.setNameClaim("name");
        provider.setActive(true);
        providerRepository.save(provider);
    }

    private User saveUser(boolean systemAdmin) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setSystemAdmin(systemAdmin);
        return userRepository.save(u);
    }

    private void disableLocalLogin() {
        providerService.updateAuthSettings(new UpdateAuthSettingsRequest(false));
    }

    @Test
    void bothCanLogInWhileLocalLoginIsEnabled() {
        assertThatCode(() -> authService.login(new LoginRequest(adminEmail, PASSWORD), "10.0.0.1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authService.login(new LoginRequest(regularEmail, PASSWORD), "10.0.0.2"))
                .doesNotThrowAnyException();
    }

    @Test
    void adminKeepsPasswordAccessWhenLocalLoginIsDisabled() {
        disableLocalLogin();

        // The whole point: a misconfigured IdP must still leave a way back in.
        assertThat(authService.login(new LoginRequest(adminEmail, PASSWORD), "10.0.0.3").token())
                .isNotBlank();
    }

    @Test
    void regularUserIsPushedToSsoWhenLocalLoginIsDisabled() {
        disableLocalLogin();

        assertThatThrownBy(() -> authService.login(new LoginRequest(regularEmail, PASSWORD), "10.0.0.4"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("single sign-on");
    }

    @Test
    void aWrongPasswordStillFailsAsAWrongPasswordForAdmins() {
        disableLocalLogin();

        assertThatThrownBy(() -> authService.login(new LoginRequest(adminEmail, "wrong"), "10.0.0.5"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void theDisabledMessageOnlyAppearsAfterCorrectCredentials() {
        disableLocalLogin();

        // Checking the policy before verifying the password would turn this into an oracle for
        // "is this address a system admin?".
        assertThatThrownBy(() -> authService.login(new LoginRequest(regularEmail, "wrong"), "10.0.0.6"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
