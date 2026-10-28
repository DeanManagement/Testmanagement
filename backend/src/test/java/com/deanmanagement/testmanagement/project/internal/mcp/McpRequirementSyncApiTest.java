package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.StubHttpServer;
import com.deanmanagement.testmanagement.project.internal.StubHttpServer.Response;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementSyncConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerTokenCipher;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.service.RequirementSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TM19: sync_requirements for ADMIN keys, and get_requirement naming where a requirement comes from. */
class McpRequirementSyncApiTest extends McpToolApiTestSupport {

    @Autowired private RequirementTools requirementTools;
    @Autowired private RequirementSyncService syncService;
    @Autowired private IssueTrackerConfigRepository trackerRepository;
    @Autowired private IssueTrackerTokenCipher tokenCipher;

    private StubHttpServer azure;

    @BeforeEach
    void setUpTracker() throws IOException {
        azure = new StubHttpServer();
        IssueTrackerConfig tracker = new IssueTrackerConfig();
        tracker.setProjectId(project.getId());
        tracker.setProvider(IssueTrackerProviderType.AZURE_DEVOPS);
        tracker.setBaseUrl(azure.baseUrl() + "/contoso");
        tracker.setProjectRef("Payments");
        tracker.setApiTokenEncrypted(tokenCipher.encrypt("pat"));
        trackerRepository.save(tracker);
        syncService.save(project.getId(), new SaveRequirementSyncConfigRequest(true, false,
                "SELECT [System.Id] FROM WorkItems", null, null, null));
        azure.respond(Response.json("{\"workItems\":[{\"id\":7}]}"), Response.json("{\"value\":[{\"id\":7,"
                + "\"fields\":{\"System.Title\":\"Pay by card\",\"System.State\":\"Active\"}}]}"));
    }

    @AfterEach
    void closeTracker() {
        azure.close();
    }

    @Test
    void aTesterKeyCannotSync() {
        authenticateAs(project, ProjectRole.TESTER);

        assertThatThrownBy(() -> requirementTools.syncRequirements(true))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("requires ADMIN");
    }

    @Test
    void anAdminKeySyncsAndGetRequirementNamesTheSource() {
        authenticateAs(project, ProjectRole.ADMIN);

        McpDtos.RequirementSyncResult result = requirementTools.syncRequirements(null);
        McpDtos.Requirement synced = requirementTools.getRequirement("7");

        assertThat(result.created()).isEqualTo(1);
        assertThat(synced.source()).isEqualTo("AZURE_DEVOPS");
        assertThat(synced.sourceUrl()).endsWith("/contoso/Payments/_workitems/edit/7");
        assertThat(synced.lastSyncedAt()).isNotNull();
    }

    @Test
    void updateRequirementRefusesASyncedField() {
        authenticateAs(project, ProjectRole.ADMIN);
        requirementTools.syncRequirements(false);
        var id = requirementTools.getRequirement("7").id();

        assertThatThrownBy(() -> requirementTools.updateRequirement(id, null, "Other title", null, null, null,
                null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("synced from Azure DevOps; change title there");
    }
}
