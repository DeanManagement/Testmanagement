package com.deanmanagement.testmanagement.project.internal.service;

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

    @Transactional
    public Screenshot upload(UUID stepResultId, String fileName, String contentType, byte[] data) {
        StepResult stepResult = stepResultRepository.findById(stepResultId)
                .orElseThrow(() -> new ResourceNotFoundException("StepResult", stepResultId));

        // Replace existing screenshot if any
        screenshotRepository.findByStepResultId(stepResultId)
                .ifPresent(screenshotRepository::delete);

        Screenshot screenshot = new Screenshot();
        screenshot.setStepResult(stepResult);
        screenshot.setFileName(fileName);
        screenshot.setContentType(contentType);
        screenshot.setData(data);

        return screenshotRepository.save(screenshot);
    }

    public Screenshot findById(UUID id) {
        return screenshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screenshot", id));
    }

    @Transactional
    public void delete(UUID id) {
        Screenshot screenshot = screenshotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screenshot", id));
        screenshotRepository.delete(screenshot);
    }
}
