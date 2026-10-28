package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCaseLink.TestCaseLinkResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseLink.TestCaseLinkResponse.Direction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLink;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLinkType;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseLinkRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Links between test cases (TM07). Both cases must be in the caller's project — a foreign id is a
 * 404, as everywhere else. Copying a case does not copy its links; deleting one removes them (the
 * foreign keys cascade).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseLinkService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseLinkRepository linkRepository;
    private final AuditService auditService;

    /** The case's links in both directions, preconditions first, then by the other case's key. */
    public List<TestCaseLinkResponse> list(UUID projectId, UUID testCaseId) {
        requireCase(projectId, testCaseId);
        return linkRepository.findInvolving(testCaseId).stream()
                .map(link -> toResponse(link, testCaseId))
                .sorted(Comparator.comparing(TestCaseLinkResponse::linkType)
                        .thenComparing(TestCaseLinkResponse::direction)
                        .thenComparing(TestCaseLinkResponse::testCaseKey))
                .toList();
    }

    /**
     * Idempotent: an existing link (or, for RELATES_TO, the same link the other way round) is left
     * as it is. A precondition back to a case that is already this one's precondition is refused —
     * neither could ever run first.
     */
    @Transactional
    public List<TestCaseLinkResponse> link(UUID projectId, UUID sourceId, UUID targetId,
                                           TestCaseLinkType linkType, UUID actorId) {
        TestCase source = requireCase(projectId, sourceId);
        TestCase target = requireCase(projectId, targetId);
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("A test case cannot be linked to itself");
        }
        boolean exists = linkRepository.findBySourceIdAndTargetIdAndLinkType(sourceId, targetId, linkType).isPresent();
        boolean reverseExists = linkRepository.findBySourceIdAndTargetIdAndLinkType(targetId, sourceId, linkType)
                .isPresent();
        if (reverseExists && linkType == TestCaseLinkType.PRECONDITION) {
            throw new IllegalArgumentException(target.getKey() + " already has " + source.getKey()
                    + " as its precondition; the two cannot each run first");
        }
        if (!exists && !reverseExists) {
            TestCaseLink link = new TestCaseLink();
            link.setSource(source);
            link.setTarget(target);
            link.setLinkType(linkType);
            linkRepository.save(link);
            auditService.log(projectId, actorId, AuditAction.UPDATED, AuditEntityType.TEST_CASE,
                    source.getId(), source.getTitle(), "Linked " + target.getKey() + " (" + linkType + ")");
        }
        return list(projectId, sourceId);
    }

    /** Removes one link the case takes part in, from either side. */
    @Transactional
    public void unlink(UUID projectId, UUID testCaseId, UUID linkId, UUID actorId) {
        TestCase testCase = requireCase(projectId, testCaseId);
        TestCaseLink link = linkRepository.findById(linkId)
                .filter(l -> l.getSource().getId().equals(testCaseId) || l.getTarget().getId().equals(testCaseId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCaseLink", linkId));
        delete(projectId, testCase, link, actorId);
    }

    /**
     * Removes the links between two cases, either way round; {@code linkType} null means both
     * types. Returns how many went — zero is not an error.
     */
    @Transactional
    public int unlink(UUID projectId, UUID caseA, UUID caseB, TestCaseLinkType linkType, UUID actorId) {
        TestCase first = requireCase(projectId, caseA);
        requireCase(projectId, caseB);
        List<TestCaseLink> matching = linkRepository.findInvolving(caseA).stream()
                .filter(l -> l.getSource().getId().equals(caseB) || l.getTarget().getId().equals(caseB))
                .filter(l -> linkType == null || l.getLinkType() == linkType)
                .toList();
        matching.forEach(link -> delete(projectId, first, link, actorId));
        return matching.size();
    }

    private void delete(UUID projectId, TestCase from, TestCaseLink link, UUID actorId) {
        TestCase other = link.getSource().getId().equals(from.getId()) ? link.getTarget() : link.getSource();
        linkRepository.delete(link);
        auditService.log(projectId, actorId, AuditAction.UPDATED, AuditEntityType.TEST_CASE,
                from.getId(), from.getTitle(), "Unlinked " + other.getKey() + " (" + link.getLinkType() + ")");
    }

    private TestCase requireCase(UUID projectId, UUID id) {
        return testCaseRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));
    }

    private static TestCaseLinkResponse toResponse(TestCaseLink link, UUID perspective) {
        boolean outgoing = link.getSource().getId().equals(perspective);
        TestCase other = outgoing ? link.getTarget() : link.getSource();
        return new TestCaseLinkResponse(link.getId(), link.getLinkType(),
                outgoing ? Direction.OUTGOING : Direction.INCOMING,
                other.getId(), other.getKey(), other.getTitle());
    }
}
