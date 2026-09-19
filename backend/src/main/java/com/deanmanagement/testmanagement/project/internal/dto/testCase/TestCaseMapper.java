package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring", imports = CustomFieldValueMaps.class)
public abstract class TestCaseMapper {

    @Mapping(target = "folderId", expression = "java(testCase.getFolder() != null ? testCase.getFolder().getId() : null)")
    @Mapping(target = "customFields", expression = "java(CustomFieldValueMaps.toMap(testCase.getCustomFieldValues()))")
    public abstract TestCaseResponse toDetailResponse(TestCase testCase, Long medianActualMs,
                                                      List<AttachmentSummary> attachments);

    /** Without the median or attachments, which cost queries and are only read where they are shown. */
    public TestCaseResponse toResponse(TestCase testCase) {
        return toDetailResponse(testCase, null, null);
    }

    @Mapping(target = "imageId", expression = "java(step.getImage() != null ? step.getImage().getId() : null)")
    @Mapping(target = "sharedStepId", source = "usesSharedStep.id")
    @Mapping(target = "sharedStepTitle", source = "usesSharedStep.title")
    @Mapping(target = "expandedSteps", expression = "java(expandedStepsOf(step))")
    public abstract TestStepResponse toStepResponse(TestStep step);

    /** A reference's block steps in order; null for a local step. Blocks do not nest, so this ends. */
    protected List<TestStepResponse> expandedStepsOf(TestStep step) {
        if (step.getUsesSharedStep() == null) {
            return null;
        }
        return step.getUsesSharedStep().getSteps().stream()
                .sorted(Comparator.comparingInt(TestStep::getOrderIndex))
                .map(this::toStepResponse)
                .toList();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "folder", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "testSuites", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "customFieldValues", ignore = true)
    public abstract TestCase toEntity(CreateTestCaseRequest request);
}
