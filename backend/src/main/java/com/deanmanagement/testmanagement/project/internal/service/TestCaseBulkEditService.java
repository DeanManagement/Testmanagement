package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkEditTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.BulkOperationResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.MoveTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * TM11: priority, labels, folder and custom fields for many cases at once. Each case goes through
 * {@link TestCaseService#update}, so it gets the version, review and audit entry a single edit would;
 * one transaction, so a refused case leaves the whole selection as it was.
 */
@Service
@RequiredArgsConstructor
public class TestCaseBulkEditService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseService testCaseService;
    private final TestCaseFolderService folderService;

    @Transactional
    public BulkOperationResponse edit(UUID projectId, BulkEditTestCasesRequest request, UUID userId) {
        if (!changesCaseFields(request) && !Boolean.TRUE.equals(request.moveToFolder())) {
            throw new IllegalArgumentException("Nothing to change: pass a priority, labels, a folder or custom fields");
        }
        List<TestCase> cases = testCaseRepository.findByIdInAndProjectId(request.testCaseIds(), projectId);
        if (cases.size() != request.testCaseIds().size()) {
            Set<UUID> missing = new HashSet<>(request.testCaseIds());
            cases.forEach(tc -> missing.remove(tc.getId()));
            throw new ResourceNotFoundException("TestCase", missing.iterator().next());
        }

        if (changesCaseFields(request)) {
            for (TestCase tc : cases) {
                UpdateTestCaseRequest update = new UpdateTestCaseRequest(null, null, null, request.priority(), null,
                        labelsAfter(tc, request), null, request.customFields(), null);
                testCaseService.update(projectId, tc.getId(), update, userId, CustomFieldWriteMode.MACHINE);
            }
        }
        if (Boolean.TRUE.equals(request.moveToFolder())) {
            folderService.moveTestCases(projectId,
                    new MoveTestCasesRequest(List.copyOf(request.testCaseIds()), request.folderId()), userId);
        }
        return new BulkOperationResponse(cases.size(), cases.size() + " test cases updated");
    }

    private static boolean changesCaseFields(BulkEditTestCasesRequest request) {
        return request.priority() != null || hasAny(request.addLabels()) || hasAny(request.removeLabels())
                || (request.customFields() != null && !request.customFields().isEmpty());
    }

    /** Null when the labels do not change, so the update leaves them alone. */
    private static Set<String> labelsAfter(TestCase tc, BulkEditTestCasesRequest request) {
        if (!hasAny(request.addLabels()) && !hasAny(request.removeLabels())) {
            return null;
        }
        Set<String> labels = new HashSet<>(tc.getLabels());
        if (request.removeLabels() != null) {
            labels.removeAll(request.removeLabels());
        }
        if (request.addLabels() != null) {
            request.addLabels().stream().map(String::strip).filter(l -> !l.isEmpty()).forEach(labels::add);
        }
        return labels;
    }

    private static boolean hasAny(Set<String> values) {
        return values != null && !values.isEmpty();
    }
}
