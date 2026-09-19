package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditEntryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditFilter;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.service.AuditExportService;
import com.deanmanagement.testmanagement.project.internal.service.AuditService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Project activity (PRD-046). With {@code entityId} it is one object's history, including what
 * happened inside it (comments on a case, say). Every filter is optional; {@code sort} is
 * {@code asc} or {@code desc} by time.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/activity")
@Tag(name = "Activity", description = "Project activity and audit log endpoints")
@RequiredArgsConstructor
public class AuditController {

    private static final String SORT_FIELD = "createdAt";

    private final AuditService auditService;
    private final AuditExportService exportService;

    @GetMapping
    @RequireProjectRole
    public Page<AuditEntryResponse> getActivity(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) List<AuditEntityType> entityType,
            @RequestParam(required = false) List<UUID> userId,
            @RequestParam(required = false) List<AuditAction> action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuditFilter filter = new AuditFilter(entityId, entityType, userId, action, from, to);
        int pageSize = Math.min(Math.max(size, 1), PageableUtils.MAX_SIZE);
        return auditService.find(projectId, filter, PageRequest.of(Math.max(page, 0), pageSize, byTime(sort)));
    }

    @GetMapping("/export")
    @RequireProjectRole
    public ResponseEntity<byte[]> export(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) List<AuditEntityType> entityType,
            @RequestParam(required = false) List<UUID> userId,
            @RequestParam(required = false) List<AuditAction> action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "desc") String sort) {
        AuditFilter filter = new AuditFilter(entityId, entityType, userId, action, from, to);
        byte[] csv = exportService.exportCsv(projectId, filter, byTime(sort));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("activity.csv").build().toString())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }

    /** Ties on the same millisecond fall back to the id, so pages never repeat or skip an entry. */
    private static Sort byTime(String direction) {
        // fromString takes asc/desc in any case, and refuses anything else with a 400.
        Sort.Direction parsed = Sort.Direction.fromString(direction);
        return Sort.by(parsed, SORT_FIELD).and(Sort.by(parsed, "id"));
    }
}
