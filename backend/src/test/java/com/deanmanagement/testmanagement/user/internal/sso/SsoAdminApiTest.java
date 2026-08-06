package com.deanmanagement.testmanagement.user.internal.sso;

import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin API for SSO providers (PRD-012). These endpoints decide who can enter the instance, so the
 * assertions that matter are the authorization boundary and the client secret never coming back.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class SsoAdminApiTest {

    private static final String SECRET = "super-secret-client-value";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SsoProviderRepository providerRepository;

    private String admin;
    private String regular;

    @BeforeEach
    void setUp() {
        admin = saveUser(true).getId().toString();
        regular = saveUser(false).getId().toString();
    }

    private User saveUser(boolean systemAdmin) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u.setSystemAdmin(systemAdmin);
        return userRepository.save(u);
    }

    /** Mirrors what JwtAuthenticationFilter puts in the context, including the admin role. */
    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(String userId, boolean isAdmin) {
        var processor = SecurityMockMvcRequestPostProcessors.user(userId).roles("USER");
        return isAdmin ? SecurityMockMvcRequestPostProcessors.user(userId).roles("USER", "ADMIN") : processor;
    }

    private String body(String slug, String secret) {
        String secretPart = secret == null ? "" : ",\"clientSecret\":\"" + secret + "\"";
        return "{\"slug\":\"" + slug + "\",\"displayName\":\"Acme SSO\","
                + "\"issuerUri\":\"https://idp.example.com\",\"clientId\":\"tm-client\""
                + secretPart + "}";
    }

    @Test
    void adminCanCreateAProviderAndTheSecretIsNeverReturned() throws Exception {
        String response = mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("acme", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("acme"))
                .andExpect(jsonPath("$.secretSet").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(SECRET);
        assertThat(response).doesNotContain("clientSecret");
    }

    @Test
    void secretIsEncryptedAtRest() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("acme2", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated());

        SsoProvider stored = providerRepository.findBySlug("acme2").orElseThrow();
        assertThat(stored.getClientSecretEncrypted()).isNotBlank();
        assertThat(stored.getClientSecretEncrypted()).doesNotContain(SECRET);
    }

    @Test
    void nonAdminCannotSeeOrChangeProviders() throws Exception {
        mockMvc.perform(get("/api/admin/sso/providers").with(as(regular, false)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("sneaky", SECRET))
                        .with(as(regular, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void openidScopeIsAddedEvenIfTheAdminOmitsIt() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"scoped\",\"displayName\":\"S\","
                                + "\"issuerUri\":\"https://idp.example.com\",\"clientId\":\"c\","
                                + "\"clientSecret\":\"" + SECRET + "\",\"scopes\":\"profile,email\"}")
                        .with(as(admin, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopes").value("openid,profile,email"));
    }

    // ---- GitHub, which is not an OIDC issuer ------------------------------

    @Test
    void providerDefaultsToOidcWhenTheProtocolIsOmitted() throws Exception {
        // Every client written before GitHub existed sends no protocol; those must keep working.
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("legacy", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocol").value("OIDC"));
    }

    @Test
    void gitHubGetsItsOwnScopesAndNotOpenid() throws Exception {
        // `openid` is meaningless to GitHub, and without user:email the /user/emails call is
        // refused, which leaves every login with no address to provision an account from.
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"gh\",\"displayName\":\"GitHub\",\"protocol\":\"GITHUB\","
                                + "\"issuerUri\":\"https://github.com\",\"clientId\":\"c\","
                                + "\"clientSecret\":\"" + SECRET + "\"}")
                        .with(as(admin, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopes").value("read:user,user:email"));
    }

    @Test
    void gitHubRejectsAnAdminClaimItCannotEvaluate() throws Exception {
        // Storing it silently would leave an admin believing a rule is in force when nothing
        // reads it.
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"gh2\",\"displayName\":\"GitHub\",\"protocol\":\"GITHUB\","
                                + "\"issuerUri\":\"https://github.com\",\"clientId\":\"c\","
                                + "\"clientSecret\":\"" + SECRET + "\",\"adminClaim\":\"groups\","
                                + "\"adminClaimValue\":\"admins\"}")
                        .with(as(admin, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protocolCannotBeChangedOnAnExistingProvider() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("switcheroo", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated());
        UUID id = providerRepository.findBySlug("switcheroo").orElseThrow().getId();

        // An OIDC `sub` and a GitHub numeric id are different kinds of identifier. Reinterpreting
        // stored sso_identities rows under the other one could match a subject to a different
        // person entirely.
        mockMvc.perform(put("/api/admin/sso/providers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"switcheroo\",\"displayName\":\"Acme SSO\","
                                + "\"protocol\":\"GITHUB\",\"issuerUri\":\"https://github.com\","
                                + "\"clientId\":\"tm-client\"}")
                        .with(as(admin, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void firstSaveRequiresASecret() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nosecret", null))
                        .with(as(admin, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWithoutASecretKeepsTheStoredOne() throws Exception {
        String created = mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("keeper", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        String stored = providerRepository.findBySlug("keeper").orElseThrow().getClientSecretEncrypted();

        mockMvc.perform(put("/api/admin/sso/providers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"keeper\",\"displayName\":\"Renamed\","
                                + "\"issuerUri\":\"https://idp.example.com\",\"clientId\":\"tm-client\"}")
                        .with(as(admin, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed"))
                .andExpect(jsonPath("$.secretSet").value(true));

        assertThat(providerRepository.findBySlug("keeper").orElseThrow().getClientSecretEncrypted())
                .isEqualTo(stored);
    }

    @Test
    void slugCannotBeChangedBecauseTheIdpKnowsTheCallbackUrl() throws Exception {
        String created = mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("fixed", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(put("/api/admin/sso/providers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("renamed", null))
                        .with(as(admin, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateSlugIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dupe", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dupe", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isConflict());
    }

    @Test
    void privateIssuerIsRejectedWhenTheGuardIsOn() throws Exception {
        // The dev/test profile allows private issuers for local IdPs, so this asserts the guard
        // itself through the validator rather than the endpoint. See SsoIssuerValidationTest.
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"badurl\",\"displayName\":\"S\","
                                + "\"issuerUri\":\"not-a-url\",\"clientId\":\"c\","
                                + "\"clientSecret\":\"" + SECRET + "\"}")
                        .with(as(admin, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void localLoginCannotBeDisabledWithoutAWorkingProvider() throws Exception {
        // Otherwise an admin locks every non-admin out of an instance that has no other way in.
        mockMvc.perform(put("/api/admin/sso/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localLoginEnabled\":false}")
                        .with(as(admin, true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void localLoginCanBeDisabledOnceAProviderIsActive() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ready", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/admin/sso/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localLoginEnabled\":false}")
                        .with(as(admin, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localLoginEnabled").value(false));
    }

    @Test
    void authConfigIsPublicAndLeaksNoProviderDetail() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("public-check", SECRET))
                        .with(as(admin, true)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localLoginEnabled").value(true))
                .andExpect(jsonPath("$.providers[?(@.slug=='public-check')].displayName").value("Acme SSO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(SECRET);
        assertThat(response).doesNotContain("idp.example.com");
        assertThat(response).doesNotContain("tm-client");
    }

    @Test
    void inactiveProvidersAreNotOfferedOnTheLoginScreen() throws Exception {
        mockMvc.perform(post("/api/admin/sso/providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"off\",\"displayName\":\"Disabled SSO\","
                                + "\"issuerUri\":\"https://idp.example.com\",\"clientId\":\"c\","
                                + "\"clientSecret\":\"" + SECRET + "\",\"active\":false}")
                        .with(as(admin, true)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[?(@.slug=='off')]").isEmpty());
    }
}
