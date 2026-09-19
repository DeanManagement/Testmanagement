package com.deanmanagement.testmanagement.project.internal.dto.testplan;

import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TestPlanMapper {

    @Mapping(target = "testRunCount", expression = "java(testPlan.getTestRuns().size())")
    @Mapping(target = "assigneeId", source = "assignee.id")
    @Mapping(target = "assigneeName", source = "assignee.displayName")
    @Mapping(target = "gate", expression = "java(gateOf(testPlan))")
    TestPlanResponse toResponse(TestPlan testPlan);

    default ReleaseGate gateOf(TestPlan plan) {
        return new ReleaseGate(plan.getGateMinPassRate(), plan.getGateMaxBlockerBugs(), plan.getGateMinCoverage(),
                plan.getGateMaxFlaky());
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "testRuns", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "gateMinPassRate", ignore = true)
    @Mapping(target = "gateMaxBlockerBugs", ignore = true)
    @Mapping(target = "gateMinCoverage", ignore = true)
    @Mapping(target = "gateMaxFlaky", ignore = true)
    TestPlan toEntity(CreateTestPlanRequest request);
}
