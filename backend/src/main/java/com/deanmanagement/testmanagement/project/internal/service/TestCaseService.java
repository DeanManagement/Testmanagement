package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseMapper testCaseMapper;

    public List<TestCaseResponse> findByProject(UUID projectId) {
        return testCaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(testCaseMapper::toResponse)
                .toList();
    }

    public TestCaseResponse findById(UUID projectId, UUID id) {
        TestCase tc = testCaseRepository.findById(id)
                .filter(t -> t.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));
        return testCaseMapper.toResponse(tc);
    }

    @Transactional
    public TestCaseResponse create(UUID projectId, CreateTestCaseRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestCase tc = testCaseMapper.toEntity(request);
        tc.setProject(project);
        tc.setLabels(request.labels() != null ? request.labels() : new HashSet<>());
        tc.setSteps(buildSteps(request.steps(), tc));

        int number = project.getNextTestCaseNumber();
        tc.setKey(project.getKey() + "-" + number);
        project.setNextTestCaseNumber(number + 1);
        projectRepository.save(project);

        tc = testCaseRepository.save(tc);
        return testCaseMapper.toResponse(tc);
    }

    @Transactional
    public TestCaseResponse update(UUID projectId, UUID id, UpdateTestCaseRequest request) {
        TestCase tc = testCaseRepository.findById(id)
                .filter(t -> t.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));

        tc.setTitle(request.title());
        tc.setDescription(request.description());
        tc.setPreconditions(request.preconditions());
        if (request.priority() != null) tc.setPriority(request.priority());
        if (request.status() != null) tc.setStatus(request.status());
        if (request.labels() != null) tc.setLabels(request.labels());
        if (request.steps() != null) {
            tc.getSteps().clear();
            tc.getSteps().addAll(buildSteps(request.steps(), tc));
        }

        tc = testCaseRepository.save(tc);
        return testCaseMapper.toResponse(tc);
    }

    @Transactional
    public void delete(UUID projectId, UUID id) {
        TestCase tc = testCaseRepository.findById(id)
                .filter(t -> t.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", id));
        testCaseRepository.delete(tc);
    }

    private List<TestStep> buildSteps(List<TestStepRequest> stepRequests, TestCase testCase) {
        if (stepRequests == null) return new ArrayList<>();
        List<TestStep> steps = new ArrayList<>();
        for (int i = 0; i < stepRequests.size(); i++) {
            TestStepRequest sr = stepRequests.get(i);
            TestStep step = new TestStep();
            step.setAction(sr.action());
            step.setExpectedResult(sr.expectedResult());
            step.setOrderIndex(i);
            step.setTestCase(testCase);
            steps.add(step);
        }
        return steps;
    }
}
