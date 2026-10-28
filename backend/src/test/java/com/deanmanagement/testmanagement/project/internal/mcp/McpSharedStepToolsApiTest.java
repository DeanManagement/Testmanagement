package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SaveSharedStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.sharedStep.SharedStepStepRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.SharedStepService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Agents see, use and — since TM14 — maintain shared steps (PRD-030 §3.5). */
class McpSharedStepToolsApiTest extends McpToolApiTestSupport {

    @Autowired private SharedStepService sharedStepService;
    @Autowired private SharedStepTools sharedStepTools;

    private SharedStepResponse loginIn(Project target) {
        return sharedStepService.create(target.getId(), new SaveSharedStepRequest("Log in", null, List.of(
                new SharedStepStepRequest(null, "Open the login page", "Form shown", null),
                new SharedStepStepRequest(null, "Sign in", "Dashboard", null))), null);
    }

    private McpDtos.CreatedTestCase caseWith(List<McpDtos.Step> steps) {
        return testCaseTools.createTestCase("Checkout", Priority.MEDIUM, null, null, null, null, steps, null, null,
                null, null);
    }

    private static McpDtos.Step local(String action) {
        return new McpDtos.Step(action, null, null);
    }

    private static McpDtos.Step shared(UUID id) {
        return new McpDtos.Step(null, null, null, id, null);
    }

    @Test
    void getTestCaseShowsTheSharedStepsExpandedAndTagged() {
        SharedStepResponse login = loginIn(project);
        authenticateAs(project, ProjectRole.TESTER);

        McpDtos.CreatedTestCase created = caseWith(List.of(local("Reset"), shared(login.id()), local("Pay")));

        assertThat(testCaseTools.getTestCase(created.key()).steps())
                .extracting(McpDtos.Step::action, McpDtos.Step::sharedStepTitle).containsExactly(
                        tuple("Reset", null), tuple("Open the login page", "Log in"),
                        tuple("Sign in", "Log in"), tuple("Pay", null));
    }

    @Test
    void stepsEchoedBackKeepTheReference() {
        SharedStepResponse login = loginIn(project);
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase created = caseWith(List.of(local("Reset"), shared(login.id())));
        List<McpDtos.Step> asRead = testCaseTools.getTestCase(created.key()).steps();

        testCaseTools.updateTestCase(created.key(), "Renamed", null, null, null, null, null, asRead, null, null, null);

        assertThat(sharedStepService.usages(project.getId(), login.id())).hasSize(1);
        assertThat(testCaseTools.getTestCase(created.key()).steps()).hasSize(3);
    }

    @Test
    void aStepNeedsAnActionOrASharedStep() {
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> caseWith(List.of(new McpDtos.Step(" ", null, null))))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("sharedStepId");
    }

    @Test
    void anotherProjectsSharedStepIsNeitherListedNorUsable() {
        SharedStepResponse foreign = loginIn(otherProject);
        loginIn(project);
        authenticateAs(project, ProjectRole.TESTER);

        assertThat(sharedStepTools.listSharedSteps(null).sharedSteps()).singleElement()
                .satisfies(s -> assertThat(s.id()).isNotEqualTo(foreign.id()));
        assertThatThrownBy(() -> caseWith(List.of(shared(foreign.id())))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void listSharedStepsFiltersByTitleAndCountsUsage() {
        SharedStepResponse login = loginIn(project);
        sharedStepService.create(project.getId(), new SaveSharedStepRequest("Reset the basket", null, List.of()), null);
        authenticateAs(project, ProjectRole.VIEWER);

        McpDtos.SharedStepList list = sharedStepTools.listSharedSteps("LOG");

        assertThat(list.sharedSteps()).extracting(McpDtos.SharedStepItem::title, McpDtos.SharedStepItem::stepCount)
                .containsExactly(tuple("Log in", 2));
        assertThat(list.sharedSteps().getFirst().id()).isEqualTo(login.id());
    }

    // --- maintenance (TM14) ----------------------------------------------------------------

    private static McpDtos.SharedStepStep step(UUID id, String action) {
        return new McpDtos.SharedStepStep(id, action, null, null);
    }

    @Test
    void anAgentCreatesASharedStepAndReadsItBack() {
        authenticateAs(project, ProjectRole.TESTER);

        McpDtos.SharedStepDetail created = sharedStepTools.createSharedStep("Log in",
                List.of(step(null, "Open the login page"), step(null, "Sign in")), null);

        assertThat(sharedStepTools.getSharedStep(created.id()).steps())
                .extracting(McpDtos.SharedStepStep::action).containsExactly("Open the login page", "Sign in");
    }

    @Test
    void renamingKeepsTheStepsAndTheirIds() {
        SharedStepResponse login = loginIn(project);
        authenticateAs(project, ProjectRole.TESTER);
        List<UUID> idsBefore = sharedStepTools.getSharedStep(login.id()).steps().stream()
                .map(McpDtos.SharedStepStep::id).toList();

        McpDtos.SharedStepDetail renamed = sharedStepTools.updateSharedStep(login.id(), "Sign in as admin", null, null);

        assertThat(renamed.title()).isEqualTo("Sign in as admin");
        assertThat(renamed.steps()).extracting(McpDtos.SharedStepStep::id).containsExactlyElementsOf(idsBefore);
    }

    @Test
    void stepsSentBackWithTheirIdsAreEditedInPlace() {
        SharedStepResponse login = loginIn(project);
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.SharedStepStep first = sharedStepTools.getSharedStep(login.id()).steps().getFirst();

        McpDtos.SharedStepDetail updated = sharedStepTools.updateSharedStep(login.id(), null, null,
                List.of(step(first.id(), "Open /login"), step(null, "Accept cookies")));

        assertThat(updated.steps()).extracting(McpDtos.SharedStepStep::action)
                .containsExactly("Open /login", "Accept cookies");
        assertThat(updated.steps().getFirst().id()).isEqualTo(first.id());
    }

    @Test
    void aSharedStepInUseCannotBeDeletedButAnUnusedOneCan() {
        SharedStepResponse login = loginIn(project);
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase checkout = caseWith(List.of(shared(login.id())));

        assertThatThrownBy(() -> sharedStepTools.deleteSharedStep(login.id()))
                .hasMessageContaining("used by 1 test case");

        testCaseTools.updateTestCase(checkout.key(), null, null, null, null, null, null, List.of(local("Pay")),
                null, null, null);
        sharedStepTools.deleteSharedStep(login.id());
        assertThat(sharedStepTools.listSharedSteps(null).sharedSteps()).isEmpty();
    }

    @Test
    void aViewerKeyCannotCreateASharedStep() {
        authenticateAs(project, ProjectRole.VIEWER);

        assertThatThrownBy(() -> sharedStepTools.createSharedStep("Log in", List.of(step(null, "Sign in")), null))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("TESTER");
    }
}
