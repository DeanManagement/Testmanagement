package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP wiring of PRD-035: role guards on definitions, and values travelling on entity endpoints. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class CustomFieldApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;

    private Project project;
    private UUID admin;
    private UUID tester;
    private UUID viewer;
    private UUID outsider;

    @BeforeEach
    void setUp() {
        project = project("CFAPI");
        admin = member(project, ProjectRole.ADMIN);
        tester = member(project, ProjectRole.TESTER);
        viewer = member(project, ProjectRole.VIEWER);
        outsider = member(project("CFOTH"), ProjectRole.ADMIN);
    }

    private Project project(String key) {
        Project p = new Project();
        p.setName(key);
        p.setKey(key);
        return projectRepository.save(p);
    }

    private UUID member(Project target, ProjectRole role) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u = userRepository.save(u);
        ProjectMember m = new ProjectMember();
        m.setUser(u);
        m.setProject(target);
        m.setRole(role);
        memberRepository.save(m);
        return u.getId();
    }

    private static RequestPostProcessor as(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private String fields() {
        return "/api/projects/" + project.getId() + "/custom-fields";
    }

    private String createField(String json) throws Exception {
        String body = mockMvc.perform(post(fields()).with(as(admin)).with(csrf())
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static final String COMPONENT = """
            {"entityType":"TEST_CASE","name":"Component","fieldType":"SELECT","options":["Checkout","Search"]}""";

    @Test
    void anAdminDefinesFieldsAndAViewerReadsThem() throws Exception {
        createField(COMPONENT);

        mockMvc.perform(get(fields()).param("entityType", "TEST_CASE").with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Component"))
                .andExpect(jsonPath("$[0].options[1]").value("Search"));
    }

    @Test
    void aTesterCannotChangeDefinitions() throws Exception {
        String id = createField(COMPONENT);

        mockMvc.perform(post(fields()).with(as(tester)).with(csrf())
                        .contentType("application/json").content(COMPONENT.replace("Component", "Other")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(fields() + "/" + id + "/archive").with(as(tester)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(fields() + "/" + id).with(as(tester)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNonMemberCannotReadDefinitions() throws Exception {
        mockMvc.perform(get(fields()).with(as(outsider)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aTesterWritesValuesByNameThroughTheTestCaseEndpoints() throws Exception {
        createField(COMPONENT);
        createField("""
                {"entityType":"TEST_CASE","name":"Effort","fieldType":"NUMBER"}""");

        String body = mockMvc.perform(post("/api/projects/" + project.getId() + "/test-cases")
                        .with(as(tester)).with(csrf()).contentType("application/json").content("""
                                {"title":"Pay","priority":"HIGH","status":"DRAFT",
                                 "customFields":{"component":"checkout","Effort":2.50}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customFields.Component").value("Checkout"))
                .andExpect(jsonPath("$.customFields.Effort").value(2.5))
                .andReturn().getResponse().getContentAsString();
        String caseId = JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/projects/" + project.getId() + "/test-cases/" + caseId)
                        .with(as(tester)).with(csrf()).contentType("application/json")
                        .content("{\"customFields\":{\"Effort\":null}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customFields.Component").value("Checkout"))
                .andExpect(jsonPath("$.customFields.Effort").doesNotExist());
    }

    @Test
    void anUnknownFieldNameIsABadRequestListingTheValidNames() throws Exception {
        createField(COMPONENT);

        mockMvc.perform(post("/api/projects/" + project.getId() + "/test-cases")
                        .with(as(tester)).with(csrf()).contentType("application/json").content("""
                                {"title":"Pay","priority":"HIGH","status":"DRAFT","customFields":{"Sprint":"12"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Valid fields: Component")));
    }

    @Test
    void deletingAFieldThatHoldsValuesNeedsForce() throws Exception {
        String id = createField(COMPONENT);
        mockMvc.perform(post("/api/projects/" + project.getId() + "/test-cases")
                        .with(as(tester)).with(csrf()).contentType("application/json").content("""
                                {"title":"Pay","priority":"HIGH","status":"DRAFT","customFields":{"Component":"Search"}}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(fields() + "/" + id).with(as(admin)).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(fields() + "/" + id).param("force", "true").with(as(admin)).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
