package com.deanmanagement.testmanagement.project.internal.dto.testrun;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestRunResponse;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import com.deanmanagement.testmanagement.project.internal.dto.effort.EffortSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {CustomFieldValueMaps.class, EffortSummary.class})
public abstract class TestRunMapper {

    @Mapping(target = "executorName", source = "executor.displayName")
    @Mapping(target = "completedByName", source = "completedBy.displayName")
    @Mapping(target = "testPlanId", source = "testPlan.id")
    @Mapping(target = "testPlanName", source = "testPlan.name")
    @Mapping(target = "allureReportId", source = "allureReport.id")
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectKey", source = "project.key")
    @Mapping(target = "customFields", expression = "java(CustomFieldValueMaps.toMap(testRun.getCustomFieldValues()))")
    @Mapping(target = "effort", expression = "java(EffortSummary.of(testRun.getResults()))")
    public abstract TestRunResponse toResponse(TestRun testRun);

    @Mapping(target = "testCaseId", source = "testCase.id")
    @Mapping(target = "testCaseTitle", source = "testCase.title")
    @Mapping(target = "estimateMinutes", source = "testCase.estimateMinutes")
    public abstract TestResultResponse toResultResponse(TestResult testResult);

    @Mapping(target = "testStepId", source = "testStep.id")
    @Mapping(target = "action", source = "testStep.action")
    @Mapping(target = "expectedResult", source = "testStep.expectedResult")
    @Mapping(target = "testData", source = "testStep.testData")
    @Mapping(target = "orderIndex", expression = "java(orderIndexOf(stepResult))")
    @Mapping(target = "sharedStepTitle", source = "testStep.sharedStep.title")
    @Mapping(target = "screenshotId", source = "screenshot.id")
    @Mapping(target = "stepImageId", expression = "java(stepResult.getTestStep() != null && stepResult.getTestStep().getImage() != null ? stepResult.getTestStep().getImage().getId() : null)")
    public abstract StepResultResponse toStepResultResponse(StepResult stepResult);

    /**
     * The result's own order (PRD-030): expanded steps come from several owners, each numbered from
     * 0. Rows without one (their step was gone before V62 backfilled positions) fall back to 0.
     */
    protected int orderIndexOf(StepResult stepResult) {
        if (stepResult.getPosition() != null) {
            return stepResult.getPosition();
        }
        return stepResult.getTestStep() != null ? stepResult.getTestStep().getOrderIndex() : 0;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "key", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "executor", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "reopenReason", ignore = true)
    @Mapping(target = "abortReason", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "results", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "testPlan", ignore = true)
    @Mapping(target = "allureReport", ignore = true)
    @Mapping(target = "customFieldValues", ignore = true)
    public abstract TestRun toEntity(CreateTestRunRequest request);
}
