package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.session.CreateExploratorySessionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.CreateSessionNoteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.EndSessionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.ExploratorySessionResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.SessionListFilter;
import com.deanmanagement.testmanagement.project.internal.dto.session.SessionNoteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.session.UpdateExploratorySessionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.session.UpdateSessionNoteRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySessionNoteImage;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.deanmanagement.testmanagement.project.internal.service.ExploratorySessionService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * Exploratory testing sessions (PRD-034). Everything but "my sessions" lives under the project
 * path, so {@link RequireProjectRole} authorizes it, image downloads included.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Exploratory sessions", description = "Time-boxed exploratory testing with a note log")
@RequiredArgsConstructor
public class ExploratorySessionController {

    private static final String BASE = "/projects/{projectId}/exploratory-sessions";
    private static final String NOTE = BASE + "/{id}/notes/{noteId}";

    private final ExploratorySessionService sessionService;

    @GetMapping(BASE)
    @RequireProjectRole
    public Page<ExploratorySessionResponse> list(@PathVariable UUID projectId,
                                                 @RequestParam(required = false) TestRunStatus status,
                                                 @RequestParam(required = false) UUID testPlanId,
                                                 @RequestParam(required = false) UUID testerId,
                                                 @PageableDefault(size = PageableUtils.DEFAULT_SIZE) Pageable pageable) {
        return sessionService.list(projectId, new SessionListFilter(status, testPlanId, testerId),
                PageableUtils.normalize(pageable));
    }

    /** The caller's planned and running sessions in every project. */
    @GetMapping("/exploratory-sessions/assigned-to-me")
    public List<ExploratorySessionResponse> assignedToMe(Authentication authentication) {
        return sessionService.listActiveFor(userId(authentication));
    }

    @GetMapping(BASE + "/{id}")
    @RequireProjectRole
    public ExploratorySessionResponse get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return sessionService.get(projectId, id);
    }

    @PostMapping(BASE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public ExploratorySessionResponse create(@PathVariable UUID projectId,
                                             @Valid @RequestBody CreateExploratorySessionRequest request,
                                             Authentication authentication) {
        return sessionService.create(projectId, request, userId(authentication));
    }

    @PutMapping(BASE + "/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public ExploratorySessionResponse update(@PathVariable UUID projectId, @PathVariable UUID id,
                                             @Valid @RequestBody UpdateExploratorySessionRequest request,
                                             Authentication authentication) {
        return sessionService.update(projectId, id, request, userId(authentication));
    }

    @PostMapping(BASE + "/{id}/start")
    @RequireProjectRole(ProjectRole.TESTER)
    public ExploratorySessionResponse start(@PathVariable UUID projectId, @PathVariable UUID id,
                                            Authentication authentication) {
        return sessionService.start(projectId, id, userId(authentication));
    }

    @PostMapping(BASE + "/{id}/complete")
    @RequireProjectRole(ProjectRole.TESTER)
    public ExploratorySessionResponse complete(@PathVariable UUID projectId, @PathVariable UUID id,
                                               @RequestBody(required = false) EndSessionRequest request,
                                               Authentication authentication) {
        return sessionService.complete(projectId, id, request != null ? request.summary() : null,
                userId(authentication));
    }

    @PostMapping(BASE + "/{id}/abort")
    @RequireProjectRole(ProjectRole.TESTER)
    public ExploratorySessionResponse abort(@PathVariable UUID projectId, @PathVariable UUID id,
                                            @RequestBody(required = false) EndSessionRequest request,
                                            Authentication authentication) {
        return sessionService.abort(projectId, id, request != null ? request.summary() : null,
                userId(authentication));
    }

    @DeleteMapping(BASE + "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id, Authentication authentication) {
        sessionService.delete(projectId, id, userId(authentication));
    }

    @PostMapping(BASE + "/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public SessionNoteResponse addNote(@PathVariable UUID projectId, @PathVariable UUID id,
                                       @Valid @RequestBody CreateSessionNoteRequest request) {
        return sessionService.addNote(projectId, id, request);
    }

    /** Author or project admin; the service checks which. */
    @PutMapping(NOTE)
    @RequireProjectRole(ProjectRole.TESTER)
    public SessionNoteResponse updateNote(@PathVariable UUID projectId, @PathVariable UUID id,
                                          @PathVariable UUID noteId,
                                          @Valid @RequestBody UpdateSessionNoteRequest request,
                                          Authentication authentication) {
        return sessionService.updateNote(projectId, id, noteId, request, userId(authentication));
    }

    @DeleteMapping(NOTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void deleteNote(@PathVariable UUID projectId, @PathVariable UUID id, @PathVariable UUID noteId,
                           Authentication authentication) {
        sessionService.deleteNote(projectId, id, noteId, userId(authentication));
    }

    @PostMapping(value = NOTE + "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void uploadImage(@PathVariable UUID projectId, @PathVariable UUID id, @PathVariable UUID noteId,
                            @RequestParam MultipartFile file, Authentication authentication) throws IOException {
        sessionService.uploadImage(projectId, id, noteId, file.getOriginalFilename(), file.getContentType(),
                file.getBytes(), userId(authentication));
    }

    @GetMapping(NOTE + "/image")
    @RequireProjectRole
    public ResponseEntity<byte[]> image(@PathVariable UUID projectId, @PathVariable UUID id,
                                        @PathVariable UUID noteId,
                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                        String ifNoneMatch) {
        ExploratorySessionNoteImage image = sessionService.getImage(projectId, id, noteId);
        return ImageResponses.of(image.getUpdatedAt(), image.getContentType(), image.getFileName(), "screenshot",
                image.getData(), ifNoneMatch);
    }

    @DeleteMapping(NOTE + "/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void deleteImage(@PathVariable UUID projectId, @PathVariable UUID id, @PathVariable UUID noteId,
                            Authentication authentication) {
        sessionService.deleteImage(projectId, id, noteId, userId(authentication));
    }

    private static UUID userId(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
