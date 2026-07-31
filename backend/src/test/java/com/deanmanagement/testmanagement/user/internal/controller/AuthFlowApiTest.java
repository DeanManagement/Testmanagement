package com.deanmanagement.testmanagement.user.internal.controller;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD-020: real JWT round trips — login throttling, server-side logout via token_version,
 * and token rotation on password change. Unlike most API tests (which use the
 * SecurityMockMvc user() post-processor), these requests carry actual Bearer tokens so the
 * JwtAuthenticationFilter's version check is exercised.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class AuthFlowApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BCryptPasswordEncoder encoder;

    private String email;
    private static final String PASSWORD = "correct-horse-battery";

    @BeforeEach
    void setUp() {
        email = "auth-" + UUID.randomUUID() + "@test.local";
        User u = new User();
        u.setEmail(email);
        u.setDisplayName("auth user");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setSystemAdmin(false);
        userRepository.save(u);
    }

    private String loginBody(String pw) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}";
    }

    private String extract(String json, String field) {
        return json.replaceAll(".*\"" + field + "\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String stripTimestamp(String json) {
        return json.replaceAll("\"timestamp\"\\s*:\\s*\"[^\"]+\"", "\"timestamp\":\"-\"");
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return extract(body, "token");
    }

    @Test
    void loginToken_carriesVersion_andWorksOnMe() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void logout_invalidatesOutstandingTokens() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChange_rotatesToken_oldDiesNewWorks() throws Exception {
        String oldToken = login();

        String body = mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD
                                + "\",\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newToken = extract(body, "token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEmail_andWrongPassword_returnIdenticalResponses() throws Exception {
        String wrongPw = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("nope")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost-" + UUID.randomUUID()
                                + "@test.local\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Identical apart from the response timestamp — no user-enumeration oracle.
        assertThat(stripTimestamp(unknown)).isEqualTo(stripTimestamp(wrongPw));
    }

    @Test
    void sixthFailedAttempt_isThrottledWith429() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(loginBody("wrong-" + i)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody("wrong-6")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").exists());
        // Even the correct password is rejected while locked out.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody(PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }
}
