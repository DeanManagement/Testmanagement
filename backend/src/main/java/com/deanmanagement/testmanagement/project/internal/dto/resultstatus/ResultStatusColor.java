package com.deanmanagement.testmanagement.project.internal.dto.resultstatus;

/** A status colour is a {@code #rrggbb} hex value, the format {@code <input type="color">} produces. */
final class ResultStatusColor {

    static final String PATTERN = "^#[0-9a-fA-F]{6}$";

    private ResultStatusColor() {
    }
}
