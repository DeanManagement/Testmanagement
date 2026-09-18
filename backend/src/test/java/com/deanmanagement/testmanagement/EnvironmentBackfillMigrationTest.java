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
 * V56 backfills the environment catalogue from the free-text columns (PRD-032 §3.1): one entry
 * per project and case/whitespace-insensitive name, every reference set, blanks cleared.
 *
 * <p>Runs on a fresh in-memory H2 by default. Point it at an EMPTY PostgreSQL database to check
 * the other vendor: {@code -Dmigration-test.url=jdbc:postgresql://localhost:5432/scratch
 * -Dmigration-test.user=... -Dmigration-test.password=...}.
 */
class EnvironmentBackfillMigrationTest {

    private static final String URL = System.getProperty("migration-test.url",
            "jdbc:h2:mem:backfill-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    private static final String USER = System.getProperty("migration-test.user", "sa");
    private static final String PASSWORD = System.getProperty("migration-test.password", "");
    private static final String VERSION_BEFORE = "55";

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
    void backfillsOneEnvironmentPerNormalisedNameAndLinksEveryRow() throws SQLException {
        flyway(VERSION_BEFORE).migrate();
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement sql = connection.createStatement()) {
            insertFixtures(sql);

            flyway("latest").migrate();

            List<String> names = strings(sql, "SELECT name FROM project_environments WHERE project_id = '"
                    + project + "' ORDER BY name_normalized");
            assertThat(names).hasSize(2);
            assertThat(names.get(0)).isEqualToIgnoringCase("prod");
            assertThat(names.get(1)).isEqualToIgnoringCase("staging");
            assertThat(strings(sql, "SELECT name FROM project_environments WHERE project_id = '"
                    + otherProject + "'")).containsExactly("staging");

            assertThat(count(sql, "SELECT COUNT(*) FROM test_runs WHERE environment IS NOT NULL AND environment_id IS NULL"))
                    .as("every named run is linked").isZero();
            assertThat(count(sql, "SELECT COUNT(*) FROM bug_reports WHERE environment IS NOT NULL AND environment_id IS NULL"))
                    .as("every named bug is linked").isZero();
            assertThat(count(sql, "SELECT COUNT(*) FROM test_runs r JOIN project_environments e ON e.id = r.environment_id "
                    + "WHERE r.environment <> e.name OR e.project_id <> r.project_id"))
                    .as("copies are canonical and never cross projects").isZero();
            assertThat(count(sql, "SELECT COUNT(*) FROM test_runs WHERE name = 'blank' AND environment IS NULL"))
                    .as("whitespace-only names are cleared").isEqualTo(1);
            assertThat(count(sql, "SELECT COUNT(*) FROM bug_reports b JOIN project_environments e ON e.id = b.environment_id "
                    + "WHERE e.name_normalized = 'prod'")).isEqualTo(1);
        }
    }

    private void insertFixtures(Statement sql) throws SQLException {
        sql.execute(project(project, "BF1"));
        sql.execute(project(otherProject, "BF2"));
        sql.execute(run(project, "a", "'Staging'"));
        sql.execute(run(project, "b", "' staging  '"));
        sql.execute(run(project, "c", "'STAGING'"));
        sql.execute(run(project, "d", "'prod'"));
        sql.execute(run(project, "blank", "'   '"));
        sql.execute(run(project, "none", "NULL"));
        sql.execute(run(otherProject, "e", "'staging'"));
        sql.execute("INSERT INTO bug_reports (id, title, priority, status, environment, project_id, created_at, updated_at) "
                + "VALUES ('" + UUID.randomUUID() + "', 'bug', 'HIGH', 'OPEN', ' Prod', '" + project
                + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    private static String project(UUID id, String key) {
        return "INSERT INTO projects (id, name, project_key, created_at, updated_at) VALUES ('" + id + "', '"
                + key + "', '" + key + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String run(UUID projectId, String name, String environmentLiteral) {
        return "INSERT INTO test_runs (id, test_run_key, name, environment, status, project_id, created_at, updated_at) "
                + "VALUES ('" + UUID.randomUUID() + "', 'BF-Run-" + name + "', '" + name + "', " + environmentLiteral
                + ", 'PLANNED', '" + projectId + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
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

    private static long count(Statement sql, String query) throws SQLException {
        try (ResultSet rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
