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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestCaseImportExportApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;

    private UUID projectId;
    private String admin;

    private static final String VALID_CSV = """
            title,description,priority,status,labels,steps
            Login works,Sign in flow,HIGH,ACTIVE,smoke;ui,Open page|Page loads;;Click login|Dashboard shown
            Logout,,LOW,DRAFT,,
            """;

    @BeforeEach
    void setUp() {
        User sysAdmin = new User();
        sysAdmin.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        sysAdmin.setDisplayName("sa");
        sysAdmin.setPasswordHash("x");
        sysAdmin.setSystemAdmin(true);
        admin = userRepository.save(sysAdmin).getId().toString();

        Project project = new Project();
        project.setName("Import Project");
        project.setKey("IMP");
        projectId = projectRepository.save(project).getId();
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "test-cases.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importCsv_createsTestCases() throws Exception {
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectId)
                        .file(csv(VALID_CSV)).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.dryRun").value(false));

        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(2);
    }

    @Test
    void importCsv_reportsPerRowErrors() throws Exception {
        String csv = """
                title,priority
                ,HIGH
                Valid case,HIGH
                Bad enum,NOPE
                """;
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectId)
                        .file(csv(csv)).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].row").value(2))
                .andExpect(jsonPath("$.errors[1].row").value(4));
    }

    @Test
    void dryRun_persistsNothing() throws Exception {
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectId)
                        .file(csv(VALID_CSV)).param("dryRun", "true").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.dryRun").value(true));

        assertThat(testCaseRepository.countByProjectId(projectId)).isZero();
    }

    @Test
    void overLimit_returns400() throws Exception {
        StringBuilder sb = new StringBuilder("title\n");
        for (int i = 0; i < 501; i++) {
            sb.append("Case ").append(i).append('\n');
        }
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectId)
                        .file(csv(sb.toString())).with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTitleHeader_returns400() throws Exception {
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectId)
                        .file(csv("name,priority\nFoo,HIGH\n")).with(user(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportJson_roundTripsIntoAnotherProject() throws Exception {
        // Seed project A via CSV import.
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectId)
                .file(csv(VALID_CSV)).with(user(admin))).andExpect(status().isOk());

        // Export A as JSON.
        MvcResult export = mockMvc.perform(get("/api/projects/{p}/test-cases/export", projectId)
                        .param("format", "json").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] json = export.getResponse().getContentAsByteArray();

        // Import the exported JSON into a fresh project B.
        Project b = new Project();
        b.setName("Target");
        b.setKey("TGT");
        UUID projectB = projectRepository.save(b).getId();

        MockMultipartFile jsonFile = new MockMultipartFile("file", "test-cases.json", "application/json", json);
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectB)
                        .file(jsonFile).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(0));

        assertThat(testCaseRepository.countByProjectId(projectB)).isEqualTo(2);

        // Export B and confirm labels + steps survived the round trip.
        String exportedB = mockMvc.perform(get("/api/projects/{p}/test-cases/export", projectB)
                        .param("format", "json").with(user(admin)))
                .andReturn().getResponse().getContentAsString();
        assertThat(exportedB).contains("Login works").contains("smoke").contains("Open page");
    }

    @Test
    void exportCsv_excelAddsBom() throws Exception {
        mockMvc.perform(multipart("/api/projects/{p}/test-cases/import", projectId)
                .file(csv(VALID_CSV)).with(user(admin))).andExpect(status().isOk());

        byte[] csv = mockMvc.perform(get("/api/projects/{p}/test-cases/export", projectId)
                        .param("format", "csv").param("excel", "true").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // UTF-8 BOM bytes.
        assertThat(csv[0] & 0xFF).isEqualTo(0xEF);
        assertThat(csv[1] & 0xFF).isEqualTo(0xBB);
        assertThat(csv[2] & 0xFF).isEqualTo(0xBF);
    }

    @Test
    void exportCsv_neutralizesFormulaInjection() throws Exception {
        // PRD-021: cells starting with = + - @ must not execute when opened in Excel.
        TestCase evil = new TestCase();
        evil.setProject(projectRepository.findById(projectId).orElseThrow());
        evil.setTitle("=HYPERLINK(\"http://evil.example\",\"click\")");
        evil.setDescription("+1234");
        evil.setKey("IMP-99");
        evil.setStatus(TestCaseStatus.ACTIVE);
        evil.setPriority(Priority.LOW);
        testCaseRepository.save(evil);

        String exported = mockMvc.perform(get("/api/projects/{p}/test-cases/export", projectId)
                        .param("format", "csv").with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(exported).contains("'=HYPERLINK");
        assertThat(exported).contains("'+1234");
        assertThat(exported).doesNotContainPattern("(?m)^\"?=HYPERLINK");
    }
}
