package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.project.internal.service.TestRunService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One screenshot per step result and one image per test step (V63). Duplicates made the owner
 * unloadable, and with it the whole run (bug report bd5b0f76).
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class OneImagePerOwnerApiTest {

    /** A 1x1 PNG, so the content-type allowlist accepts it. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String admin;
    private UUID stepId;
    private UUID stepResultId;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("sa");
        u.setPasswordHash("x");
        u.setSystemAdmin(true);
        admin = userRepository.save(u).getId().toString();

        Project project = new Project();
        project.setName("Images");
        project.setKey("IMG");
        project = projectRepository.save(project);
        TestCaseResponse tc = testCaseService.create(project.getId(), new CreateTestCaseRequest("Case", null, null,
                Priority.MEDIUM, TestCaseStatus.ACTIVE, Set.of(), List.of(new TestStepRequest("Do it", null, null)), null), null);
        stepId = tc.steps().getFirst().id();
        TestRunResponse run = testRunService.create(project.getId(), new CreateTestRunRequest("Run", null,
                Set.of(tc.id()), null, null, null, null, null), null);
        stepResultId = run.results().getFirst().stepResults().getFirst().id();
    }

    private MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    private int count(String sql, UUID owner) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(sql, Integer.class, owner);
    }

    @Test
    void aSecondScreenshotReplacesTheFirst() throws Exception {
        for (String name : List.of("first.png", "second.png")) {
            mockMvc.perform(multipart("/api/screenshots").file(png(name))
                            .param("stepResultId", stepResultId.toString()).with(user(admin)))
                    .andExpect(status().isCreated());
        }

        assertThat(count("SELECT COUNT(*) FROM screenshots WHERE step_result_id = ?", stepResultId)).isEqualTo(1);
        entityManager.clear();
        assertThat(jdbcTemplate.queryForObject("SELECT file_name FROM screenshots WHERE step_result_id = ?",
                String.class, stepResultId)).isEqualTo("second.png");
    }

    @Test
    void aSecondStepImageReplacesTheFirst() throws Exception {
        for (String name : List.of("first.png", "second.png")) {
            mockMvc.perform(multipart("/api/step-images").file(png(name))
                            .param("testStepId", stepId.toString()).with(user(admin)))
                    .andExpect(status().isCreated());
        }

        assertThat(count("SELECT COUNT(*) FROM step_images WHERE test_step_id = ?", stepId)).isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesASecondScreenshotForOneStepResult() {
        entityManager.flush(); // the step result from setUp must exist for the foreign key
        insertScreenshot();

        assertThatThrownBy(this::insertScreenshot).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertScreenshot() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("INSERT INTO screenshots (id, file_name, content_type, data, step_result_id, created_at, updated_at) "
                + "VALUES (?, 'x.png', 'image/png', ?, ?, ?, ?)", UUID.randomUUID(), PNG, stepResultId, now, now);
    }
}
