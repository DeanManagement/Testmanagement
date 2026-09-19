package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Files on a bug report (PRD-051). Resolves the bug within the project, by id or key and only while
 * bug reports are enabled there, then hands the storage to {@link AttachmentService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportAttachmentService {

    private final BugReportService bugReportService;
    private final AttachmentService attachmentService;

    public List<AttachmentSummary> list(UUID projectId, String bugIdOrKey) {
        bugReportService.requireBugReportsEnabled(projectId);
        return attachmentService.list(bugReportService.require(projectId, bugIdOrKey));
    }

    /** The attachment with its bytes, for download. */
    public Attachment get(UUID projectId, String bugIdOrKey, UUID id) {
        bugReportService.requireBugReportsEnabled(projectId);
        return attachmentService.get(bugReportService.require(projectId, bugIdOrKey), id);
    }

    @Transactional
    public AttachmentSummary upload(UUID projectId, String bugIdOrKey, String fileName, String declaredType,
                                    byte[] data, UUID userId) {
        bugReportService.requireBugReportsEnabled(projectId);
        return attachmentService.upload(projectId, bugReportService.require(projectId, bugIdOrKey), fileName,
                declaredType, data, userId);
    }

    @Transactional
    public void delete(UUID projectId, String bugIdOrKey, UUID id, UUID userId) {
        bugReportService.requireBugReportsEnabled(projectId);
        attachmentService.delete(projectId, bugReportService.require(projectId, bugIdOrKey), id, userId);
    }
}
