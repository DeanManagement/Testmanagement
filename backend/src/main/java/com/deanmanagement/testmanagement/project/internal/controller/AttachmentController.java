package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.AttachmentService;
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
 * Files attached to a test case (PRD-044). Nested under the project, so {@code @RequireProjectRole}
 * authorizes every call; any member reads, TESTER writes.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-cases/{testCaseId}/attachments")
@Tag(name = "Test Case Attachments", description = "Files attached to a test case")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    @RequireProjectRole
    public List<AttachmentSummary> list(@PathVariable UUID projectId, @PathVariable UUID testCaseId) {
        return attachmentService.list(projectId, testCaseId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public AttachmentSummary upload(@PathVariable UUID projectId, @PathVariable UUID testCaseId,
                                    @RequestParam MultipartFile file, Authentication authentication) throws IOException {
        return attachmentService.upload(projectId, testCaseId, file.getOriginalFilename(), file.getContentType(),
                file.getBytes(), actor(authentication));
    }

    /**
     * Stored-media headers from {@link ImageResponses}: private caching, nosniff, a sandbox CSP, and
     * inline only for allowlisted images; every other attachment is a download.
     */
    @GetMapping("/{id}")
    @RequireProjectRole
    public ResponseEntity<byte[]> download(@PathVariable UUID projectId, @PathVariable UUID testCaseId,
                                           @PathVariable UUID id,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Attachment attachment = attachmentService.get(projectId, testCaseId, id);
        return ImageResponses.of(attachment.getUpdatedAt(), attachment.getContentType(), attachment.getFileName(),
                "attachment", attachment.getData(), ifNoneMatch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID testCaseId, @PathVariable UUID id,
                       Authentication authentication) {
        attachmentService.delete(projectId, testCaseId, id, actor(authentication));
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
