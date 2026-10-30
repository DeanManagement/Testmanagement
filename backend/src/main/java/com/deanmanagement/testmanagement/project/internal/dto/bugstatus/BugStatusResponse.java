package com.deanmanagement.testmanagement.project.internal.dto.bugstatus;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;

import java.util.List;
import java.util.UUID;

/** {@code allowedTargetIds}: the active statuses a bug in this one may move to (TM47). */
public record BugStatusResponse(UUID id, String name, String color, BugReportStatus category, int position,
                                boolean archived, List<UUID> allowedTargetIds) {
}
