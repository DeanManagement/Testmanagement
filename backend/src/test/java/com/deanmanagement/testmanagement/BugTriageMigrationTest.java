package com.deanmanagement.testmanagement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V66 (PRD-045 §3.1): existing bugs get keys per project in creation order, the counter continues
 * after them, and WONTFIX becomes CLOSED with a WONT_FIX resolution.
 *
 * <p>Runs on a fresh in-memory H2 by default; see {@link EnvironmentBackfillMigrationTest} for
 * pointing it at an empty PostgreSQL database.
 */
class BugTriageMigrationTest {

    private static final String URL = System.getProperty("migration-test.url",
            "jdbc:h2:mem:bugtriage-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    private static final String USER = System.getProperty("migration-test.user", "sa");
    private static final String PASSWORD = System.getProperty("migration-test.password", "");
    private static final String VERSION_BEFORE = "65";

    private final UUID project = UUID.randomUUID();
    private final UUID otherProject = UUID.randomUUID();

    private Flyway flyway(String target) {
        String vendor = URL.startsWith("jdbc:postgresql") ? "postgresql" : "h2";
        return Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/specific/" + vendor)
                .target(target)
                .load();
    }

    @Test
    void backfillsKeysInCreationOrderAndMovesWontFixToAResolution() throws SQLException {
        flyway(VERSION_BEFORE).migrate();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement sql = connection.createStatement()) {
            sql.execute(project(project, "TRI"));
            sql.execute(project(otherProject, "OTH"));
            sql.execute(bug(project, "second", "OPEN", "2026-02-01 10:00:00"));
            sql.execute(bug(project, "first", "WONTFIX", "2026-01-01 10:00:00"));
            sql.execute(bug(project, "third", "CLOSED", "2026-03-01 10:00:00"));
            sql.execute(bug(otherProject, "elsewhere", "IN_PROGRESS", "2026-01-15 10:00:00"));

            flyway("latest").migrate();

            assertThat(strings(sql, "SELECT bug_key FROM bug_reports WHERE project_id = '" + project
                    + "' ORDER BY created_at")).containsExactly("TRI-BUG-1", "TRI-BUG-2", "TRI-BUG-3");
            assertThat(strings(sql, "SELECT bug_key FROM bug_reports WHERE project_id = '" + otherProject + "'"))
                    .containsExactly("OTH-BUG-1");
            assertThat(strings(sql, "SELECT next_bug_number FROM projects WHERE id = '" + project + "'"))
                    .containsExactly("4");
            assertThat(strings(sql, "SELECT status || '/' || resolution FROM bug_reports WHERE title = 'first'"))
                    .containsExactly("CLOSED/WONT_FIX");
            assertThat(strings(sql, "SELECT status || '/' || resolution FROM bug_reports WHERE title = 'third'"))
                    .as("closed without a recorded reason").containsExactly("CLOSED/FIXED");
            assertThat(strings(sql, "SELECT status FROM bug_reports WHERE title = 'second' AND resolution IS NULL"))
                    .as("open bugs stay open, without a resolution").containsExactly("OPEN");
        }
    }

    private static String project(UUID id, String key) {
        return "INSERT INTO projects (id, name, project_key, created_at, updated_at) VALUES ('" + id + "', '"
                + key + "', '" + key + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String bug(UUID projectId, String title, String status, String createdAt) {
        return "INSERT INTO bug_reports (id, title, priority, status, project_id, created_at, updated_at) VALUES ('"
                + UUID.randomUUID() + "', '" + title + "', 'HIGH', '" + status + "', '" + projectId + "', TIMESTAMP '"
                + createdAt + "', CURRENT_TIMESTAMP)";
    }

    private static List<String> strings(Statement sql, String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rows = sql.executeQuery(query)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
