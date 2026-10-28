package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM09: POST /test-cases/{id}/copy. The content rules are covered by McpTestCaseEditToolsApiTest. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestCaseCopyApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;

    private String admin;
    private Project project;
    private TestCase original;

    @BeforeEach
    void setUp() {
        User sysAdmin = new User();
        sysAdmin.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        sysAdmin.setDisplayName("sa");
        sysAdmin.setPasswordHash("x");
        sysAdmin.setSystemAdmin(true);
        admin = userRepository.save(sysAdmin).getId().toString();

        project = new Project();
        project.setName("Copy");
        project.setKey("CP" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);

        original = new TestCase();
        original.setProject(project);
        original.setKey("CPY-1");
        original.setTitle("Login");
        original.setPriority(Priority.HIGH);
        original.setStatus(TestCaseStatus.ACTIVE);
        original.setLabels(new HashSet<>(Set.of("smoke")));
        original = testCaseRepository.save(original);
    }

    @Test
    void withoutABodyTheCopyIsNamedAfterTheOriginalAndStartsAsDraft() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-cases/{id}/copy", project.getId(), original.getId())
                        .with(user(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Login (copy)"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.labels[0]").value("smoke"));
    }

    @Test
    void theCallerMayNameTheCopy() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-cases/{id}/copy", project.getId(), original.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Login (Kopie)\"}")
                        .with(user(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Login (Kopie)"));
    }

    @Test
    void anUnknownCaseIsNotFound() throws Exception {
        mockMvc.perform(post("/api/projects/{p}/test-cases/{id}/copy", project.getId(), UUID.randomUUID())
                        .with(user(admin)))
                .andExpect(status().isNotFound());
    }
}
