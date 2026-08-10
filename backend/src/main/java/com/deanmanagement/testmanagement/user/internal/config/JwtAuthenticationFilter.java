package com.deanmanagement.testmanagement.user.internal.config;

import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder, UserRepository userRepository) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/auth/login")
                || path.startsWith("/api/external/")
                // PRD-025: API-key territory. Redundant — the @Order(1) chain claims these first —
                // but it mirrors the /api/external/ entry and costs nothing. Note the trailing
                // slash: /api/mcp-activity is an admin endpoint and must keep its JWT.
                || path.equals("/api/mcp") || path.startsWith("/api/mcp/")
                // An API key presented as a bearer token is not a JWT and must not be decoded as
                // one: this filter would answer with its own "Invalid or expired token" 401, which
                // is exactly the dead end ApiKeySignpostEntryPoint exists to replace.
                || carriesApiKeyBearer(request);
    }

    /** {@code tm_} is the API-key prefix; nothing else uses it, so this cannot swallow a JWT. */
    private static boolean carriesApiKeyBearer(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer tm_");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        // PRD-018: the former allure_session cookie path is gone — Allure report viewing now
        // uses short-lived view tokens validated in AllureReportController.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String userId = jwt.getSubject();
            Boolean systemAdmin = jwt.getClaim("systemAdmin");

            // PRD-020: reject tokens minted before the user's last logout / password change.
            // Tokens issued before this feature carry no claim and are treated as version 0.
            Long claimedVersion = jwt.getClaim("tokenVersion");
            int currentVersion = userRepository.findById(java.util.UUID.fromString(userId))
                    .map(com.deanmanagement.testmanagement.user.User::getTokenVersion)
                    .orElse(-1);
            if (currentVersion < 0 || (claimedVersion == null ? 0 : claimedVersion) != currentVersion) {
                throw new JwtException("Token has been invalidated");
            }

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            if (Boolean.TRUE.equals(systemAdmin)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, jwt, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            log.warn("JWT authentication failed for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Invalid or expired token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
