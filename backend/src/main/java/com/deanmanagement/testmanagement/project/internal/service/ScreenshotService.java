package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ScreenshotRepository;
import com.deanmanagement.testmanagement.project.internal.repository.StepResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenshotService {

    private final ScreenshotRepository screenshotRepository;
    private final StepResultRepository stepResultRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public Screenshot upload(UUID stepResultId, String fileName, String contentType, byte[] data) {
        StepResult stepResult = stepResultRepository.findById(stepResultId)
                .orElseThrow(() -> new ResourceNotFoundException("StepResult", stepResultId));
        projectAccessService.requireRoleForCurrentUser(projectIdOf(stepResult), ProjectRole.TESTER);

        // Replace existing screenshot if any
        screenshotRepository.findByStepResultId(stepResultId)
                .ifPresent(screenshotRepository::delete);

        Screenshot screenshot = new Screenshot();
        screenshot.setStepResult(stepResult);
        screenshot.setFileName(fileName);
        screenshot.setContentType(ImageMediaTypes.requireAllowed(contentType));
        screenshot.setData(data);

        return screenshotRepository.save(screenshot);
    }

    public Screenshot findById(UUID id) {
        Screenshot screenshot = screenshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screenshot", id));
        projectAccessService.requireRoleForCurrentUser(
                projectIdOf(screenshot.getStepResult()), ProjectRole.VIEWER);
        return screenshot;
    }

    @Transactional
    public void delete(UUID id) {
        Screenshot screenshot = screenshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screenshot", id));
        projectAccessService.requireRoleForCurrentUser(
                projectIdOf(screenshot.getStepResult()), ProjectRole.TESTER);
        screenshotRepository.delete(screenshot);
    }

    private UUID projectIdOf(StepResult stepResult) {
        return stepResult.getTestResult().getTestRun().getProject().getId();
    }
}
