package com.deanmanagement.testmanagement.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application-wide secret cipher.
 *
 * <p>One key, {@code app.security.encryption-key}, covers every feature that stores a third-party
 * secret. Per-feature keys were considered — they limit blast radius — but they multiply the number
 * of secrets an operator has to generate, distribute and rotate, and an unset key is a hard failure
 * at the point of use. One key that people actually set beats three that they don't.
 */
@Configuration
public class SecretCipherConfig {

    public static final String KEY_PROPERTY = "app.security.encryption-key";

    @Bean
    public AesGcmCipher secretCipher(@Value("${app.security.encryption-key:}") String key) {
        return new AesGcmCipher(key, KEY_PROPERTY);
    }
}
