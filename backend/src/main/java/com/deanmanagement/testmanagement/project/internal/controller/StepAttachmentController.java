package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.StepAttachmentService;
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
 * Documents on a test step and evidence on a step result (TM05). Same shape and serving rules as a
 * case's attachments ({@link AttachmentController}): any member reads, TESTER writes.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Step Attachments", description = "Files on test steps and evidence on step results")
@RequiredArgsConstructor
public class StepAttachmentController {

    private static final String DISPOSITION = "attachment";

    private final StepAttachmentService service;

    // ---- a step's documents ---------------------------------------------------------------------

    @GetMapping("/test-steps/{testStepId}/attachments")
    @RequireProjectRole
    public List<AttachmentSummary> listOnStep(@PathVariable UUID projectId, @PathVariable UUID testStepId) {
        return service.listOnStep(projectId, testStepId);
    }

    @PostMapping(value = "/test-steps/{testStepId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public AttachmentSummary uploadToStep(@PathVariable UUID projectId, @PathVariable UUID testStepId,
                                          @RequestParam MultipartFile file, Authentication authentication) throws IOException {
        return service.uploadToStep(projectId, testStepId, file.getOriginalFilename(), file.getContentType(),
                file.getBytes(), actor(authentication));
    }

    @GetMapping("/test-steps/{testStepId}/attachments/{id}")
    @RequireProjectRole
    public ResponseEntity<byte[]> downloadFromStep(@PathVariable UUID projectId, @PathVariable UUID testStepId,
                                                   @PathVariable UUID id,
                                                   @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return serve(service.getOnStep(projectId, testStepId, id), ifNoneMatch);
    }

    @DeleteMapping("/test-steps/{testStepId}/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void deleteFromStep(@PathVariable UUID projectId, @PathVariable UUID testStepId, @PathVariable UUID id,
                               Authentication authentication) {
        service.deleteFromStep(projectId, testStepId, id, actor(authentication));
    }

    /** Every step's files of one case, its shared steps' included, in one call (each names its step). */
    @GetMapping("/test-cases/{testCaseId}/step-attachments")
    @RequireProjectRole
    public List<AttachmentSummary> listOnStepsOfCase(@PathVariable UUID projectId, @PathVariable UUID testCaseId) {
        return service.listOnStepsOfCase(projectId, testCaseId);
    }

    // ---- a step result's evidence ---------------------------------------------------------------

    @GetMapping("/step-results/{stepResultId}/attachments")
    @RequireProjectRole
    public List<AttachmentSummary> listEvidence(@PathVariable UUID projectId, @PathVariable UUID stepResultId) {
        return service.listEvidence(projectId, stepResultId);
    }

    @PostMapping(value = "/step-results/{stepResultId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public AttachmentSummary uploadEvidence(@PathVariable UUID projectId, @PathVariable UUID stepResultId,
                                            @RequestParam MultipartFile file, Authentication authentication) throws IOException {
        return service.uploadEvidence(projectId, stepResultId, file.getOriginalFilename(), file.getContentType(),
                file.getBytes(), actor(authentication));
    }

    @GetMapping("/step-results/{stepResultId}/attachments/{id}")
    @RequireProjectRole
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable UUID projectId, @PathVariable UUID stepResultId,
                                                   @PathVariable UUID id,
                                                   @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return serve(service.getEvidence(projectId, stepResultId, id), ifNoneMatch);
    }

    @DeleteMapping("/step-results/{stepResultId}/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void deleteEvidence(@PathVariable UUID projectId, @PathVariable UUID stepResultId, @PathVariable UUID id) {
        service.deleteEvidence(projectId, stepResultId, id);
    }

    /** A run's evidence and the documents on the steps it executes; each names its step or step result. */
    @GetMapping("/test-runs/{testRunId}/step-attachments")
    @RequireProjectRole
    public List<AttachmentSummary> listForRun(@PathVariable UUID projectId, @PathVariable UUID testRunId) {
        return service.listForRun(projectId, testRunId);
    }

    /** Stored-media headers from {@link ImageResponses}, as for a case's attachments. */
    private static ResponseEntity<byte[]> serve(Attachment attachment, String ifNoneMatch) {
        return ImageResponses.of(attachment.getUpdatedAt(), attachment.getContentType(), attachment.getFileName(),
                DISPOSITION, attachment.getData(), ifNoneMatch);
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
