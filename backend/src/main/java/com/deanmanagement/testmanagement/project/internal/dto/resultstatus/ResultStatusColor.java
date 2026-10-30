package com.deanmanagement.testmanagement.project.internal.dto.resultstatus;

/** A status colour is a {@code #rrggbb} hex value, the format {@code <input type="color">} produces. */
public final class ResultStatusColor {

    public static final String PATTERN = "^#[0-9a-fA-F]{6}$";

    private ResultStatusColor() {
    }
}
