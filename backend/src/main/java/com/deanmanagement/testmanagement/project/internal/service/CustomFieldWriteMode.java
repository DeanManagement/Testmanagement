package com.deanmanagement.testmanagement.project.internal.service;

/**
 * Who is writing custom field values (PRD-035 §3.3). A person filling in a form must satisfy
 * {@code required} fields; CI uploads, imports, clones and agents never fail because an admin
 * made a field required after they were written.
 */
public enum CustomFieldWriteMode {
    INTERACTIVE,
    MACHINE
}
