package com.deanmanagement.testmanagement.project.internal.dto.savedfilter;

import com.deanmanagement.testmanagement.project.internal.entity.SavedFilterView;

import java.util.UUID;

/**
 * @param mine     the caller owns it
 * @param editable the caller may rename, share or delete it: its owner, or a project ADMIN for a
 *                 shared one
 */
public record SavedFilterResponse(
        UUID id,
        String name,
        SavedFilterView view,
        String queryString,
        boolean shared,
        boolean mine,
        boolean editable
) {
}
