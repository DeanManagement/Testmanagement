package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLinkType;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseLinkRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM07: precondition and related-case links, committed (not @Transactional). */
class McpTestCaseLinkApiTest extends McpToolApiTestSupport {

    @Autowired private TestCaseLinkTools linkTools;
    @Autowired private TestCaseEditTools editTools;
    @Autowired private TestCaseLinkRepository linkRepository;

    @Test
    void getTestCaseShowsALinkFromBothSides() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase login = createCase("Log in");
        McpDtos.CreatedTestCase checkout = createCase("Checkout");

        linkTools.linkTestCases(checkout.key(), login.key(), TestCaseLinkType.PRECONDITION);

        assertThat(testCaseTools.getTestCase(checkout.key()).links()).containsExactly(
                new McpDtos.TestCaseLinkRef("PRECONDITION", "OUTGOING", login.id(), login.key(), "Log in"));
        assertThat(testCaseTools.getTestCase(login.key()).links()).containsExactly(
                new McpDtos.TestCaseLinkRef("PRECONDITION", "INCOMING", checkout.id(), checkout.key(), "Checkout"));
    }

    @Test
    void linkingTwiceKeepsOneLink() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        McpDtos.CreatedTestCase b = createCase("B");

        linkTools.linkTestCases(a.key(), b.key(), TestCaseLinkType.RELATES_TO);
        linkTools.linkTestCases(b.key(), a.key(), TestCaseLinkType.RELATES_TO);

        assertThat(linkTools.linkTestCases(a.key(), b.key(), TestCaseLinkType.RELATES_TO).links()).hasSize(1);
    }

    @Test
    void aCaseCannotBeLinkedToItselfOrBeItsPreconditionsPrecondition() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        McpDtos.CreatedTestCase b = createCase("B");
        linkTools.linkTestCases(a.key(), b.key(), TestCaseLinkType.PRECONDITION);

        assertThatThrownBy(() -> linkTools.linkTestCases(a.key(), a.key(), TestCaseLinkType.RELATES_TO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> linkTools.linkTestCases(b.key(), a.key(), TestCaseLinkType.PRECONDITION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCaseInAnotherProjectIsNotFound() {
        authenticateAs(otherProject, ProjectRole.TESTER);
        McpDtos.CreatedTestCase foreign = createCase("Foreign");
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase own = createCase("Own");

        assertThatThrownBy(() -> linkTools.linkTestCases(own.key(), foreign.id().toString(),
                TestCaseLinkType.RELATES_TO)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unlinkRemovesTheLinkWhicheverWayRoundItIsNamed() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        McpDtos.CreatedTestCase b = createCase("B");
        linkTools.linkTestCases(a.key(), b.key(), TestCaseLinkType.PRECONDITION);

        assertThat(linkTools.unlinkTestCases(b.key(), a.key(), null).removed()).isEqualTo(1);
        assertThat(testCaseTools.getTestCase(a.key()).links()).isEmpty();
    }

    @Test
    void aCopyHasNoLinksAndDeletingACaseRemovesItsLinks() {
        authenticateAs(project, ProjectRole.TESTER);
        McpDtos.CreatedTestCase a = createCase("A");
        McpDtos.CreatedTestCase b = createCase("B");
        linkTools.linkTestCases(a.key(), b.key(), TestCaseLinkType.PRECONDITION);

        McpDtos.CreatedTestCase copy = editTools.copyTestCase(a.key(), null);
        assertThat(testCaseTools.getTestCase(copy.key()).links()).isEmpty();

        testCaseTools.deleteTestCase(b.key());
        assertThat(linkRepository.findInvolving(a.id())).isEmpty();
    }
}
