package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldValue;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseParameterSetRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Copies a test case (TM09): its wording, steps with their images and shared-step references, labels,
 * folder, custom fields, parameter sets and attachments, its steps' files included (TM05). The copy
 * is a new case of its own — a new key, DRAFT, no approval, no history, no results and no
 * requirement links, since none of those were earned by the copy.
 */
@Service
@RequiredArgsConstructor
public class TestCaseCopyService {

    /** The suffix when the caller names no title; the web UI sends a translated title instead. */
    static final String DEFAULT_TITLE_SUFFIX = " (copy)";
    private static final int MAX_TITLE_LENGTH = 255;

    private final TestCaseRepository testCaseRepository;
    private final TestCaseParameterSetRepository parameterSetRepository;
    private final ProjectSequenceService projectSequenceService;
    private final AttachmentService attachmentService;
    private final StepAttachmentService stepAttachmentService;
    private final AuditService auditService;
    private final TestCaseMapper testCaseMapper;

    /** {@code title} null or blank means the original's title plus {@value #DEFAULT_TITLE_SUFFIX}. */
    @Transactional
    public TestCaseResponse copy(UUID projectId, UUID sourceId, String title, UUID userId) {
        TestCase source = testCaseRepository.findByIdWithSteps(sourceId)
                .filter(tc -> tc.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", sourceId));

        TestCase copy = new TestCase();
        copy.setProject(source.getProject());
        copy.setTitle(title == null || title.isBlank() ? defaultTitle(source.getTitle()) : title.strip());
        copy.setDescription(source.getDescription());
        copy.setPreconditions(source.getPreconditions());
        copy.setPriority(source.getPriority());
        copy.setStatus(TestCaseStatus.DRAFT);
        copy.setEstimateMinutes(source.getEstimateMinutes());
        copy.setLabels(new HashSet<>(source.getLabels()));
        copy.setFolder(source.getFolder());
        Map<TestStep, TestStep> stepCopies = new LinkedHashMap<>();
        source.getSteps().forEach(step -> stepCopies.put(step, copyStep(step, copy)));
        copy.getSteps().addAll(stepCopies.values());
        source.getCustomFieldValues().forEach(value -> copy.getCustomFieldValues().add(copyValue(value, copy)));
        copy.setKey(source.getProject().getKey() + "-" + projectSequenceService.nextTestCaseNumber(projectId));

        TestCase saved = testCaseRepository.save(copy);
        copyParameterSets(source.getId(), saved.getId());
        attachmentService.copyTestCaseAttachments(projectId, source, saved, userId);
        stepAttachmentService.copyStepFiles(projectId, stepCopies, userId);
        auditService.log(projectId, userId, AuditAction.CREATED, AuditEntityType.TEST_CASE,
                saved.getId(), saved.getTitle(), "Copied from " + source.getKey());
        return testCaseMapper.toResponse(saved);
    }

    /** Shortens the original so the suffix always fits the title column. */
    static String defaultTitle(String original) {
        int room = MAX_TITLE_LENGTH - DEFAULT_TITLE_SUFFIX.length();
        String base = original.length() > room ? original.substring(0, room) : original;
        return base + DEFAULT_TITLE_SUFFIX;
    }

    private static TestStep copyStep(TestStep source, TestCase owner) {
        TestStep step = new TestStep();
        step.setTestCase(owner);
        step.setOrderIndex(source.getOrderIndex());
        step.setAction(source.getAction());
        step.setExpectedResult(source.getExpectedResult());
        step.setTestData(source.getTestData());
        step.setUsesSharedStep(source.getUsesSharedStep());
        if (source.getImage() != null) {
            StepImage image = new StepImage();
            image.setFileName(source.getImage().getFileName());
            image.setContentType(source.getImage().getContentType());
            image.setData(source.getImage().getData());
            image.setTestStep(step);
            step.setImage(image);
        }
        return step;
    }

    private static CustomFieldValue copyValue(CustomFieldValue source, TestCase owner) {
        CustomFieldValue value = new CustomFieldValue();
        value.setField(source.getField());
        value.setTestCase(owner);
        value.setValueText(source.getValueText());
        value.setValueNumber(source.getValueNumber());
        value.setValueDate(source.getValueDate());
        return value;
    }

    private void copyParameterSets(UUID sourceId, UUID targetId) {
        for (TestCaseParameterSet original : parameterSetRepository.findByTestCaseIdOrderByOrderIndexAsc(sourceId)) {
            TestCaseParameterSet set = new TestCaseParameterSet();
            set.setTestCaseId(targetId);
            set.setName(original.getName());
            set.setValuesJson(original.getValuesJson());
            set.setOrderIndex(original.getOrderIndex());
            parameterSetRepository.save(set);
        }
    }
}
