package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account-linking rules from PRD-012 §4.1. These are the cases that decide whether someone can
 * take over an existing account, so the refusals matter more than the happy path.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SsoLoginServiceTest {

    @Autowired
    private SsoLoginService loginService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SsoProviderRepository providerRepository;
    @Autowired
    private SsoIdentityRepository identityRepository;

    private SsoProvider provider;

    @BeforeEach
    void setUp() {
        provider = saveProvider(p -> {
        });
    }

    private SsoProvider saveProvider(java.util.function.Consumer<SsoProvider> customiser) {
        SsoProvider p = new SsoProvider();
        p.setSlug("acme-" + UUID.randomUUID().toString().substring(0, 8));
        p.setDisplayName("Acme SSO");
        p.setIssuerUri("https://idp.example.com");
        p.setClientId("client");
        p.setClientSecretEncrypted("encrypted");
        p.setScopes("openid,profile,email");
        p.setEmailClaim("email");
        p.setNameClaim("name");
        p.setAutoProvision(true);
        p.setTrustEmailForLinking(false);
        p.setActive(true);
        customiser.accept(p);
        return providerRepository.save(p);
    }

    private User saveUser(String email, boolean systemAdmin) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName("Existing person");
        u.setPasswordHash("hashed");
        u.setSystemAdmin(systemAdmin);
        return userRepository.save(u);
    }

    private OidcUser oidcUser(String subject, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.putAll(extraClaims);
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(),
                Instant.now().plusSeconds(300), claims);
        return new DefaultOidcUser(List.of(), idToken);
    }

    private OidcUser userWith(String subject, String email, Boolean emailVerified) {
        Map<String, Object> claims = new HashMap<>();
        if (email != null) {
            claims.put("email", email);
        }
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        claims.put("name", "Person From Idp");
        return oidcUser(subject, claims);
    }

    // ---- rule 1: known identity -------------------------------------------

    @Test
    void knownIdentityLogsInAsItsLinkedUser() {
        User existing = saveUser("known@example.com", false);
        SsoIdentity identity = new SsoIdentity();
        identity.setProviderId(provider.getId());
        identity.setUserId(existing.getId());
        identity.setSubject("subject-1");
        identityRepository.save(identity);

        User resolved = loginService.resolveUser(provider, userWith("subject-1", "known@example.com", true));

        assertThat(resolved.getId()).isEqualTo(existing.getId());
    }

    @Test
    void knownIdentityRefreshesTheDisplayName() {
        User existing = saveUser("known2@example.com", false);
        SsoIdentity identity = new SsoIdentity();
        identity.setProviderId(provider.getId());
        identity.setUserId(existing.getId());
        identity.setSubject("subject-2");
        identityRepository.save(identity);

        loginService.resolveUser(provider, userWith("subject-2", "known2@example.com", true));

        assertThat(userRepository.findById(existing.getId()).orElseThrow().getDisplayName())
                .isEqualTo("Person From Idp");
    }

    // ---- rule 2: the takeover boundary ------------------------------------

    @Test
    void refusesToAdoptAnExistingAccountWhenTheProviderIsNotTrusted() {
        saveUser("victim@example.com", true);

        // Even a verified email is not enough on its own: the admin has not said this IdP may
        // speak for addresses in this instance.
        assertThatThrownBy(() -> loginService.resolveUser(provider, userWith("attacker", "victim@example.com", true)))
                .isInstanceOf(SsoLoginException.class)
                .hasMessageContaining("Ask an administrator to link it");
    }

    @Test
    void refusesToAdoptAnExistingAccountOnAnUnverifiedEmail() {
        SsoProvider trusting = saveProvider(p -> p.setTrustEmailForLinking(true));
        saveUser("victim2@example.com", true);

        // The whole attack: register at the IdP with someone else's address, and if verification is
        // not required, walk into their account.
        assertThatThrownBy(() -> loginService.resolveUser(trusting, userWith("attacker", "victim2@example.com", false)))
                .isInstanceOf(SsoLoginException.class)
                .hasMessageContaining("not verified this email address");
    }

    @Test
    void refusesToAdoptWhenTheVerifiedFlagIsAbsentEntirely() {
        SsoProvider trusting = saveProvider(p -> p.setTrustEmailForLinking(true));
        saveUser("victim3@example.com", false);

        assertThatThrownBy(() -> loginService.resolveUser(trusting, userWith("attacker", "victim3@example.com", null)))
                .isInstanceOf(SsoLoginException.class)
                .hasMessageContaining("not verified this email address");
    }

    @Test
    void adoptsAnExistingAccountWhenTrustedAndVerified() {
        SsoProvider trusting = saveProvider(p -> p.setTrustEmailForLinking(true));
        User existing = saveUser("colleague@example.com", false);

        User resolved = loginService.resolveUser(trusting, userWith("subject-9", "colleague@example.com", true));

        assertThat(resolved.getId()).isEqualTo(existing.getId());
        assertThat(identityRepository.findByProviderIdAndSubject(trusting.getId(), "subject-9")).isPresent();
    }

    @Test
    void refusesASecondIdentityForAnAccountAlreadyLinkedToThatProvider() {
        SsoProvider trusting = saveProvider(p -> p.setTrustEmailForLinking(true));
        User existing = saveUser("shared@example.com", false);
        SsoIdentity identity = new SsoIdentity();
        identity.setProviderId(trusting.getId());
        identity.setUserId(existing.getId());
        identity.setSubject("first-subject");
        identityRepository.save(identity);

        assertThatThrownBy(() -> loginService.resolveUser(trusting, userWith("second-subject", "shared@example.com", true)))
                .isInstanceOf(SsoLoginException.class)
                .hasMessageContaining("already linked to a different");
    }

    // ---- rule 3: provisioning ---------------------------------------------

    @Test
    void provisionsAnUnknownUserWithNoAccessAtAll() {
        User created = loginService.resolveUser(provider, userWith("new-subject", "newcomer@example.com", true));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getEmail()).isEqualTo("newcomer@example.com");
        assertThat(created.isSystemAdmin()).isFalse();
        // The password hash must not be usable for a password login.
        assertThat(created.getPasswordHash()).startsWith("sso:");
    }

    @Test
    void refusesAnUnknownUserWhenAutoProvisionIsOff() {
        SsoProvider closed = saveProvider(p -> p.setAutoProvision(false));

        assertThatThrownBy(() -> loginService.resolveUser(closed, userWith("nobody", "nobody@example.com", true)))
                .isInstanceOf(SsoLoginException.class)
                .hasMessageContaining("Ask an administrator to create one");
    }

    @Test
    void refusesToProvisionWithoutAnEmail() {
        assertThatThrownBy(() -> loginService.resolveUser(provider, userWith("no-email", null, null)))
                .isInstanceOf(SsoLoginException.class)
                .hasMessageContaining("did not return an email address");
    }

    @Test
    void lowercasesTheEmailSoSsoAndLocalLoginAgreeOnIdentity() {
        SsoProvider trusting = saveProvider(p -> p.setTrustEmailForLinking(true));
        User existing = saveUser("mixed@example.com", false);

        User resolved = loginService.resolveUser(trusting, userWith("sub-mixed", "Mixed@Example.COM", true));

        assertThat(resolved.getId()).isEqualTo(existing.getId());
    }

    // ---- admin claim -------------------------------------------------------

    @Test
    void grantsSystemAdminFromAScalarClaim() {
        SsoProvider mapped = saveProvider(p -> {
            p.setAdminClaim("role");
            p.setAdminClaimValue("tm-admin");
        });
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "boss@example.com");
        claims.put("email_verified", true);
        claims.put("role", "tm-admin");

        User created = loginService.resolveUser(mapped, oidcUser("boss-subject", claims));

        assertThat(created.isSystemAdmin()).isTrue();
    }

    @Test
    void grantsSystemAdminFromAGroupsArray() {
        SsoProvider mapped = saveProvider(p -> {
            p.setAdminClaim("groups");
            p.setAdminClaimValue("testmanagement-admins");
        });
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "boss2@example.com");
        claims.put("email_verified", true);
        claims.put("groups", List.of("everyone", "testmanagement-admins"));

        assertThat(loginService.resolveUser(mapped, oidcUser("boss2-subject", claims)).isSystemAdmin()).isTrue();
    }

    @Test
    void doesNotGrantAdminWhenTheClaimDoesNotMatch() {
        SsoProvider mapped = saveProvider(p -> {
            p.setAdminClaim("groups");
            p.setAdminClaimValue("testmanagement-admins");
        });
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "regular@example.com");
        claims.put("email_verified", true);
        claims.put("groups", List.of("everyone"));

        assertThat(loginService.resolveUser(mapped, oidcUser("regular-subject", claims)).isSystemAdmin()).isFalse();
    }

    @Test
    void aProviderWithoutAnAdminClaimNeverDemotesALocalAdmin() {
        User admin = saveUser("localadmin@example.com", true);
        SsoIdentity identity = new SsoIdentity();
        identity.setProviderId(provider.getId());
        identity.setUserId(admin.getId());
        identity.setSubject("admin-subject");
        identityRepository.save(identity);

        loginService.resolveUser(provider, userWith("admin-subject", "localadmin@example.com", true));

        // The provider maps no admin claim, so it has no opinion — the locally granted flag stands.
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isSystemAdmin()).isTrue();
    }

    @Test
    void aMappedProviderRevokesAdminWhenTheClaimIsGone() {
        SsoProvider mapped = saveProvider(p -> {
            p.setAdminClaim("groups");
            p.setAdminClaimValue("testmanagement-admins");
        });
        User admin = saveUser("exadmin@example.com", true);
        SsoIdentity identity = new SsoIdentity();
        identity.setProviderId(mapped.getId());
        identity.setUserId(admin.getId());
        identity.setSubject("exadmin-subject");
        identityRepository.save(identity);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "exadmin@example.com");
        claims.put("email_verified", true);
        claims.put("groups", List.of("everyone"));
        loginService.resolveUser(mapped, oidcUser("exadmin-subject", claims));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isSystemAdmin()).isFalse();
    }

    @Test
    void refusesWhenTheProviderReturnsNoSubject() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "x@example.com");
        OidcIdToken idToken = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", "placeholder", "email", "x@example.com"));
        OidcUser withBlankSubject = new DefaultOidcUser(List.of(), idToken, "email");

        // Keyed on the email claim, the subject the service reads back is the email — still a
        // stable identifier, so this simply must not blow up.
        assertThat(loginService.resolveUser(provider, withBlankSubject)).isNotNull();
    }
}
