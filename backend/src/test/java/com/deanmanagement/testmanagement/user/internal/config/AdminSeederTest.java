package com.deanmanagement.testmanagement.user.internal.config;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PRD-019: never seed the admin with a well-known default password. */
@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminSeeder seeder(String configuredPassword) {
        AdminConfig config = new AdminConfig();
        config.setEmail("admin@test.local");
        config.setDisplayName("Admin");
        config.setPassword(configuredPassword);
        return new AdminSeeder(userRepository, encoder, config);
    }

    @Test
    void blankPassword_generatesRandomOne_andForcesChange() {
        when(userRepository.existsByEmail("admin@test.local")).thenReturn(false);

        seeder("").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.isForcePasswordChange()).isTrue();
        assertThat(admin.getPasswordHash()).isNotBlank();
        // Must not be the old default.
        assertThat(encoder.matches("admin", admin.getPasswordHash())).isFalse();
    }

    @Test
    void configuredPassword_isUsed_andForcesChange() {
        when(userRepository.existsByEmail("admin@test.local")).thenReturn(false);

        seeder("chosen-password-123").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(encoder.matches("chosen-password-123", captor.getValue().getPasswordHash())).isTrue();
        assertThat(captor.getValue().isForcePasswordChange()).isTrue();
    }

    @Test
    void existingAdmin_isNeverTouched() {
        when(userRepository.existsByEmail("admin@test.local")).thenReturn(true);

        seeder("").run(null);

        verify(userRepository, never()).save(any());
    }
}
