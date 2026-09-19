package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.AttachmentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
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
 * Files attached to a test case (PRD-044). Every lookup goes through the project and the case, so an
 * attachment id from elsewhere is a 404. Limits bound what one case and one project can store, since
 * every byte lives in the database and in every backup.
 */
@Service
@Transactional(readOnly = true)
public class AttachmentService {

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final String FALLBACK_FILE_NAME = "attachment";

    private final AttachmentRepository attachmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final AuditService auditService;
    private final int maxPerCase;
    private final long maxProjectBytes;

    public AttachmentService(AttachmentRepository attachmentRepository, TestCaseRepository testCaseRepository,
                             AuditService auditService,
                             @Value("${app.attachments.max-per-case:20}") int maxPerCase,
                             @Value("${app.attachments.max-project-bytes:524288000}") long maxProjectBytes) {
        this.attachmentRepository = attachmentRepository;
        this.testCaseRepository = testCaseRepository;
        this.auditService = auditService;
        this.maxPerCase = maxPerCase;
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
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("The file is empty");
        }
        String fileName = sanitizeFileName(originalFileName);
        String contentType = AttachmentMediaTypes.verify(declaredType, fileName, data);
        if (attachmentRepository.countByTestCaseId(testCaseId) >= maxPerCase) {
            throw new ConflictException("A test case can have at most " + maxPerCase + " attachments");
        }
        if (maxProjectBytes > 0 && attachmentRepository.totalBytesInProject(projectId) + data.length > maxProjectBytes) {
            throw new ConflictException("The project's attachments would exceed " + maxProjectBytes / (1024 * 1024)
                    + " MB. Remove files you no longer need, or ask an administrator to raise the limit.");
        }

        Attachment attachment = new Attachment();
        attachment.setTestCase(testCase);
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(data.length);
        attachment.setSha256(sha256(data));
        attachment.setData(data);
        attachment = attachmentRepository.save(attachment);
        audit(projectId, userId, AuditAction.CREATED, attachment, testCase);
        return summaryOf(attachment);
    }

    @Transactional
    public void delete(UUID projectId, UUID testCaseId, UUID id, UUID userId) {
        Attachment attachment = get(projectId, testCaseId, id);
        attachmentRepository.delete(attachment);
        audit(projectId, userId, AuditAction.DELETED, attachment, attachment.getTestCase());
    }

    // ---- helpers --------------------------------------------------------------------------------

    private TestCase requireCase(UUID projectId, UUID testCaseId) {
        return testCaseRepository.findByIdAndProjectId(testCaseId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    }

    /** "Which file was attached when" lives in the audit log; attachments are not versioned (§3.5). */
    private void audit(UUID projectId, UUID userId, AuditAction action, Attachment attachment, TestCase testCase) {
        auditService.log(projectId, userId, action, AuditEntityType.ATTACHMENT, attachment.getId(),
                attachment.getFileName(), testCase.getKey() + " · sha256 " + attachment.getSha256(),
                FieldChanges.none(), new AuditParent(AuditEntityType.TEST_CASE, testCase.getId()));
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
        return new AttachmentSummary(a.getId(), a.getTestCase().getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.getSha256(),
                a.getCreatedAt(), a.getCreatedBy());
    }
}
