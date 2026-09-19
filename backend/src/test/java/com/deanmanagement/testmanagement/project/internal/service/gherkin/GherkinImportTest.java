package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse.ImportError;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseParameterSetRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseImportExportService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gherkin import through the real import entry point (PRD-040 §3.3). Deliberately not
 * {@code @Transactional}, so what is asserted is what was committed.
 */
@SpringBootTest
@ActiveProfiles("dev")
class GherkinImportTest {

    private static final String LOGIN_FEATURE = """
            @auth
            Feature: Login
              Background:
                Given the app is running

              @smoke
              Scenario: Valid credentials
                When they sign in
                Then the dashboard is shown

              Rule: Lockout
                Scenario Outline: Wrong password
                  When they sign in with <password>
                  Then they see <message>

                  Examples:
                    | password | message |
                    | x        | Wrong   |
                    | y        | Locked  |
            """;

    @Autowired private TestCaseImportExportService importExportService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TestCaseRepository testCaseRepository;
    @Autowired private TestCaseFolderRepository folderRepository;
    @Autowired private TestCaseParameterSetRepository parameterSetRepository;
    @Autowired private ProjectService projectService;
    @Autowired private TestCaseService testCaseService;

    private UUID projectId;
    private String projectKey;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Gherkin import");
        projectKey = "GH" + Integer.toHexString(new Random().nextInt(0xFFFFF)).toUpperCase();
        project.setKey(projectKey);
        projectId = projectRepository.save(project).getId();
    }

    @AfterEach
    void tearDown() {
        projectService.delete(projectId, null);
    }

    private ImportResultResponse importFeature(String text) {
        return importFile("login.feature", text.getBytes(StandardCharsets.UTF_8), false);
    }

    private ImportResultResponse importFile(String name, byte[] content, boolean dryRun) {
        return importExportService.importData(projectId, name, content, null, dryRun, null);
    }

    private List<TestCase> cases() {
        return testCaseRepository.findByProjectIdWithSteps(projectId);
    }

    private TestCase caseTitled(String title) {
        return cases().stream().filter(tc -> tc.getTitle().equals(title)).findFirst().orElseThrow();
    }

    private List<String> folderNames() {
        return folderRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .map(TestCaseFolder::getName).toList();
    }

    /** Through the repository: the case's folder is a lazy proxy, and this test has no session. */
    private String folderNameOf(TestCase tc) {
        return folderRepository.findById(tc.getFolder().getId()).orElseThrow().getName();
    }

    /** The file with {@code @tm:} keys stamped on its scenarios, as a later export will write it. */
    private String keyed(String feature) {
        String validKey = caseTitled("Valid credentials").getKey();
        String outlineKey = caseTitled("Wrong password").getKey();
        return feature
                .replace("  @smoke\n", "  @tm:" + validKey + " @smoke\n")
                .replace("    Scenario Outline:", "    @tm:" + outlineKey + "\n    Scenario Outline:");
    }

    @Test
    void aFeatureFileBecomesFoldersCasesAndParameterSets() {
        ImportResultResponse result = importFeature(LOGIN_FEATURE);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        assertThat(folderNames()).containsExactly("Login", "Lockout");

        TestCase valid = caseTitled("Valid credentials");
        assertThat(folderNameOf(valid)).isEqualTo("Login");
        assertThat(valid.getStatus()).isEqualTo(TestCaseStatus.ACTIVE);
        assertThat(testCaseService.findById(projectId, valid.getId()).labels()).containsExactlyInAnyOrder("auth", "smoke");
        assertThat(valid.getPreconditions()).isEqualTo("Given the app is running");
        assertThat(valid.getSteps()).extracting(TestStep::getAction)
                .containsExactly("When they sign in", "Then the dashboard is shown");

        TestCase outline = caseTitled("Wrong password");
        assertThat(folderNameOf(outline)).isEqualTo("Lockout");
        assertThat(outline.getSteps().getFirst().getAction()).isEqualTo("When they sign in with {password}");
        assertThat(parameterSetRepository.findByTestCaseIdOrderByOrderIndexAsc(outline.getId()))
                .extracting(TestCaseParameterSet::getName).containsExactly("Example #1", "Example #2");
    }

    @Test
    void aDryRunReportsTheSameCountsAndWritesNothing() {
        ImportResultResponse result = importFile("login.feature", LOGIN_FEATURE.getBytes(StandardCharsets.UTF_8), true);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.dryRun()).isTrue();
        assertThat(cases()).isEmpty();
        assertThat(folderNames()).isEmpty();
    }

    @Test
    void importingIntoAFolderPutsTheFeatureUnderItAndReusesSameNamedFolders() {
        importFeature(LOGIN_FEATURE);
        UUID loginFolder = caseTitled("Valid credentials").getFolder().getId();

        importFeature(LOGIN_FEATURE.replace("Valid credentials", "Second copy"));

        assertThat(folderNames()).containsExactly("Login", "Lockout");
        assertThat(caseTitled("Second copy").getFolder().getId()).isEqualTo(loginFolder);
    }

    @Test
    void aTargetFolderOfAnotherProjectIsNotFound() {
        UUID elsewhere = UUID.randomUUID();

        assertThatThrownBy(() -> importExportService.importData(projectId, "login.feature",
                LOGIN_FEATURE.getBytes(StandardCharsets.UTF_8), elsewhere, false, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reImportingAnUnchangedKeyedFileChangesNothing() {
        importFeature(LOGIN_FEATURE);
        String keyedFile = keyed(LOGIN_FEATURE);
        int versionBefore = caseTitled("Valid credentials").getCurrentVersion();

        ImportResultResponse result = importFeature(keyedFile);

        assertThat(result.unchanged()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.imported()).isZero();
        assertThat(cases()).hasSize(2);
        assertThat(caseTitled("Valid credentials").getCurrentVersion()).isEqualTo(versionBefore);
    }

    @Test
    void aChangedKeyedScenarioUpdatesItsCaseWithANewVersion() {
        importFeature(LOGIN_FEATURE);
        String keyedFile = keyed(LOGIN_FEATURE);
        TestCase before = caseTitled("Valid credentials");

        ImportResultResponse result = importFeature(keyedFile
                .replace("Scenario: Valid credentials", "Scenario: Renamed")
                .replace("| y        | Locked  |", "| z        | Locked  |"));

        assertThat(result.updated()).isEqualTo(2);
        assertThat(result.unchanged()).isZero();
        TestCase renamed = caseTitled("Renamed");
        assertThat(renamed.getId()).isEqualTo(before.getId());
        assertThat(renamed.getCurrentVersion()).isEqualTo(before.getCurrentVersion() + 1);
        TestCase outline = caseTitled("Wrong password");
        assertThat(parameterSetRepository.findByTestCaseIdOrderByOrderIndexAsc(outline.getId()))
                .extracting(set -> set.getValuesJson()).last().asString().contains("\"z\"");
    }

    @Test
    void anUnknownKeyIsAnErrorRowAndTheRestStillImports() {
        ImportResultResponse result = importFeature(LOGIN_FEATURE.replace("  @smoke\n", "  @tm:NOPE-1\n"));

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.errors()).singleElement().extracting(ImportError::message).asString()
                .contains("@tm:NOPE-1 is not a test case of this project");
        assertThat(cases()).extracting(TestCase::getTitle).containsExactly("Wrong password");
    }

    @Test
    void aCaseInTheFeatureFolderThatNoScenarioClaimsIsReportedNotDeleted() {
        importFeature(LOGIN_FEATURE);
        String keyedFile = keyed(LOGIN_FEATURE);
        String validKey = caseTitled("Valid credentials").getKey();

        ImportResultResponse result = importFeature(keyedFile.replaceAll("(?s)  @tm:[^\\n]+\\n  Scenario: Valid.*?shown\\n", ""));

        assertThat(result.warnings()).extracting(ImportError::message)
                .anyMatch(m -> m.contains(validKey) && m.contains("has no scenario in the upload"));
        assertThat(cases()).hasSize(2);
    }

    @Test
    void aZipImportsEveryFeatureInIt() throws IOException {
        byte[] zip = zip("features/login.feature", LOGIN_FEATURE,
                "features/logout.feature", "Feature: Logout\n  Scenario: Bye\n    When they sign out\n",
                "README.md", "ignored");

        ImportResultResponse result = importFile("features.zip", zip, false);

        assertThat(result.imported()).isEqualTo(3);
        assertThat(folderNames()).containsExactlyInAnyOrder("Login", "Lockout", "Logout");
    }

    @Test
    void aProjectThatRequiresReviewGetsCasesInReview() {
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.setReviewRequired(true);
        projectRepository.save(project);

        ImportResultResponse result = importFeature(LOGIN_FEATURE);

        assertThat(cases()).extracting(TestCase::getStatus).containsOnly(TestCaseStatus.IN_REVIEW);
        assertThat(result.warnings()).extracting(ImportError::message).anyMatch(m -> m.contains("IN_REVIEW"));
    }

    private static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
