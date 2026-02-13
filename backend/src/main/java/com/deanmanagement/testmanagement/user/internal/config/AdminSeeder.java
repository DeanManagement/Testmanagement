package com.deanmanagement.testmanagement.user.internal.config;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminConfig adminConfig;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminConfig.getEmail())) {
            return;
        }
        User admin = new User();
        admin.setEmail(adminConfig.getEmail());
        admin.setDisplayName(adminConfig.getDisplayName());
        admin.setPasswordHash(passwordEncoder.encode(adminConfig.getPassword()));
        admin.setSystemAdmin(true);
        userRepository.save(admin);
    }
}
