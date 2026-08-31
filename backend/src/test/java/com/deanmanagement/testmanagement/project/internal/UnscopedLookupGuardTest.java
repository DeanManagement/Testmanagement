package com.deanmanagement.testmanagement.project.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-027 §3.5 — a narrow static guard against the bug shape that has now been found three times.
 *
 * <h2>Why this is not the ArchUnit rule the PRD proposed</h2>
 *
 * <p>PRD-027 §3.5 proposed failing "any service method that resolves a caller-supplied id through a
 * bare {@code findById}". That rule cannot be written honestly. Whether a {@code findById} is safe
 * depends on what happens to its <em>result</em> — {@code findById(id).filter(x ->
 * x.getProject().getId().equals(projectId))} is correct and is the idiom used throughout these
 * services, while the identical call without the filter is the bug. That is a dataflow property,
 * and ArchUnit sees static structure. A rule broad enough to catch the real cases flags dozens of
 * correct ones, acquires a suppression list, and stops meaning anything; a rule narrow enough to
 * stay quiet catches nothing. Shipping it would have been worse than shipping nothing, because it
 * would look like coverage.
 *
 * <p>So this guards exactly one call shape, chosen because it is the only one where the rule can be
 * zero-tolerance: <b>{@code findAllById(..)} has no project-aware form.</b> Every use resolves
 * caller-supplied ids across the whole table, and a scoped alternative
 * ({@code findByIdInAndProjectId}) already exists. It is the exact call behind the hole PRD-025 §8
 * fixed in {@code TestSuiteService} and the one PRD-027 §3.5 found again in {@code TestRunService}.
 *
 * <h2>The rule that was tried and deleted</h2>
 *
 * <p>A companion rule on {@code userService.findEntityById(..)} was written and thrown away. It
 * produced nine hits and <em>none</em> of them was a bug: three were the {@code
 * requireProjectMember} helpers that exist to make the call safe, two resolved the authenticated
 * actor to stamp {@code completedBy}, and the rest were display-name lookups. A rule that is wrong
 * every time it fires trains people to add allowlist entries without reading, which is worse than
 * having no rule — so the assignee/executor cases are covered behaviourally instead, in {@code
 * ProjectScopedChildIdApiTest}.
 *
 * <p>That test is the safety net and the place a new child-id parameter should acquire its
 * assertion. This one is a cheap tripwire on a single call.
 */
class UnscopedLookupGuardTest {

    private static final Path SERVICES = Path.of(
            "src/main/java/com/deanmanagement/testmanagement/project/internal/service");

    /**
     * Empty, and worth keeping empty. Every use found when this test was written was replaced with
     * a scoped query rather than allowlisted — including two in {@code TestCaseService} that were
     * safe, because their safety lived in a size check further down the method instead of in the
     * lookup, and that is precisely the arrangement that failed three times.
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of();

    @Test
    void noServiceResolvesCallerSuppliedIdsWithFindAllById() throws IOException {
        List<String> offenders = scan("findAllById(", ALLOWED);

        assertThat(offenders)
                .as("""
                    findAllById has no project-scoped form, so it resolves ids across every \
                    project. Use findByIdInAndProjectId and fail the call on an unknown id — see \
                    TestRunService.resolveTestCases. Filtering by project after the fact is not \
                    good enough: it works until someone edits the method and drops the filter.""")
                .isEmpty();
    }

    /**
     * Returns {@code File.java:12} style locations for every occurrence of {@code needle} outside a
     * comment, skipping any that sits inside an allowlisted method.
     */
    private static List<String> scan(String needle, Map<String, Set<String>> allowlist)
            throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.list(SERVICES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fileName = file.getFileName().toString();
                Set<String> allowedMethods = allowlist.getOrDefault(fileName, Set.of());

                List<String> lines = Files.readAllLines(file);
                String enclosingMethod = "";

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String trimmed = line.strip();

                    String declared = methodNameOf(line);
                    if (declared != null) {
                        enclosingMethod = declared;
                    }

                    // Comments explain these calls in several places; only real code counts.
                    if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                        continue;
                    }
                    if (line.contains(needle) && !allowedMethods.contains(enclosingMethod)) {
                        offenders.add(fileName + ":" + (i + 1) + " in " + enclosingMethod + "()");
                    }
                }
            }
        }
        return offenders;
    }

    /** Crude but sufficient: the name from a {@code public|private ... name(} declaration. */
    private static String methodNameOf(String line) {
        String trimmed = line.strip();
        if (!trimmed.startsWith("public ") && !trimmed.startsWith("private ")
                && !trimmed.startsWith("protected ")) {
            return null;
        }
        int paren = trimmed.indexOf('(');
        if (paren < 0) {
            return null;
        }
        String beforeParen = trimmed.substring(0, paren);
        int lastSpace = beforeParen.lastIndexOf(' ');
        return lastSpace < 0 ? null : beforeParen.substring(lastSpace + 1);
    }
}
