package com.deanmanagement.testmanagement.user.internal.services;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.requests.UserResponse;
import com.deanmanagement.testmanagement.user.internal.requests.LoginRequest;
import com.deanmanagement.testmanagement.user.internal.requests.LoginResponse;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.deanmanagement.testmanagement.user.internal.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtEncoder jwtEncoder;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = generateToken(user);
        UserResponse userResponse = toUserResponse(user);
        return new LoginResponse(token, userResponse);
    }

    public UserResponse getCurrentUser(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return toUserResponse(user);
    }

    private String generateToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("systemAdmin", user.isSystemAdmin())
                .issuedAt(now)
                .expiresAt(now.plusMillis(jwtConfig.getExpirationMs()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isSystemAdmin(),
                user.getCreatedAt()
        );
    }
}
