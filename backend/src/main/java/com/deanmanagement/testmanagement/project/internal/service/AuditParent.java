package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;

import java.util.UUID;

/** The object an audited thing lives in, e.g. the test case a comment is on (PRD-046). */
public record AuditParent(AuditEntityType type, UUID id) {
}
