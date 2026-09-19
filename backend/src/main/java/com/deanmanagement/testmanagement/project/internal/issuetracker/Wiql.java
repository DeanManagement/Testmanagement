package com.deanmanagement.testmanagement.project.internal.issuetracker;

/**
 * Builds the WIQL for a work item search (PRD-026 §3.3). WIQL has no parameter binding, so the
 * user's text becomes part of the query: it is cut to a bounded length, stripped of control
 * characters and has every single quote doubled, which is how WIQL escapes one inside a string
 * literal. The text can then only ever be the inside of that literal.
 */
final class Wiql {

    static final int MAX_QUERY_LENGTH = 200;

    private Wiql() {
    }

    /** Bugs and issues of the project whose title contains the text, or whose id it is. */
    static String search(String workItemType, String query) {
        String text = literal(query);
        String idClause = query != null && query.strip().matches("\\d{1,9}")
                ? " OR [System.Id] = " + query.strip() : "";
        return "SELECT [System.Id] FROM WorkItems"
                + " WHERE [System.TeamProject] = @project"
                + " AND [System.WorkItemType] IN (" + literal(workItemType) + ", 'Bug', 'Issue')"
                + " AND ([System.Title] CONTAINS " + text + idClause + ")"
                + " ORDER BY [System.ChangedDate] DESC";
    }

    /** A WIQL string literal holding exactly {@code value}. */
    static String literal(String value) {
        String text = value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").strip();
        if (text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        return "'" + text.replace("'", "''") + "'";
    }
}
