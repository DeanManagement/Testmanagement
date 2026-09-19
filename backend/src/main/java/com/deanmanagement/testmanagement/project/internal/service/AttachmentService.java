package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.AttachmentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ScreenshotRepository;
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
import java.util.stream.Collectors;

/**
 * Files attached to a test case (PRD-044) or a bug report (PRD-051). Every lookup goes through the
 * project and the owner, so an attachment id from elsewhere is a 404. Limits bound what one owner and
 * one project can store, since every byte lives in the database and in every backup.
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

    private final AttachmentRepository attachmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final ScreenshotRepository screenshotRepository;
    private final AuditService auditService;
    private final int maxPerOwner;
    private final long maxProjectBytes;

    public AttachmentService(AttachmentRepository attachmentRepository, TestCaseRepository testCaseRepository,
                             ScreenshotRepository screenshotRepository, AuditService auditService,
                             @Value("${app.attachments.max-per-owner:20}") int maxPerOwner,
                             @Value("${app.attachments.max-project-bytes:524288000}") long maxProjectBytes) {
        this.attachmentRepository = attachmentRepository;
        this.testCaseRepository = testCaseRepository;
        this.screenshotRepository = screenshotRepository;
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

    /** The attachment with its bytes, for download. */
    public Attachment get(UUID projectId, UUID testCaseId, UUID id) {
        requireCase(projectId, testCaseId);
        return attachmentRepository.findByIdAndTestCaseId(id, testCaseId)
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
     * Copies a result's step screenshots onto a bug filed from it (PRD-051 §3.4). Copies, not
     * references: a step's screenshot is replaced when someone uploads another, and the evidence on a
     * bug must not change after it is filed. Stops at the per-owner limit or the project quota rather
     * than failing: the bug matters more than its screenshots.
     *
     * @return how many screenshots were not copied
     */
    @Transactional
    public int copyScreenshots(UUID projectId, BugReport bug, UUID testResultId, UUID userId) {
        List<Screenshot> screenshots = screenshotRepository.findByTestResultId(testResultId);
        int copied = 0;
        for (Screenshot screenshot : screenshots) {
            if (copied >= maxPerOwner) {
                break;
            }
            Attachment attachment = new Attachment();
            attachment.setBugReport(bug);
            try {
                store(projectId, attachment, "step-" + stepNumber(screenshot, copied) + "-" + screenshot.getFileName(),
                        screenshot.getContentType(), screenshot.getData(), userId);
                copied++;
            } catch (IllegalArgumentException e) {
                // A legacy screenshot outside today's allowlist: skip it, the others may still fit.
                log.warn("Screenshot {} not copied to bug {}: {}", screenshot.getId(), bug.getKey(), e.getMessage());
            } catch (ConflictException e) {
                log.warn("Screenshots of result {} not copied to bug {}: {}", testResultId, bug.getKey(), e.getMessage());
                break;
            }
        }
        int skipped = screenshots.size() - copied;
        if (skipped > 0) {
            log.warn("{} of {} screenshots of result {} not copied to bug {}", skipped, screenshots.size(),
                    testResultId, bug.getKey());
        }
        return skipped;
    }

    // ---- helpers --------------------------------------------------------------------------------

    private TestCase requireCase(UUID projectId, UUID testCaseId) {
        return testCaseRepository.findByIdAndProjectId(testCaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    }

    /** Checks and saves a file on the owner already set on {@code attachment}. */
    private AttachmentSummary store(UUID projectId, Attachment attachment, String originalFileName,
                                    String declaredType, byte[] data, UUID userId) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("The file is empty");
        }
        String fileName = sanitizeFileName(originalFileName);
        String contentType = AttachmentMediaTypes.verify(declaredType, fileName, data);
        if (maxProjectBytes > 0 && attachmentRepository.totalBytesInProject(projectId) + data.length > maxProjectBytes) {
            throw new ConflictException("The project's attachments would exceed " + maxProjectBytes / (1024 * 1024)
                    + " MB. Remove files you no longer need, or ask an administrator to raise the limit.");
        }
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(data.length);
        attachment.setSha256(sha256(data));
        attachment.setData(data);
        Attachment saved = attachmentRepository.save(attachment);
        audit(projectId, userId, AuditAction.CREATED, saved);
        return summaryOf(saved);
    }

    /** Steps are numbered from 1; a step without a recorded position takes its place in the list. */
    private static int stepNumber(Screenshot screenshot, int index) {
        Integer position = screenshot.getStepResult().getPosition();
        return (position == null ? index : position) + 1;
    }

    /**
     * "Which file was attached when" lives in the audit log; attachments are not versioned (§3.5).
     * Logged against the owner, so it shows in the case's or the bug's history.
     */
    private void audit(UUID projectId, UUID userId, AuditAction action, Attachment attachment) {
        TestCase testCase = attachment.getTestCase();
        BugReport bug = attachment.getBugReport();
        String ownerKey = testCase != null ? testCase.getKey() : bug.getKey();
        AuditParent parent = testCase != null
                ? new AuditParent(AuditEntityType.TEST_CASE, testCase.getId())
                : new AuditParent(AuditEntityType.BUG_REPORT, bug.getId());
        auditService.log(projectId, userId, action, AuditEntityType.ATTACHMENT, attachment.getId(),
                attachment.getFileName(), ownerKey + " · sha256 " + attachment.getSha256(), FieldChanges.none(), parent);
    }

    /**
     * The base name only, without control characters. The name is shown and used for downloads, so
     * a path or a line break in it must not survive; the extension is not trusted for anything.
     */
    static String sanitizeFileName(String original) {
        if (original == null) {
            return FALLBACK_FILE_NAME;
        }
        String base = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
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
        return new AttachmentSummary(a.getId(), testCaseId, bugReportId, a.getFileName(), a.getContentType(),
                a.getSizeBytes(), a.getSha256(), a.getCreatedAt(), a.getCreatedBy());
    }
}
