package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.StubHttpServer;
import com.deanmanagement.testmanagement.project.internal.StubHttpServer.Response;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerTokenCipher;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementRepository;
import com.deanmanagement.testmanagement.project.internal.service.ProjectService;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TM19: requirement sync from Azure DevOps, end to end against a stub of the work item API. Not
 * {@code @Transactional}: the sync commits in a transaction of its own (and a dry run rolls one
 * back), which a test transaction around it would hide.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class RequirementSyncApiTest {

    private static final String PARENT = "System.LinkTypes.Hierarchy-Reverse";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private IssueTrackerConfigRepository trackerRepository;
    @Autowired private IssueTrackerTokenCipher tokenCipher;
    @Autowired private RequirementRepository requirementRepository;

    private StubHttpServer azure;
    private Project project;
    private String admin;
    private String tester;

    @BeforeEach
    void setUp() throws Exception {
        azure = new StubHttpServer();
        project = new Project();
        project.setName("Sync");
        project.setKey("SY" + Integer.toHexString(new java.util.Random().nextInt(0xFFFF)).toUpperCase());
        project = projectRepository.save(project);
        admin = member(ProjectRole.ADMIN);
        tester = member(ProjectRole.TESTER);

        IssueTrackerConfig tracker = new IssueTrackerConfig();
        tracker.setProjectId(project.getId());
        tracker.setProvider(IssueTrackerProviderType.AZURE_DEVOPS);
        tracker.setBaseUrl(azure.baseUrl() + "/contoso");
        tracker.setProjectRef("Payments");
        tracker.setApiTokenEncrypted(tokenCipher.encrypt("pat"));
        trackerRepository.save(tracker);

        mockMvc.perform(put(url("")).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"query\":\"SELECT [System.Id] FROM WorkItems\","
                                + "\"externalIdPrefix\":\"AB#\",\"approvedStates\":\"Active\","
                                + "\"obsoleteStates\":\"Removed\"}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        azure.close();
        projectService.delete(project.getId(), null);
    }

    private String member(ProjectRole role) {
        User user = new User();
        user.setEmail("u-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName("u");
        user.setPasswordHash("x");
        user = userRepository.save(user);
        ProjectMember member = new ProjectMember();
        member.setUser(user);
        member.setProject(project);
        member.setRole(role);
        memberRepository.save(member);
        return user.getId().toString();
    }

    private String url(String suffix) {
        return "/api/projects/" + project.getId() + "/requirement-sync" + suffix;
    }

    private static String workItem(int id, String state, int priority, String description, Integer parent) {
        String relations = parent == null ? "[]"
                : "[{\"rel\":\"" + PARENT + "\",\"url\":\"https://dev.azure.com/contoso/_apis/wit/workItems/"
                + parent + "\"}]";
        return "{\"id\":" + id + ",\"fields\":{\"System.Title\":\"Item " + id + "\",\"System.State\":\"" + state
                + "\",\"Microsoft.VSTS.Common.Priority\":" + priority + ",\"System.TeamProject\":\"Payments\""
                + (description == null ? "" : ",\"System.Description\":\"" + description + "\"")
                + "},\"relations\":" + relations + "}";
    }

    /** The WIQL answer and one batch read with these work items. */
    private void azureReturns(String... items) {
        String ids = java.util.Arrays.stream(items)
                .map(item -> "{\"id\":" + item.substring(6, item.indexOf(',')) + "}")
                .collect(Collectors.joining(","));
        azure.respond(Response.json("{\"workItems\":[" + ids + "]}"),
                Response.json("{\"value\":[" + String.join(",", items) + "]}"));
    }

    private ResultActions run(boolean dryRun, String asUser) throws Exception {
        return mockMvc.perform(post(url("/run")).param("dryRun", String.valueOf(dryRun)).with(user(asUser)));
    }

    private Map<String, Requirement> requirements() {
        return requirementRepository.findByProjectIdOrderByExternalIdAsc(project.getId()).stream()
                .collect(Collectors.toMap(Requirement::getExternalId, r -> r));
    }

    @Test
    void aSyncCommitsTheWorkItemsWithMappedFieldsAndParents() throws Exception {
        azureReturns(workItem(1, "Active", 1, "<p>Pay <b>by card</b></p><ul><li>Visa</li></ul>", null),
                workItem(2, "New", 4, null, 1),
                workItem(3, "Active", 2, null, 99));

        run(false, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.dryRun").value(false));

        Map<String, Requirement> synced = requirements();
        Requirement epic = synced.get("AB#1");
        assertThat(epic.getTitle()).isEqualTo("Item 1");
        assertThat(epic.getDescription()).isEqualTo("Pay by card\n\n- Visa");
        assertThat(epic.getStatus()).isEqualTo(RequirementStatus.APPROVED);
        assertThat(epic.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(epic.getSource()).isEqualTo(IssueTrackerProviderType.AZURE_DEVOPS);
        assertThat(epic.getSourceUrl()).endsWith("/contoso/Payments/_workitems/edit/1");
        assertThat(epic.getLastSyncedAt()).isNotNull();
        assertThat(synced.get("AB#2").getStatus()).isEqualTo(RequirementStatus.DRAFT);
        assertThat(synced.get("AB#2").getPriority()).isEqualTo(Priority.LOW);
        assertThat(synced.get("AB#2").getParentId()).isEqualTo(epic.getId());
        assertThat(synced.get("AB#3").getParentId()).as("parent outside the query").isNull();
        assertThat(azure.requests().get(1).pathAndQuery())
                .isEqualTo("/contoso/Payments/_apis/wit/workitemsbatch?api-version=7.1");
    }

    @Test
    void aDryRunCountsWhatWouldChangeAndWritesNothing() throws Exception {
        azureReturns(workItem(1, "Active", 2, null, null), workItem(2, "Active", 2, null, null));
        run(false, admin).andExpect(status().isOk());
        azureReturns(workItem(1, "Removed", 2, null, null), workItem(3, "Active", 2, null, null));

        run(true, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.obsoleted").value(1))
                .andExpect(jsonPath("$.dryRun").value(true));

        assertThat(requirements()).containsOnlyKeys("AB#1", "AB#2");
        assertThat(requirements().get("AB#1").getStatus()).isEqualTo(RequirementStatus.APPROVED);
    }

    @Test
    void aWorkItemThatLeavesTheQueryIsMarkedObsoleteNotDeleted() throws Exception {
        azureReturns(workItem(1, "Active", 2, null, null), workItem(2, "Active", 2, null, null));
        run(false, admin).andExpect(status().isOk());
        azureReturns(workItem(1, "Active", 2, null, null));

        run(false, admin).andExpect(jsonPath("$.obsoleted").value(1)).andExpect(jsonPath("$.unchanged").value(1));

        assertThat(requirements().get("AB#2").getStatus()).isEqualTo(RequirementStatus.OBSOLETE);
    }

    @Test
    void syncedFieldsAreRefusedButAnUnchangedSaveIsAccepted() throws Exception {
        azureReturns(workItem(1, "Active", 2, "<p>Text</p>", null));
        run(false, admin).andExpect(status().isOk());
        UUID id = requirements().get("AB#1").getId();
        String path = "/api/projects/" + project.getId() + "/requirements/" + id;

        mockMvc.perform(put(path).with(user(tester)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"AB#1\",\"title\":\"Renamed\",\"description\":\"Text\","
                                + "\"status\":\"APPROVED\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("synced from Azure DevOps; change title there")));
        mockMvc.perform(put(path).with(user(tester)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"AB#1\",\"title\":\"Item 1\",\"description\":\"Text\","
                                + "\"status\":\"APPROVED\",\"priority\":\"HIGH\",\"customFields\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AZURE_DEVOPS"));
    }

    @Test
    void aTrackerFailureAnswers424WithTheProvidersReasonAndIsRecorded() throws Exception {
        azure.respond(Response.json(400, "{\"message\":\"TF51005: The query references a field that does not exist.\"}"));

        run(false, admin).andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.message").value(containsString("TF51005")));

        mockMvc.perform(get(url("/status")).with(user(tester)))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.lastError").value(containsString("TF51005")));
    }

    @Test
    void onlyAnAdminMaySync() throws Exception {
        run(true, tester).andExpect(status().isForbidden());
    }

    @Test
    void settingsNeedAnAzureDevOpsTrackerAndAWiqlQuery() throws Exception {
        mockMvc.perform(put(url("")).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"query\":\"DROP everything\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("WIQL SELECT")));
    }
}
