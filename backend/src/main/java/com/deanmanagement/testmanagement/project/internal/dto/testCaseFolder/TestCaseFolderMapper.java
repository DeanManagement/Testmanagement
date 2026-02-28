package com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class TestCaseFolderMapper {

    @Mapping(target = "parentId", expression = "java(folder.getParent() != null ? folder.getParent().getId() : null)")
    @Mapping(target = "testCaseCount", expression = "java(folder.getTestCases() != null ? folder.getTestCases().size() : 0)")
    @Mapping(target = "children", ignore = true)
    public abstract TestCaseFolderResponse toResponse(TestCaseFolder folder);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "testCases", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract TestCaseFolder toEntity(CreateTestCaseFolderRequest request);
}
