package com.deanmanagement.testmanagement.project.internal.dto;

import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TestRunMapper {

    TestRunResponse toResponse(TestRun testRun);

    @Mapping(target = "testCaseId", source = "testCase.id")
    @Mapping(target = "testCaseTitle", source = "testCase.title")
    TestResultResponse toResultResponse(TestResult testResult);

    @Mapping(target = "testStepId", source = "testStep.id")
    @Mapping(target = "action", source = "testStep.action")
    @Mapping(target = "expectedResult", source = "testStep.expectedResult")
    @Mapping(target = "orderIndex", source = "testStep.orderIndex")
    @Mapping(target = "screenshotId", source = "screenshot.id")
    StepResultResponse toStepResultResponse(StepResult stepResult);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "executor", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "results", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TestRun toEntity(CreateTestRunRequest request);
}
