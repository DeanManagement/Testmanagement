package com.deanmanagement.testmanagement.project.internal.entity;

/** The value type of a custom field; fixed once the field holds values (PRD-035 §4). */
public enum CustomFieldType {
    TEXT,
    NUMBER,
    DATE,
    SELECT,
    MULTI_SELECT;

    public boolean hasOptions() {
        return this == SELECT || this == MULTI_SELECT;
    }
}
