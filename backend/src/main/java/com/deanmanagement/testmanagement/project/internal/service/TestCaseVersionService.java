package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionResponse;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionSummary;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseVersion;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseVersionRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Test case history (PRD-011).
 *
 * <p>The live test case <em>is</em> the current version; only superseded states get a
 * {@code test_case_versions} row. So a case at {@code current_version = 3} has snapshots 1 and 2,
 * and version 3 is synthesised from the case itself. The alternative — duplicating the live state
 * into a row on every save — would double every write and leave two copies to keep in step.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseVersionService {

    private final TestCaseVersionRepository versionRepository;
    private final TestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;

    /**
     * Captures the case's current state and advances its version counter. Called from
     * {@code TestCaseService.update} <em>before</em> the entity is mutated, inside the same
     * transaction, so a failed edit leaves no orphan snapshot.
     */
    @Transactional
    public void snapshotBeforeEdit(TestCase testCase) {
        TestCaseVersion version = new TestCaseVersion();
        version.setTestCaseId(testCase.getId());
        version.setVersionNumber(testCase.getCurrentVersion());
        version.setVersionAt(Instant.now());
        version.setTitle(testCase.getTitle());
        version.setDescription(testCase.getDescription());
        version.setPreconditions(testCase.getPreconditions());
        version.setPriority(testCase.getPriority());
        version.setStatus(testCase.getStatus());
        version.setLabels(String.join(",", testCase.getLabels()));
        version.setStepsSnapshot(serialiseSteps(testCase.getSteps()));
        versionRepository.save(version);

        testCase.setCurrentVersion(testCase.getCurrentVersion() + 1);
    }

    /** Newest first, with the live state at the top. */
    public List<TestCaseVersionSummary> list(UUID projectId, UUID testCaseId) {
        TestCase testCase = require(projectId, testCaseId);

        List<TestCaseVersionSummary> summaries = new ArrayList<>();
        summaries.add(new TestCaseVersionSummary(
                null,
                testCase.getCurrentVersion(),
                testCase.getUpdatedAt(),
                testCase.getTitle(),
                testCase.getUpdatedBy(),
                true));

        versionRepository.findByTestCaseIdOrderByVersionNumberDesc(testCaseId).stream()
                .map(v -> new TestCaseVersionSummary(
                        v.getId(), v.getVersionNumber(), v.getVersionAt(),
                        v.getTitle(), v.getCreatedBy(), false))
                .forEach(summaries::add);

        return summaries;
    }

    public TestCaseVersionResponse get(UUID projectId, UUID testCaseId, int versionNumber) {
        TestCase testCase = require(projectId, testCaseId);

        if (versionNumber == testCase.getCurrentVersion()) {
            return fromLiveCase(testCase);
        }
        TestCaseVersion version = versionRepository
                .findByTestCaseIdAndVersionNumber(testCaseId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("TestCaseVersion", testCaseId));
        return fromSnapshot(version);
    }

    // ---- mapping ----------------------------------------------------------

    private TestCaseVersionResponse fromLiveCase(TestCase testCase) {
        List<TestCaseVersionResponse.StepSnapshot> steps = testCase.getSteps().stream()
                .sorted(Comparator.comparingInt(TestStep::getOrderIndex))
                .map(s -> new TestCaseVersionResponse.StepSnapshot(
                        s.getOrderIndex(), s.getAction(), s.getExpectedResult(), s.getTestData()))
                .toList();

        return new TestCaseVersionResponse(
                null,
                testCase.getCurrentVersion(),
                testCase.getUpdatedAt(),
                testCase.getTitle(),
                testCase.getDescription(),
                testCase.getPreconditions(),
                testCase.getPriority(),
                testCase.getStatus(),
                testCase.getLabels().stream().sorted().toList(),
                steps,
                testCase.getUpdatedBy());
    }

    private TestCaseVersionResponse fromSnapshot(TestCaseVersion version) {
        return new TestCaseVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getVersionAt(),
                version.getTitle(),
                version.getDescription(),
                version.getPreconditions(),
                version.getPriority(),
                version.getStatus(),
                parseLabels(version.getLabels()),
                deserialiseSteps(version.getStepsSnapshot()),
                version.getCreatedBy());
    }

    private String serialiseSteps(List<TestStep> steps) {
        List<TestCaseVersionResponse.StepSnapshot> snapshots = steps.stream()
                .sorted(Comparator.comparingInt(TestStep::getOrderIndex))
                .map(s -> new TestCaseVersionResponse.StepSnapshot(
                        s.getOrderIndex(), s.getAction(), s.getExpectedResult(), s.getTestData()))
                .toList();
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (Exception e) {
            // A snapshot that cannot be written would silently lose history, so fail the edit.
            throw new IllegalStateException("Could not snapshot the test case steps");
        }
    }

    private List<TestCaseVersionResponse.StepSnapshot> deserialiseSteps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TestCaseVersionResponse.StepSnapshot>>() {
            });
        } catch (Exception e) {
            // A corrupt snapshot must not take out the History tab for every other version.
            return List.of();
        }
    }

    private static List<String> parseLabels(String labels) {
        if (labels == null || labels.isBlank()) {
            return List.of();
        }
        return Arrays.stream(labels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .toList();
    }

    private TestCase require(UUID projectId, UUID testCaseId) {
        return testCaseRepository.findById(testCaseId)
                .filter(tc -> tc.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
    }
}
