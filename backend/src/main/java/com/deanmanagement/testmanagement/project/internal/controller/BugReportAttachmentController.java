package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.BugReportAttachmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Files on a bug report (PRD-051), the twin of {@link AttachmentController}: any member reads, TESTER
 * writes, and downloads carry the {@link ImageResponses} headers. The bug is named by id or key.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/bug-reports/{bugId}/attachments")
@Tag(name = "Bug Report Attachments", description = "Screenshots and files on a bug report")
@RequiredArgsConstructor
public class BugReportAttachmentController {

    private final BugReportAttachmentService attachmentService;

    @GetMapping
    @RequireProjectRole
    public List<AttachmentSummary> list(@PathVariable UUID projectId, @PathVariable String bugId) {
        return attachmentService.list(projectId, bugId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public AttachmentSummary upload(@PathVariable UUID projectId, @PathVariable String bugId,
                                    @RequestParam MultipartFile file, Authentication authentication) throws IOException {
        return attachmentService.upload(projectId, bugId, file.getOriginalFilename(), file.getContentType(),
                file.getBytes(), actor(authentication));
    }

    @GetMapping("/{id}")
    @RequireProjectRole
    public ResponseEntity<byte[]> download(@PathVariable UUID projectId, @PathVariable String bugId, @PathVariable UUID id,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Attachment attachment = attachmentService.get(projectId, bugId, id);
        return ImageResponses.of(attachment.getUpdatedAt(), attachment.getContentType(), attachment.getFileName(),
                "attachment", attachment.getData(), ifNoneMatch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void delete(@PathVariable UUID projectId, @PathVariable String bugId, @PathVariable UUID id,
                       Authentication authentication) {
        attachmentService.delete(projectId, bugId, id, actor(authentication));
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
