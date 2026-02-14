package com.deanmanagement.testmanagement.project.internal.dto.testCase;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class TestCaseMapper {

    public abstract TestCaseResponse toResponse(TestCase testCase);

    @Mapping(target = "imageId", expression = "java(step.getImage() != null ? step.getImage().getId() : null)")
    public abstract TestStepResponse toStepResponse(TestStep step);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "testSuites", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract TestCase toEntity(CreateTestCaseRequest request);
}
