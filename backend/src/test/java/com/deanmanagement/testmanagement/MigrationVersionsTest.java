package com.deanmanagement.testmanagement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway resolves {@code db/migration} and {@code db/specific/{vendor}} into a single timeline, so
 * a version number has to be unique across BOTH. Reusing one aborts startup with "Found more than
 * one migration with version N".
 *
 * <p>Nothing caught that before: the vendor migrations only load when the vendor is postgresql, so
 * the H2 test suite never sees them, and the dev profile used to load only db/migration. A
 * duplicate therefore stayed invisible until a production container tried to boot — which is
 * precisely what happened with two V38s. This test reads the files directly, so it does not care
 * which database is in play.
 */
class MigrationVersionsTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.*\\.sql$");

    private static final List<String> LOCATIONS =
            List.of("db/migration", "db/specific/postgresql", "db/specific/h2");

    @Test
    void everyMigrationVersionIsUniqueAcrossAllLocations() throws Exception {
        Map<String, List<String>> byVersion = new LinkedHashMap<>();

        for (String location : LOCATIONS) {
            for (Path file : listFiles(location)) {
                Matcher matcher = VERSIONED.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    byVersion.computeIfAbsent(matcher.group(1), v -> new ArrayList<>())
                            .add(location + "/" + file.getFileName());
                }
            }
        }

        List<String> duplicates = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "V" + entry.getKey() + " -> " + entry.getValue())
                .toList();

        assertThat(duplicates)
                .as("migration versions must be unique across db/migration and db/specific/*, "
                        + "because Flyway merges them into one timeline")
                .isEmpty();
        assertThat(byVersion).as("no migrations found — is the classpath layout still right?")
                .isNotEmpty();
    }

    private static List<Path> listFiles(String location) throws IOException, URISyntaxException {
        URL url = MigrationVersionsTest.class.getClassLoader().getResource(location);
        if (url == null) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            return files.toList();
        }
    }
}
