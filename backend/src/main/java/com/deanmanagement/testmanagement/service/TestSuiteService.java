package com.deanmanagement.testmanagement.service;

import com.deanmanagement.testmanagement.dto.CreateTestSuiteRequest;
import com.deanmanagement.testmanagement.dto.TestSuiteMapper;
import com.deanmanagement.testmanagement.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.dto.UpdateTestSuiteRequest;
import com.deanmanagement.testmanagement.entity.Project;
import com.deanmanagement.testmanagement.entity.TestCase;
import com.deanmanagement.testmanagement.entity.TestSuite;
import com.deanmanagement.testmanagement.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.repository.ProjectRepository;
import com.deanmanagement.testmanagement.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.repository.TestSuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteMapper testSuiteMapper;

    public List<TestSuiteResponse> findByProject(UUID projectId) {
        return testSuiteRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(testSuiteMapper::toResponse)
                .toList();
    }

    public TestSuiteResponse findById(UUID projectId, UUID id) {
        TestSuite suite = testSuiteRepository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", id));
        return testSuiteMapper.toResponse(suite);
    }

    @Transactional
    public TestSuiteResponse create(UUID projectId, CreateTestSuiteRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestSuite suite = testSuiteMapper.toEntity(request);
        suite.setProject(project);
        suite.setTestCases(resolveTestCases(request.testCaseIds()));

        suite = testSuiteRepository.save(suite);
        return testSuiteMapper.toResponse(suite);
    }

    @Transactional
    public TestSuiteResponse update(UUID projectId, UUID id, UpdateTestSuiteRequest request) {
        TestSuite suite = testSuiteRepository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", id));

        suite.setName(request.name());
        suite.setDescription(request.description());
        if (request.testCaseIds() != null) {
            suite.setTestCases(resolveTestCases(request.testCaseIds()));
        }

        suite = testSuiteRepository.save(suite);
        return testSuiteMapper.toResponse(suite);
    }

    @Transactional
    public void delete(UUID projectId, UUID id) {
        TestSuite suite = testSuiteRepository.findById(id)
                .filter(s -> s.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestSuite", id));
        testSuiteRepository.delete(suite);
    }

    private Set<TestCase> resolveTestCases(Set<UUID> testCaseIds) {
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(testCaseRepository.findAllById(testCaseIds));
    }
}
