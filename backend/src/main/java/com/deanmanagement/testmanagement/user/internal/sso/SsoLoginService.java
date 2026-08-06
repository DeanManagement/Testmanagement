package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a successful SSO authentication into a local {@link User} (PRD-012 §4.1).
 *
 * <p>Takes an {@link OAuth2User} rather than an {@code OidcUser} so an OIDC login and a GitHub
 * login are subject to one identical set of rules. Everything below reads from the principal's
 * attributes, and {@code GitHubOAuth2UserService} is responsible for making GitHub's response look
 * like a claim set — including only ever presenting an email GitHub has verified.
 *
 * <p>This is the security-critical part of the feature, so the rules are spelled out rather than
 * inferred:
 *
 * <ol>
 *   <li>A known {@code (provider, sub)} logs in as its linked user.</li>
 *   <li>An unknown subject whose email matches an existing account is adopted <em>only</em> when the
 *       provider is trusted for email linking and the IdP asserts the email is verified. Otherwise
 *       the login is refused.</li>
 *   <li>An unknown subject with no matching email is provisioned with no project access, if the
 *       provider allows it.</li>
 * </ol>
 *
 * <p>Rule 2 is the account-takeover boundary. Email is not proof of identity: some IdPs let a user
 * set any address they like, and addresses are reassigned between employees. Adopting an account on
 * an unverified or untrusted email would let anyone who can register at a configured IdP claim an
 * existing system admin's account, so both conditions are required and both default to off.
 */
@Service
@RequiredArgsConstructor
public class SsoLoginService {

    private static final Logger log = LoggerFactory.getLogger(SsoLoginService.class);

    private final UserRepository userRepository;
    private final SsoIdentityRepository identityRepository;

    /**
     * @return the local user this login corresponds to
     * @throws SsoLoginException when the login must be refused; the message is shown to the user
     */
    @Transactional
    public User resolveUser(SsoProvider provider, OAuth2User principal) {
        String subject = subjectOf(principal);
        if (subject == null || subject.isBlank()) {
            throw new SsoLoginException("The identity provider did not return a subject claim");
        }

        Map<String, Object> claims = principal.getAttributes();
        String email = normaliseEmail(claimAsString(claims, provider.getEmailClaim()));
        String displayName = firstNonBlank(
                claimAsString(claims, provider.getNameClaim()),
                email,
                subject);
        boolean shouldBeAdmin = matchesAdminClaim(provider, claims);

        Optional<SsoIdentity> existing = identityRepository.findByProviderIdAndSubject(provider.getId(), subject);
        if (existing.isPresent()) {
            User user = userRepository.findById(existing.get().getUserId())
                    .orElseThrow(() -> new SsoLoginException("The linked account no longer exists"));
            refreshProfile(user, email, displayName, provider, shouldBeAdmin);
            touch(existing.get());
            return user;
        }

        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                return adoptExistingAccount(provider, byEmail.get(), subject, claims, displayName, shouldBeAdmin);
            }
        }

        if (!provider.isAutoProvision()) {
            throw new SsoLoginException(
                    "No account exists for you here. Ask an administrator to create one.");
        }
        return provision(provider, subject, email, displayName, shouldBeAdmin);
    }

    private User adoptExistingAccount(SsoProvider provider, User user, String subject,
                                      Map<String, Object> claims, String displayName, boolean shouldBeAdmin) {
        if (!provider.isTrustEmailForLinking()) {
            log.warn("Refused SSO login for subject {} on provider {}: email matches an existing "
                    + "account but the provider is not trusted for email linking", subject, provider.getSlug());
            throw new SsoLoginException(
                    "An account with this email already exists. Ask an administrator to link it to "
                            + provider.getDisplayName() + ".");
        }
        if (!isEmailVerified(claims)) {
            log.warn("Refused SSO login for subject {} on provider {}: email is not verified",
                    subject, provider.getSlug());
            throw new SsoLoginException(
                    "Your identity provider has not verified this email address, so it cannot be "
                            + "used to sign in to an existing account.");
        }
        // A second subject must not attach to a user already linked to this provider — that would
        // mean two IdP identities sharing one account.
        if (identityRepository.findByProviderIdAndUserId(provider.getId(), user.getId()).isPresent()) {
            throw new SsoLoginException(
                    "This account is already linked to a different " + provider.getDisplayName() + " identity.");
        }

        link(provider, user, subject);
        refreshProfile(user, user.getEmail(), displayName, provider, shouldBeAdmin);
        log.info("Linked existing user {} to provider {} via verified email", user.getId(), provider.getSlug());
        return user;
    }

    private User provision(SsoProvider provider, String subject, String email,
                           String displayName, boolean shouldBeAdmin) {
        if (email == null) {
            throw new SsoLoginException(
                    "The identity provider did not return an email address, which is required to "
                            + "create an account.");
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        // No usable password: this account can only be reached through its provider unless an admin
        // sets one. A random hash rather than null keeps the column non-null and unmatched by BCrypt.
        user.setPasswordHash("sso:" + UUID.randomUUID());
        user.setSystemAdmin(shouldBeAdmin);
        user.setForcePasswordChange(false);
        user = userRepository.save(user);

        link(provider, user, subject);
        // Deliberately no ProjectMember rows: a new arrival can sign in but sees nothing until an
        // admin grants access. Auto-joining projects would let anyone who can authenticate at the
        // IdP read test data.
        log.info("Provisioned user {} from provider {}", user.getId(), provider.getSlug());
        return user;
    }

    private void link(SsoProvider provider, User user, String subject) {
        SsoIdentity identity = new SsoIdentity();
        identity.setProviderId(provider.getId());
        identity.setUserId(user.getId());
        identity.setSubject(subject);
        identity.setLastLoginAt(Instant.now());
        identityRepository.save(identity);
    }

    private void touch(SsoIdentity identity) {
        identity.setLastLoginAt(Instant.now());
        identityRepository.save(identity);
    }

    /**
     * Refreshes profile fields from the token on each login. The email is only moved when the
     * account has no other way in, so an SSO login cannot rewrite the address a local user signs in
     * with.
     */
    private void refreshProfile(User user, String email, String displayName,
                                SsoProvider provider, boolean shouldBeAdmin) {
        boolean changed = false;
        if (displayName != null && !displayName.equals(user.getDisplayName())) {
            user.setDisplayName(displayName);
            changed = true;
        }
        if (email != null && !email.equals(user.getEmail())
                && userRepository.findByEmail(email).isEmpty()) {
            user.setEmail(email);
            changed = true;
        }
        // Only touch the admin flag when the provider actually maps one, so a provider without an
        // admin claim never demotes an admin who was granted the flag locally.
        if (provider.getAdminClaim() != null && user.isSystemAdmin() != shouldBeAdmin) {
            user.setSystemAdmin(shouldBeAdmin);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
    }

    private static boolean matchesAdminClaim(SsoProvider provider, Map<String, Object> claims) {
        if (provider.getAdminClaim() == null || provider.getAdminClaimValue() == null) {
            return false;
        }
        Object value = claims.get(provider.getAdminClaim());
        if (value == null) {
            return false;
        }
        // Group and role claims are usually arrays, so membership counts as a match.
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (provider.getAdminClaimValue().equals(String.valueOf(item))) {
                    return true;
                }
            }
            return false;
        }
        return provider.getAdminClaimValue().equals(String.valueOf(value));
    }

    /**
     * The stable, provider-scoped identifier this login is keyed on.
     *
     * <p>For OIDC that is the {@code sub} claim, which the spec guarantees is never reassigned. For
     * a plain OAuth2 principal it is {@code getName()}, which resolves to whatever the registration
     * named as its username attribute — GitHub's immutable numeric {@code id}, deliberately not the
     * renameable {@code login}. Both are read through the principal rather than the attribute map so
     * neither can be spoofed by an attribute of the same name.
     */
    private static String subjectOf(OAuth2User principal) {
        return principal instanceof OidcUser oidcUser ? oidcUser.getSubject() : principal.getName();
    }

    private static boolean isEmailVerified(Map<String, Object> claims) {
        Object verified = claims.get("email_verified");
        if (verified instanceof Boolean b) {
            return b;
        }
        // Some IdPs send the claim as a string; anything absent or unparseable counts as unverified.
        return "true".equalsIgnoreCase(String.valueOf(verified));
    }

    private static String claimAsString(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static String normaliseEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        // Local login lower-cases on lookup, so SSO must too or the same person gets two accounts.
        return email.trim().toLowerCase();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
