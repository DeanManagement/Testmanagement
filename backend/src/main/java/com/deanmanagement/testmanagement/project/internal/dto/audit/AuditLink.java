package com.deanmanagement.testmanagement.project.internal.dto.audit;

import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;

import java.util.UUID;

/** Where an activity entry leads: its object, or the object it lives in. Absent once deleted. */
public record AuditLink(AuditEntityType type, UUID id) {
}
