package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.project.internal.service.RequirementService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM19: requirements from CSV, upserted by externalId. Not @Transactional, so writes must commit. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class RequirementImportApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;
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
        project.setName("Import");
        project.setKey("RI" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);
        requirementService.create(project.getId(), new SaveRequirementRequest("REQ-1", "Old title", "kept"), null);
    }

    @AfterEach
    void tearDown() {
        projectService.delete(project.getId(), null);
    }

    private ResultActions upload(String csv, boolean dryRun) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "requirements.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/projects/{p}/requirements/import", project.getId())
                .file(file).param("dryRun", String.valueOf(dryRun)).with(user(admin)));
    }

    private RequirementResponse requirement(String externalId) {
        return requirementService.get(project.getId(), externalId);
    }

    private static final String CSV = """
            externalId,title,parentExternalId,status,priority
            REQ-2,Log out,EPIC-1,APPROVED,high
            REQ-1,Log in,EPIC-1,,
            EPIC-1,Accounts,,,
            """;

    @Test
    void aDryRunReportsWhatWouldHappenAndWritesNothing() throws Exception {
        upload(CSV, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.dryRun").value(true));

        assertThat(requirementService.list(project.getId(), null, org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void anImportCreatesUpdatesAndNestsWithParentsLaterInTheFile() throws Exception {
        upload(CSV, false).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(2));

        UUID epic = requirement("EPIC-1").id();
        RequirementResponse logIn = requirement("REQ-1");
        RequirementResponse logOut = requirement("REQ-2");
        assertThat(logIn.title()).isEqualTo("Log in");
        assertThat(logIn.description()).isEqualTo("kept");
        assertThat(logIn.parentId()).isEqualTo(epic);
        assertThat(logOut.status()).isEqualTo(RequirementStatus.APPROVED);
        assertThat(logOut.priority()).isEqualTo(Priority.HIGH);
        assertThat(logOut.parentId()).isEqualTo(epic);
    }

    @Test
    void rowsWithAnUnknownParentACycleOrABadValueAreSkippedWithTheirRow() throws Exception {
        upload(CSV, false);

        upload("""
                externalId,title,parentExternalId,status
                EPIC-1,Accounts,REQ-1,
                REQ-3,Orphan,NOPE,
                REQ-4,Odd,,DONE
                REQ-5,Fine,,
                """, false)
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(3))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("below itself")))
                .andExpect(jsonPath("$.errors[1].message").value(containsString("NOPE")))
                .andExpect(jsonPath("$.errors[2].message").value(containsString("DONE")));

        assertThat(requirement("EPIC-1").parentId()).isNull();
    }

    @Test
    void aFileWithoutTheRequiredColumnsIsRefused() throws Exception {
        upload("id,name\nREQ-9,Nine\n", false).andExpect(status().isBadRequest());
    }
}
