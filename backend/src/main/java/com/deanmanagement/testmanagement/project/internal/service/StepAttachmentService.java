package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.StepFileRef;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.AttachmentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.StepResultRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestStepRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Documents on a test step and evidence on a step result (TM05, PRD-053 §1). Both are attachments
 * with the same checks, limits and quota as a case's files ({@link AttachmentService}). A step of a
 * shared step belongs to the shared step, so its files show in every case that uses it.
 *
 * <p>Every lookup goes through the project: a step is in a project through its case or its shared
 * step, a step result through its run.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StepAttachmentService {

    private final AttachmentService attachmentService;
    private final AttachmentRepository attachmentRepository;
    private final TestStepRepository testStepRepository;
    private final StepResultRepository stepResultRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;

    // ---- a step's documents ---------------------------------------------------------------------

    public List<AttachmentSummary> listOnStep(UUID projectId, UUID testStepId) {
        requireStep(projectId, testStepId);
        return attachmentRepository.summariesByTestStep(testStepId);
    }

    /** Files on every step the case executes, its shared steps' included; one query. */
    public List<AttachmentSummary> listOnStepsOfCase(UUID projectId, UUID testCaseId) {
        testCaseRepository.findByIdAndProjectId(testCaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
        return attachmentRepository.summariesOnStepsOfCase(testCaseId);
    }

    /** Files per step, for steps already resolved within a project (the spec export). */
    public Map<UUID, List<AttachmentSummary>> summariesByTestStep(Collection<UUID> testStepIds) {
        if (testStepIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.summariesByTestSteps(testStepIds).stream()
                .collect(Collectors.groupingBy(AttachmentSummary::testStepId));
    }

    public Attachment getOnStep(UUID projectId, UUID testStepId, UUID id) {
        requireStep(projectId, testStepId);
        return attachmentRepository.findByIdAndTestStepId(id, testStepId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
    }

    @Transactional
    public AttachmentSummary uploadToStep(UUID projectId, UUID testStepId, String fileName, String declaredType,
                                          byte[] data, UUID userId) {
        TestStep step = requireStep(projectId, testStepId);
        if (step.getUsesSharedStep() != null) {
            throw new IllegalArgumentException("A shared step reference has no files of its own; attach them to "
                    + "the shared step's steps");
        }
        if (attachmentRepository.countByTestStepId(testStepId) >= attachmentService.maxPerOwner()) {
            throw new ConflictException("A test step can have at most " + attachmentService.maxPerOwner()
                    + " attachments");
        }
        Attachment attachment = new Attachment();
        attachment.setTestStep(step);
        return attachmentService.store(projectId, attachment, fileName, declaredType, data, userId);
    }

    @Transactional
    public void deleteFromStep(UUID projectId, UUID testStepId, UUID id, UUID userId) {
        Attachment attachment = getOnStep(projectId, testStepId, id);
        attachmentRepository.delete(attachment);
        attachmentService.audit(projectId, userId, AuditAction.DELETED, attachment);
    }

    /**
     * Copies each source step's files onto its copy (TM09, and a shared step converted to local
     * steps), all or none. The copies must be saved already.
     */
    @Transactional
    public void copyStepFiles(UUID projectId, Map<TestStep, TestStep> copies, UUID userId) {
        Map<UUID, TestStep> bySourceId = copies.entrySet().stream()
                .filter(e -> e.getKey().getId() != null)
                .collect(Collectors.toMap(e -> e.getKey().getId(), Map.Entry::getValue));
        if (bySourceId.isEmpty()) {
            return;
        }
        attachmentService.copyAll(projectId, attachmentRepository.findByTestStepIdIn(bySourceId.keySet()),
                (original, copy) -> copy.setTestStep(bySourceId.get(original.getTestStep().getId())), userId);
    }

    /**
     * First half of replacing a case's steps (TM06): the files of the steps about to be replaced are
     * parked on the case, so deleting the old step rows does not take them along. Call before the
     * steps change; hand the result to {@link #unparkStepFiles} once the new steps are flushed.
     *
     * @return the parked file ids per old step id; empty when the steps have no files
     */
    @Transactional
    public Map<UUID, List<UUID>> parkStepFiles(TestCase testCase, Collection<UUID> oldStepIds) {
        if (oldStepIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> parked = attachmentRepository.refsOnSteps(oldStepIds).stream()
                .collect(Collectors.groupingBy(StepFileRef::testStepId,
                        Collectors.mapping(StepFileRef::id, Collectors.toList())));
        if (!parked.isEmpty()) {
            attachmentRepository.parkOnCase(parked.values().stream().flatMap(List::stream).toList(), testCase);
        }
        return parked;
    }

    /**
     * Second half: each old step's files go to its successor; a step without one takes its files
     * with it, as deleting the step would have.
     */
    @Transactional
    public void unparkStepFiles(Map<UUID, List<UUID>> parked, Map<UUID, TestStep> successors) {
        parked.forEach((oldStepId, fileIds) -> {
            TestStep successor = successors.get(oldStepId);
            if (successor != null) {
                attachmentRepository.moveToStep(fileIds, successor);
            } else {
                attachmentRepository.deleteByIds(fileIds);
            }
        });
    }

    // ---- a step result's evidence ---------------------------------------------------------------

    public List<AttachmentSummary> listEvidence(UUID projectId, UUID stepResultId) {
        requireStepResult(projectId, stepResultId);
        return attachmentRepository.summariesByStepResult(stepResultId);
    }

    /** A run's evidence and the documents of every step it executes, for the run view and report. */
    public List<AttachmentSummary> listForRun(UUID projectId, UUID testRunId) {
        testRunRepository.findByIdAndProjectId(testRunId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunId));
        return attachmentRepository.summariesForRun(testRunId);
    }

    public Attachment getEvidence(UUID projectId, UUID stepResultId, UUID id) {
        requireStepResult(projectId, stepResultId);
        return attachmentRepository.findByIdAndStepResultId(id, stepResultId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
    }

    /** Any id the run's evidence lists, with its bytes; for the run report (PRD-048). */
    public Optional<Attachment> findById(UUID id) {
        return attachmentRepository.findById(id);
    }

    @Transactional
    public AttachmentSummary uploadEvidence(UUID projectId, UUID stepResultId, String fileName, String declaredType,
                                            byte[] data, UUID userId) {
        StepResult stepResult = requireStepResult(projectId, stepResultId);
        if (attachmentRepository.countByStepResultId(stepResultId) >= attachmentService.maxPerOwner()) {
            throw new ConflictException("A step result can have at most " + attachmentService.maxPerOwner()
                    + " evidence files");
        }
        Attachment attachment = new Attachment();
        attachment.setStepResult(stepResult);
        return attachmentService.store(projectId, attachment, fileName, declaredType, data, userId);
    }

    @Transactional
    public void deleteEvidence(UUID projectId, UUID stepResultId, UUID id) {
        attachmentRepository.delete(getEvidence(projectId, stepResultId, id));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private TestStep requireStep(UUID projectId, UUID testStepId) {
        return testStepRepository.findById(testStepId)
                .filter(step -> projectId.equals(projectIdOf(step)))
                .orElseThrow(() -> new ResourceNotFoundException("TestStep", testStepId));
    }

    private StepResult requireStepResult(UUID projectId, UUID stepResultId) {
        return stepResultRepository.findById(stepResultId)
                .filter(sr -> projectId.equals(sr.getTestResult().getTestRun().getProject().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("StepResult", stepResultId));
    }

    /** Through the case, or through the shared step for a shared step's own step (PRD-030). */
    private static UUID projectIdOf(TestStep step) {
        return step.getTestCase() != null
                ? step.getTestCase().getProject().getId()
                : step.getSharedStep().getProject().getId();
    }
}
