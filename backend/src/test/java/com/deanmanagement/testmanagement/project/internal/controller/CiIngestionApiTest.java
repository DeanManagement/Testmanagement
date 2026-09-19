package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.apiKey.CreateApiKeyRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.service.ApiKeyService;
import com.deanmanagement.testmanagement.project.internal.service.ProjectEnvironmentService;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.webhook.WebhookEvent;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.service.ParameterSetService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
@RecordApplicationEvents
class CiIngestionApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TestCaseRepository testCaseRepository;
    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private TestPlanRepository testPlanRepository;
    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private ProjectEnvironmentService environmentService;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private ParameterSetService parameterSetService;

    private UUID projectId;
    private String apiKey;
    private UUID foreignPlanId;
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

        apiKey = apiKeyService.create(new CreateApiKeyRequest("ci-test-key", projectId, null)).rawKey();

        Project other = new Project();
        other.setName("Other Project");
        other.setKey("OTHER");
        other = projectRepository.save(other);

        TestPlan foreignPlan = new TestPlan();
        foreignPlan.setProject(other);
        foreignPlan.setName("Their plan");
        foreignPlan.setStatus(TestPlanStatus.OPEN);
        foreignPlanId = testPlanRepository.save(foreignPlan).getId();
    }

    /**
     * PRD-027 §3.5. {@code testPlanId} is a free query parameter here and {@code requireTester}
     * only authorizes the project in the path, so an unscoped lookup let a CI key scoped to one
     * project file its run against another project's plan — where the results then counted toward
     * that plan's pass rate, and the response echoed the plan's name back.
     *
     * <p>The neighbouring {@code pipelineRunId} was scoped from the start, which is what makes this
     * an oversight rather than a design choice.
     */
    @Test
    void junit_againstAnotherProjectsTestPlan_isNotFound() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .header("X-API-Key", apiKey)
                        .param("testPlanId", foreignPlanId.toString())
                        .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML))
                .andExpect(status().isNotFound());

        assertThat(testCaseRepository.countByProjectId(projectId))
                .as("a refused import must not auto-create its test cases").isZero();
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

    /** PRD-031: run-level events only, never one TEST_FAILED per ingested result. */
    @Test
    void junit_publishesRunEventsButNoPerTestEvents() throws Exception {
        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_XML).content(SUREFIRE_XML))
                .andExpect(status().isCreated());

        assertThat(applicationEvents.stream(WebhookEvent.class).map(WebhookEvent::type))
                .containsExactly(WebhookEventType.RUN_COMPLETED, WebhookEventType.RUN_FAILED);
    }

    /** PRD-032: a CI upload naming an existing environment in another case joins it. */
    @Test
    void junit_environmentNameResolvesToTheExistingEntry() throws Exception {
        environmentService.resolve(projectId, null, "staging");

        mockMvc.perform(post("/api/external/projects/{k}/test-runs/junit", KEY)
                        .header("X-API-Key", apiKey)
                        .param("environment", "Staging")
                        .contentType(MediaType.APPLICATION_XML).content(JEST_XML))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.environment").value("staging"));

        assertThat(environmentService.list(projectId, true)).hasSize(1);
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

    // ---- PRD-040 §3.5: matching by @tm: key -------------------------------------------------------

    private static String cucumberScenario(String name, String tag, String status) {
        return "{\"name\":\"" + name + "\",\"type\":\"scenario\",\"tags\":[{\"name\":\"" + tag + "\"}],"
                + "\"steps\":[{\"keyword\":\"Given \",\"name\":\"a\",\"result\":{\"status\":\"" + status + "\"}}]}";
    }

    private org.springframework.test.web.servlet.ResultActions postCucumber(String... scenarios) throws Exception {
        String json = "[{\"name\":\"Login\",\"elements\":[" + String.join(",", scenarios) + "]}]";
        return mockMvc.perform(post("/api/external/projects/{k}/test-runs/cucumber", KEY)
                .header("X-API-Key", apiKey).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private TestCaseResponse caseWithSets(String title, String... setNames) {
        TestCaseResponse tc = testCaseService.create(projectId, new CreateTestCaseRequest(title, null, null,
                Priority.MEDIUM, TestCaseStatus.ACTIVE, Set.of(), List.of(), null), null);
        for (String name : setNames) {
            parameterSetService.create(projectId, tc.id(), new SaveParameterSetRequest(name, Map.of("x", name), null));
        }
        return tc;
    }

    @Test
    void cucumber_aKeyedScenarioLandsOnItsCaseEvenAfterARename() throws Exception {
        TestCaseResponse tc = caseWithSets("Renamed in the tool");

        postCucumber(cucumberScenario("Old scenario name", "@tm:" + tc.key(), "passed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].testCaseId").value(tc.id().toString()));

        assertThat(testCaseRepository.countByProjectId(projectId)).isEqualTo(1);
    }

    @Test
    void cucumber_anUnknownKeyFallsBackToTheTitleAndSaysSo() throws Exception {
        postCucumber(cucumberScenario("Typo", "@tm:CI-999", "passed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].testCaseTitle").value("Login - Typo"))
                .andExpect(jsonPath("$.results[0].comment").value(containsString("Unknown test case key tm:CI-999")));
    }

    @Test
    void cucumber_outlineRowsMapToParameterSetsInOrder() throws Exception {
        TestCaseResponse tc = caseWithSets("Convert", "Example #1", "Example #2");
        String tag = "@tm:" + tc.key();

        postCucumber(cucumberScenario("Convert", tag, "passed"), cucumberScenario("Convert", tag, "failed"),
                cucumberScenario("Convert", tag, "passed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].parameterSetName").value("Example #1"))
                .andExpect(jsonPath("$.results[1].parameterSetName").value("Example #2"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[2].parameterSetName").doesNotExist())
                .andExpect(jsonPath("$.results[2].comment").value(containsString("Example row 3 has no parameter set")));
    }
}
