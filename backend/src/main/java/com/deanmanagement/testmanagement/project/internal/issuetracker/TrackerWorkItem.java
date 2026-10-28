package com.deanmanagement.testmanagement.project.internal.issuetracker;

/**
 * A work item as requirement sync needs it (TM19): description already converted to plain text,
 * {@code priority} the tracker's number (1 highest; null when unset), {@code parentId} the id of the
 * parent work item whether or not that one was part of the same query.
 */
public record TrackerWorkItem(String id, String title, String description, String state, Integer priority,
                              String url, String parentId) {
}
