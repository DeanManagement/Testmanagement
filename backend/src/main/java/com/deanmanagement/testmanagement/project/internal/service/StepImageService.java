package com.deanmanagement.testmanagement.project.internal.service;

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

    @Transactional
    public StepImage upload(UUID testStepId, String fileName, String contentType, byte[] data) {
        TestStep testStep = testStepRepository.findById(testStepId)
                .orElseThrow(() -> new ResourceNotFoundException("TestStep", testStepId));

        stepImageRepository.findByTestStepId(testStepId)
                .ifPresent(stepImageRepository::delete);

        StepImage image = new StepImage();
        image.setTestStep(testStep);
        image.setFileName(fileName);
        image.setContentType(contentType);
        image.setData(data);

        return stepImageRepository.save(image);
    }

    public StepImage findById(UUID id) {
        return stepImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StepImage", id));
    }

    @Transactional
    public void delete(UUID id) {
        StepImage image = stepImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StepImage", id));
        stepImageRepository.delete(image);
    }
}
