package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.CreateIssueLinkRequest;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueLinkResponse;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueSearchResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.IssueLink;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.issuetracker.Issue;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueDraft;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.repository.IssueLinkRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Search, link, create and unlink issues against a test result (PRD-010).
 *
 * <p>Every method resolves the result through its run's project, so a caller holding a role on one
 * project cannot reach a result — or a tracker config — belonging to another.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueLinkService {

    private static final int MAX_TITLE_LENGTH = 250;

    private final IssueLinkRepository issueLinkRepository;
    private final TestResultRepository testResultRepository;
    private final IssueTrackerConfigService configService;
    private final IssueTrackerProviderRegistry providerRegistry;
    private final AuditService auditService;

    public List<IssueSearchResponse> search(UUID projectId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        IssueTrackerConfig config = configService.requireActiveConfig(projectId);
        return configService.call(config, decrypted ->
                        providerRegistry.require(config.getProvider()).search(decrypted, query.trim()))
                .stream()
                .map(issue -> new IssueSearchResponse(issue.externalId(), issue.url(), issue.title(), issue.state()))
                .toList();
    }

    public List<IssueLinkResponse> findForResult(UUID projectId, UUID resultId) {
        requireResultInProject(projectId, resultId);
        return issueLinkRepository.findByTestResultIdOrderByCreatedAtAsc(resultId).stream()
                .map(IssueLinkService::toResponse)
                .toList();
    }

    /**
     * Links an existing issue, or files a new one and links that. Creation is the interesting path:
     * the body is templated from the result so a tester filing from a failure gets the test case
     * key, run key and actual results without retyping them.
     */
    @Transactional
    public IssueLinkResponse link(UUID projectId, UUID resultId, CreateIssueLinkRequest request, UUID actorId) {
        TestResult result = requireResultInProject(projectId, resultId);
        IssueTrackerConfig config = configService.requireActiveConfig(projectId);

        Issue issue;
        if (request.isCreate()) {
            IssueDraft draft = new IssueDraft(
                    isBlank(request.title()) ? defaultTitle(result) : request.title().trim(),
                    isBlank(request.body()) ? buildBody(result) : request.body());
            issue = configService.call(config, decrypted ->
                    providerRegistry.require(config.getProvider()).create(decrypted, draft));
        } else {
            if (isBlank(request.externalId())) {
                throw new IllegalArgumentException("Provide an issue reference, or set create to true");
            }
            String externalId = request.externalId().trim();
            issue = configService.call(config, decrypted ->
                    providerRegistry.require(config.getProvider()).get(decrypted, externalId));
        }

        // Re-linking the same issue refreshes the cached state instead of creating a duplicate,
        // which the unique constraint would reject anyway.
        IssueLink link = issueLinkRepository
                .findByTestResultIdAndExternalId(resultId, issue.externalId())
                .orElseGet(() -> {
                    IssueLink created = new IssueLink();
                    created.setTestResultId(resultId);
                    created.setExternalId(issue.externalId());
                    return created;
                });
        link.setProvider(config.getProvider());
        link.setUrl(issue.url());
        link.setTitle(truncate(issue.title(), 500));
        link.setState(issue.state());
        link.setStateCheckedAt(Instant.now());
        link = issueLinkRepository.save(link);

        auditService.log(projectId, actorId, AuditAction.UPDATED,
                AuditEntityType.TEST_RESULT, resultId, result.getTestCase().getKey(),
                (request.isCreate() ? "Created issue " : "Linked issue ") + issue.externalId());

        return toResponse(link);
    }

    @Transactional
    public void unlink(UUID projectId, UUID resultId, UUID linkId, UUID actorId) {
        TestResult result = requireResultInProject(projectId, resultId);

        IssueLink link = issueLinkRepository.findById(linkId)
                .filter(l -> l.getTestResultId().equals(resultId))
                .orElseThrow(() -> new ResourceNotFoundException("IssueLink", linkId));

        auditService.log(projectId, actorId, AuditAction.UPDATED,
                AuditEntityType.TEST_RESULT, resultId, result.getTestCase().getKey(),
                "Unlinked issue " + link.getExternalId());

        issueLinkRepository.delete(link);
    }

    /**
     * Re-reads state for one result's links on demand, so opening a result shows current state
     * without waiting for the poller. A tracker failure leaves the cached state in place.
     */
    @Transactional
    public List<IssueLinkResponse> refresh(UUID projectId, UUID resultId) {
        requireResultInProject(projectId, resultId);
        List<IssueLink> links = issueLinkRepository.findByTestResultIdOrderByCreatedAtAsc(resultId);
        if (links.isEmpty()) {
            return List.of();
        }
        configService.activeConfig(projectId).ifPresent(config -> {
            for (IssueLink link : links) {
                if (link.getProvider() != config.getProvider()) {
                    continue;
                }
                try {
                    Issue issue = configService.call(config, decrypted ->
                            providerRegistry.require(config.getProvider()).get(decrypted, link.getExternalId()));
                    link.setState(issue.state());
                    link.setTitle(truncate(issue.title(), 500));
                    link.setStateCheckedAt(Instant.now());
                    issueLinkRepository.save(link);
                } catch (RuntimeException e) {
                    // Showing a stale pill beats failing the whole result view.
                    return;
                }
            }
        });
        return links.stream().map(IssueLinkService::toResponse).toList();
    }

    // ---- Issue templating -------------------------------------------------

    private static String defaultTitle(TestResult result) {
        String caseKey = result.getTestCase().getKey();
        String title = result.getTestCase().getTitle();
        return truncate("[" + caseKey + "] " + title, MAX_TITLE_LENGTH);
    }

    /** Markdown body carrying the context a developer needs to reproduce, per PRD §2. */
    private static String buildBody(TestResult result) {
        StringBuilder body = new StringBuilder();
        body.append("**Test case:** ").append(result.getTestCase().getKey())
                .append(" — ").append(result.getTestCase().getTitle()).append("\n");
        body.append("**Test run:** ").append(result.getTestRun().getKey())
                .append(" — ").append(result.getTestRun().getName()).append("\n");
        body.append("**Result:** ").append(result.getStatus()).append("\n");

        String environment = result.getTestRun().getEnvironment();
        if (environment != null && !environment.isBlank()) {
            body.append("**Environment:** ").append(environment).append("\n");
        }
        if (result.getComment() != null && !result.getComment().isBlank()) {
            body.append("\n**Tester comment:**\n").append(result.getComment()).append("\n");
        }

        List<StepResult> failedSteps = result.getStepResults().stream()
                .filter(step -> step.getActualResult() != null && !step.getActualResult().isBlank())
                .toList();
        if (!failedSteps.isEmpty()) {
            body.append("\n**Actual results:**\n");
            for (StepResult step : failedSteps) {
                body.append("- ").append(step.getStatus()).append(": ")
                        .append(step.getActualResult().trim()).append("\n");
            }
        }
        return body.toString();
    }

    // ---- Helpers ----------------------------------------------------------

    private TestResult requireResultInProject(UUID projectId, UUID resultId) {
        TestResult result = testResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("TestResult", resultId));
        if (!result.getTestRun().getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("TestResult", resultId);
        }
        return result;
    }

    private static IssueLinkResponse toResponse(IssueLink link) {
        return new IssueLinkResponse(
                link.getId(),
                link.getTestResultId(),
                link.getProvider(),
                link.getExternalId(),
                link.getUrl(),
                link.getTitle(),
                link.getState(),
                link.getStateCheckedAt());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
