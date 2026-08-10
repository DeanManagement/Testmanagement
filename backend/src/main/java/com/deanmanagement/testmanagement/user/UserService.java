package com.deanmanagement.testmanagement.user;

import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.internal.requests.UserResponse;
import com.deanmanagement.testmanagement.user.internal.requests.CreateUserRequest;
import com.deanmanagement.testmanagement.user.internal.requests.UpdateUserRequest;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /** Human accounts only — service accounts (PRD-025 §3.2) are managed via API keys, not here. */
    public List<UserResponse> findAll() {
        return userRepository.findByServiceAccountFalse().stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .filter(candidate -> !candidate.isServiceAccount())
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return toResponse(user);
    }

    public Optional<User> findEntityById(UUID id) {
        return userRepository.findById(id);
    }

    /** Batch variant of {@link #findEntityById} for resolving display names without per-row queries. */
    public Map<UUID, String> findDisplayNamesByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getDisplayName());
        }
        return names;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        var email = request.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateKeyException(email, "email");
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setSystemAdmin(request.systemAdmin());
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.isServiceAccount()) {
            // Otherwise an admin holding a service account's id — which is now readable from
            // created_by on every row its key wrote — could set systemAdmin, and
            // ProjectAccessService short-circuits on that. The key would become an unrestricted
            // global admin while staying invisible in the user list, defeating the VIEWER/TESTER
            // ceiling that ApiKeyService.create enforces.
            throw new ForbiddenException("Service accounts are managed through their API key");
        }

        user.setDisplayName(request.displayName());
        if (request.systemAdmin() != null) {
            user.setSystemAdmin(request.systemAdmin());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user = userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.isServiceAccount()) {
            // Deleting it here would orphan the API key that authenticates as it.
            throw new ForbiddenException("Service accounts are removed by revoking their API key");
        }
        userRepository.delete(user);
    }

    /**
     * PRD-025 §3.2: creates the non-human account an API key authenticates as. Exposed on the
     * module's public surface because {@code ApiKeyService} lives in the {@code project} module and
     * must not reach into {@code user.internal}.
     *
     * <p>No password hash is set, and {@code serviceAccount} is checked explicitly on both sign-in
     * paths — a null hash alone would not block SSO, which links by email.
     */
    @Transactional
    public User createServiceAccount(String email, String displayName) {
        User user = new User();
        user.setEmail(email.toLowerCase());
        user.setDisplayName(displayName);
        user.setPasswordHash(null);
        user.setSystemAdmin(false);
        user.setServiceAccount(true);
        return userRepository.save(user);
    }


    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isSystemAdmin(),
                user.getCreatedAt()
        );
    }
}
