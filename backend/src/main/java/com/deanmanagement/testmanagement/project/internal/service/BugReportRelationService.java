package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportLinksResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.RelateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.BugRelationType;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportRelation;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRelationRepository;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Links made by hand between a bug report and test cases, requirements and other bug reports
 * (TM46). Results and runs are linked by {@link BugReportLinkService}, where the bug showed up.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BugReportRelationService {

    private final BugReportService bugReportService;
    private final BugReportRelationRepository relationRepository;
    private final TestCaseRepository testCaseRepository;
    private final RequirementRepository requirementRepository;
    private final AuditService auditService;

    public BugReportLinksResponse links(UUID projectId, String bugIdOrKey) {
        return links(bugReportService.require(projectId, bugIdOrKey));
    }

    /** Idempotent, like linking a result. */
    @Transactional
    public BugReportLinksResponse linkTestCase(UUID projectId, String bugIdOrKey, String testCase, UUID userId) {
        BugReport bug = requireWritable(projectId, bugIdOrKey);
        TestCase target = requireTestCase(projectId, testCase);
        if (bug.getLinkedTestCases().add(target)) {
            audit(projectId, userId, bug, "Linked to test case " + target.getKey());
        }
        return links(bug);
    }

    @Transactional
    public BugReportLinksResponse unlinkTestCase(UUID projectId, String bugIdOrKey, String testCase, UUID userId) {
        BugReport bug = requireWritable(projectId, bugIdOrKey);
        TestCase target = requireTestCase(projectId, testCase);
        if (bug.getLinkedTestCases().remove(target)) {
            audit(projectId, userId, bug, "Unlinked from test case " + target.getKey());
        }
        return links(bug);
    }

    @Transactional
    public BugReportLinksResponse linkRequirement(UUID projectId, String bugIdOrKey, String requirement, UUID userId) {
        BugReport bug = requireWritable(projectId, bugIdOrKey);
        Requirement target = requireRequirement(projectId, requirement);
        if (bug.getLinkedRequirements().add(target)) {
            // Both sides, so a requirement read later in this transaction lists the bug.
            target.getBugReports().add(bug);
            audit(projectId, userId, bug, "Linked to requirement " + target.getExternalId());
        }
        return links(bug);
    }

    @Transactional
    public BugReportLinksResponse unlinkRequirement(UUID projectId, String bugIdOrKey, String requirement,
                                                    UUID userId) {
        BugReport bug = requireWritable(projectId, bugIdOrKey);
        Requirement target = requireRequirement(projectId, requirement);
        if (bug.getLinkedRequirements().remove(target)) {
            target.getBugReports().remove(bug);
            audit(projectId, userId, bug, "Unlinked from requirement " + target.getExternalId());
        }
        return links(bug);
    }

    /** Idempotent; RELATES_TO counts as existing whichever bug it was made from. */
    @Transactional
    public BugReportLinksResponse relate(UUID projectId, String bugIdOrKey, RelateBugReportRequest request,
                                         UUID userId) {
        BugReport bug = requireWritable(projectId, bugIdOrKey);
        BugReport other = bugReportService.require(projectId, request.relatedBug());
        if (bug.getId().equals(other.getId())) {
            throw new IllegalArgumentException("A bug report cannot be linked to itself");
        }
        boolean exists = relationRepository.findInvolving(bug.getId()).stream()
                .anyMatch(r -> sameRelation(r, bug, other, request.type()));
        if (!exists) {
            BugReportRelation relation = new BugReportRelation();
            relation.setBugReport(bug);
            relation.setRelatedBug(other);
            relation.setType(request.type());
            relationRepository.save(relation);
            audit(projectId, userId, bug, request.type() + " " + other.getKey());
        }
        return links(bug);
    }

    @Transactional
    public BugReportLinksResponse unrelate(UUID projectId, String bugIdOrKey, UUID relationId, UUID userId) {
        BugReport bug = requireWritable(projectId, bugIdOrKey);
        BugReportRelation relation = relationRepository.findInvolving(bug.getId()).stream()
                .filter(r -> r.getId().equals(relationId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BugReportRelation", relationId));
        relationRepository.delete(relation);
        audit(projectId, userId, bug, "Removed " + relation.getType() + " " + otherSide(relation, bug).getKey());
        return links(bug);
    }

    private static boolean sameRelation(BugReportRelation r, BugReport bug, BugReport other, BugRelationType type) {
        if (r.getType() != type) {
            return false;
        }
        boolean forward = r.getBugReport().getId().equals(bug.getId()) && r.getRelatedBug().getId().equals(other.getId());
        boolean backward = r.getBugReport().getId().equals(other.getId()) && r.getRelatedBug().getId().equals(bug.getId());
        return forward || (type == BugRelationType.RELATES_TO && backward);
    }

    private static BugReport otherSide(BugReportRelation relation, BugReport bug) {
        return relation.getBugReport().getId().equals(bug.getId()) ? relation.getRelatedBug() : relation.getBugReport();
    }

    private BugReportLinksResponse links(BugReport bug) {
        List<BugReportLinksResponse.Item> testCases = bug.getLinkedTestCases().stream()
                .sorted(Comparator.comparing(TestCase::getKey))
                .map(tc -> new BugReportLinksResponse.Item(tc.getId(), tc.getKey(), tc.getTitle()))
                .toList();
        List<BugReportLinksResponse.Item> requirements = bug.getLinkedRequirements().stream()
                .sorted(Comparator.comparing(Requirement::getExternalId))
                .map(r -> new BugReportLinksResponse.Item(r.getId(), r.getExternalId(), r.getTitle()))
                .toList();
        List<BugReportLinksResponse.Relation> relations = relationRepository.findInvolving(bug.getId()).stream()
                .map(r -> {
                    BugReport other = otherSide(r, bug);
                    boolean outgoing = r.getType() == BugRelationType.RELATES_TO
                            || r.getBugReport().getId().equals(bug.getId());
                    return new BugReportLinksResponse.Relation(r.getId(), r.getType(), outgoing, other.getId(),
                            other.getKey(), other.getTitle(), other.getStatus());
                })
                .toList();
        return new BugReportLinksResponse(testCases, requirements, relations);
    }

    private BugReport requireWritable(UUID projectId, String bugIdOrKey) {
        bugReportService.requireBugReportsEnabled(projectId);
        return bugReportService.require(projectId, bugIdOrKey);
    }

    private TestCase requireTestCase(UUID projectId, String reference) {
        return parseUuid(reference)
                .flatMap(id -> testCaseRepository.findByIdAndProjectId(id, projectId))
                .or(() -> testCaseRepository.findByKeyAndProjectId(reference.strip(), projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", reference));
    }

    private Requirement requireRequirement(UUID projectId, String reference) {
        return parseUuid(reference)
                .flatMap(id -> requirementRepository.findByIdAndProjectId(id, projectId))
                .or(() -> requirementRepository.findByProjectIdAndExternalId(projectId, reference.strip()))
                .orElseThrow(() -> new ResourceNotFoundException("Requirement", reference));
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private void audit(UUID projectId, UUID userId, BugReport bug, String details) {
        auditService.log(projectId, userId, AuditAction.UPDATED, AuditEntityType.BUG_REPORT, bug.getId(),
                BugReportService.label(bug), details);
    }
}
