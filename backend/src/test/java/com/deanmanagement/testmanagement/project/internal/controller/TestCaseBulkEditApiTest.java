package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldDefinition;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.CustomFieldDefinitionRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TM11: one edit for every selected case. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TestCaseBulkEditApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestCaseFolderRepository folderRepository;
    @Autowired private CustomFieldDefinitionRepository definitionRepository;

    private String admin;
    private Project project;
    private TestCase first;
    private TestCase second;

    @BeforeEach
    void setUp() {
        User sysAdmin = new User();
        sysAdmin.setEmail("sa-" + UUID.randomUUID() + "@test.local");
        sysAdmin.setDisplayName("sa");
        sysAdmin.setPasswordHash("x");
        sysAdmin.setSystemAdmin(true);
        admin = userRepository.save(sysAdmin).getId().toString();

        project = new Project();
        project.setName("Bulk");
        project.setKey("BE" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);
        first = testCase("BE-1", "smoke", "old");
        second = testCase("BE-2", "old");
    }

    private TestCase testCase(String key, String... labels) {
        TestCase tc = new TestCase();
        tc.setProject(project);
        tc.setKey(key);
        tc.setTitle(key);
        tc.setPriority(Priority.LOW);
        tc.setStatus(TestCaseStatus.DRAFT);
        tc.setLabels(new HashSet<>(Set.of(labels)));
        return testCaseRepository.save(tc);
    }

    private ResultActions bulkEdit(String fields) throws Exception {
        String body = "{\"testCaseIds\":[\"" + first.getId() + "\",\"" + second.getId() + "\"]," + fields + "}";
        return mockMvc.perform(post("/api/projects/{p}/test-cases/bulk-edit", project.getId())
                .contentType(MediaType.APPLICATION_JSON).content(body).with(user(admin)));
    }

    private ResultActions read(TestCase testCase) throws Exception {
        return mockMvc.perform(get("/api/projects/{p}/test-cases/{id}", project.getId(), testCase.getId())
                .with(user(admin)));
    }

    @Test
    void setsThePriorityAndAddsAndRemovesLabelsOnEveryCase() throws Exception {
        bulkEdit("\"priority\":\"HIGH\",\"addLabels\":[\"regression\"],\"removeLabels\":[\"old\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affected").value(2));

        read(first).andExpect(jsonPath("$.priority").value("HIGH"));
        assertThat(testCaseRepository.findById(first.getId()).orElseThrow().getLabels())
                .containsExactlyInAnyOrder("smoke", "regression");
        assertThat(testCaseRepository.findById(second.getId()).orElseThrow().getLabels())
                .containsExactly("regression");
    }

    @Test
    void movesTheCasesToAFolderOrToTheRoot() throws Exception {
        TestCaseFolder folder = new TestCaseFolder();
        folder.setProject(project);
        folder.setName("Checkout");
        folder = folderRepository.save(folder);

        bulkEdit("\"moveToFolder\":true,\"folderId\":\"" + folder.getId() + "\"").andExpect(status().isOk());
        assertThat(testCaseRepository.findById(second.getId()).orElseThrow().getFolder()).isEqualTo(folder);

        bulkEdit("\"moveToFolder\":true").andExpect(status().isOk());
        assertThat(testCaseRepository.findById(second.getId()).orElseThrow().getFolder()).isNull();
    }

    @Test
    void setsACustomFieldOnEveryCase() throws Exception {
        CustomFieldDefinition component = new CustomFieldDefinition();
        component.setProject(project);
        component.setEntityType(CustomFieldEntityType.TEST_CASE);
        component.setName("Component");
        component.setFieldType(CustomFieldType.TEXT);
        definitionRepository.save(component);

        bulkEdit("\"customFields\":{\"Component\":\"Payments\"}").andExpect(status().isOk());

        read(second).andExpect(jsonPath("$.customFields.Component").value("Payments"));
    }

    @Test
    void anEditThatChangesNothingIsRefused() throws Exception {
        bulkEdit("\"addLabels\":[]").andExpect(status().isBadRequest());
    }

    @Test
    void aCaseOfAnotherProjectFailsTheWholeEdit() throws Exception {
        String body = "{\"testCaseIds\":[\"" + first.getId() + "\",\"" + UUID.randomUUID() + "\"],\"priority\":\"HIGH\"}";

        mockMvc.perform(post("/api/projects/{p}/test-cases/bulk-edit", project.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(user(admin)))
                .andExpect(status().isNotFound());
        assertThat(testCaseRepository.findById(first.getId()).orElseThrow().getPriority()).isEqualTo(Priority.LOW);
    }
}
