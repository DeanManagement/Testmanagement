package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse.ImportError;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SaveSharedStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared steps through JSON, CSV and Gherkin (PRD-030 §3.2). Not {@code @Transactional}: what is
 * asserted is what was committed, and project deletion runs with shared steps in place.
 */
@SpringBootTest
@ActiveProfiles("dev")
class SharedStepImportExportTest {

    @Autowired private TestCaseImportExportService importExportService;
    @Autowired private GherkinExporter gherkinExporter;
    @Autowired private SharedStepService sharedStepService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;

    private Project source;
    private Project target;

    @BeforeEach
    void setUp() {
        source = project("Source");
        target = project("Target");
    }

    @AfterEach
    void tearDown() {
        projectService.delete(source.getId(), null);
        projectService.delete(target.getId(), null);
    }

    private Project project(String name) {
        Project p = new Project();
        p.setName(name);
        p.setKey("SX" + Integer.toHexString(new Random().nextInt(0xFFFFF)).toUpperCase());
        return projectRepository.save(p);
    }

    private SharedStepResponse loginIn(Project project) {
        return sharedStepService.create(project.getId(), new SaveSharedStepRequest("Log in", null, List.of(
                new SharedStepStepRequest(null, "Given the login page", null, null),
                new SharedStepStepRequest(null, "When I sign in", null, null))), null);
    }

    private TestCaseResponse checkoutIn(Project project, UUID loginId) {
        return testCaseService.create(project.getId(), new CreateTestCaseRequest("Checkout", null, null,
                Priority.MEDIUM, TestCaseStatus.ACTIVE, Set.of(),
                List.of(new TestStepRequest("Given a basket", null, null), new TestStepRequest(null, null, null, loginId),
                        new TestStepRequest("Then I pay", null, null)), null), null);
    }

    private ImportResultResponse importInto(Project project, String fileName, byte[] content, boolean dryRun) {
        return importExportService.importData(project.getId(), fileName, content, null, dryRun, null);
    }

    @Test
    void aJsonExportKeepsReferencesIntoAProjectWithTheSameSharedStep() {
        checkoutIn(source, loginIn(source).id());
        SharedStepResponse targetLogin = loginIn(target);

        ImportResultResponse result = importInto(target, "cases.json", importExportService.exportJson(source.getId()), false);

        assertThat(result.errors()).isEmpty();
        assertThat(sharedStepService.usages(target.getId(), targetLogin.id())).hasSize(1);
    }

    @Test
    void anUnknownSharedStepIsARowErrorInTheDryRun() {
        checkoutIn(source, loginIn(source).id());

        ImportResultResponse result = importInto(target, "cases.json", importExportService.exportJson(source.getId()), true);

        assertThat(result.errors()).singleElement().extracting(ImportError::message).asString()
                .contains("unknown shared step(s): 'Log in'");
    }

    @Test
    void aCsvExportWritesTheSharedStepsOut() {
        checkoutIn(source, loginIn(source).id());

        String csv = new String(importExportService.exportCsv(source.getId(), false), StandardCharsets.UTF_8);

        assertThat(csv).contains("Given a basket|;;Given the login page|;;When I sign in|;;Then I pay|");
    }

    @Test
    void aGherkinReImportOfACaseUsingASharedStepIsUnchanged() {
        checkoutIn(source, loginIn(source).id());
        GherkinExporter.ExportFile exported = gherkinExporter.export(source.getId(), null);

        ImportResultResponse result = importInto(source, exported.fileName(), exported.content(), false);

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(new String(exported.content(), StandardCharsets.UTF_8))
                .contains("Given a basket\n    Given the login page\n    When I sign in\n    Then I pay");
    }

    @Test
    void aChangedGherkinScenarioReplacesTheSharedStepAndSaysSoInTheDryRun() {
        TestCaseResponse checkout = checkoutIn(source, loginIn(source).id());
        String feature = "Feature: Unfiled\n  @tm:" + checkout.key() + "\n  Scenario: Checkout\n    Given a basket\n    Then I pay\n";

        ImportResultResponse dryRun = importInto(source, "unfiled.feature", feature.getBytes(StandardCharsets.UTF_8), true);
        importInto(source, "unfiled.feature", feature.getBytes(StandardCharsets.UTF_8), false);

        assertThat(dryRun.updated()).isEqualTo(1);
        assertThat(dryRun.warnings()).extracting(ImportError::message).anyMatch(m -> m.contains("uses shared steps"));
        assertThat(testCaseService.findById(source.getId(), checkout.id()).steps())
                .extracting(TestStepResponse::sharedStepId).containsOnlyNulls();
    }
}
