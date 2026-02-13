package com.deanmanagement.testmanagement.user;

import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.internal.requests.UserResponse;
import com.deanmanagement.testmanagement.user.internal.requests.CreateUserRequest;
import com.deanmanagement.testmanagement.user.internal.requests.UpdateUserRequest;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return toResponse(user);
    }

    public Optional<User> findEntityById(UUID id) {
        return userRepository.findById(id);
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
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
        userRepository.deleteById(id);
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
