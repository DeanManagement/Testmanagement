package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CustomFieldValueMaps.class)
public abstract class BugReportMapper {

    @Mapping(target = "projectId", source = "bugReport.project.id")
    @Mapping(target = "testResultId", source = "bugReport.testResult.id")
    @Mapping(target = "testCaseTitle", source = "bugReport.testResult.testCase.title")
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
    public abstract BugReportResponse toResponse(BugReport bugReport, String reporterName);
}
