package com.deanmanagement.testmanagement.project.internal.dto.savedfilter;

import com.deanmanagement.testmanagement.project.internal.entity.SavedFilterView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param queryString the list's URL query string, without the leading {@code ?} */
public record CreateSavedFilterRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull SavedFilterView view,
        @NotNull @Size(max = 4000) String queryString,
        boolean shared
) {
}
