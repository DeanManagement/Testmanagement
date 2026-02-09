package com.deanmanagement.testmanagement.dto;

import com.deanmanagement.testmanagement.entity.Project;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    @Mapping(target = "key", ignore = true)
    @Mapping(target = "nextTestCaseNumber", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "testCases", ignore = true)
    @Mapping(target = "testSuites", ignore = true)
    @Mapping(target = "testRuns", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Project toEntity(CreateProjectRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateProjectRequest request, @MappingTarget Project project);
}
