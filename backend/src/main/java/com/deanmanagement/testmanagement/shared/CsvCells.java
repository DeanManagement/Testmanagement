package com.deanmanagement.testmanagement.shared;

/**
 * Spreadsheet formula injection (PRD-021): a cell starting with {@code = + - @}, a tab or a carriage
 * return runs as a formula when the CSV is opened in Excel or LibreOffice. A leading single quote
 * makes it text. Every CSV export that holds user-entered text goes through this.
 */
public final class CsvCells {

    private CsvCells() {
    }

    public static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + value;
        }
        return value;
    }
}
