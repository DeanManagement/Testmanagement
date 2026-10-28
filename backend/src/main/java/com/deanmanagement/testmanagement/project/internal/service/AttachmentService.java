package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.AttachmentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Files attached to a test case (PRD-044) or a bug report (PRD-051). Every lookup goes through the
 * project and the owner, so an attachment id from elsewhere is a 404. Limits bound what one owner and
 * one project can store, since every byte lives in the database and in every backup. Files on test
 * steps and step results (TM05) go through {@link StepAttachmentService}, which stores through here.
 *
 * <p>Bug report methods take the bug already resolved: {@code BugReportAttachmentService} does that
 * within the project, and checks that bug reports are enabled there.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AttachmentService {

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final String FALLBACK_FILE_NAME = "attachment";
    private static final long BYTES_PER_MB = 1024L * 1024;

    private final AttachmentRepository attachmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final AuditService auditService;
    private final int maxPerOwner;
    private final long maxProjectBytes;

    public AttachmentService(AttachmentRepository attachmentRepository, TestCaseRepository testCaseRepository,
                             AuditService auditService,
                             @Value("${app.attachments.max-per-owner:20}") int maxPerOwner,
                             @Value("${app.attachments.max-project-bytes:524288000}") long maxProjectBytes) {
        this.attachmentRepository = attachmentRepository;
        this.testCaseRepository = testCaseRepository;
        this.auditService = auditService;
        this.maxPerOwner = maxPerOwner;
        this.maxProjectBytes = maxProjectBytes;
    }

    public List<AttachmentSummary> list(UUID projectId, UUID testCaseId) {
        requireCase(projectId, testCaseId);
        return attachmentRepository.summariesByTestCase(testCaseId);
    }

    /**
     * Each case's attachments, for the ids given; one query however many cases. Callers pass ids they
     * already resolved within a project.
     */
    public Map<UUID, List<AttachmentSummary>> summariesByTestCase(Collection<UUID> testCaseIds) {
        if (testCaseIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.summariesByTestCases(testCaseIds).stream()
                .collect(Collectors.groupingBy(AttachmentSummary::testCaseId));
    }

    /**
     * The attachment with its bytes, for download: one on the case, or on a step the case executes
     * (TM05), so a client that knows the case reaches every file it shows.
     */
    public Attachment get(UUID projectId, UUID testCaseId, UUID id) {
        requireCase(projectId, testCaseId);
        return attachmentRepository.findOnCaseOrItsSteps(id, testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
    }

    @Transactional
    public AttachmentSummary upload(UUID projectId, UUID testCaseId, String originalFileName, String declaredType,
                                    byte[] data, UUID userId) {
        TestCase testCase = requireCase(projectId, testCaseId);
        if (attachmentRepository.countByTestCaseId(testCaseId) >= maxPerOwner) {
            throw new ConflictException("A test case can have at most " + maxPerOwner + " attachments");
        }
        Attachment attachment = new Attachment();
        attachment.setTestCase(testCase);
        return store(projectId, attachment, originalFileName, declaredType, data, userId);
    }

    @Transactional
    public void delete(UUID projectId, UUID testCaseId, UUID id, UUID userId) {
        Attachment attachment = get(projectId, testCaseId, id);
        attachmentRepository.delete(attachment);
        audit(projectId, userId, AuditAction.DELETED, attachment);
    }

    /**
     * Copies every file of {@code source} onto {@code target}, for a copied test case (TM09). The
     * files were checked when uploaded, so only the project quota is checked again; a copy that
     * would exceed it fails as a whole rather than leaving the new case with some of its files.
     */
    @Transactional
    public void copyTestCaseAttachments(UUID projectId, TestCase source, TestCase target, UUID userId) {
        copyAll(projectId, attachmentRepository.findByTestCaseId(source.getId()), (original, copy) -> copy.setTestCase(target),
                userId);
    }

    // ---- bug reports (PRD-051) ------------------------------------------------------------------

    public List<AttachmentSummary> list(BugReport bug) {
        return attachmentRepository.summariesByBugReport(bug.getId());
    }

    public Attachment get(BugReport bug, UUID id) {
        return attachmentRepository.findByIdAndBugReportId(id, bug.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
    }

    @Transactional
    public AttachmentSummary upload(UUID projectId, BugReport bug, String originalFileName, String declaredType,
                                    byte[] data, UUID userId) {
        if (attachmentRepository.countByBugReportId(bug.getId()) >= maxPerOwner) {
            throw new ConflictException("A bug report can have at most " + maxPerOwner + " attachments");
        }
        Attachment attachment = new Attachment();
        attachment.setBugReport(bug);
        return store(projectId, attachment, originalFileName, declaredType, data, userId);
    }

    @Transactional
    public void delete(UUID projectId, BugReport bug, UUID id, UUID userId) {
        Attachment attachment = get(bug, id);
        attachmentRepository.delete(attachment);
        audit(projectId, userId, AuditAction.DELETED, attachment);
    }

    /**
     * Copies a result's step evidence onto a bug filed from it (PRD-051 §3.4, TM05). Copies, not
     * references: evidence can be deleted from the run later, and the evidence on a bug must not
     * change after it is filed. Stops at the per-owner limit or the project quota rather than
     * failing: the bug matters more than its files.
     *
     * @return how many files were not copied
     */
    @Transactional
    public int copyEvidence(UUID projectId, BugReport bug, UUID testResultId, UUID userId) {
        List<Attachment> evidence = attachmentRepository.findEvidenceOfResult(testResultId);
        int copied = 0;
        for (Attachment file : evidence) {
            if (copied >= maxPerOwner) {
                break;
            }
            Attachment attachment = new Attachment();
            attachment.setBugReport(bug);
            try {
                store(projectId, attachment, "step-" + stepNumber(file, copied) + "-" + file.getFileName(),
                        file.getContentType(), file.getData(), userId);
                copied++;
            } catch (IllegalArgumentException e) {
                // A legacy screenshot outside today's allowlist: skip it, the others may still fit.
                log.warn("Evidence {} not copied to bug {}: {}", file.getId(), bug.getKey(), e.getMessage());
            } catch (ConflictException e) {
                log.warn("Evidence of result {} not copied to bug {}: {}", testResultId, bug.getKey(), e.getMessage());
                break;
            }
        }
        int skipped = evidence.size() - copied;
        if (skipped > 0) {
            log.warn("{} of {} evidence files of result {} not copied to bug {}", skipped, evidence.size(),
                    testResultId, bug.getKey());
        }
        return skipped;
    }

    // ---- shared with StepAttachmentService ------------------------------------------------------

    /** At most this many files per case, bug, step or step result. */
    int maxPerOwner() {
        return maxPerOwner;
    }

    /** Checks and saves a file on the owner already set on {@code attachment}. */
    AttachmentSummary store(UUID projectId, Attachment attachment, String originalFileName,
                            String declaredType, byte[] data, UUID userId) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("The file is empty");
        }
        String fileName = sanitizeFileName(originalFileName);
        String contentType = AttachmentMediaTypes.verify(declaredType, fileName, data);
        requireRoomFor(projectId, data.length, "The project's attachments would exceed "
                + maxProjectBytes / BYTES_PER_MB
                + " MB. Remove files you no longer need, or ask an administrator to raise the limit.");
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(data.length);
        attachment.setSha256(sha256(data));
        attachment.setData(data);
        Attachment saved = attachmentRepository.save(attachment);
        audit(projectId, userId, AuditAction.CREATED, saved);
        return summaryOf(saved);
    }

    /**
     * Copies already-checked files onto new owners, all or none: a copy that would exceed the quota
     * fails before anything is written. {@code owner} sets the new owner on each (original, copy).
     */
    void copyAll(UUID projectId, List<Attachment> originals, BiConsumer<Attachment, Attachment> owner, UUID userId) {
        if (originals.isEmpty()) {
            return;
        }
        requireRoomFor(projectId, originals.stream().mapToLong(Attachment::getSizeBytes).sum(),
                "Copying the attachments would exceed the project's " + maxProjectBytes / BYTES_PER_MB
                        + " MB. Remove files you no longer need first.");
        for (Attachment original : originals) {
            Attachment copy = new Attachment();
            owner.accept(original, copy);
            copy.setFileName(original.getFileName());
            copy.setContentType(original.getContentType());
            copy.setSizeBytes(original.getSizeBytes());
            copy.setSha256(original.getSha256());
            copy.setData(original.getData());
            audit(projectId, userId, AuditAction.CREATED, attachmentRepository.save(copy));
        }
    }

    /**
     * "Which file was attached when" lives in the audit log; attachments are not versioned (§3.5).
     * Logged against the owner, so it shows in the case's or the bug's history; a step's file in its
     * case's (or shared step's) history. A result's evidence is not logged, as screenshots never
     * were: the row's created_by says who recorded it.
     */
    void audit(UUID projectId, UUID userId, AuditAction action, Attachment attachment) {
        AuditParent parent = auditParentOf(attachment);
        if (parent == null) {
            return;
        }
        auditService.log(projectId, userId, action, AuditEntityType.ATTACHMENT, attachment.getId(),
                attachment.getFileName(), ownerKeyOf(attachment) + " · sha256 " + attachment.getSha256(),
                FieldChanges.none(), parent);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private TestCase requireCase(UUID projectId, UUID testCaseId) {
        return testCaseRepository.findByIdAndProjectId(testCaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    }

    private void requireRoomFor(UUID projectId, long addedBytes, String refusal) {
        if (maxProjectBytes > 0 && attachmentRepository.totalBytesInProject(projectId) + addedBytes > maxProjectBytes) {
            throw new ConflictException(refusal);
        }
    }

    /** Steps are numbered from 1; a step without a recorded position takes its place in the list. */
    private static int stepNumber(Attachment evidence, int index) {
        Integer position = evidence.getStepResult().getPosition();
        return (position == null ? index : position) + 1;
    }

    private static AuditParent auditParentOf(Attachment attachment) {
        TestStep step = attachment.getTestStep();
        if (attachment.getTestCase() != null) {
            return new AuditParent(AuditEntityType.TEST_CASE, attachment.getTestCase().getId());
        }
        if (attachment.getBugReport() != null) {
            return new AuditParent(AuditEntityType.BUG_REPORT, attachment.getBugReport().getId());
        }
        if (step != null && step.getTestCase() != null) {
            return new AuditParent(AuditEntityType.TEST_CASE, step.getTestCase().getId());
        }
        if (step != null) {
            return new AuditParent(AuditEntityType.SHARED_STEP, step.getSharedStep().getId());
        }
        return null;
    }

    private static String ownerKeyOf(Attachment attachment) {
        if (attachment.getTestCase() != null) {
            return attachment.getTestCase().getKey();
        }
        if (attachment.getBugReport() != null) {
            return attachment.getBugReport().getKey();
        }
        TestStep step = attachment.getTestStep();
        String owner = step.getTestCase() != null ? step.getTestCase().getKey() : step.getSharedStep().getTitle();
        return owner + " step " + (step.getOrderIndex() + 1);
    }

    /**
     * The base name only, without control characters. The name is shown and used for downloads, so
     * a path or a line break in it must not survive; the extension is not trusted for anything.
     * Browsers send {@code "}, CR and LF in a multipart file name as {@code %22}, {@code %0D} and
     * {@code %0A} (HTML's form encoding) and nothing else escaped, so only those are decoded
     * (TES-BUG-22). A name that really contains {@code %22} arrives as that same text and is decoded too.
     */
    static String sanitizeFileName(String original) {
        if (original == null) {
            return FALLBACK_FILE_NAME;
        }
        String decoded = original.replace("%22", "\"").replace("%0D", "\r").replace("%0A", "\n");
        String base = decoded.substring(Math.max(decoded.lastIndexOf('/'), decoded.lastIndexOf('\\')) + 1);
        String clean = base.replaceAll("\\p{Cntrl}", "").strip();
        if (clean.isEmpty() || clean.equals(".") || clean.equals("..")) {
            return FALLBACK_FILE_NAME;
        }
        return clean.length() > MAX_FILE_NAME_LENGTH ? clean.substring(0, MAX_FILE_NAME_LENGTH) : clean;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM must provide SHA-256; if one does not, nothing here can work.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static AttachmentSummary summaryOf(Attachment a) {
        UUID testCaseId = a.getTestCase() == null ? null : a.getTestCase().getId();
        UUID bugReportId = a.getBugReport() == null ? null : a.getBugReport().getId();
        UUID testStepId = a.getTestStep() == null ? null : a.getTestStep().getId();
        UUID stepResultId = a.getStepResult() == null ? null : a.getStepResult().getId();
        return new AttachmentSummary(a.getId(), testCaseId, bugReportId, testStepId, stepResultId, a.getFileName(),
                a.getContentType(), a.getSizeBytes(), a.getSha256(), a.getCreatedAt(), a.getCreatedBy());
    }
}
