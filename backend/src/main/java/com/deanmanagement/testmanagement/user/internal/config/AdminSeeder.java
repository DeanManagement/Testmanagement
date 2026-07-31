package com.deanmanagement.testmanagement.user.internal.config;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    // Unambiguous alphabet (no 0/O, 1/l/I) — the password is read from a log line.
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int GENERATED_PASSWORD_LENGTH = 20;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminConfig adminConfig;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminConfig.getEmail())) {
            return;
        }

        String password = adminConfig.getPassword();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            // No ADMIN_PASSWORD configured (PRD-019): never seed a well-known default.
            password = generatePassword();
        }

        User admin = new User();
        admin.setEmail(adminConfig.getEmail());
        admin.setDisplayName(adminConfig.getDisplayName());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setSystemAdmin(true);
        admin.setForcePasswordChange(true);
        userRepository.save(admin);

        if (generated) {
            log.warn("""

                    ====================================================================
                    Seeded admin account '{}' with a GENERATED password:

                        {}

                    This is printed ONCE. You must change it at first login.
                    Set ADMIN_PASSWORD to seed a chosen password instead.
                    ====================================================================""",
                    adminConfig.getEmail(), password);
        }
    }

    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
