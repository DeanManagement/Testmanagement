package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.AuditEntryRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.SharedStepRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared step blocks (PRD-030 §3.1-3.3): roles, project scoping, in-place edits, delete in use. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class SharedStepApiTest {

    private static final String LOGIN_BLOCK = """
            {"title":"Log in as admin","description":"Standard login","steps":[
              {"action":"Open the login page","expectedResult":"Form shown"},
              {"action":"Sign in as {username}","expectedResult":"Dashboard","testData":"admin / secret"}
            ]}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private SharedStepRepository sharedStepRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private AuditEntryRepository auditEntryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Project project;
    private String viewer;
    private String tester;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setName("Shared steps");
        project.setKey("SHS");
        project = projectRepository.save(project);
        viewer = member(ProjectRole.VIEWER);
        tester = member(ProjectRole.TESTER);
    }

    private String member(ProjectRole role) {
        User u = new User();
        u.setEmail(role + "-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName(role.name());
        u.setPasswordHash("x");
        u = userRepository.save(u);
        ProjectMember m = new ProjectMember();
        m.setProject(project);
        m.setUser(u);
        m.setRole(role);
        projectMemberRepository.save(m);
        return u.getId().toString();
    }

    private String base() {
        return "/api/projects/" + project.getId() + "/shared-steps";
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(base()).with(user(tester)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createLoginBlock() throws Exception {
        String json = create(LOGIN_BLOCK).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    /** A case with one step standing for the block, written directly: references arrive in the next commit. */
    private void caseUsing(String blockId) {
        TestCase tc = new TestCase();
        tc.setProject(project);
        tc.setTitle("Uses the block");
        tc.setKey("SHS-" + UUID.randomUUID().toString().substring(0, 6));
        tc.setPriority(Priority.MEDIUM);
        tc.setStatus(TestCaseStatus.ACTIVE);
        TestStep reference = new TestStep();
        reference.setTestCase(tc);
        reference.setAction("Log in as admin");
        reference.setOrderIndex(0);
        reference.setUsesSharedStep(sharedStepRepository.findById(UUID.fromString(blockId)).orElseThrow());
        tc.setSteps(new java.util.ArrayList<>(List.of(reference)));
        testCaseRepository.saveAndFlush(tc);
    }

    @Nested
    class Roles {

        @Test
        void aTesterCreatesABlockWithItsStepsInOrder() throws Exception {
            create(LOGIN_BLOCK)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Log in as admin"))
                    .andExpect(jsonPath("$.steps[0].action").value("Open the login page"))
                    .andExpect(jsonPath("$.steps[1].orderIndex").value(1))
                    .andExpect(jsonPath("$.steps[1].testData").value("admin / secret"))
                    .andExpect(jsonPath("$.usedByCount").value(0));
        }

        @Test
        void aViewerReadsButCannotCreate() throws Exception {
            String id = createLoginBlock();

            mockMvc.perform(get(base()).with(user(viewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("Log in as admin"))
                    .andExpect(jsonPath("$.content[0].stepCount").value(2));
            mockMvc.perform(get(base() + "/" + id).with(user(viewer))).andExpect(status().isOk());
            mockMvc.perform(post(base()).with(user(viewer)).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BLOCK))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aBlockOfAnotherProjectIsNotFound() throws Exception {
            String id = createLoginBlock();
            Project other = new Project();
            other.setName("Other");
            other.setKey("OTH");
            other = projectRepository.save(other);
            // A member there too, so what is checked is the lookup's scope, not project access.
            ProjectMember m = new ProjectMember();
            m.setProject(other);
            m.setUser(userRepository.findById(UUID.fromString(tester)).orElseThrow());
            m.setRole(ProjectRole.TESTER);
            projectMemberRepository.save(m);

            mockMvc.perform(get("/api/projects/" + other.getId() + "/shared-steps/" + id).with(user(tester)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Editing {

        @Test
        void stepsAreUpdatedInPlaceSoTheirIdsSurvive() throws Exception {
            String id = createLoginBlock();
            String json = mockMvc.perform(get(base() + "/" + id).with(user(tester))).andReturn().getResponse().getContentAsString();
            String first = JsonPath.read(json, "$.steps[0].id");
            String second = JsonPath.read(json, "$.steps[1].id");

            // Reorder, reword the first, drop nothing, add one at the end.
            String body = """
                    {"title":"Log in as admin","steps":[
                      {"id":"%s","action":"Sign in as {username}"},
                      {"id":"%s","action":"Open the new login page"},
                      {"action":"Accept the cookie banner"}
                    ]}""".formatted(second, first);
            mockMvc.perform(put(base() + "/" + id).with(user(tester)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.steps[0].id").value(second))
                    .andExpect(jsonPath("$.steps[1].id").value(first))
                    .andExpect(jsonPath("$.steps[1].action").value("Open the new login page"))
                    .andExpect(jsonPath("$.steps[2].action").value("Accept the cookie banner"));
        }

        @Test
        void aStepLeftOutIsRemoved() throws Exception {
            String id = createLoginBlock();
            String json = mockMvc.perform(get(base() + "/" + id).with(user(tester))).andReturn().getResponse().getContentAsString();
            String first = JsonPath.read(json, "$.steps[0].id");

            mockMvc.perform(put(base() + "/" + id).with(user(tester)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Log in as admin\",\"steps\":[{\"id\":\"" + first + "\",\"action\":\"Open\"}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.steps.length()").value(1));
        }

        @Test
        void aStepIdFromElsewhereIsRefused() throws Exception {
            String id = createLoginBlock();

            mockMvc.perform(put(base() + "/" + id).with(user(tester)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"T\",\"steps\":[{\"id\":\"" + UUID.randomUUID() + "\",\"action\":\"x\"}]}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aTitleAlreadyTakenIsAConflict() throws Exception {
            createLoginBlock();

            create(LOGIN_BLOCK).andExpect(status().isConflict());
        }

        @Test
        void theListSearchesTitles() throws Exception {
            createLoginBlock();
            create("{\"title\":\"Reset the basket\",\"steps\":[]}").andExpect(status().isCreated());

            mockMvc.perform(get(base()).param("q", "BASKET").with(user(viewer)))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Reset the basket"));
        }
    }

    @Nested
    class Deleting {

        @Test
        void aBlockInUseIsNotDeletedAndSaysWhoUsesIt() throws Exception {
            String id = createLoginBlock();
            caseUsing(id);

            mockMvc.perform(delete(base() + "/" + id).with(user(tester)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("used by 1 test case")));
            mockMvc.perform(get(base() + "/" + id + "/usages").with(user(viewer)))
                    .andExpect(jsonPath("$[0].title").value("Uses the block"));
            mockMvc.perform(get(base() + "/" + id).with(user(viewer)))
                    .andExpect(jsonPath("$.usedByCount").value(1));
        }

        @Test
        void anUnusedBlockIsDeletedWithItsStepsAndAudited() throws Exception {
            String id = createLoginBlock();

            mockMvc.perform(delete(base() + "/" + id).with(user(tester))).andExpect(status().isNoContent());

            sharedStepRepository.flush(); // the JDBC count below does not see unflushed deletes
            assertThat(sharedStepRepository.findById(UUID.fromString(id))).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_steps WHERE shared_step_id = ?",
                    Integer.class, UUID.fromString(id))).isZero();
            assertThat(auditEntryRepository.findAll()).anyMatch(e ->
                    e.getEntityType() == AuditEntityType.SHARED_STEP && UUID.fromString(id).equals(e.getEntityId()));
        }
    }

    @Nested
    class Schema {

        private void insertStep(UUID testCaseId, UUID sharedStepId, UUID usesSharedStepId) {
            Instant now = Instant.now();
            jdbcTemplate.update("INSERT INTO test_steps (id, action, order_index, test_case_id, shared_step_id, "
                            + "uses_shared_step_id, created_at, updated_at) VALUES (?, 'x', 0, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), testCaseId, sharedStepId, usesSharedStepId,
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        }

        @Test
        void aStepCannotHaveTwoOwners() throws Exception {
            UUID block = UUID.fromString(createLoginBlock());
            caseUsing(block.toString());
            UUID caseId = testCaseRepository.findAll().stream()
                    .filter(tc -> tc.getProject().getId().equals(project.getId())).findFirst().orElseThrow().getId();

            assertThatThrownBy(() -> insertStep(caseId, block, null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertStep(null, null, null)).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void aBlockStepCannotReferenceABlock() throws Exception {
            UUID block = UUID.fromString(createLoginBlock());

            assertThatThrownBy(() -> insertStep(null, block, block)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
