package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.shared.crypto.AesGcmCipher;
import com.deanmanagement.testmanagement.shared.crypto.SecretCipherConfig;
import org.springframework.stereotype.Component;

/**
 * Encrypts stored issue-tracker API tokens (PRD-010 §3.4).
 *
 * <p>Uses the application-wide key, falling back to the feature-specific
 * {@code app.issuetracker.encryption-key} when that is the one an operator has already set. The
 * fallback exists so upgrading does not silently render stored tokens undecryptable — a deployment
 * configured before the shared key was introduced keeps working untouched.
 */
@Component
public class IssueTrackerTokenCipher {

    private static final String LEGACY_KEY_PROPERTY =
            "app.issuetracker.encryption-key (env ISSUE_TRACKER_ENCRYPTION_KEY)";

    private final AesGcmCipher delegate;

    public IssueTrackerTokenCipher(IssueTrackerProperties properties, AesGcmCipher sharedCipher) {
        String legacyKey = properties.encryptionKey();
        this.delegate = (legacyKey != null && !legacyKey.isBlank())
                ? new AesGcmCipher(legacyKey, LEGACY_KEY_PROPERTY)
                : sharedCipher;
    }

    /** Whether a usable key is configured. Endpoints check this to fail with a clear message. */
    public boolean isConfigured() {
        return delegate.isConfigured();
    }

    public String encrypt(String plaintext) {
        return delegate.encrypt(plaintext);
    }

    public String decrypt(String stored) {
        return delegate.decrypt(stored);
    }
}
