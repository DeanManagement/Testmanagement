package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkDeleteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.repository.spec.TestCaseSpecifications;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseFolderRepository folderRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseMapper testCaseMapper;
    private final AuditService auditService;
    private final TestCaseVersionService versionService;
    private final TestResultRepository testResultRepository;
    private final ProjectSequenceService projectSequenceService;
    private final TestCaseReviewService reviewService;
    private final CustomFieldValueWriter customFieldWriter;

    public Page<TestCaseResponse> findByProject(UUID projectId, TestCaseListFilter filter, Pageable pageable) {
        Set<UUID> folderIds = null;
        if (filter.folderId() != null) {
            folderIds = filter.includeSubfolders()
                    ? folderRepository.findSubtreeIds(projectId, filter.folderId())
                    : Set.of(filter.folderId());
        }
        return testCaseRepository.findAll(TestCaseSpecifications.build(projectId, filter, folderIds), pageable)
                .map(testCaseMapper::toResponse);
    }

    public TestCaseResponse findById(UUID projectId, UUID id) {
        TestCase tc = testCaseRepository.findByIdWithSteps(id)
                .filter(t -> t.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));
        return testCaseMapper.toResponse(tc);
    }

    @Transactional
    public TestCaseResponse create(UUID projectId, CreateTestCaseRequest request, UUID userId) {
        return create(projectId, request, userId, CustomFieldWriteMode.INTERACTIVE);
    }

    /** {@code mode} decides whether required custom fields must be filled (PRD-035 §3.3). */
    @Transactional
    public TestCaseResponse create(UUID projectId, CreateTestCaseRequest request, UUID userId,
                                   CustomFieldWriteMode mode) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestCase tc = testCaseMapper.toEntity(request);
        tc.setProject(project);
        reviewService.checkStatusWrite(project, null, tc.getStatus(), userId);
        tc.setLabels(request.labels() != null ? request.labels() : new HashSet<>());
        tc.setSteps(buildSteps(request.steps(), tc));
        customFieldWriter.write(tc, request.customFields(), mode);

        if (request.folderId() != null) {
            TestCaseFolder folder = folderRepository.findById(request.folderId())
                    .filter(f -> f.getProject().getId().equals(projectId))
                    .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", request.folderId()));
            tc.setFolder(folder);
        }

        int number = projectSequenceService.nextTestCaseNumber(projectId);
        tc.setKey(project.getKey() + "-" + number);

        tc = testCaseRepository.save(tc);
        auditService.log(projectId, userId, AuditAction.CREATED,
                AuditEntityType.TEST_CASE, tc.getId(), tc.getTitle(), null);
        return testCaseMapper.toResponse(tc);
    }

    @Transactional
    public TestCaseResponse update(UUID projectId, UUID id, UpdateTestCaseRequest request, UUID userId) {
        TestCase tc = testCaseRepository.findById(id)
                .filter(t -> t.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));

        TestCaseStatus statusBefore = tc.getStatus();
        int versionBefore = tc.getCurrentVersion();
        boolean contentChanged = TestCaseReviewService.isContentEdit(tc, request);
        reviewService.checkStatusWrite(tc.getProject(), statusBefore, request.status(), userId);

        // Snapshot before mutating, in this transaction: "version N" must name the wording that
        // results stamped N actually executed (PRD-011).
        versionService.snapshotBeforeEdit(tc);

        // Null-guarded like every other field: absent means "leave alone", and a caller clearing a
        // text field sends "". Previously these three were assigned unconditionally, which made
        // partial updates impossible — a caller had to read the case and send its current values
        // back, and any concurrent human edit in between was silently reverted (PRD-025 §3.4).
        if (request.title() != null) {
            if (request.title().isBlank()) {
                // @NotBlank moved off the DTO so that null could mean "unchanged"; a title that is
                // present but empty is still not a title.
                throw new IllegalArgumentException("title must not be blank");
            }
            tc.setTitle(request.title());
        }
        if (request.description() != null) tc.setDescription(request.description());
        if (request.preconditions() != null) tc.setPreconditions(request.preconditions());
        if (request.priority() != null) tc.setPriority(request.priority());
        if (request.status() != null) tc.setStatus(request.status());
        if (request.labels() != null) tc.setLabels(request.labels());
        customFieldWriter.write(tc, request.customFields(), CustomFieldWriteMode.INTERACTIVE);
        if (request.steps() != null) {
            Map<Integer, StepImage> existingImages = new HashMap<>();
            for (TestStep oldStep : tc.getSteps()) {
                if (oldStep.getImage() != null) {
                    StepImage img = oldStep.getImage();
                    oldStep.setImage(null);
                    img.setTestStep(null);
                    existingImages.put(oldStep.getOrderIndex(), img);
                }
            }
            tc.getSteps().clear();
            List<TestStep> newSteps = buildSteps(request.steps(), tc);
            for (TestStep newStep : newSteps) {
                StepImage img = existingImages.remove(newStep.getOrderIndex());
                if (img != null) {
                    img.setTestStep(newStep);
                    newStep.setImage(img);
                }
            }
            tc.getSteps().addAll(newSteps);
        }
        reviewService.afterEdit(tc, statusBefore, versionBefore, contentChanged);

        tc = testCaseRepository.save(tc);
        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_CASE, tc.getId(), tc.getTitle(), null);
        return testCaseMapper.toResponse(tc);
    }

    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        TestCase tc = testCaseRepository.findById(id)
                .filter(t -> t.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));
        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.TEST_CASE, tc.getId(), tc.getTitle(), null);
        testCaseRepository.delete(tc);
    }

    @Transactional
    public BulkOperationResponse bulkUpdateStatus(UUID projectId, BulkStatusRequest request, UUID userId) {
        // Scoped in the query rather than loaded across every project and filtered afterwards.
        // The old form was safe — the size check below rejected foreign ids before anything was
        // read off them — but it made the guard a property of this method rather than of the
        // lookup, which is how the same shape became a real hole three times (PRD-027 §3.5).
        List<TestCase> projectTestCases =
                testCaseRepository.findByIdInAndProjectId(request.testCaseIds(), projectId);

        if (projectTestCases.size() != request.testCaseIds().size()) {
            throw new IllegalArgumentException("Some test case IDs do not belong to this project");
        }

        // Every case is checked before any changes, so a refused one leaves the batch untouched.
        for (TestCase tc : projectTestCases) {
            reviewService.checkStatusWrite(tc.getProject(), tc.getStatus(), request.status(), userId);
        }
        for (TestCase tc : projectTestCases) {
            tc.setStatus(request.status());
        }
        testCaseRepository.saveAll(projectTestCases);

        auditService.log(projectId, userId, AuditAction.STATUS_CHANGED,
                AuditEntityType.TEST_CASE, null, null,
                "Bulk status change to " + request.status() + " for " + projectTestCases.size() + " test cases");

        return new BulkOperationResponse(projectTestCases.size(),
                "Status updated for " + projectTestCases.size() + " test cases");
    }

    @Transactional
    public BulkOperationResponse bulkDelete(UUID projectId, BulkDeleteRequest request, UUID userId) {
        // Scoped in the query — see bulkUpdateStatus.
        List<TestCase> projectTestCases =
                testCaseRepository.findByIdInAndProjectId(request.testCaseIds(), projectId);

        if (projectTestCases.size() != request.testCaseIds().size()) {
            throw new IllegalArgumentException("Some test case IDs do not belong to this project");
        }

        if (testResultRepository.existsByTestCaseIdIn(request.testCaseIds())) {
            throw new IllegalArgumentException("Cannot delete test cases that have test results. Remove the test results first.");
        }

        testCaseRepository.deleteAll(projectTestCases);

        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.TEST_CASE, null, null,
                "Bulk deleted " + projectTestCases.size() + " test cases");

        return new BulkOperationResponse(projectTestCases.size(),
                projectTestCases.size() + " test cases deleted");
    }

    private List<TestStep> buildSteps(List<TestStepRequest> stepRequests, TestCase testCase) {
        if (stepRequests == null) return new ArrayList<>();
        List<TestStep> steps = new ArrayList<>();
        for (int i = 0; i < stepRequests.size(); i++) {
            TestStepRequest sr = stepRequests.get(i);
            TestStep step = new TestStep();
            step.setAction(sr.action());
            step.setExpectedResult(sr.expectedResult());
            step.setTestData(sr.testData());
            step.setOrderIndex(i);
            step.setTestCase(testCase);
            steps.add(step);
        }
        return steps;
    }
}
