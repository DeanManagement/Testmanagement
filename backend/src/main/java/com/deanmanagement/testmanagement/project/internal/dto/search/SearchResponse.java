package com.deanmanagement.testmanagement.project.internal.dto.search;

import java.util.List;

/** Search results grouped by entity type. */
public record SearchResponse(
        List<SearchHit> testCases,
        List<SearchHit> testRuns,
        List<SearchHit> bugReports,
        List<SearchHit> projects
) {
}
