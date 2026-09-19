package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID>, JpaSpecificationExecutor<AuditEntry> {
}
