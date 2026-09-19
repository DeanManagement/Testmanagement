package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportLink;
import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CustomFieldValueMaps.class)
public abstract class BugReportMapper {

    @Mapping(target = "projectId", source = "bugReport.project.id")
    @Mapping(target = "testResultId", source = "bugReport.testResult.id")
    @Mapping(target = "testCaseTitle", source = "bugReport.testResult.testCase.title")
    @Mapping(target = "testCaseId", source = "bugReport.testResult.testCase.id")
    @Mapping(target = "testCaseKey", source = "bugReport.testResult.testCase.key")
    @Mapping(target = "stepResultId", source = "bugReport.stepResult.id")
    @Mapping(target = "stepNumber", expression = "java(stepNumber(bugReport.getStepResult()))")
    @Mapping(target = "testRunId", source = "bugReport.testRun.id")
    @Mapping(target = "testRunName", source = "bugReport.testRun.name")
    @Mapping(target = "assigneeId", source = "bugReport.assignee.id")
    @Mapping(target = "assigneeName", source = "bugReport.assignee.displayName")
    @Mapping(target = "duplicateOfId", source = "bugReport.duplicateOf.id")
    @Mapping(target = "duplicateOfKey", source = "bugReport.duplicateOf.key")
    @Mapping(target = "projectKey", source = "bugReport.project.key")
    @Mapping(target = "exploratorySessionId", source = "bugReport.exploratorySession.id")
    @Mapping(target = "exploratorySessionKey", source = "bugReport.exploratorySession.key")
    @Mapping(target = "customFields", expression = "java(CustomFieldValueMaps.toMap(bugReport.getCustomFieldValues()))")
    public abstract BugReportResponse toResponse(BugReport bugReport, String reporterName, String updatedByName);

    @Mapping(target = "testRunId", source = "testResult.testRun.id")
    @Mapping(target = "testRunKey", source = "testResult.testRun.key")
    @Mapping(target = "testRunName", source = "testResult.testRun.name")
    @Mapping(target = "testResultId", source = "testResult.id")
    @Mapping(target = "testCaseId", source = "testResult.testCase.id")
    @Mapping(target = "testCaseKey", source = "testResult.testCase.key")
    @Mapping(target = "stepResultId", source = "stepResult.id")
    @Mapping(target = "stepNumber", expression = "java(stepNumber(link.getStepResult()))")
    public abstract BugReportLinkResponse toLinkResponse(BugReportLink link);

    /** Steps are shown numbered from 1; a step without a recorded position has no number. */
    protected Integer stepNumber(StepResult stepResult) {
        return stepResult == null || stepResult.getPosition() == null ? null : stepResult.getPosition() + 1;
    }
}
