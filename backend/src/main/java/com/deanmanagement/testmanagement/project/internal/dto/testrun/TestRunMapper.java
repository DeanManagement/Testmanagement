package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class TestRunMapper {

    @Mapping(target = "executorName", source = "executor.displayName")
    @Mapping(target = "completedByName", source = "completedBy.displayName")
    @Mapping(target = "testPlanId", source = "testPlan.id")
    @Mapping(target = "testPlanName", source = "testPlan.name")
    @Mapping(target = "allureReportId", source = "allureReport.id")
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectKey", source = "project.key")
    public abstract TestRunResponse toResponse(TestRun testRun);

    @Mapping(target = "testCaseId", source = "testCase.id")
    @Mapping(target = "testCaseTitle", source = "testCase.title")
    public abstract TestResultResponse toResultResponse(TestResult testResult);

    @Mapping(target = "testStepId", source = "testStep.id")
    @Mapping(target = "action", source = "testStep.action")
    @Mapping(target = "expectedResult", source = "testStep.expectedResult")
    @Mapping(target = "testData", source = "testStep.testData")
    @Mapping(target = "orderIndex", source = "testStep.orderIndex")
    @Mapping(target = "screenshotId", source = "screenshot.id")
    @Mapping(target = "stepImageId", expression = "java(stepResult.getTestStep() != null && stepResult.getTestStep().getImage() != null ? stepResult.getTestStep().getImage().getId() : null)")
    public abstract StepResultResponse toStepResultResponse(StepResult stepResult);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "executor", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "reopenReason", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "results", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "testPlan", ignore = true)
    @Mapping(target = "allureReport", ignore = true)
    public abstract TestRun toEntity(CreateTestRunRequest request);
}
