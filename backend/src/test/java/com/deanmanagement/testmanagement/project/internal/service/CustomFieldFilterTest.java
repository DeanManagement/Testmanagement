package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestRunSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestRunListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.CreateTestRunRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** List filtering by custom fields (PRD-035 §3.5), from query parameters to page contents. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomFieldFilterTest {

    @Autowired private CustomFieldService fieldService;
    @Autowired private CustomFieldFilterParser parser;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService testRunService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EntityManager entityManager;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Filters");
        project.setKey("CFF");
        projectId = projectRepository.save(project).getId();
        field(CustomFieldEntityType.TEST_CASE, "Customer", CustomFieldType.TEXT);
        field(CustomFieldEntityType.TEST_CASE, "Effort", CustomFieldType.NUMBER);
        field(CustomFieldEntityType.TEST_CASE, "Due", CustomFieldType.DATE);
        field(CustomFieldEntityType.TEST_CASE, "Component", CustomFieldType.SELECT, "Checkout", "Search", "Admin");
        field(CustomFieldEntityType.TEST_CASE, "Browsers", CustomFieldType.MULTI_SELECT, "Chrome", "Firefox");

        createCase("A", TestCaseStatus.ACTIVE, Map.of("Customer", "ACME Corp", "Effort", 2, "Due", "2026-10-01",
                "Component", "Checkout", "Browsers", List.of("Chrome", "Firefox")));
        createCase("B", TestCaseStatus.DRAFT, Map.of("Customer", "Globex", "Effort", 5, "Due", "2026-11-15",
                "Component", "Search", "Browsers", List.of("Chrome")));
        createCase("C", TestCaseStatus.ACTIVE, Map.of("Effort", 8, "Component", "Admin"));
        createCase("D", TestCaseStatus.ACTIVE, null);
        entityManager.flush();
    }

    private void field(CustomFieldEntityType entityType, String name, CustomFieldType type, String... options) {
        fieldService.create(projectId, new CreateCustomFieldRequest(entityType, name, type, List.of(options), null),
                null);
    }

    private void createCase(String title, TestCaseStatus status, Map<String, Object> values) {
        testCaseService.create(projectId, new CreateTestCaseRequest(title, null, null, Priority.MEDIUM, status,
                null, null, null, values), null);
    }

    private Page<TestCaseResponse> cases(Map<String, List<String>> params, List<TestCaseStatus> status, int pageSize) {
        var filter = new TestCaseListFilter(null, status, null, null, null, false, false, null,
                parser.parse(projectId, CustomFieldEntityType.TEST_CASE, params));
        return testCaseService.findByProject(projectId, filter, PageRequest.of(0, pageSize));
    }

    private List<String> titles(Map<String, List<String>> params) {
        return cases(params, null, 50).map(TestCaseResponse::title).getContent().stream().sorted().toList();
    }

    @Test
    void matchesASelectOptionIgnoringCase() {
        assertThat(titles(Map.of("cf.component", List.of("search")))).containsExactly("B");
    }

    @Test
    void repeatedValuesOfOneFieldMatchAny() {
        assertThat(titles(Map.of("cf.Component", List.of("Checkout", "Admin")))).containsExactly("A", "C");
    }

    @Test
    void differentFieldsMustAllMatch() {
        assertThat(titles(Map.of("cf.Browsers", List.of("Chrome"), "cf.Component", List.of("Search"))))
                .containsExactly("B");
    }

    @Test
    void textMatchesASubstringIgnoringCase() {
        assertThat(titles(Map.of("cf.Customer", List.of("acme")))).containsExactly("A");
    }

    @Test
    void numberRangeIsInclusive() {
        assertThat(titles(Map.of("cf.Effort.min", List.of("5"), "cf.Effort.max", List.of("8"))))
                .containsExactly("B", "C");
    }

    @Test
    void dateRangeWithOnlyALowerBound() {
        assertThat(titles(Map.of("cf.Due.min", List.of("2026-11-01")))).containsExactly("B");
    }

    @Test
    void combinesWithTheBuiltInFilters() {
        Page<TestCaseResponse> page = cases(Map.of("cf.Browsers", List.of("Chrome")), List.of(TestCaseStatus.ACTIVE), 50);

        assertThat(page.getContent()).extracting(TestCaseResponse::title).containsExactly("A");
    }

    @Test
    void aMultiSelectMatchingSeveralOptionsCountsTheCaseOnce() {
        Page<TestCaseResponse> page = cases(Map.of("cf.Browsers", List.of("Chrome", "Firefox")), null, 1);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void blankValuesAndOtherParametersAreIgnored() {
        assertThat(titles(Map.of("cf.Component", List.of(""), "status", List.of("ACTIVE")))).hasSize(4);
    }

    @Test
    void anUnknownFieldIsRefused() {
        assertThatThrownBy(() -> titles(Map.of("cf.Sprint", List.of("12"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cf.Sprint");
    }

    @Test
    void aRangeOnATextFieldIsRefused() {
        assertThatThrownBy(() -> titles(Map.of("cf.Customer.min", List.of("a"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only NUMBER and DATE");
    }

    @Test
    void anUnknownOptionIsRefused() {
        assertThatThrownBy(() -> titles(Map.of("cf.Component", List.of("Payments"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payments");
    }

    @Test
    void testRunsFilterTheSameWay() {
        field(CustomFieldEntityType.TEST_RUN, "Sprint", CustomFieldType.NUMBER);
        testRunService.create(projectId, new CreateTestRunRequest("Sprint 12", null, null, null, null, null, null,
                Map.of("Sprint", 12)), null);
        testRunService.create(projectId, new CreateTestRunRequest("Sprint 13", null, null, null, null, null, null,
                Map.of("Sprint", 13)), null);
        entityManager.flush();

        var filter = new TestRunListFilter(null, null, null, null, null, null,
                parser.parse(projectId, CustomFieldEntityType.TEST_RUN, Map.of("cf.Sprint", List.of("13"))));

        assertThat(testRunService.findByProject(projectId, filter, PageRequest.of(0, 50)).getContent())
                .extracting(TestRunSummaryResponse::name).containsExactly("Sprint 13");
    }
}
