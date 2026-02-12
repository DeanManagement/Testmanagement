package com.deanmanagement.testmanagement.project.internal.dto.testSuite;

import com.deanmanagement.testmanagement.project.internal.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestSuite;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TestSuiteMapper {

    TestSuiteResponse toResponse(TestSuite testSuite);

    TestSuiteResponse.TestCaseSummary toTestCaseSummary(TestCase testCase);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "testCases", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TestSuite toEntity(CreateTestSuiteRequest request);
}
