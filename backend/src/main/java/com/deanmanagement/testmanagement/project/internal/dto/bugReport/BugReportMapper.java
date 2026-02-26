package com.deanmanagement.testmanagement.project.internal.dto.bugReport;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class BugReportMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "testResultId", source = "testResult.id")
    @Mapping(target = "testCaseTitle", source = "testResult.testCase.title")
    @Mapping(target = "testRunId", source = "testRun.id")
    @Mapping(target = "testRunName", source = "testRun.name")
    @Mapping(target = "assigneeId", source = "assignee.id")
    @Mapping(target = "assigneeName", source = "assignee.displayName")
    @Mapping(target = "reporterName", ignore = true)
    public abstract BugReportResponse toResponse(BugReport bugReport);
}
