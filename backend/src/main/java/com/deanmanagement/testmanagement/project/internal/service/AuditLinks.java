package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditLink;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntry;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.ExploratorySession;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.SharedStep;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestSuite;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which activity entries can link to a page (PRD-046 §3.4): an entry about something inside another
 * object leads to that object, and a deleted target leads nowhere. One query per object type on the
 * page, however many entries it has.
 */
@Component
@RequiredArgsConstructor
class AuditLinks {

    /** The types with a page of their own, and the entity each is stored as. */
    private static final Map<AuditEntityType, Class<?>> LINKABLE = Map.of(
            AuditEntityType.TEST_CASE, TestCase.class,
            AuditEntityType.TEST_SUITE, TestSuite.class,
            AuditEntityType.TEST_RUN, TestRun.class,
            AuditEntityType.TEST_PLAN, TestPlan.class,
            AuditEntityType.BUG_REPORT, BugReport.class,
            AuditEntityType.REQUIREMENT, Requirement.class,
            AuditEntityType.EXPLORATORY_SESSION, ExploratorySession.class,
            AuditEntityType.SHARED_STEP, SharedStep.class);

    private final EntityManager entityManager;

    /** Links for the entries that have one; an entry without a live target is absent from the map. */
    Map<UUID, AuditLink> resolve(List<AuditEntry> entries) {
        Map<UUID, AuditLink> candidates = entries.stream()
                .filter(e -> target(e) != null)
                .collect(Collectors.toMap(AuditEntry::getId, AuditLinks::target));
        Set<AuditLink> existing = existing(new HashSet<>(candidates.values()));
        candidates.values().retainAll(existing);
        return candidates;
    }

    private static AuditLink target(AuditEntry entry) {
        if (entry.getParentEntityType() != null && entry.getParentEntityId() != null) {
            return LINKABLE.containsKey(entry.getParentEntityType())
                    ? new AuditLink(entry.getParentEntityType(), entry.getParentEntityId()) : null;
        }
        return entry.getEntityId() != null && LINKABLE.containsKey(entry.getEntityType())
                ? new AuditLink(entry.getEntityType(), entry.getEntityId()) : null;
    }

    private Set<AuditLink> existing(Collection<AuditLink> links) {
        Map<AuditEntityType, Set<UUID>> idsByType = links.stream().collect(Collectors.groupingBy(
                AuditLink::type, Collectors.mapping(AuditLink::id, Collectors.toSet())));
        Set<AuditLink> existing = new HashSet<>();
        idsByType.forEach((type, ids) -> entityManager
                .createQuery("SELECT e.id FROM " + LINKABLE.get(type).getSimpleName() + " e WHERE e.id IN :ids",
                        UUID.class)
                .setParameter("ids", ids)
                .getResultList()
                .forEach(id -> existing.add(new AuditLink(type, id))));
        return existing;
    }
}
