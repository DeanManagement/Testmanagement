package com.deanmanagement.testmanagement.project.internal.dto.search;

import java.util.UUID;

/** A single search result. {@code key} may be null (bug reports have no key). */
public record SearchHit(
        String type,
        UUID id,
        String key,
        String title,
        UUID projectId,
        String snippet
) {
}
