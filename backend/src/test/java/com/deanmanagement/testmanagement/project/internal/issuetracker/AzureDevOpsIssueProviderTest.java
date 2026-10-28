package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.StubHttpServer;
import com.deanmanagement.testmanagement.project.internal.StubHttpServer.Response;
import com.deanmanagement.testmanagement.project.internal.entity.IssueState;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PRD-026 §3.3: the Azure Boards adapter against a stub of the work item API. */
class AzureDevOpsIssueProviderTest {

    /** A custom process: no state here is named like an Agile or Scrum one. */
    private static final String CUSTOM_STATES = "{\"value\":["
            + "{\"name\":\"Triage\",\"category\":\"Proposed\"},"
            + "{\"name\":\"Fixing\",\"category\":\"InProgress\"},"
            + "{\"name\":\"Shipped\",\"category\":\"Completed\"},"
            + "{\"name\":\"Binned\",\"category\":\"Removed\"},"
            + "{\"name\":\"Odd\",\"category\":\"SomethingNew\"}]}";

    private final ObjectMapper json = new ObjectMapper();
    private StubHttpServer server;
    private AzureDevOpsIssueProvider provider;
    private IssueTrackerConfig entity;
    private IssueTrackerProvider.DecryptedConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubHttpServer();
        provider = new AzureDevOpsIssueProvider(
                new IssueTrackerProperties(null, true, false, 2000, 3000, 3600000L, 50, 0L), json);
        entity = new IssueTrackerConfig();
        entity.setId(UUID.randomUUID());
        entity.setProvider(IssueTrackerProviderType.AZURE_DEVOPS);
        entity.setBaseUrl(server.baseUrl() + "/contoso");
        entity.setProjectRef("Payments");
        config = new IssueTrackerProvider.DecryptedConfig(entity, "pat-123");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static String workItem(int id, String state) {
        return "{\"id\":" + id + ",\"fields\":{\"System.Title\":\"Checkout fails\",\"System.State\":\"" + state
                + "\",\"System.WorkItemType\":\"Bug\",\"System.TeamProject\":\"Payments\"}}";
    }

    @Test
    void searchRunsWiqlThenReadsTheWorkItemsInOneBatch() {
        server.respond(Response.json("{\"workItems\":[{\"id\":7},{\"id\":9}]}"),
                Response.json("{\"value\":[" + workItem(7, "Triage") + "," + workItem(9, "Shipped") + "]}"),
                Response.json(CUSTOM_STATES));

        var issues = provider.search(config, "checkout");

        StubHttpServer.Request wiql = server.requests().get(0);
        assertThat(wiql.method()).isEqualTo("POST");
        assertThat(wiql.pathAndQuery()).isEqualTo("/contoso/Payments/_apis/wit/wiql?$top=20&api-version=7.1");
        assertThat(json.readTree(wiql.body()).get("query").asString()).contains("CONTAINS 'checkout'");
        assertThat(server.requests().get(1).pathAndQuery()).startsWith("/contoso/_apis/wit/workitems?ids=7,9&fields=");
        assertThat(issues).extracting(Issue::externalId, Issue::state)
                .containsExactly(Tuple.tuple("7", IssueState.OPEN), Tuple.tuple("9", IssueState.CLOSED));
        assertThat(issues.get(0).url()).endsWith("/contoso/Payments/_workitems/edit/7");
    }

    @Test
    void aSearchWithoutMatchesMakesNoBatchCall() {
        server.respond(Response.json("{\"workItems\":[]}"));

        assertThat(provider.search(config, "nothing")).isEmpty();
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void createsABugAsAJsonPatchWithAnUnencodedTypeSegmentAndHtmlBody() {
        server.respond(Response.json(workItem(11, "Triage")), Response.json(CUSTOM_STATES));

        Issue issue = provider.create(config, new IssueDraft("Checkout fails", "**Step 2** <failed>\nsee run"));

        StubHttpServer.Request create = server.requests().get(0);
        assertThat(create.pathAndQuery()).isEqualTo("/contoso/Payments/_apis/wit/workitems/$Bug?api-version=7.1");
        assertThat(create.contentType()).isEqualTo("application/json-patch+json");
        JsonNode operations = json.readTree(create.body());
        assertThat(operations.get(0).get("path").asString()).isEqualTo("/fields/System.Title");
        assertThat(operations.get(1).get("path").asString()).isEqualTo("/fields/Microsoft.VSTS.TCM.ReproSteps");
        assertThat(operations.get(1).get("value").asString()).isEqualTo("**Step 2** &lt;failed&gt;<br>see run");
        assertThat(issue.externalId()).isEqualTo("11");
    }

    @Test
    void aTypeWithoutReproStepsIsRetriedWithDescription() {
        entity.setWorkItemType("Product Backlog Item");
        server.respond(Response.json(400,
                        "{\"message\":\"TF401326: Invalid field 'Microsoft.VSTS.TCM.ReproSteps'.\"}"),
                Response.json(workItem(12, "Triage")), Response.json(CUSTOM_STATES));

        provider.create(config, new IssueDraft("t", "b"));

        assertThat(server.requests().get(0).pathAndQuery())
                .startsWith("/contoso/Payments/_apis/wit/workitems/$Product%20Backlog%20Item?");
        assertThat(json.readTree(server.requests().get(1).body()).get(1).get("path").asString())
                .isEqualTo("/fields/System.Description");
    }

    @Test
    void aTypeTheProcessDoesNotHaveSaysWhichSettingToChange() {
        server.respond(Response.json(404, "{\"message\":\"VS402323: Work item type Bug does not exist\"}"));

        assertThatThrownBy(() -> provider.create(config, new IssueDraft("t", "b")))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("type 'Bug'");
    }

    @Test
    void stateComesFromItsCategoryInACustomProcess() {
        server.respond(Response.json(workItem(1, "Binned")), Response.json(CUSTOM_STATES));
        assertThat(provider.get(config, "1").state()).isEqualTo(IssueState.CLOSED);

        server.respond(Response.json(workItem(2, "Fixing")));
        assertThat(provider.get(config, "2").state()).isEqualTo(IssueState.OPEN);

        server.respond(Response.json(workItem(3, "Odd")));
        assertThat(provider.get(config, "3").state()).isEqualTo(IssueState.UNKNOWN);
    }

    @Test
    void theStatesOfATypeAreReadOnceForSeveralWorkItems() {
        server.respond(Response.json(workItem(1, "Triage")), Response.json(CUSTOM_STATES),
                Response.json(workItem(2, "Shipped")));

        provider.get(config, "1");
        provider.get(config, "2");

        assertThat(server.requests()).extracting(StubHttpServer.Request::pathAndQuery)
                .filteredOn(path -> path.contains("/states")).hasSize(1);
    }

    @Test
    void aSignInPageAnsweredWith203IsARejectedToken() {
        server.respond(new Response(203, "text/html", "<html>Sign in</html>"));

        assertThatThrownBy(() -> provider.testConnection(config))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("rejected the configured access token");
    }

    @Test
    void testConnectionReadsTheConfiguredProject() {
        server.respond(Response.json("{\"id\":\"p\"}"));

        provider.testConnection(config);

        assertThat(server.lastRequest().pathAndQuery()).isEqualTo("/contoso/_apis/projects/Payments?api-version=7.1");
    }

    @Test
    void anUnsupportedApiVersionSaysWhichSettingToChange() {
        server.respond(Response.json(400,
                "{\"message\":\"The requested REST API version of 7.1 is out of range. api-version\"}"));

        assertThatThrownBy(() -> provider.testConnection(config))
                .hasMessageContaining("5.0 for Server 2019");
    }

    // ---- requirement sync (TM19) ----------------------------------------------------------------

    @Test
    void queryWorkItemsReadsTheIdsInBatchesOfTwoHundredWithRelations() {
        String ids = java.util.stream.IntStream.rangeClosed(1, 450).mapToObj(i -> "{\"id\":" + i + "}")
                .collect(java.util.stream.Collectors.joining(","));
        server.respond(Response.json("{\"workItems\":[" + ids + "]}"),
                Response.json("{\"value\":[" + workItem(1, "Active") + "]}"),
                Response.json("{\"value\":[" + workItem(201, "Active") + "]}"),
                Response.json("{\"value\":[" + workItem(401, "Active") + "]}"));

        var items = provider.queryWorkItems(config, "SELECT [System.Id] FROM WorkItems");

        assertThat(items).extracting(TrackerWorkItem::id).containsExactly("1", "201", "401");
        assertThat(server.requests()).hasSize(4);
        JsonNode second = json.readTree(server.requests().get(2).body());
        assertThat(second.get("ids")).hasSize(200);
        assertThat(second.get("ids").get(0).asInt()).isEqualTo(201);
        assertThat(second.get("$expand").asString()).isEqualTo("Relations");
        assertThat(json.readTree(server.requests().get(3).body()).get("ids")).hasSize(50);
        assertThat(json.readTree(server.requests().get(0).body()).get("query").asString())
                .isEqualTo("SELECT [System.Id] FROM WorkItems");
    }

    @Test
    void aQueryWithoutResultsMakesNoBatchCall() {
        server.respond(Response.json("{\"workItems\":[]}"));

        assertThat(provider.queryWorkItems(config, "SELECT [System.Id] FROM WorkItems")).isEmpty();
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void aWorkItemCarriesItsParentPriorityAndPlainTextDescription() {
        server.respond(Response.json("{\"workItems\":[{\"id\":5}]}"), Response.json("{\"value\":[{\"id\":5,"
                + "\"fields\":{\"System.Title\":\"Pay\",\"System.State\":\"New\",\"System.TeamProject\":\"Payments\","
                + "\"Microsoft.VSTS.Common.Priority\":2,\"System.Description\":\"<div>a &amp; b</div>\"},"
                + "\"relations\":[{\"rel\":\"System.LinkTypes.Related\",\"url\":\"https://x/_apis/wit/workItems/8\"},"
                + "{\"rel\":\"System.LinkTypes.Hierarchy-Reverse\",\"url\":\"https://x/_apis/wit/workItems/3\"}]}]}"));

        TrackerWorkItem item = provider.queryWorkItems(config, "SELECT [System.Id] FROM WorkItems").getFirst();

        assertThat(item.parentId()).isEqualTo("3");
        assertThat(item.priority()).isEqualTo(2);
        assertThat(item.description()).isEqualTo("a & b");
        assertThat(item.url()).endsWith("/contoso/Payments/_workitems/edit/5");
    }

    @Test
    void aRejectedQuerySurfacesTheProvidersReason() {
        server.respond(Response.json(400, "{\"message\":\"TF51005: The query references a field that does not exist.\"}"));

        assertThatThrownBy(() -> provider.queryWorkItems(config, "SELECT [Nope] FROM WorkItems"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("TF51005: The query references a field that does not exist.");
    }
}
