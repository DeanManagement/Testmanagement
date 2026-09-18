package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionResponse;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Custom fields in import/export (PRD-035 §3.7) and in version snapshots (§3.3). */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomFieldImportExportTest {

    @Autowired private CustomFieldService fieldService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestCaseImportExportService importExportService;
    @Autowired private TestCaseVersionService versionService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EntityManager entityManager;

    private UUID source;
    private UUID target;

    @BeforeEach
    void setUp() {
        source = project("CFIA");
        target = project("CFIB");
        for (UUID projectId : List.of(source, target)) {
            field(projectId, "Effort", CustomFieldType.NUMBER);
            field(projectId, "Component", CustomFieldType.SELECT, "Checkout", "Search");
            field(projectId, "Browsers", CustomFieldType.MULTI_SELECT, "Chrome", "Firefox");
            field(projectId, "Customer", CustomFieldType.TEXT);
        }
    }

    private UUID project(String key) {
        Project project = new Project();
        project.setName(key);
        project.setKey(key);
        return projectRepository.save(project).getId();
    }

    private UUID field(UUID projectId, String name, CustomFieldType type, String... options) {
        return fieldService.create(projectId, new CreateCustomFieldRequest(CustomFieldEntityType.TEST_CASE, name,
                type, List.of(options), null), null).id();
    }

    private TestCaseResponse createCase(UUID projectId, String title, Map<String, Object> values) {
        TestCaseResponse created = testCaseService.create(projectId, new CreateTestCaseRequest(title, null, null,
                Priority.MEDIUM, TestCaseStatus.DRAFT, null, null, null, values), null);
        entityManager.flush();
        return created;
    }

    private ImportResultResponse importInto(UUID projectId, String fileName, byte[] content, boolean dryRun) {
        ImportResultResponse result = importExportService.importData(projectId, fileName, content, dryRun, null);
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private Map<String, Object> onlyCaseIn(UUID projectId) {
        List<TestCaseResponse> cases = testCaseService.findByProject(projectId,
                new TestCaseListFilter(null, null, null, null, null, false, false, null),
                PageRequest.of(0, 10)).getContent();
        assertThat(cases).hasSize(1);
        return cases.getFirst().customFields();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static final Map<String, Object> VALUES = Map.of("Effort", new BigDecimal("-2.5"),
            "Component", "Search", "Browsers", List.of("Chrome", "Firefox"), "Customer", "=ACME");

    @Nested
    class Csv {

        @Test
        void exportAddsOneColumnPerFieldIncludingArchived() {
            UUID sprint = field(source, "Sprint", CustomFieldType.TEXT);
            fieldService.archive(source, sprint, null);
            createCase(source, "Pay", VALUES);

            String csv = new String(importExportService.exportCsv(source, false), StandardCharsets.UTF_8);

            assertThat(csv.lines().findFirst().orElseThrow())
                    .endsWith("steps,estimateMinutes,cf:Effort,cf:Component,cf:Browsers,cf:Customer,cf:Sprint");
            assertThat(csv).contains("-2.5,Search,Chrome;Firefox,'=ACME,");
        }

        @Test
        void roundTripPreservesValuesIncludingMultiSelect() {
            createCase(source, "Pay", VALUES);
            byte[] csv = importExportService.exportCsv(source, false);

            ImportResultResponse result = importInto(target, "cases.csv", csv, false);

            assertThat(result.imported()).isEqualTo(1);
            assertThat(onlyCaseIn(target))
                    .containsEntry("Effort", new BigDecimal("-2.5"))
                    .containsEntry("Component", "Search")
                    .containsEntry("Browsers", List.of("Chrome", "Firefox"))
                    // csvSafe's apostrophe survives a round trip, as it does for every text cell.
                    .containsEntry("Customer", "'=ACME");
        }

        @Test
        void dryRunReportsUnknownFieldsAndOptionsPerRow() {
            String csv = """
                    title,cf:Component,cf:Sprint
                    Good,Search,
                    Bad option,Payments,
                    Unknown field,Search,12
                    """;

            ImportResultResponse result = importInto(target, "cases.csv", utf8(csv), true);

            assertThat(result.imported()).isEqualTo(1);
            assertThat(result.errors()).extracting(ImportResultResponse.ImportError::row).containsExactly(3, 4);
            assertThat(result.errors().get(0).message()).contains("Payments");
            assertThat(result.errors().get(1).message()).contains("Sprint");
        }

        @Test
        void aRealImportSkipsTheSameRows() {
            String csv = """
                    title,cf:Component
                    Good,Search
                    Bad option,Payments
                    """;

            ImportResultResponse result = importInto(target, "cases.csv", utf8(csv), false);

            assertThat(result.imported()).isEqualTo(1);
            assertThat(result.skipped()).isEqualTo(1);
            assertThat(onlyCaseIn(target)).containsEntry("Component", "Search");
        }
    }

    @Nested
    class Json {

        @Test
        void roundTripPreservesValues() {
            createCase(source, "Pay", VALUES);
            byte[] json = importExportService.exportJson(source);

            importInto(target, "cases.json", json, false);

            assertThat(onlyCaseIn(target))
                    .containsEntry("Component", "Search")
                    .containsEntry("Browsers", List.of("Chrome", "Firefox"))
                    .containsEntry("Customer", "=ACME");
        }
    }

    @Nested
    class Versions {

        @Test
        void aSnapshotKeepsTheValuesTheCaseHadBeforeTheEdit() {
            UUID id = createCase(source, "Pay", Map.of("Component", "Checkout")).id();

            testCaseService.update(source, id, new UpdateTestCaseRequest(null, null, null, null, null, null, null,
                    Map.of("Component", "Search")), null);
            entityManager.flush();
            entityManager.clear();

            TestCaseVersionResponse before = versionService.get(source, id, 1);
            TestCaseVersionResponse current = versionService.get(source, id, 2);
            assertThat(before.customFields()).containsEntry("Component", "Checkout");
            assertThat(current.customFields()).containsEntry("Component", "Search");
        }
    }
}
