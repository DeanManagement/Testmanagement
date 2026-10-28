package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.RequirementService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM20: the traceability matrix as a CSV download. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TraceabilityExportApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private RequirementService requirementService;

    private String admin;
    private Project project;

    @BeforeEach
    void setUp() {
        User sysAdmin = new User();
        sysAdmin.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        sysAdmin.setDisplayName("sa");
        sysAdmin.setPasswordHash("x");
        sysAdmin.setSystemAdmin(true);
        admin = userRepository.save(sysAdmin).getId().toString();

        project = new Project();
        project.setName("Trace");
        project.setKey("TR" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);

        TestCase login = new TestCase();
        login.setProject(project);
        login.setKey("TR-1");
        login.setTitle("Log in");
        login.setPriority(Priority.MEDIUM);
        login.setStatus(TestCaseStatus.ACTIVE);
        login = testCaseRepository.save(login);

        UUID covered = requirementService.create(project.getId(),
                new SaveRequirementRequest("REQ-1", "Users can log in", null), null).id();
        requirementService.linkTestCase(project.getId(), covered, login.getId(), null);
        requirementService.create(project.getId(), new SaveRequirementRequest("REQ-2", "=HYPERLINK(\"x\")", null), null);
    }

    @Test
    void listsEachRequirementWithItsCasesAndTheGapsToo() throws Exception {
        String csv = mockMvc.perform(get("/api/projects/{p}/traceability/export", project.getId()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"traceability.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv.lines()).containsExactly(
                "requirementId,requirementTitle,requirementCoverage,testCaseKey,testCaseTitle,latestResult",
                "REQ-1,Users can log in,UNTESTED,TR-1,Log in,UNTESTED",
                // A formula is neutralised, and an uncovered requirement still gets its row.
                "REQ-2,\"'=HYPERLINK(\"\"x\"\")\",UNCOVERED,,,");
    }

    @Test
    void anUnknownTestPlanIsNotFound() throws Exception {
        mockMvc.perform(get("/api/projects/{p}/traceability/export", project.getId())
                        .param("testPlanId", UUID.randomUUID().toString()).with(user(admin)))
                .andExpect(status().isNotFound());
    }
}
