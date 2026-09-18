package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CustomFieldValueMaps.class)
public abstract class TestCaseMapper {

    @Mapping(target = "folderId", expression = "java(testCase.getFolder() != null ? testCase.getFolder().getId() : null)")
    @Mapping(target = "customFields", expression = "java(CustomFieldValueMaps.toMap(testCase.getCustomFieldValues()))")
    public abstract TestCaseResponse toDetailResponse(TestCase testCase, Long medianActualMs);

    /** Without the median, which costs a query per case and is only shown on the detail page. */
    public TestCaseResponse toResponse(TestCase testCase) {
        return toDetailResponse(testCase, null);
    }

    @Mapping(target = "imageId", expression = "java(step.getImage() != null ? step.getImage().getId() : null)")
    public abstract TestStepResponse toStepResponse(TestStep step);

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
