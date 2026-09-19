package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.StepImageRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StepImageService {

    private final StepImageRepository stepImageRepository;
    private final TestStepRepository testStepRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public StepImage upload(UUID testStepId, String fileName, String contentType, byte[] data) {
        TestStep testStep = testStepRepository.findById(testStepId)
                .orElseThrow(() -> new ResourceNotFoundException("TestStep", testStepId));
        projectAccessService.requireRoleForCurrentUser(projectIdOf(testStep), ProjectRole.TESTER);

        stepImageRepository.findByTestStepId(testStepId)
                .ifPresent(stepImageRepository::delete);
        // Flushed now: Hibernate orders INSERTs before DELETEs, and the replacement would otherwise
        // collide with the old row on the one-per-owner constraint (V63). A concurrent upload that
        // loses the race gets a constraint violation (409) instead of a second row.
        stepImageRepository.flush();

        StepImage image = new StepImage();
        image.setTestStep(testStep);
        image.setFileName(fileName);
        image.setContentType(ImageMediaTypes.requireAllowed(contentType));
        image.setData(data);

        return stepImageRepository.save(image);
    }

    public StepImage findById(UUID id) {
        StepImage image = stepImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StepImage", id));
        projectAccessService.requireRoleForCurrentUser(projectIdOf(image.getTestStep()), ProjectRole.VIEWER);
        return image;
    }

    @Transactional
    public void delete(UUID id) {
        StepImage image = stepImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StepImage", id));
        projectAccessService.requireRoleForCurrentUser(projectIdOf(image.getTestStep()), ProjectRole.TESTER);
        stepImageRepository.delete(image);
    }

    /** Through the case, or through the shared block for a block's step (PRD-030). */
    private UUID projectIdOf(TestStep testStep) {
        return testStep.getTestCase() != null
                ? testStep.getTestCase().getProject().getId()
                : testStep.getSharedStep().getProject().getId();
    }
}
