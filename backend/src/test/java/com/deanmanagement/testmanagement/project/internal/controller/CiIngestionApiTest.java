package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class CiIngestionApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private ApiKeyService apiKeyService;

    private UUID projectId;
    private String apiKey;
    private static final String KEY = "CI";

    private static final String SUREFIRE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="com.example.CalcTest" tests="4">
              <testcase classname="com.example.CalcTest" name="adds"/>
              <testcase classname="com.example.CalcTest" name="subtracts">
                <failure message="expected 2 but was 3">at CalcTest.subtracts(CalcTest.java:20)</failure>
              </testcase>
              <testcase classname="com.example.CalcTest" name="divides">
                <error message="NullPointerException">at CalcTest.divides(CalcTest.java:30)</error>
              </testcase>
              <testcase classname="com.example.CalcTest" name="ignored">
                <skipped/>
              </testcase>
            </testsuite>
            """;

    private static final String JEST_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuites name="jest tests" tests="2">
              <testsuite name="Button">
                <testcase classname="Button" name="renders"/>
                <testcase classname="Button" name="handles click">
                  <failure>Error: expected handler to be called</failure>
                </testcase>
              </testsuite>
            </testsuites>
            """;

    private static final String PYTEST_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuites>
              <testsuite name="pytest" tests="2">
                <testcase classname="tests.test_math" name="test_add" time="0.01"/>
                <testcase classname="tests.test_math" name="test_fail" time="0.02">
                  <failure message="assert 1 == 2">tests/test_math.py:5</failure>
                </testcase>
              </testsuite>
            </testsuites>
            """;

    private static final String CUCUMBER_JSON = """
            [
              {"name":"Login","elements":[
                {"name":"Successful login","type":"scenario","steps":[
                  {"keyword":"Given ","name":"a registered user","result":{"status":"passed"}},
                  {"keyword":"When ","name":"they sign in","result":{"status":"passed"}}
                ]},
                {"name":"Failed login","type":"scenario","steps":[
                  {"keyword":"Given ","name":"a registered user","result":{"status":"passed"}},
                  {"keyword":"When ","name":"they use a wrong password","result":{"status":"failed","error_message":"AssertionError: access denied"}}
                ]}
              ]}
            ]
            """;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("CI Project");
        project.setKey(KEY);
        projectId = projectRepository.save(project).getId();

        apiKey = apiKeyService.create(new CreateApiKeyRequest("ci-test-key", projectId)).rawKey();
    }

    @Test
    void junitSurefire_mapsAllStatusesAndStackTraces() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[*].status",
                        containsInAnyOrder("PASSED", "FAILED", "BLOCKED", "SKIPPED")))
                .andExpect(jsonPath("$.results[?(@.status=='FAILED')].comment",
                        hasItem(containsString("expected 2 but was 3"))));

        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(4);
    }

    @Test
    void junitJest_parsesTestsuitesWrapper() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_XML).content(JEST_XML))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[*].status", containsInAnyOrder("PASSED", "FAILED")));
    }

    @Test
    void junitPytest_parsesNestedSuites() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_XML).content(PYTEST_XML))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[*].status", containsInAnyOrder("PASSED", "FAILED")));
    }

    @Test
    void junit_autoCreateIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML)).andExpect(status().isCreated());
        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(4);

        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML)).andExpect(status().isCreated());
        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(4);
    }

    @Test
    void cucumber_mapsScenariosAndSteps() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/cucumber", KEY)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content(CUCUMBER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[*].status", containsInAnyOrder("PASSED", "FAILED")));

        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(2);
        assertThat(testCaseRepository.findFirstByProjectIdAndTitle(projectId, "Login - Successful login"))
                .isPresent()
                .get()
                .satisfies(tc -> assertThat(tc.getSteps()).hasSize(2));
    }

    @Test
    void malformedXml_returns400() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_XML).content("<not-valid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/cucumber", KEY)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void junit_requiresApiKeyAuth() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopedKey_cannotIngestIntoOtherProject() throws Exception {
        // PRD-021 §4.2: the key minted in setUp is scoped to project CI.
        Project other = new Project();
        other.setName("Other Project");
        other.setKey("OTHR");
        projectRepository.save(other);

        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", "OTHR")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_XML)
                        .content(SUREFIRE_XML))
                .andExpect(status().isForbidden());
    }
}
